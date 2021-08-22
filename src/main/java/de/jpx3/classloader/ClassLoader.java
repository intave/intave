package de.jpx3.classloader;

import de.jpx3.intave.annotate.NameIntrinsicallyImportant;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;

@NameIntrinsicallyImportant
public final class ClassLoader {
  public final static boolean USE_NATIVE_ACCESS = currentJavaVersion() >= 15;

  private static boolean loaded;

  public static void setupEnvironment(File parentTempDirectory) {
    if (USE_NATIVE_ACCESS) {
      NativeLibrary nativeLibrary = new NativeLibrary("classloader", 0, parentTempDirectory, Arrays.asList("EDA34D4D1003958D62DA21A4D86D05740A3CD2D81D7B3A23A7643C957F8144BB", "EC32DA5F0FC58DEA075C1B4F8562A369684264CA051D892BC97F70115A3607EA"));
      nativeLibrary.load();
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
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      version = version.substring(2, 3);
    } else {
      int dot = version.indexOf(".");
      if (dot != -1) {
        version = version.substring(0, dot);
      }
    }
    return Integer.parseInt(version);
  }
}
