package de.jpx3.intave.clazz.rewrite;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.annotate.Native;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PatchyLoadingInjector {
  @Native
  public static <T> Class<T> loadUnloadedClassPatched(ClassLoader classLoader, String className) {
    if (className.isEmpty()) {
      return null;
    }
    className = className.replace("/", ".");
    byte[] classBytes;
    try {
      if (!classIsLoaded(classLoader, className)) {
        classBytes = classBytesOf(classLoader, className);
        classBytes = PatchyTranslator.translateClass(classBytes);
//        defineClass(classLoader, classBytes);
//        System.out.println("[Intave/Patchy] Loaded class " + className);
        de.jpx3.classloader.ClassLoader.classLoad(classBytes);
      }
      return classByName(className);
    } catch (Error | Exception e) {
      throw new IllegalStateException("Failed to load class " + className, e);
    }
  }

  @Native
  private static boolean classIsLoaded(ClassLoader classLoader, String className) {
//    try {
//      Method findLoadedClass = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
//      if (!findLoadedClass.isAccessible()) {
//        findLoadedClass.setAccessible(true);
//      }
//      return findLoadedClass.invoke(classLoader, className) != null;
//    } catch (Exception exception) {
//      exception.printStackTrace();
//      return true;
//    }
    return de.jpx3.classloader.ClassLoader.classLoaded(className);
  }

  @Native
  private static byte[] classBytesOf(ClassLoader classLoader, String className) throws IOException {
    className = className.replace('.', '/') + ".class";
    InputStream stream = classLoader.getResourceAsStream(className);
    if (stream == null) {
      IntaveLogger.logger().printLine("Unable to resolve class bytes for class " + className + ". Performing manual load attempt..");
      String path;
      try {
        path = PatchyLoadingInjector.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
      return resourceFromJar(new File(path), className);
    }
    return byteArrayFrom(stream);
  }

  @Native
  private static byte[] resourceFromJar(File inputFile, String fileName) {
    try {
      ZipFile zipFile = new ZipFile(inputFile);
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        if (!zipEntry.isDirectory() && zipEntry.getName().equals(fileName)) {
          InputStream inputStream = zipFile.getInputStream(zipEntry);
          byte[] bytes = byteArrayFrom(inputStream);
          zipFile.close();
          return bytes;
        }
      }
      zipFile.close();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    throw new IllegalStateException("Unable to locate " + fileName);
  }

  @Native
  private static byte[] byteArrayFrom(InputStream inputStream) throws IOException {
    ByteArrayOutputStream var1 = new ByteArrayOutputStream();
    copy(inputStream, var1);
    return var1.toByteArray();
  }

  private static int copy(InputStream var0, OutputStream var1) throws IOException {
    long var2 = copyLarge(var0, var1);
    return var2 > 2147483647L ? -1 : (int)var2;
  }

  private static long copyLarge(InputStream var0, OutputStream var1) throws IOException {
    return copyLarge(var0, var1, new byte[4096]);
  }

  private static long copyLarge(InputStream var0, OutputStream var1, byte[] var2) throws IOException {
    long var3 = 0L;
    int var6;
    for(; -1 != (var6 = var0.read(var2)); var3 += (long)var6) {
      var1.write(var2, 0, var6);
    }
    return var3;
  }

//  private static void defineClass(ClassLoader classLoader, byte[] classBytes) {
//    try {
//      Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, int.class, int.class);
//      defineClass.setAccessible(true);
//      defineClass.invoke(classLoader, classBytes, 0, classBytes.length);
//    } catch (Exception exception) {
//      exception.printStackTrace();
//    }
//  }

  private static <T> Class<T> classByName(String className) {
    try {
      //noinspection unchecked
      return (Class<T>) Class.forName(className);
    } catch (ClassNotFoundException exception) {
      exception.printStackTrace();
      return null;
    }
  }
}
