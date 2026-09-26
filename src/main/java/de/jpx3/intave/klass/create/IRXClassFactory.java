package de.jpx3.intave.klass.create;

import de.jpx3.intave.library.asm.MethodVisitor;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

public final class IRXClassFactory {
  public static <T> Class<T> assembleCallerClass(
    ClassLoader classLoader,
    Class<? super T> superClass, String sourceClassName,
    String callerMethodName, String callerMethodDescription, String castCalledMethodDescription,
    String calledClassName,
    String calledMethodName, String calledMethodDescription,
    boolean isStatic, boolean interfaceCall,
    IntUnaryOperator swaps
  ) {
    //noinspection unchecked
    return (Class<T>) IRXClassAssembler.generateCallerClass(
      classLoader,
      sourceClassName,
      findClassName(), superClass,
      callerMethodName, callerMethodDescription, castCalledMethodDescription,
      calledClassName,
      calledMethodName, calledMethodDescription,
      isStatic, interfaceCall,
      swaps, null
    );
  }

  public static <T> Class<T> assembleCallerClass(
    ClassLoader classLoader,
    Class<? super T> superClass, String sourceClassName,
    String callerMethodName, String callerMethodDescription, String castCalledMethodDescription,
    String calledClassName,
    String calledMethodName, String calledMethodDescription,
    boolean isStatic, boolean interfaceCall,
    IntUnaryOperator swaps, Function<String, BiConsumer<String, MethodVisitor>> additionalParameterInstructions
  ) {
    //noinspection unchecked
    return (Class<T>) IRXClassAssembler.generateCallerClass(
      classLoader,
      sourceClassName,
      findClassName(), superClass,
      callerMethodName, callerMethodDescription, castCalledMethodDescription,
      calledClassName,
      calledMethodName, calledMethodDescription,
      isStatic, interfaceCall,
      swaps, additionalParameterInstructions
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
    return "de/jpx3/intave/" + randomClassName;
  }

  private static final Set<String> CLASSES_FOUND = new HashSet<>();

  private static boolean classExists(String className) {
    if (CLASSES_CREATED.contains(className) || CLASSES_FOUND.contains(className)) {
      return true;
    }
    if (de.jpx3.classloader.ClassLoader.classLoaded("de.jpx3.intave." + className)) {
      CLASSES_FOUND.add(className);
      return true;
    }
    ClassLoader classLoader = IRXClassFactory.class.getClassLoader();
    try (
      InputStream stream = classLoader.getResourceAsStream(String.format("de/jpx3/intave/%s.class", className));
    ) {
      if (stream != null) {
        CLASSES_FOUND.add(className);
        return true;
      }
    } catch (IOException ignored) {}
    return false;
  }
}
