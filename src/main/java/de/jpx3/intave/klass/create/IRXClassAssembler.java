package de.jpx3.intave.klass.create;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.library.asm.ClassWriter;
import de.jpx3.intave.library.asm.Label;
import de.jpx3.intave.library.asm.MethodVisitor;
import de.jpx3.intave.library.asm.Type;
import de.jpx3.intave.packet.reader.PacketReader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.*;

import static de.jpx3.intave.library.asm.Opcodes.*;

final class IRXClassAssembler {
  private static final boolean DEBUG_SAVE_GENERATED_CLASSES = false;
  static boolean TEST_MODE = false;
  static Class<?> generateCallerClass(
    ClassLoader classLoader,
    String sourceName,
    String className,
    Class<?> superClass,
    String callerMethodName, String callerMethodDescription, String castedCallerMethodDescription,
    String calledClassName,
    String calledMethodName, String calledMethodDescription,
    boolean isStatic, boolean interfaceCall,
    IntUnaryOperator swaps, @Nullable Function<String, BiConsumer<String, MethodVisitor>> additionalParameterInstructions
  ) {
    byte[] callerClassBytes = prepareCallerClassBytes(
      className,
      sourceName,
      superClass,
      callerMethodName, callerMethodDescription, castedCallerMethodDescription,
      calledClassName,
      calledMethodName, calledMethodDescription,
      isStatic, interfaceCall,
      swaps, additionalParameterInstructions
    );
    return loadAndGetClass(classLoader, className, callerClassBytes);
  }

