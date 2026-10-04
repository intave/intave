package de.jpx3.intave.klass.create;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.library.asm.MethodVisitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class IRXClassFactory {
  private static final boolean DEBUG_SAVE_GENERATED_CLASSES = false;
  static boolean TEST_MODE = false;

  @SuppressWarnings("unchecked")
  public static <T> Class<T> assembleCallerClass(
    ClassLoader classLoader,
    Method toImplement,
    Method target,
    @Nullable Function<String, BiConsumer<String, MethodVisitor>> extraParamInstructions
  ) {
    String className = findClassName();
    byte[] classBytes = IRXClassAssembler.generateCallerClass(
      className, toImplement, target, extraParamInstructions
    );
    loadClass(classLoader, className, classBytes);
    try {
      return (Class<T>) Class.forName(className.replace('/', '.'), false, classLoader);
    } catch (ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private static final Set<String> CLASSES_CREATED = new HashSet<>();
  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

  private static synchronized String findClassName() {
    StringBuilder randomClassName = new StringBuilder();
    int length = 1;
    int attempts = 0;
    do {
      randomClassName.delete(0, randomClassName.length());
      int rem = length;
      int pos = 0;
      while (rem-- >= 0) {
        char c = ALPHABET.charAt(ThreadLocalRandom.current().nextInt(0, (pos == 0 ? 26 : ALPHABET.length()) - 1));
        if (ThreadLocalRandom.current().nextBoolean() && pos != 0) {
          c = Character.toUpperCase(c);
        }
        randomClassName.append(c);
        pos++;
      }
      attempts++;
      for (int i = 9; i > 0; i--) {
        int limit = i * ALPHABET.length();
        if (attempts > limit) {
          length = i;
          break;
        }
      }
    } while (classExists(randomClassName.toString()));
    CLASSES_CREATED.add(randomClassName.toString());
    return "de/jpx3/intave/generated/" + randomClassName;
  }

  private static final Set<String> CLASSES_FOUND = new HashSet<>();

  private static boolean classExists(String className) {
    if (CLASSES_CREATED.contains(className) || CLASSES_FOUND.contains(className)) {
      return true;
    }
    if (TEST_MODE) {
      try {
        Method findLoadedClass = java.lang.ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
        if (!findLoadedClass.isAccessible()) {
          findLoadedClass.setAccessible(true);
        }
        return findLoadedClass.invoke(
          de.jpx3.classloader.ClassLoader.class.getClassLoader(),
          "de.jpx3.intave.generated." + className
        ) != null;
      } catch (Exception ex) {
        ex.printStackTrace();
        return true;
      }
    }
    if (de.jpx3.classloader.ClassLoader.classLoaded("de.jpx3.intave.generated." + className)) {
      CLASSES_FOUND.add(className);
      return true;
    }
    ClassLoader classLoader = IRXClassFactory.class.getClassLoader();
    try (
      InputStream stream = classLoader.getResourceAsStream("de/jpx3/intave/generated/" + className + ".class");
    ) {
      if (stream != null) {
        CLASSES_FOUND.add(className);
        return true;
      }
    } catch (IOException ignored) {
    }
    return false;
  }

  private static void loadClass(ClassLoader classLoader, String className, byte[] classBytes) {
    if (DEBUG_SAVE_GENERATED_CLASSES) {
      try {
        File file = new File("generated_classes/" + className.replace('/', '_') + ".class");
        file.getParentFile().mkdirs();
        Path path = file.toPath();
        System.out.println("Writing generated class to " + path.toAbsolutePath());
        Files.write(path, classBytes);
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
    }
    if (TEST_MODE) {
      try {
        Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
        try {
          defineClass.setAccessible(true);
        } catch (Exception exception) {
          throw new IntaveInternalException(
            "Failed to acquire class-loading permissions from the JVM. If you are running Intave on Java 16, add" +
              " \"--add-opens java.base/java.lang=ALL-UNNAMED\" to your startup arguments",
            exception
          );
        }
        defineClass.invoke(classLoader, classBytes, 0, classBytes.length);
      } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
        throw new IntaveInternalException(exception);
      }
      return;
    }
    de.jpx3.classloader.ClassLoader.classLoad(classBytes);
  }

}
