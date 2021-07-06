package de.jpx3.intave.reflect.locate;

public final class ClassLocator {
  private final static ClassLocationFileCompiler fileCompiler = new ClassLocationFileCompiler();
  private final static ClassLocations classLocations = fileCompiler.fromResource("/mappings/class-locate");

  public static Class<?> locateByShortName(String name) {
    return null;
  }

  public static Class<?> crossLocateByFullName(String name) {
    return null;
  }
}
