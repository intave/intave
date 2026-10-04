package de.jpx3.intave.klass.create;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.library.asm.ClassWriter;
import de.jpx3.intave.library.asm.Label;
import de.jpx3.intave.library.asm.MethodVisitor;
import de.jpx3.intave.library.asm.Type;
import de.jpx3.intave.packet.reader.PacketReader;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static de.jpx3.intave.library.asm.Opcodes.*;

final class IRXClassAssembler {
  private static final int CLASS_VERSION = V1_8;
  private static final int CLASS_FLAGS = ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC;
  private static final int METHOD_FLAGS = ACC_PUBLIC | ACC_SYNTHETIC;
  private static final int CLASS_WRITER_FLAGS = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;

  private static final String PACKET_READER = Type.getInternalName(PacketReader.class);

  static byte[] generateCallerClass(
    String className,
    Method toImplement,
    Method target,
    @Nullable Function<String, BiConsumer<String, MethodVisitor>> extraParamInstructions
  ) {
    Set<Class<?>> targetParamClasses = new HashSet<>();
    for (Class<?> paramClass : target.getParameterTypes()) {
      if (!targetParamClasses.add(paramClass)) {
        IntaveLogger.logger().warn("Target method '" + target + "' has parameter type "
          + paramClass.getName() + " multiple times");
      }
    }
    for (Class<?> parameterType : toImplement.getParameterTypes()) {
      if (parameterType == Object.class) {
        throw new IllegalArgumentException("toImplement method parameters must be specific types: " + toImplement);
      }
    }
    return prepareCallerClassBytes(className, toImplement, target, extraParamInstructions);
  }

  private static byte[] prepareCallerClassBytes(
    String className,
    Method toImplement,
    Method target,
    @Nullable Function<String, BiConsumer<String, MethodVisitor>> extraParamInstructions
  ) {
    ClassWriter classWriter = new ClassWriter(CLASS_WRITER_FLAGS);
    Class<?> superClass = pushClassData(classWriter, className, toImplement.getDeclaringClass());
    if (containsPacketReaderParameter(target)) {
      classWriter.visitField(ACC_PRIVATE | ACC_SYNTHETIC, "block", "Z", null, null).visitEnd();
    }
    pushConstructor(classWriter, superClass);
    pushCallerMethod(className, classWriter, toImplement, target, extraParamInstructions);
    return endAndFetchBytes(classWriter);
  }

  private static Class<?> pushClassData(ClassWriter classWriter, String className, Class<?> superClass) {
    String superClassName;
    boolean superClassIsInterface;

    if (superClass == null) {
      superClassName = "java/lang/Object";
      superClassIsInterface = false;
    } else {
      superClassName = Type.getInternalName(superClass);
      superClassIsInterface = superClass.isInterface();
    }

    String[] interfaces;
    if (superClassIsInterface) {
      interfaces = new String[]{superClassName};
      superClassName = "java/lang/Object";
    } else {
      interfaces = null;
    }
    classWriter.visit(CLASS_VERSION, CLASS_FLAGS, className, null, superClassName, interfaces);
    classWriter.visitSource("<irx>", null);
    return superClassIsInterface ? Object.class : superClass;
  }

