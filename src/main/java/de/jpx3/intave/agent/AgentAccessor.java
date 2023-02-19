package de.jpx3.intave.agent;


import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.library.asm.*;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

public final class AgentAccessor {
  public static boolean agentAvailable() {
    try {
      Class.forName("de.jpx3.intaveagent.IntaveAgent");
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  private static Instrumentation instrumentation;

  public static Instrumentation instrumentation() {
    if (instrumentation == null && agentAvailable()) {
      try {
        Class<?> agentClass = Class.forName("de.jpx3.intaveagent.IntaveAgent");
        Method universalInstrumentation = agentClass.getMethod("universalInstrumentation");
        instrumentation = (Instrumentation) universalInstrumentation.invoke(null);
      } catch (Exception exception) {
        throw new IntaveInternalException(exception);
      }
    }
    return instrumentation;
  }

  public static void redefineMethodInNMSClass(
    Class<?> clazz,
    String methodName,
    String methodDescription,
    MethodTransformer methodTransformer
  ) {
    if (!agentAvailable()) {
      throw new UnsupportedOperationException();
    }
    if (!isNMSClass(clazz)) {
      throw new IntaveInternalException("Can not redefine non-server class");
    }
    redefineNMSClass(clazz,
      // no lambda use, as this method will not work as lambda in java 9 - java 14
      new ClassFileTransformer() {
        @Override
        public byte[] transform(
          ClassLoader loader,
          String className,
          Class<?> classBeingRedefined,
          ProtectionDomain protectionDomain,
          byte[] classfileBuffer
        ) {
          return rewriteMethodBytes(classfileBuffer, methodName, methodDescription, methodTransformer);
        }
      }
    );
  }

  public static void resetNMSClass(
    Class<?> clazz
  ) {
    if (!agentAvailable()) {
      throw new UnsupportedOperationException();
    }
    if (!isNMSClass(clazz)) {
      throw new IntaveInternalException("Can not redefine non-server class");
    }
    redefineNMSClass(clazz,
      // no lambda use, as this method will not work as lambda in java 9 - java 14
      new ClassFileTransformer() {
        @Override
        public byte[] transform(
          ClassLoader loader,
          String className,
          Class<?> classBeingRedefined,
          ProtectionDomain protectionDomain,
          byte[] classfileBuffer
        ) {
          return sourceBytesOfMinecraftClass(clazz.getCanonicalName());
        }
      }
    );
  }

  public static void redefineNMSClass(Class<?> clazz, ClassFileTransformer transformer) {
    if (!agentAvailable() || !isNMSClass(clazz)) {
      throw new UnsupportedOperationException();
    }
    try {
      byte[] newClass = transformer.transform(
        ClassLoader.getSystemClassLoader(),
        clazz.getCanonicalName(),
        clazz,
        null,
        sourceBytesOfMinecraftClass(clazz.getCanonicalName())
      );
      instrumentation().redefineClasses(new ClassDefinition(clazz, newClass));
    } catch (Exception exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private static byte[] rewriteMethodBytes(
    byte[] input,
    String methodName,
    String methodDescription,
    MethodTransformer methodTransformer
  ) {
    ClassReader classReader = new ClassReader(input);
    ClassWriter cw = new ClassWriter(classReader, /*ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS*/0);
    classReader.accept(new ClassVisitor(Opcodes.ASM7, cw) {
      @Override
      public MethodVisitor visitMethod(
        int access,
        String name,
        String descriptor, String signature,
        String[] exceptions
      ) {
        MethodVisitor originalMethod = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (methodsEqual(name, descriptor, methodName, methodDescription)) {
          originalMethod = methodTransformer.replace(originalMethod, access, name, descriptor);
        }
        return originalMethod;
      }
    }, ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  private static boolean methodsEqual(
    String methodAName, String methodBName,
    String methodADescription, String methodBDescription
  ) {
    return methodAName.equalsIgnoreCase(methodBName)
      && methodADescription.equalsIgnoreCase(methodBDescription);
  }

  private static boolean isNMSClass(Class<?> clazz) {
    return clazz.getCanonicalName().startsWith("net.minecraft.server");
  }

  private static Method classBytesOfMethod;

  public static byte[] sourceBytesOfMinecraftClass(String minecraftClassName) {
    try {
      if (classBytesOfMethod == null) {
        classBytesOfMethod = Class.forName("de.jpx3.intaveagent.IntaveAgent").getMethod("classBytesOfNMSClass", String.class);
      }
      minecraftClassName = minecraftClassName.replace(".", "/");
      return (byte[]) classBytesOfMethod.invoke(null, minecraftClassName);
    } catch (Exception exception) {
      throw new IntaveInternalException(exception);
    }
  }
}
