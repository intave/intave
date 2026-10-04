package de.jpx3.intave.klass.create;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.library.asm.MethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class IRXClassFactory {
  @SuppressWarnings("unchecked")
  public static <T> Class<T> assembleCallerClass(
    ClassLoader classLoader,
    Method toImplement,
    Method target,
    @Nullable Function<String, BiConsumer<String, MethodVisitor>> additionalParameterInstructions
  ) {
    return (Class<T>) IRXClassAssembler.generateCallerClass(
      classLoader,
      findClassName(), toImplement, target,
      additionalParameterInstructions
    );
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
    if (IRXClassAssembler.TEST_MODE) {
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
      InputStream stream = classLoader.getResourceAsStream(String.format("de/jpx3/intave/generated/%s.class", className));
    ) {
      if (stream != null) {
        CLASSES_FOUND.add(className);
        return true;
      }
    } catch (IOException ignored) {
    }
    return false;
  }
}
