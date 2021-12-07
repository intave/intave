package de.jpx3.intave.klass.rewrite;

import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.klass.locate.Locate;
import de.jpx3.intave.lib.asm.ClassReader;
import de.jpx3.intave.lib.asm.ClassWriter;
import de.jpx3.intave.lib.asm.Handle;
import de.jpx3.intave.lib.asm.Type;
import de.jpx3.intave.lib.asm.tree.*;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.lib.asm.ClassReader.SKIP_FRAMES;
import static de.jpx3.intave.lib.asm.ClassWriter.COMPUTE_FRAMES;
import static de.jpx3.intave.lib.asm.Opcodes.*;
import static de.jpx3.intave.lib.asm.Type.OBJECT;

final class PatchyTranslator {
  public static final String TRANSLATION_MARKER_ANNOTATION_PATH = slashify(PatchyAutoTranslation.class.getName());
  public static final String CURRENT_SERVER_VERSION;

  static {
    String packageName = Bukkit.getServer().getClass().getPackage().getName();
    CURRENT_SERVER_VERSION = packageName.substring(packageName.lastIndexOf(".") + 1);
  }

  public static byte[] translateClass(byte[] inputBytes) {
    ClassNode classNode = classNodeOf(inputBytes);
    translateClassDependencies(classNode);
    processMethods(selectedMethodsIn(classNode));
    return byteArrayOf(classNode);
  }

  private static void translateClassDependencies(ClassNode classNode) {
    classNode.superName = translate(classNode.superName);
    String[] strings = classNode.interfaces.toArray(new String[0]);
    for (int i = 0; i < strings.length; i++) {
      String newName = translate(strings[i]);
      strings[i] = newName;
    }
    classNode.interfaces = Arrays.stream(strings).collect(Collectors.toList());
  }

  private static void processMethods(List<MethodNode> methodNodes) {
    for (MethodNode methodNode : methodNodes) {
      processMethod(methodNode);
    }
  }

  private static void processMethod(MethodNode methodNode) {
    processMethodDescription(methodNode);
    processMethodInstructions(methodNode);
  }

  private static void processMethodDescription(MethodNode methodNode) {
    PatchyTranslationConfiguration configuration = PatchyTranslationConfiguration.createFrom(methodNode);
    if (!configuration.translateParameters()) {
      return;
    }
    if (!configuration.translateEverything()) {
      throw new IllegalStateException("Custom translations not yet supported for parameters");
    }
    methodNode.signature = null;
    methodNode.desc = translate(methodNode.desc);
  }

