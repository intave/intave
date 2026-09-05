/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.library;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public final class Libraries {
  private Libraries() {}

  public static boolean isBundled() {
    try {
      File source = new File(Libraries.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (source.isDirectory()) {
        return false;
      }
      try (JarFile jar = new JarFile(source)) {
        return jar.getEntry("META-INF/intave/bundled") != null;
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to determine Intave's dependency packaging", exception);
    }
  }

  public static void setupLibraries(Consumer<String> log) {
    if (isBundled()) {
      return;
    }
    try (InputStream input = Libraries.class.getResourceAsStream("/META-INF/intave/libraries.txt")) {
      if (input == null) {
        throw new IllegalStateException("Intave's dependency list is missing; reinstall the plugin");
      }
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) {
            continue;
          }
          String[] coordinate = line.split(":");
          if (coordinate.length != 3) {
            throw new IllegalStateException("Invalid library coordinate: " + line);
          }
          Library library = new Library(coordinate[0], coordinate[1], coordinate[2],
            "https://repo1.maven.org/maven2");
          if (!library.isInCache()) {
            log.accept("Downloading library " + library.name() + " " + library.version() + " to cache");
            library.downloadToCache();
          }
          if (!library.isInCache()) {
            throw new IllegalStateException("Unable to obtain library " + line);
          }
          library.pushToClasspath();
        }
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to prepare Intave's libraries", exception);
    }
  }
}
