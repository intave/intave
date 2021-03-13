package de.jpx3.intave.reflect.irx;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.lib.asm.ClassWriter;
import de.jpx3.intave.lib.asm.Label;
import de.jpx3.intave.lib.asm.MethodVisitor;
import de.jpx3.intave.lib.asm.Type;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static de.jpx3.intave.lib.asm.Opcodes.*;

final class IRXClassAssembler {
  static Class<?> generateCallerClass(
    ClassLoader classLoader,
    String sourceName,
    String className,
    Class<?> superClass,
    String callerMethodName, String callerMethodDescription, String castedCallerMethodDescription,
    String calledClassName,
    String calledMethodName, String calledMethodDescription,
    boolean isStatic, boolean interfaceCall
  ) {
    byte[] callerClassBytes = prepareCallerClassBytes(
      className,
      sourceName,
      superClass,
      callerMethodName, callerMethodDescription, castedCallerMethodDescription,
      calledClassName,
      calledMethodName, calledMethodDescription,
      isStatic, interfaceCall
    );
    return loadAndGetClass(classLoader, className, callerClassBytes);
  }

  private static byte[] prepareCallerClassBytes(
    String className, String sourceName,
    Class<?> superClass,
    String callerMethodName, String callerMethodDescription,
    String castedCallerMethodDescription, String calledClassName,
    String calledMethodName, String calledMethodDescription,
    boolean isStatic,
    boolean interfaceCall
  ) {
    ClassWriter classWriter = new ClassWriter(0);
    pushClassData(classWriter, className, sourceName, superClass);
    pushConstructor(classWriter);
    pushCallerMethod(
      classWriter,
      callerMethodName,
      callerMethodDescription,
      castedCallerMethodDescription,
      calledClassName,
      calledMethodName,
      calledMethodDescription,
      isStatic,
      interfaceCall
    );
    return endAndFetchBytes(classWriter);
  }

  private static void pushClassData(
    ClassWriter classWriter,
    String className,
    String sourceName,
    Class<?> superClass
  ) {
    int classVersion = V1_8;
    int classFlags = ACC_PUBLIC | ACC_FINAL | ACC_SUPER;
    String superClassName;
    boolean superClassIsInterface;

    if(superClass == null) {
      superClassName = "java/lang/Object";
      superClassIsInterface = false;
    } else {
      superClassName = superClass.getCanonicalName().replaceAll("\\.", "/");
      superClassIsInterface = superClass.isInterface();
    }

    if(superClassIsInterface) {
      classWriter.visit(
        classVersion,
        classFlags,
        className,
        null,
        "java/lang/Object",
        new String[]{superClassName}
      );
    } else {
      classWriter.visit(
        classVersion,
        classFlags,
        className,
        null,
        superClassName,
        null
      );
    }
    classWriter.visitSource(sourceName, null);
  }

  private static void pushConstructor(
    ClassWriter classWriter
  ) {
    MethodVisitor methodVisitor = classWriter.visitMethod(
      ACC_PUBLIC,
      "<init>",
      "()V",
      null,
      null
    );
    methodVisitor.visitCode();
    Label label0 = new Label();
    methodVisitor.visitLabel(label0);
    methodVisitor.visitVarInsn(ALOAD, 0);
    methodVisitor.visitMethodInsn(
      INVOKESPECIAL,
      "java/lang/Object",
      "<init>",
      "()V",
      false
    );
    methodVisitor.visitInsn(RETURN);
    Label label1 = new Label();
    methodVisitor.visitLabel(label1);
    methodVisitor.visitMaxs(1, 1);
    methodVisitor.visitEnd();
  }

  private static void pushCallerMethod(
    ClassWriter classWriter,
    String callerMethodName,
    String callerMethodDescription,
    String castCallerMethodDescription,
    String calledClassName,
    String calledMethodName,
    String calledMethodDescription,
    boolean isStatic,
    boolean interfaceCall
  ) {
    MethodVisitor methodVisitor = classWriter.visitMethod(
      ACC_PUBLIC | ACC_SYNTHETIC,
      callerMethodName,
      callerMethodDescription,
      null,
      null
    );
    methodVisitor.visitCode();
    Label label0 = new Label();
    methodVisitor.visitLabel(label0);
    Type[] callerMethodParameterTypes = resolveTypes(callerMethodDescription);
    Type[] castCallerMethodParameterTypes = resolveTypes(castCallerMethodDescription);
    int callerMethodParameterAmount = callerMethodParameterTypes.length;
    Type callerMethodReturnType = resolveReturnType(callerMethodDescription);
    int index = 0;
    for (Type type : callerMethodParameterTypes) {
      Type nestedType = castCallerMethodParameterTypes[index];
      int typeOpcode = resolveTypeOpcode(type, ILOAD);
      methodVisitor.visitVarInsn(typeOpcode, ++index);
      if(!nestedType.equals(type)) {
        String nestedTypeClassPath = nestedType.getClassName().replaceAll("\\.", "/");
        methodVisitor.visitTypeInsn(CHECKCAST, nestedTypeClassPath);
      }
    }
    int instructionOpCode = isStatic ? INVOKESTATIC : interfaceCall ? INVOKEINTERFACE : INVOKEVIRTUAL;
    methodVisitor.visitMethodInsn(
      instructionOpCode,
      calledClassName, calledMethodName, calledMethodDescription,
      false
    );
    methodVisitor.visitInsn(resolveTypeOpcode(callerMethodReturnType, IRETURN));
    Label label1 = new Label();
    methodVisitor.visitLabel(label1);
    methodVisitor.visitMaxs(callerMethodParameterAmount, callerMethodParameterAmount + /* this */ 1);
    methodVisitor.visitEnd();
  }

  private static byte[] endAndFetchBytes(ClassWriter classWriter) {
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  private static Class<?> loadAndGetClass(
    ClassLoader classLoader,
    String className, byte[] classBytes
  ) {
    loadClass(classLoader, classBytes);
    return fetchClass(className);
  }

  private static void loadClass(
    ClassLoader classLoader, byte[] classBytes
  ) {
    try {
      Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
      defineClass.setAccessible(true);
      defineClass.invoke(classLoader, classBytes, 0, classBytes.length);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new IntaveInternalException(e);
    }
  }

  private static Class<?> fetchClass(String name) {
    try {
      return Class.forName(name.replaceAll("/", "."), false, pluginClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IntaveInternalException(e);
    }
  }

  private static ClassLoader pluginClassLoader() {
    return IntavePlugin.class.getClassLoader();
  }

  private static int resolveTypeOpcode(
    Type type, int startOpCode
  ) {
    return type.getOpcode(startOpCode);
  }

  private static Type[] resolveTypes(String methodDescription) {
    return Type.getArgumentTypes(methodDescription);
  }

  private static Type resolveReturnType(String methodDescription) {
    return Type.getReturnType(methodDescription);
  }
}