package de.jpx3.intave.patchy;

import de.jpx3.intave.lib.asm.ClassReader;
import de.jpx3.intave.lib.asm.ClassWriter;
import de.jpx3.intave.lib.asm.Type;
import de.jpx3.intave.lib.asm.tree.*;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.tools.annotate.Native;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class PatchyTranslator {
  public static final String TRANSLATION_MARKER_ANNOTATION_PATH = slashify(PatchyAutoTranslation.class.getName());
  public static final String CURRENT_SERVER_VERSION;

  static {
    String packageName = Bukkit.getServer().getClass().getPackage().getName();
    CURRENT_SERVER_VERSION =  packageName.substring(packageName.lastIndexOf(".") + 1);
  }

  @Native
  public static byte[] translateClass(byte[] inputBytes) {
    ClassNode classNode = classNodeOf(inputBytes);
//    System.out.println("Translating " + classNode.name);
    translateClassDependencies(classNode);
//    System.out.println("Translating methods..");
    processMethods(selectedMethodsIn(classNode));
/*
    System.out.println(classNode.name + " " + classNode.superName);
    for (MethodNode method : classNode.methods) {
      System.out.println(method.name);

      Textifier textifier;
      MethodVisitor methodVisitor = new TraceMethodVisitor(textifier = new Textifier());
      method.accept(methodVisitor);
      System.out.println(textifier.text);
    }*/

    return byteArrayOf(classNode);
  }

  @Native
  private static void translateClassDependencies(ClassNode classNode) {
    String newSuperName = translateDependency(classNode.superName);
//    System.out.println("Patched " + classNode.superName + " to " + newSuperName);
    classNode.superName = newSuperName;
    String[] strings = classNode.interfaces.toArray(new String[0]);
    for (int i = 0; i < strings.length; i++) {
      String newName = translateDependency(strings[i]);
//      System.out.println("Patched " + strings[i] + " to " + newName);
      strings[i] = newName;
    }
    classNode.interfaces = Arrays.stream(strings).collect(Collectors.toList());
  }

  private static String translateDependency(String original) {
    int versionBeginIndex = original.indexOf("/v") + 1;
    int versionEndIndex = original.indexOf("/", versionBeginIndex);
    if(versionBeginIndex <= 0 || versionEndIndex <= 0) {
      return original;
    }
    String extractedVersion = original.substring(versionBeginIndex, versionEndIndex);
    return original.replace(extractedVersion, CURRENT_SERVER_VERSION);
  }

  private static void processMethods(List<MethodNode> methodNodes) {
    for (MethodNode methodNode : methodNodes) {
//      System.out.println("Processing " + methodNode.name + methodNode.desc + "..");
      processMethod(methodNode);
    }
  }

  private static void processMethod(MethodNode methodNode) {
    processMethodDescription(methodNode);
    processMethodInstructions(methodNode);
  }

  @Native
  private static void processMethodDescription(MethodNode methodNode) {
    PatchyTranslationConfiguration configuration = PatchyTranslationConfiguration.createFrom(methodNode);

    if(!configuration.translateParameters()) {
      return;
    }

    if(!configuration.translateEverything()) {
      throw new IllegalStateException("Custom translations not yet supported for parameters");
    }

    String desc = methodNode.desc;

    int versionBeginIndex = desc.indexOf("/v") + 1;
    int versionEndIndex = desc.indexOf("/", versionBeginIndex);
    if(versionBeginIndex <= 0 || versionEndIndex <= 0) {
      return;
    }

    String extractedVersion = desc.substring(versionBeginIndex, versionEndIndex);
    String newDesc = desc.replace(extractedVersion, CURRENT_SERVER_VERSION);

//    System.out.println("Patched " + methodNode.desc + " with " + newDesc + " (method desc)");
    methodNode.desc = newDesc;
  }

  @Native
  private static void processMethodInstructions(MethodNode methodNode) {
    PatchyTranslationConfiguration configuration = PatchyTranslationConfiguration.createFrom(methodNode);

    for (AbstractInsnNode instruction : methodNode.instructions) {
      if(instruction instanceof MethodInsnNode) {
        MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
        InstructionTarget originalInstruction = InstructionTarget.methodInstructionTarget(
          methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc
        ), instructionTarget = originalInstruction;

        instructionTarget = process(instructionTarget, configuration);

        if (!instructionTarget.equals(originalInstruction)) {
//          System.out.println("Patched " + originalInstruction.owner + "." + originalInstruction.name + originalInstruction.desc + " with " + instructionTarget.owner + "." + instructionTarget.name + instructionTarget.desc);
        }

        methodInsnNode.owner = instructionTarget.owner;
        methodInsnNode.name = instructionTarget.name;
        methodInsnNode.desc = instructionTarget.desc;

        // TODO: 08/18/20 permute stack load order to account for parameter changes?
      } else if(instruction instanceof TypeInsnNode) {
        TypeInsnNode typeInsnNode = (TypeInsnNode) instruction;
        int versionBeginIndex = typeInsnNode.desc.indexOf("/v") + 1;
        int versionEndIndex = typeInsnNode.desc.indexOf("/", versionBeginIndex);
        if(versionBeginIndex <= 0 || versionEndIndex <= 0) {
          continue;
        }
        String extractedVersion = typeInsnNode.desc.substring(versionBeginIndex, versionEndIndex);
        typeInsnNode.desc = typeInsnNode.desc.replace(extractedVersion, CURRENT_SERVER_VERSION);
      } else if(instruction instanceof FieldInsnNode) {
        FieldInsnNode fieldInsnNode = (FieldInsnNode) instruction;
        InstructionTarget instructionTarget = InstructionTarget.fieldInstructionTarget(
          fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc
        );
        instructionTarget = process(instructionTarget, configuration);
        fieldInsnNode.owner = instructionTarget.owner;
        fieldInsnNode.name = instructionTarget.name;
        fieldInsnNode.desc = instructionTarget.desc;
      } else if(instruction instanceof InvokeDynamicInsnNode) {
        InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) instruction;
        //todo lambda resolve
      }
    }
    methodNode.localVariables = null;
  }

  @Native
  private static InstructionTarget process(InstructionTarget original, PatchyTranslationConfiguration configuration) {
//    if(!isServerClass(original.owner)) {
//      return original;
//    }

//    System.out.println("Processing method instruction " + original);

//    if(original.isMethod()) {
      VersionMethodReference translatedversionMethodReference =
        configuration.resolveCustomMethodDescriptor(original.owner, original.name, original.desc);

      if(translatedversionMethodReference == null) {
//        System.out.println("No custom translation configuration found");
        if(configuration.translateEverything()) {
//          System.out.println("Attempting heuristic replacement..");
          // heuristic replacement
          // maybe find better solution?
          String newOwner;
          String newDesc;
          String extractedVersion;

          int versionBeginIndex = original.owner.indexOf("/v") + 1;
          int versionEndIndex = original.owner.indexOf("/", versionBeginIndex);
          if(versionBeginIndex <= 0 || versionEndIndex <= 0) {
            newOwner = original.owner;
          } else {
            extractedVersion = original.owner.substring(versionBeginIndex, versionEndIndex);
            newOwner = original.owner.replace(extractedVersion, CURRENT_SERVER_VERSION);
          }

          versionBeginIndex = original.desc.indexOf("/v") + 1;
          versionEndIndex = original.desc.indexOf("/", versionBeginIndex);
          if(versionBeginIndex <= 0 || versionEndIndex <= 0) {
            newDesc = original.desc;
          } else {
            extractedVersion = original.desc.substring(versionBeginIndex, versionEndIndex);
            newDesc = original.desc.replace(extractedVersion, CURRENT_SERVER_VERSION);
          }

//          System.out.println(newOwner + " " + newDesc);

          return InstructionTarget.methodInstructionTarget(newOwner, original.name, newDesc);
        }
        return original;
      } else {
        return InstructionTarget.from(translatedversionMethodReference);
      }
//    }
//    return original;
  }

  @Native
  private static boolean isServerClass(String className) {
    return className.startsWith("net/minecraft/server") || className.startsWith("org/bukkit/craftbukkit");
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

    if(!annotationNodes.isEmpty()) {
      for (AnnotationNode visibleAnnotation : annotationNodes) {
        String annotationClassName = className(visibleAnnotation);

        //todo add other two annotations
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
    cr.accept(classNode, 4);
    return classNode;
  }

  private static byte[] byteArrayOf(ClassNode classNode) {
    ClassWriter classWriter = new ClassWriter(3);
    classNode.accept(classWriter);
    return classWriter.toByteArray();
  }

  private static String slashify(String input) {
    return input.replace('.', '/');
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
        ", name=" + owner +"#" + name + desc +
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

    public static InstructionTarget from(VersionMethodReference methodDescriptor) {
      return methodInstructionTarget(methodDescriptor.owner(), methodDescriptor.name(), methodDescriptor.description());
    }

    public static InstructionTarget methodInstructionTarget(String owner, String name, String desc) {
      return new InstructionTarget(InstructionTargetType.METHOD, owner, name, desc);
    }

    public static InstructionTarget fieldInstructionTarget(String owner, String name, String desc) {
      return new InstructionTarget(InstructionTargetType.FIELD, owner, name, desc);
    }
  }

  private enum InstructionTargetType {
    METHOD, FIELD
  }
}
