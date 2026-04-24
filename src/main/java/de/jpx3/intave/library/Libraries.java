package de.jpx3.intave.library;

import de.jpx3.intave.IntaveLogger;

public final class Libraries {
  private Libraries() {
  }

  public static void setupLibraries() {
    IntaveLogger.logger().info("Runtime library downloader disabled; using server and plugin classpath only");
  }
}
