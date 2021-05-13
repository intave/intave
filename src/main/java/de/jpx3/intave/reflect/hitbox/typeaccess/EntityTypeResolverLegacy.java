package de.jpx3.intave.reflect.hitbox.typeaccess;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.lib.asm.ClassReader;
import de.jpx3.intave.lib.asm.Opcodes;
import de.jpx3.intave.lib.asm.tree.*;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.tools.annotate.Nullable;
import net.minecraft.server.v1_13_R2.EntityTypes;
import net.minecraft.server.v1_13_R2.IRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.jpx3.intave.reflect.hitbox.typeaccess.DualEntityTypeAccess.ENTITY_ID_LOOKUP;
import static de.jpx3.intave.reflect.hitbox.typeaccess.ServerClassByteResolver.pollBytesOf;

@PatchyAutoTranslation
final class EntityTypeResolverLegacy {
  // <ClassName, EntityID>
  private static final Map<String, Integer> classNameToIDMap = new HashMap<>();
  private static final Map<ClassNode, ClassNode> hierarchy = new HashMap<>();
  private static final List<ClassNode> classNodes = new ArrayList<>();

  static void pollTo(
    Map<Integer, HitBoxBoundaries> entityHitBoxMap,
    Map<Integer, String> entityNameMap
  ) {
    linkEntityToIdMap();

    List<byte[]> classByteMap = pollBytesOf(entityPrefixOfVersion());
    acceptClassNodes(classByteMap);
    buildHierarchy();
    loadBoundaries(entityHitBoxMap);
    loadEntityNames(entityNameMap);
  }

  private static String entityPrefixOfVersion() {
    String path = ReflectiveAccess.NMS_PREFIX + ".Entity";
    return path.replace(".", "/");
  }