  private static void pushConstructor(ClassWriter classWriter, Class<?> superClass) {
    MethodVisitor methodVisitor = classWriter.visitMethod(METHOD_FLAGS, "<init>", "()V", null, null);
    methodVisitor.visitCode();
    Label label0 = new Label();
    methodVisitor.visitLabel(label0);
    methodVisitor.visitVarInsn(ALOAD, 0);
    methodVisitor.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(superClass), "<init>", "()V", false);
    methodVisitor.visitInsn(RETURN);
    Label label1 = new Label();
    methodVisitor.visitLabel(label1);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();
  }

  private static void pushCallerMethod(
    String className,
    ClassWriter classWriter,
    Method toImplement,
    Method target,
    @Nullable Function<String, BiConsumer<String, MethodVisitor>> extraParamInstructions
  ) {
    MethodVisitor methodVisitor = classWriter.visitMethod(
      METHOD_FLAGS,
      toImplement.getName(),
      Type.getMethodDescriptor(toImplement),
      null,
      null
    );
    methodVisitor.visitCode();
    Label label0 = new Label();
    methodVisitor.visitLabel(label0);
    Type[] stackArguments = getStackArguments(target);
    Type[] toImplementArguments = Type.getArgumentTypes(toImplement);
    int[] toImplementPositions = getParameterLocalPositions(toImplementArguments);

    if (extraParamInstructions == null) {
      for (Type type : stackArguments) {
        int src = findSourceParameter(type, toImplementArguments);
        if (src >= 0) {
          loadParameter(type, toImplementArguments[src], toImplementPositions[src], methodVisitor);
          continue;
        }
        IntaveLogger.logger().warn("Did not find matching parameter type '" + type.getClassName() + "' in method '"
          + toImplement + "' while generating class '" + className + "' to call '" + target + "'. Using null/0");
        pushDefaultValue(type, methodVisitor);
      }
    } else {
      for (Type type : stackArguments) {
        int src = findSourceParameter(type, toImplementArguments);
        if (src >= 0) {
          loadParameter(type, toImplementArguments[src], toImplementPositions[src], methodVisitor);
          continue;
        }
        BiConsumer<String, MethodVisitor> targetParamInstruction = findParamInstruction(type, extraParamInstructions);
        if (targetParamInstruction == null) {
          IntaveLogger.logger().warn("Unsupported parameter type '" + type.getClassName() + "' in method '"
            + toImplement + "' while generating class '" + className + "' to call '" + target + "'. Using null/0");
          pushDefaultValue(type, methodVisitor);
          continue;
        }
        targetParamInstruction.accept(className, methodVisitor);
        methodVisitor.visitTypeInsn(CHECKCAST, type.getInternalName());
      }
    }
    boolean hasPacketReader = containsPacketReaderParameter(target);
    Label invocationStart = null;
    Label invocationEnd = null;
    Label invocationFinally = null;
    if (hasPacketReader) {
      invocationStart = new Label();
      invocationEnd = new Label();
      invocationFinally = new Label();
      methodVisitor.visitLabel(invocationStart);
    }
    boolean isTargetStatic = Modifier.isStatic(target.getModifiers());
    boolean interfaceCall = target.getDeclaringClass().isInterface();
    int instructionOpCode = isTargetStatic ? INVOKESTATIC : interfaceCall ? INVOKEINTERFACE : INVOKEVIRTUAL;
    methodVisitor.visitMethodInsn(
      instructionOpCode,
      Type.getInternalName(target.getDeclaringClass()),
      target.getName(),
      Type.getMethodDescriptor(target),
      interfaceCall
    );
    if (hasPacketReader) {
      methodVisitor.visitLabel(invocationEnd);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, PACKET_READER, "releaseSafe", "()V", true);
    }
    methodVisitor.visitInsn(Type.getReturnType(toImplement).getOpcode(IRETURN));

    if (hasPacketReader) {
      methodVisitor.visitLabel(invocationFinally);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, PACKET_READER, "releaseSafe", "()V", true);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitTryCatchBlock(
        invocationStart,
        invocationEnd,
        invocationFinally,
        null
      ); // null = finally / all exceptions
    }
    int calledParameterAmount = stackArguments.length;
    methodVisitor.visitMaxs(
      calledParameterAmount + 5,
      toImplementArguments.length + /* this */ 1 + /* packet reader */ 1
    ); // they'll be calculated later on anyway, doesn't matter if they're too high
    methodVisitor.visitEnd();
  }

  private static void loadParameter(
    Type type,
    Type sourceType,
    int sourceLocalPosition,
    MethodVisitor methodVisitor
  ) {
    methodVisitor.visitVarInsn(sourceType.getOpcode(ILOAD), sourceLocalPosition);
    if (!type.equals(sourceType)) {
      methodVisitor.visitTypeInsn(CHECKCAST, type.getInternalName());
    }
  }

  private static int[] getParameterLocalPositions(Type[] stackArguments) {
    int[] parameterLocalPositions = new int[stackArguments.length];
    int localPosition = 1;
    for (int parameterIndex = 0; parameterIndex < stackArguments.length; parameterIndex++) {
      parameterLocalPositions[parameterIndex] = localPosition;
      localPosition += stackArguments[parameterIndex].getSize();
    }
    return parameterLocalPositions;
  }

  private static int findSourceParameter(Type targetType, Type[] sourceTypes) {
    for (int index = 0; index < sourceTypes.length; index++) {
      if (isAssignable(sourceTypes[index], targetType)) {
        return index;
      }
    }
    return -1;
  }

  private static Type[] getStackArguments(Method target) {
    Class<?>[] parameterClasses = target.getParameterTypes();
    int receiverOffset = Modifier.isStatic(target.getModifiers()) ? 0 : 1;
    Type[] stackArguments = new Type[parameterClasses.length + receiverOffset];
    int index = 0;
    if (receiverOffset != 0) {
      stackArguments[index++] = Type.getType(target.getDeclaringClass());
    }
    for (int parameterIndex = 0; parameterIndex < parameterClasses.length; parameterIndex++) {
      stackArguments[index++] = Type.getType(parameterClasses[parameterIndex]);
    }
    return stackArguments;
  }

  private static boolean isAssignable(Type sourceType, Type targetType) {
    if (sourceType.equals(targetType)) {
      return true;
    }
    if (sourceType.getSort() != Type.OBJECT || targetType.getSort() != Type.OBJECT) {
      return false;
    }
    try {
      return Class.forName(sourceType.getClassName()).isAssignableFrom(Class.forName(targetType.getClassName()));
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  private static byte[] endAndFetchBytes(ClassWriter classWriter) {
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  private static boolean containsPacketReaderParameter(Method method) {
    for (Class<?> parameterType : method.getParameterTypes()) {
      if (PacketReader.class.isAssignableFrom(parameterType)) {
        return true;
      }
    }
    return false;
  }

  private static BiConsumer<String, MethodVisitor> findParamInstruction(
    Type parameterType,
    Function<String, BiConsumer<String, MethodVisitor>> instructions
  ) {
    ArrayDeque<Class<?>> pending = new ArrayDeque<>();
    Set<Class<?>> visited = new HashSet<>();
    try {
      pending.add(Class.forName(parameterType.getClassName()));
    } catch (ClassNotFoundException ignored) {
      return null;
    }
    while (!pending.isEmpty()) {
      Class<?> type = pending.removeFirst();
      if (!visited.add(type)) {
        continue;
      }
      if (type == Object.class) {
        continue;
      }
      BiConsumer<String, MethodVisitor> result = instructions.apply(type.getName());
      if (result != null) {
        return result;
      }
      Class<?> superclass = type.getSuperclass();
      if (superclass != null) {
        pending.addLast(superclass);
      }
      for (Class<?> interfaceType : type.getInterfaces()) {
        pending.addLast(interfaceType);
      }
    }
    return null;
  }

  private static void pushDefaultValue(Type type, MethodVisitor methodVisitor) {
    int opcode;
    switch (type.getSort()) {
      case Type.BOOLEAN:
      case Type.BYTE:
      case Type.CHAR:
      case Type.SHORT:
      case Type.INT:
        opcode = ICONST_0;
        break;
      case Type.LONG:
        opcode = LCONST_0;
        break;
      case Type.FLOAT:
        opcode = FCONST_0;
        break;
      case Type.DOUBLE:
        opcode = DCONST_0;
        break;
      case Type.OBJECT:
      case Type.ARRAY:
        opcode = ACONST_NULL;
        break;
      default:
        throw new IllegalArgumentException("Cannot push a default value for type " + type);
    }
    methodVisitor.visitInsn(opcode);
  }
}
