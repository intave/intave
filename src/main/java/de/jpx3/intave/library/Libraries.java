package de.jpx3.intave.library;

import de.jpx3.intave.IntaveLogger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Libraries {
  public static void setupLibraries() {
    IntaveLogger.logger().info("Loading libraries...");

    // slf4j
    loadLibrary(fromMavenGradle("org.slf4j", "slf4j-api", "1.7.30"));
    loadLibrary(fromMavenGradle("org.slf4j", "slf4j-nop", "1.7.30"));

    // commons
    loadLibrary(fromMavenGradle("org.apache.commons", "commons-lang3", "3.11"));


    // apiguardian
    loadLibrary(fromMavenGradle("org.apiguardian", "apiguardian-api", "1.1.2"));

    // opentest4j
    loadLibrary(fromMavenGradle("org.opentest4j", "opentest4j", "1.2.0"));

    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    // javacpp
    loadLibrary(fromMavenGradle("org.bytedeco", "javacpp", "1.5.6"));
    loadLibrary(fromMavenGradle("org.bytedeco", "javacpp", "1.5.6", isWindows ? "-windows-x86_64" : "-linux-x86_64"));
    loadLibrary(fromMavenGradle("org.bytedeco", "openblas", "0.3.17-1.5.6"));
    loadLibrary(fromMavenGradle("org.bytedeco", "openblas", "0.3.17-1.5.6", isWindows ? "-windows-x86_64" : "-linux-x86_64"));

    List<String> smileProjects = Arrays.asList("smile-core", "smile-base");
    for (String smileProject : smileProjects) {
      loadLibrary(fromMavenGradle("com.github.haifengl", smileProject, "3.0.1"));
    }
  }

  public static void loadLibrary(Library library) {
    if (library.isInCache()) {
      library.pushToClasspath();
      return;
    }
    IntaveLogger.logger().info("Downloading library " + library.name() + library.suffix() + " to cache");
    library.downloadToCache();
    library.pushToClasspath();
  }

  public static Library fromMavenGradle(String path, String name, String version) {
    return new Library(path, name, version, "https://repo1.maven.org/maven2");
  }

  public static Library fromMavenGradle(String path, String name, String version, String suffix) {
    return new Library(path, name, version, "https://repo1.maven.org/maven2", suffix);
  }
}
