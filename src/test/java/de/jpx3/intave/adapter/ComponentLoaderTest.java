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

package de.jpx3.intave.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static de.jpx3.intave.adapter.ComponentLoader.PROTOCOL_LIB_DEV_URL;
import static de.jpx3.intave.adapter.ComponentLoader.PROTOCOL_LIB_STABLE_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ComponentLoaderTest {
  private static final String LEGACY_URL = "https://example.invalid/legacy.jar";

  @Test
  void devBuildForRecentVersions() {
    assertEquals(PROTOCOL_LIB_DEV_URL, url("(MC: 1.21.10)"));
    assertEquals(PROTOCOL_LIB_DEV_URL, url("(MC: 1.21.11)"));
    assertEquals(PROTOCOL_LIB_DEV_URL, url("(MC: 26.1)"));
    assertEquals(PROTOCOL_LIB_DEV_URL, url("(MC: 26.1.1)"));
    assertEquals(PROTOCOL_LIB_DEV_URL, url("(MC: 26.1.2)"));
    assertEquals(PROTOCOL_LIB_DEV_URL, url("(MC: 26.2)"));
    assertEquals(PROTOCOL_LIB_DEV_URL, url("(MC: 26.3)"));
  }

  @Test
  void stableBuildFor119To1219() {
    assertEquals(PROTOCOL_LIB_STABLE_URL, url("(MC: 1.19.4)"));
    assertEquals(PROTOCOL_LIB_STABLE_URL, url("(MC: 1.20.4)"));
    assertEquals(PROTOCOL_LIB_STABLE_URL, url("(MC: 1.21)"));
    assertEquals(PROTOCOL_LIB_STABLE_URL, url("(MC: 1.21.9)"));
  }

  @Test
  void legacyBuildForOldVersions() {
    assertEquals(LEGACY_URL, url("(MC: 1.8.8)"));
    assertEquals(LEGACY_URL, url("(MC: 1.12.2)"));
    assertEquals(LEGACY_URL, url("(MC: 1.16.5)"));
    assertEquals(LEGACY_URL, url("(MC: 1.18.2)"));
  }

  @Test
  void unparseableVersionFallsBackToDev() {
    assertEquals(PROTOCOL_LIB_DEV_URL, ComponentLoader.selectProtocolLibUrl("garbage", LEGACY_URL));
  }

  @Test
  void paperRuntimeBanWalksCauseChain() {
    Throwable ban = new RuntimeException("load failed",
      new IllegalStateException("Cannot register paper plugins during runtime!"));
    assertTrue(ComponentLoader.isPaperRuntimeBan(ban));
    assertFalse(ComponentLoader.isPaperRuntimeBan(new IOException("connection reset")));
    assertFalse(ComponentLoader.isPaperRuntimeBan(new RuntimeException()));
    assertFalse(ComponentLoader.isPaperRuntimeBan(null));
  }

  @Test
  void pluginJarNeedsADescriptor(@TempDir Path temp) throws IOException {
    Path paperJar = temp.resolve("paper.jar");
    writeZip(paperJar, "paper-plugin.yml");
    Path legacyJar = temp.resolve("legacy.jar");
    writeZip(legacyJar, "plugin.yml");

    // valid jars pass without touching them
    ComponentLoader.validatePluginJar(paperJar, "ProtocolLib");
    ComponentLoader.validatePluginJar(legacyJar, "ProtocolLib");
    assertTrue(Files.exists(paperJar));
    assertTrue(Files.exists(legacyJar));

    // garbage gets deleted and reported
    Path html = temp.resolve("error-page.jar");
    Files.write(html, "<html>not found</html>".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class, () -> ComponentLoader.validatePluginJar(html, "ProtocolLib"));
    assertFalse(Files.exists(html));

    // zip without any descriptor gets deleted and reported
    Path empty = temp.resolve("empty.jar");
    writeZip(empty, "random.txt");
    assertThrows(IOException.class, () -> ComponentLoader.validatePluginJar(empty, "ProtocolLib"));
    assertFalse(Files.exists(empty));
  }

  private static String url(String mcTag) {
    return ComponentLoader.selectProtocolLibUrl("This server is running Paper version x " + mcTag, LEGACY_URL);
  }

  private static void writeZip(Path file, String entry) throws IOException {
    try (OutputStream out = Files.newOutputStream(file);
         ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(entry));
      zip.write("name: test".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }
}
