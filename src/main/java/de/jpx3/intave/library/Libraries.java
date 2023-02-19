package de.jpx3.intave.library;

import de.jpx3.intave.IntaveLogger;

public final class Libraries {
  public static void setupLibraries() {
    IntaveLogger.logger().info("Loading libraries...");
    loadLibrary(fromMavenGradle("com.github.haifengl", "smile-core", "3.0.0"));
  }

  public static void loadLibrary(Library library) {
    if (library.isInCache()) {
      return;
    }
    IntaveLogger.logger().info("Downloading library " + library.path() + " to cache");
    library.downloadToCache();
  }

  public static Library fromMavenGradle(String path, String name, String version) {
    return new Library(path, name, version, "https://repo1.maven.org/maven2");
  }
}
