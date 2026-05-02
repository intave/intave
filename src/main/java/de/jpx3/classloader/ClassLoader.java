package de.jpx3.classloader;


import de.jpx3.intave.version.JavaVersion;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class ClassLoader {
  public static final boolean USE_NATIVE_ACCESS = currentJavaVersion() >= 15;

  private static boolean loaded;

  public static void setupEnvironment(File parentTempDirectory) {
    if (USE_NATIVE_ACCESS) {
      NativeLibrary nativeLibrary = new NativeLibrary(
        "classloader", 2, parentTempDirectory,
        "https://github.com/intave/classloader/releases/download/v1.0.2/",
        Arrays.asList(
          "508e43c8dc46a73c9caaa8aae79acfb7e4a93916d155215cb2142403b0f66f45",
          "4d6929ac47b20dbcb04e972920bd576ec1fb4449c0026c1f866c2c72bf64f991",
          "29411bd5e4d1f22190e9d2f592240c7a6d3a3ec2113f63d056bc726b41f443fa",
          "45bd9c97466ed0012f4857b174ef49ac3e22b25dfd3cf9a8633ffc27012587b0",
          "4900df5b06bfdd40de92dc93141f158ed7abb12143df89e1e5aa617fdac95bad",
          "9bb42f8f9d9a526c2be70256d0d7d0e2efc4eb2550c919745f0b36257596f563"
        )
      );
      nativeLibrary.load();
    }
	  if (!classLoaded("java.lang.String")) {
      throw new IllegalStateException("Something went wrong");
	  }
    if (classLoaded("de.jpx3.intave.i.will.never.ever.exist.Hopefully")) {
      throw new IllegalStateException("Something went wrong");
    }
    loaded = true;
  }

  public static boolean loaded() {
    return loaded;
  }

  public static boolean usesNativeAccess() {
    return USE_NATIVE_ACCESS;
  }

  public static boolean classLoaded(String name) {
    if (USE_NATIVE_ACCESS) {
	    return classLoaded0(name);
    } else {
      return classLoadedLegacy(name);
    }
  }

  private static boolean classLoadedLegacy(String className) {
    try {
      Method findLoadedClass = java.lang.ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
      if (!findLoadedClass.isAccessible()) {
        findLoadedClass.setAccessible(true);
      }
      return findLoadedClass.invoke(ClassLoader.class.getClassLoader(), className) != null;
    } catch (Exception exception) {
      exception.printStackTrace();
      return true;
    }
  }

  private static native boolean classLoaded0(String name);

  public static void classLoad(byte[] bytes) {
    if (USE_NATIVE_ACCESS) {
      classLoad0(bytes);
    } else {
      classLoadLegacy(bytes);
    }
  }

  private static void classLoadLegacy(byte[] bytes) {
    try {
      Method defineClass = java.lang.ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
      if (!defineClass.isAccessible()) {
        defineClass.setAccessible(true);
      }
      defineClass.invoke(ClassLoader.class.getClassLoader(), bytes, 0, bytes.length);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private static native void classLoad0(byte[] bytes);

  private static int currentJavaVersion() {
      return JavaVersion.current();
  }
}