  @Native
  private static void processMethodInstructions(MethodNode methodNode) {
    PatchyTranslationConfiguration configuration = PatchyTranslationConfiguration.createFrom(methodNode);
    for (AbstractInsnNode instruction : methodNode.instructions) {
      if (instruction instanceof MethodInsnNode) {
        MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
        InstructionTarget originalInstruction = InstructionTarget.methodInstructionTarget(
          methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc
        ), instructionTarget = originalInstruction;
        instructionTarget = process(instructionTarget, configuration);
        methodInsnNode.owner = instructionTarget.owner;
        methodInsnNode.name = instructionTarget.name;
        methodInsnNode.desc = instructionTarget.desc;
      } else if (instruction instanceof LdcInsnNode) {
        LdcInsnNode ldcInsnNode = (LdcInsnNode) instruction;
        Object cst = ldcInsnNode.cst;
        if (cst instanceof Type) {
          Type type = (Type) cst;
          if (type.getSort() == OBJECT) {
            ldcInsnNode.cst = typeTranslate(type);
          }
        }
      } else if (instruction instanceof TypeInsnNode) {
        TypeInsnNode typeInsnNode = (TypeInsnNode) instruction;
        typeInsnNode.desc = translate(typeInsnNode.desc);
      } else if (instruction instanceof FieldInsnNode) {
        FieldInsnNode fieldInsnNode = (FieldInsnNode) instruction;
        InstructionTarget instructionTarget = InstructionTarget.fieldInstructionTarget(
          fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc
        );
        instructionTarget = process(instructionTarget, configuration);
        fieldInsnNode.owner = instructionTarget.owner;
        fieldInsnNode.name = instructionTarget.name;
        fieldInsnNode.desc = instructionTarget.desc;
      } else if (instruction instanceof InvokeDynamicInsnNode) {
        InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) instruction;
        invokeDynamicInsnNode.desc = translate(invokeDynamicInsnNode.desc);
        for (Object bsmArg : invokeDynamicInsnNode.bsmArgs) {
          if(bsmArg instanceof Handle) {
            Handle arg = (Handle) bsmArg;
            int tag = arg.getTag();
            InstructionTarget instructionTarget;
            if (tag == H_PUTFIELD || tag == H_GETFIELD || tag == H_GETSTATIC || tag == H_PUTSTATIC) {
              instructionTarget = InstructionTarget.fieldInstructionTarget(
                arg.getOwner(), arg.getName(), arg.getDesc()
              );
            } else {
              instructionTarget = InstructionTarget.methodInstructionTarget(
                arg.getOwner(), arg.getName(), arg.getDesc()
              );
            }
            instructionTarget = process(instructionTarget, configuration);
            arg.setOwner(instructionTarget.owner);
            arg.setName(instructionTarget.name);
            arg.setDescriptor(instructionTarget.desc);
          }
        }
      }
    }
    methodNode.localVariables = null;
  }

  private static InstructionTarget process(InstructionTarget original, PatchyTranslationConfiguration configuration) {
    VersionMethodReference methodReference = configuration.resolveCustomMethodDescriptor(original.owner, original.name, original.desc);
    if (methodReference == null) {
      if (configuration.translateEverything()) {
        String name = original.name;
        if (original.type == InstructionTargetType.FIELD) {
          name = Locate.patchyFieldCovert(original.owner, name);
        }
        if (original.type == InstructionTargetType.METHOD) {
          String newName = Locate.patchyMethodCovert(original.owner, name, translate(original.desc));
//          if (name.equals("getBlock")) {
//            Thread.dumpStack();
//            System.out.println(name + " -> " + newName);
//          }
//          System.out.println(original.owner + name + original.desc + " -> " + original.owner + newName + original.desc);
          name = newName;
        }
        return InstructionTarget.methodInstructionTarget(translate(original.owner), name, translate(original.desc));
      }
      return original;
    } else {
      return InstructionTarget.from(methodReference);
    }
  }

  private static String translate(String input) {
    if (input.contains(".")) {
      throw new IllegalArgumentException("Input contains dot: " + input);
    }
    String output;
    if (input.startsWith("L") && input.endsWith(";")) { // is class descriptor
      output = typeTranslate(Type.getType(input)).getDescriptor();
    } else if (input.startsWith("(") && input.contains(")")) { // is method descriptor
      Type[] argumentTypes = Type.getArgumentTypes(input);
      Arrays.setAll(argumentTypes, i -> typeTranslate(argumentTypes[i]));
      Type returnType = typeTranslate(Type.getReturnType(input));
      output = Type.getMethodDescriptor(returnType, argumentTypes);
    } else {
      if (input.contains("craftbukkit")) {
        int versionBeginIndex = input.indexOf("/v") + 1;
        int versionEndIndex = input.indexOf("/", versionBeginIndex);
        if (versionBeginIndex <= 0 || versionEndIndex <= 0) {
          return input;
        }
        String extractedVersion = input.substring(versionBeginIndex, versionEndIndex);
        output = input.replace(extractedVersion, CURRENT_SERVER_VERSION);
      } else {
        output = Locate.patchyConvert(input);
      }
    }
    return output;
  }

  private static Type typeTranslate(Type input) {
    int dimensions = 0;
    if (input.getSort() == Type.ARRAY) {
      dimensions = input.getDimensions();
      input = input.getElementType();
    }
    if (input.getSort() == OBJECT) {
      input = Type.getObjectType(translate(input.getInternalName()));
    }
    return input.convertToArrayType(dimensions);
  }

  private static List<MethodNode> selectedMethodsIn(ClassNode classNode) {
    return classNode.methods.stream()
      .filter(PatchyTranslator::methodSelected)
      .collect(Collectors.toList());
  }

  @Native
  private static boolean methodSelected(MethodNode methodNode) {
    List<AnnotationNode> annotationNodes = new ArrayList<>();
    annotationNodes.addAll(methodNode.visibleAnnotations == null ? Collections.emptyList() : methodNode.visibleAnnotations);
    annotationNodes.addAll(methodNode.visibleTypeAnnotations == null ? Collections.emptyList() : methodNode.visibleTypeAnnotations);
    if (!annotationNodes.isEmpty()) {
      for (AnnotationNode visibleAnnotation : annotationNodes) {
        String annotationClassName = className(visibleAnnotation);
        if (annotationClassName.equals(TRANSLATION_MARKER_ANNOTATION_PATH)) {
          return true;
        }
      }
      return false;
    }
    return false;
  }

  private static String className(AnnotationNode annotationNode) {
    return slashify(Type.getType(annotationNode.desc).getClassName());
  }

  private static ClassNode classNodeOf(byte[] inputBytes) {
    ClassReader cr = new ClassReader(inputBytes);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, SKIP_FRAMES);
    return classNode;
  }

  private static byte[] byteArrayOf(ClassNode classNode) {
    ClassWriter classWriter = new ClassWriter(COMPUTE_FRAMES);
    classNode.accept(classWriter);
    return classWriter.toByteArray();
  }

  private static String slashify(String input) {
    return input.replace('.', '/');
  }

  private enum InstructionTargetType {
    METHOD, FIELD
  }

  private static class InstructionTarget {
    private final InstructionTargetType type;
    private final String owner;
    private final String name;
    private final String desc;

    private InstructionTarget(InstructionTargetType type, String owner, String name, String desc) {
      this.type = type;
      this.owner = owner;
      this.name = name;
      this.desc = desc;
    }

    public static InstructionTarget from(VersionMethodReference methodDescriptor) {
      return methodInstructionTarget(methodDescriptor.owner(), methodDescriptor.name(), methodDescriptor.description());
    }

    public static InstructionTarget methodInstructionTarget(String owner, String name, String desc) {
      return new InstructionTarget(InstructionTargetType.METHOD, owner, name, desc);
    }

    public static InstructionTarget fieldInstructionTarget(String owner, String name, String desc) {
      return new InstructionTarget(InstructionTargetType.FIELD, owner, name, desc);
    }

    public InstructionTargetType type() {
      return type;
    }

    public boolean isField() {
      return type == InstructionTargetType.FIELD;
    }

    public boolean isMethod() {
      return type == InstructionTargetType.METHOD;
    }

    @Override
    public String toString() {
      return "InstructionTarget{" +
        "type=" + type +
        ", name=" + owner + "#" + name + desc +
        '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InstructionTarget that = (InstructionTarget) o;

      if (type != that.type) return false;
      if (!owner.equals(that.owner)) return false;
      if (!name.equals(that.name)) return false;
      return desc.equals(that.desc);
    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + owner.hashCode();
      result = 31 * result + name.hashCode();
      result = 31 * result + desc.hashCode();
      return result;
    }
  }
}