  private static void linkEntityToIdMap() {
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      linkEntityToMap1_13();
    } else if (MinecraftVersions.VER1_11_0.atOrAbove()) {
      linkEntityToMap1_12();
    } else {
      linkEntityToMapLegacy();
    }
  }

  @PatchyAutoTranslation
  private static void linkEntityToMap1_13() {
    for (int id = 0; id < ENTITY_ID_LOOKUP; id++) {
      EntityTypes<?> entity = IRegistry.ENTITY_TYPE.fromId(id);
      if (entity != null) {
        Class<?> entityClass = entity.c();
        classNameToIDMap.put(toInternalName(entityClass.getCanonicalName()), id);
      }
    }
  }

  @PatchyAutoTranslation
  private static void linkEntityToMap1_12() {
    for (int id = 0; id < ENTITY_ID_LOOKUP; id++) {
      Class<? extends net.minecraft.server.v1_12_R1.Entity> entityClass = net.minecraft.server.v1_12_R1.EntityTypes.b.getId(id);
      if (entityClass != null) {
        classNameToIDMap.put(toInternalName(entityClass.getCanonicalName()), id);
      }
    }
  }

  private static void linkEntityToMapLegacy() {
    try {
      Class<?> entityTypes = ReflectiveAccess.lookupServerClass("EntityTypes");
      //noinspection unchecked
      Map<Integer, Class<?>> entityToIdMap = (Map<Integer, Class<?>>) ReflectiveAccess.ensureAccessible(entityTypes.getDeclaredField("e")).get(null);
      entityToIdMap.forEach(((entityID, aClass) -> classNameToIDMap.put(toInternalName(aClass.getCanonicalName()), entityID)));
    } catch (Exception e) {
      throw new IntaveInternalException(e);
    }
  }

  private static String toInternalName(String className) {
    return className.replace(".", "/");
  }

  private static void acceptClassNodes(List<byte[]> classBytes) {
    classBytes.forEach(EntityTypeResolverLegacy::acceptClassBytes);
  }

  private static void acceptClassBytes(byte[] bytes) {
    ClassReader classReader = new ClassReader(bytes);
    ClassNode classNode = new ClassNode();
    classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
    classNodes.add(classNode);
  }

  private static void buildHierarchy() {
    for (ClassNode classNode : classNodes) {
      buildHierarchyOf(classNode);
    }
  }

  private static final String ENTITY_CLASS = toInternalName(ReflectiveAccess.lookupServerClass("Entity").getCanonicalName());

  private static void buildHierarchyOf(ClassNode classNode) {
    String superClass = classNode.superName;
    ClassNode superClassNode = classNodeByName(superClass);
    hierarchy.put(classNode, superClassNode != null ? superClassNode : classNodeByName(ENTITY_CLASS));
  }

  @Nullable
  private static ClassNode classNodeByName(String className) {
    return classNodes
      .stream()
      .filter(classNode -> classNode.name.equals(className))
      .findFirst()
      .orElse(null);
  }

  private static void loadBoundaries(
    Map<Integer, HitBoxBoundaries> entityHitBoxMap
  ) {
    for (ClassNode classNode : classNodes) {
      if (entityClass(classNode)) {
        registerEntityBoxesOf(classNode, entityHitBoxMap);
      }
    }
  }

  private static boolean entityClass(ClassNode classNode) {
    return classNameToIDMap.containsKey(classNode.name);
  }

  private static void registerEntityBoxesOf(
    ClassNode classNode,
    Map<Integer, HitBoxBoundaries> entityHitBoxMap
  ) {
    String originalClass = classNode.name;
    int leeway = 10;
    LOOP:
    while (true) {
      if (--leeway < 0) {
        registerWithSize(entityHitBoxMap, originalClass, 0.6f, 1.8f);
        break;
      }
      List<MethodNode> constructors = constructorsOf(classNode.methods);
      for (MethodNode constructor : constructors) {
        EntitySizeResult result = tryResolveSizeOf(classNode.name, entityHitBoxMap, constructor.instructions);
        if (result == EntitySizeResult.RESOLVED) {
          break LOOP;
        }
      }
      classNode = hierarchy.get(classNode);
    }
  }

  private static EntitySizeResult tryResolveSizeOf(
    String className,
    Map<Integer, HitBoxBoundaries> entityHitBoxMap,
    InsnList instructions
  ) {
    for (AbstractInsnNode instruction : instructions) {
      if (!(instruction instanceof MethodInsnNode)) {
        continue;
      }
      MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
      if (entitySizeCall(methodInsnNode)) {
        Float height = floatValueOf(instruction.getPrevious());
        Float width = floatValueOf(instruction.getPrevious().getPrevious());
        if (height == null || width == null) {
          break;
        }
        registerWithSize(entityHitBoxMap, className, width, height);
        return EntitySizeResult.RESOLVED;
      }
    }
    return EntitySizeResult.UNAVAILABLE;
  }

  private static void registerWithSize(
    Map<Integer, HitBoxBoundaries> entityHitBoxMap,
    String className,
    float width,
    float height
  ) {
    HitBoxBoundaries boundaries = HitBoxBoundaries.of(width, height);
    entityHitBoxMap.put(entityIDOfClass(className), boundaries);
  }

  private static int entityIDOfClass(String className) {
    if (!classNameToIDMap.containsKey(className)) {
      return -1;
    }
    return classNameToIDMap.get(className);
  }

  private static boolean entitySizeCall(MethodInsnNode methodInsnNode) {
    return methodInsnNode.name.equals("setSize") && methodInsnNode.desc.equals("(FF)V");
  }

  private static Float floatValueOf(AbstractInsnNode instruction) {
    int opcode = instruction.getOpcode();
    if (opcode <= Opcodes.FCONST_2) {
      return (float) opcode - Opcodes.FCONST_0;
    }
    return instruction instanceof LdcInsnNode
      ? (float) ((LdcInsnNode) instruction).cst
      : null;
  }

  private static List<MethodNode> constructorsOf(List<MethodNode> methodNodes) {
    return methodNodes
      .stream()
      .filter(method -> method.name.equals("<init>"))
      .collect(Collectors.toList());
  }

  private static void loadEntityNames(Map<Integer, String> entityNameMap) {
    classNameToIDMap.forEach((entityName, entityID) -> entityNameMap.put(entityID, convertClassNameToEntityName(entityName)));
  }

  private static String convertClassNameToEntityName(String className) {
    String actualClassName = className.substring(className.lastIndexOf('/') + 1);
    return actualClassName.substring("Entity".length());
  }

  private enum EntitySizeResult {
    RESOLVED,
    UNAVAILABLE
  }
}