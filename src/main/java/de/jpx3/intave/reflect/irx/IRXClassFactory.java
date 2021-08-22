package de.jpx3.intave.reflect.irx;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class IRXClassFactory {
  public static <T> Class<T> assembleCallerClass(
    ClassLoader classLoader,
    Class<? super T> superClass, String sourceClassName,
    String callerMethodName, String callerMethodDescription, String castCalledMethodDescription,
    String calledClassName,
    String calledMethodName, String calledMethodDescription,
    boolean isStatic, boolean interfaceCall
  ) {
    //noinspection unchecked
    return (Class<T>) IRXClassAssembler.generateCallerClass(
      classLoader,
      sourceClassName,
      findClassName(), superClass,
      callerMethodName, callerMethodDescription, castCalledMethodDescription,
      calledClassName,
      calledMethodName, calledMethodDescription,
      isStatic, interfaceCall
    );
  }

  private final static List<String> assembledClassNames = Lists.newArrayList();

  private static synchronized String findClassName() {
    String randomClassName;
    do {
      randomClassName = UUID.randomUUID().toString().replaceAll("-", "").toLowerCase(Locale.ROOT).substring(0, 8);
    } while (assembledClassNames.contains(randomClassName));
    assembledClassNames.add(randomClassName);
    return "de/jpx3/intave/irx0x000000000054" + randomClassName;
  }
}