  private static byte[] prepareCallerClassBytes(
    String className, String sourceName,
    Class<?> superClass,
    String callerMethodName, String callerMethodDescription,
    String castedCallerMethodDescription, String calledClassName,
    String calledMethodName, String calledMethodDescription,
    boolean isStatic, boolean interfaceCall,
    IntUnaryOperator swaps, @Nullable Function<String, BiConsumer<String, MethodVisitor>> additionalParameterInstructions
  ) {
    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    pushClassData(classWriter, className, sourceName, superClass);
    if (calledMethodDescription.contains(PacketReader.class.getSimpleName())) {
      classWriter.visitField(ACC_PRIVATE | ACC_SYNTHETIC, "block", "Z", null, null)
        .visitEnd();
    }
    pushConstructor(classWriter);
    pushCallerMethod(
      className,
      classWriter,
      callerMethodName,
      callerMethodDescription,
      castedCallerMethodDescription,
      calledClassName,
      calledMethodName,
      calledMethodDescription,
      isStatic, interfaceCall,
      swaps, additionalParameterInstructions
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
    int classFlags = ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC;
    String superClassName;
    boolean superClassIsInterface;

    if (superClass == null) {
      superClassName = "java/lang/Object";
      superClassIsInterface = false;
    } else {
      superClassName = Type.getInternalName(superClass);
      superClassIsInterface = superClass.isInterface();
    }

    if (superClassIsInterface) {
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
      ACC_PUBLIC | ACC_SYNTHETIC,
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
    String className,
    ClassWriter classWriter,
    String callerMethodName,
    String callerMethodDescription,
    String castCallerMethodDescription,
    String calledClassName,
    String calledMethodName,
    String calledMethodDescription,
    boolean isStatic, boolean interfaceCall,
    IntUnaryOperator swaps,
    @Nullable Function<String, BiConsumer<String, MethodVisitor>> additionalParameterInstructions
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
    Type[] callerParameterTypes = resolveTypes(callerMethodDescription);
    Type[] calledParameterTypes = resolveTypes(calledMethodDescription);
    Type[] castCallerParameterTypes = resolveTypes(castCallerMethodDescription);
    Type callerReturnType = resolveReturnType(callerMethodDescription);
    int index = 0;
    if (!isStatic) {
      methodVisitor.visitVarInsn(ALOAD, ++index);
      methodVisitor.visitTypeInsn(CHECKCAST, castCallerParameterTypes[0].getInternalName());
    }
    for (Type type : calledParameterTypes) {
      if (additionalParameterInstructions != null) {
        BiConsumer<String, MethodVisitor> additionalInstructions = resolveAdditionalParameterInstructions(
          type.getClassName(), additionalParameterInstructions
        );
        if (additionalInstructions == null) {
          IntaveLogger.logger().warn("Unsupported generated caller parameter type " + type.getClassName()
            + " in method " + callerMethodName + " of class " + className + ". Using null");
          methodVisitor.visitInsn(ACONST_NULL);
        } else {
          additionalInstructions.accept(className, methodVisitor);
          methodVisitor.visitTypeInsn(CHECKCAST, type.getInternalName());
        }
        continue;
      }
      int srcIndex = swaps.applyAsInt(++index);
      if (srcIndex < 0) {
        // parameter not used in the called method
        continue;
      }
      Type nestedType = castCallerParameterTypes[index - (isStatic ? 0 : 1)];
      Type srcType = callerParameterTypes[srcIndex - (isStatic ? 0 : 1)];
      int typeOpcode = resolveTypeOpcode(type, ILOAD);
      methodVisitor.visitVarInsn(typeOpcode, srcIndex);
      if (!nestedType.equals(srcType)) {
        String nestedTypeClassPath = nestedType.getInternalName();
        methodVisitor.visitTypeInsn(CHECKCAST, nestedTypeClassPath);
      }
    }
    int instructionOpCode = isStatic ? INVOKESTATIC : interfaceCall ? INVOKEINTERFACE : INVOKEVIRTUAL;
    methodVisitor.visitMethodInsn(
      instructionOpCode,
      calledClassName, calledMethodName, calledMethodDescription,
      false
    );
    if (calledMethodDescription.contains(PacketReader.class.getSimpleName())) {
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(PacketReader.class), "releaseSafe", "()V", true);
    }
    methodVisitor.visitInsn(resolveTypeOpcode(callerReturnType, IRETURN));
    Label label1 = new Label();
    methodVisitor.visitLabel(label1);
    int calledParameterAmount = calledParameterTypes.length;
    int callerParameterAmount = resolveTypes(callerMethodDescription).length;
    methodVisitor.visitMaxs(Math.max(calledParameterAmount, callerParameterAmount) + 5, callerParameterAmount + /* this */ 1 + /* packet reader */ 1);
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
    if (DEBUG_SAVE_GENERATED_CLASSES) {
      try {
        String path = "generated_classes/" + className.replace("/", "_") + ".class";
        File file = new File(path);
        file.getParentFile().mkdirs();
        Path path1 = file.toPath();
        System.out.println("Writing generated class to " + path1.toAbsolutePath());
        Files.write(path1, classBytes);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    loadClass(classLoader, classBytes);
    return fetchClass(className);
  }

  private static void loadClass(
    ClassLoader classLoader, byte[] classBytes
  ) {
    if (TEST_MODE) {
      try {
        Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
        try {
          defineClass.setAccessible(true);
        } catch (Exception exception) {
          throw new IntaveInternalException("Failed to acquire class-loading permissions from the JVM. If you are running Intave on Java 16, add \"--add-opens java.base/java.lang=ALL-UNNAMED\" to your startup arguments", exception);
        }
        defineClass.invoke(classLoader, classBytes, 0, classBytes.length);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new IntaveInternalException(e);
      }
      return;
    }
    de.jpx3.classloader.ClassLoader.classLoad(classBytes);
  }

  private static Class<?> fetchClass(String name) {
    try {
      return Class.forName(name.replace("/", "."), false, pluginClassLoader());
    } catch (ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
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

  private static BiConsumer<String, MethodVisitor> resolveAdditionalParameterInstructions(
    String parameterTypeName,
    Function<String, BiConsumer<String, MethodVisitor>> instructions
  ) {
    ArrayDeque<Class<?>> pending = new ArrayDeque<>();
    Set<Class<?>> visited = new HashSet<>();
    try {
      pending.add(Class.forName(parameterTypeName));
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
}
