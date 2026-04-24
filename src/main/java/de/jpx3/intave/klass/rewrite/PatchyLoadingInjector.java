package de.jpx3.intave.klass.rewrite;

public final class PatchyLoadingInjector {
  public static <T> Class<T> loadUnloadedClassPatched(ClassLoader classLoader, String className) {
    if (className.isEmpty()) {
      return null;
    }
    className = className.replace("/", ".");
    return classByName(className);
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
