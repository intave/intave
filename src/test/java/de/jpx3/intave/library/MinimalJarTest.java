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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "intave.test.minimalJar", matches = ".+")
final class MinimalJarTest {
  @Test
  void containsPluginApisAndDownloadListWithoutBundledLibrariesOrNatives() throws Exception {
    try (JarFile jar = new JarFile(System.getProperty("intave.test.minimalJar"))) {
      assertNotNull(jar.getEntry("plugin.yml"));
      assertNotNull(jar.getEntry("META-INF/intave/libraries.txt"));
      assertNull(jar.getEntry("META-INF/intave/bundled"));
      assertTrue(jar.stream().anyMatch(entry -> entry.getName().startsWith("ac/intave/")));
      assertFalse(jar.stream().anyMatch(entry -> !entry.isDirectory()
        && entry.getName().startsWith("de/jpx3/classloader/native/")));
      assertNull(jar.getEntry("net/bytebuddy/ByteBuddy.class"));
      assertNull(jar.getEntry("com/github/luben/zstd/Zstd.class"));
      assertNull(jar.getEntry("org/bouncycastle/cert/X509CertificateHolder.class"));
      assertNull(jar.getEntry("it/unimi/dsi/fastutil/longs/LongSet.class"));
    }
  }

  @Test
  void loadsCachedDependenciesIntoAnIsolatedPluginClassLoader() throws Exception {
    File plugin = new File(System.getProperty("intave.test.minimalJar")).getCanonicalFile();
    // Exclude the test worker's original dependency JARs, as a real plugin must load its own.
    try (URLClassLoader loader = new URLClassLoader(new URL[] {plugin.toURI().toURL()},
      java.lang.ClassLoader.getSystemClassLoader().getParent())) {
      Class<?> libraries = loader.loadClass("de.jpx3.intave.library.Libraries");
      assertEquals(false, libraries.getMethod("isBundled").invoke(null));
      Consumer<String> rejectDownload = message -> fail("Expected the prepared local cache: " + message);
      libraries.getMethod("setupLibraries", Consumer.class).invoke(null, rejectDownload);
      assertTrue(loader.getURLs().length > 1);
      for (String name : new String[] {
        "net.bytebuddy.ByteBuddy", "org.bouncycastle.asn1.x500.X500Name",
        "org.bouncycastle.cert.X509CertificateHolder", "org.bouncycastle.asn1.oiw.OIWObjectIdentifiers",
        "com.github.luben.zstd.Zstd", "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap"
      }) {
        File source = new File(loader.loadClass(name).getProtectionDomain().getCodeSource()
          .getLocation().toURI()).getCanonicalFile();
        assertNotEquals(plugin, source);
        assertTrue(source.toPath().startsWith(new File(System.getProperty("user.home")).toPath()), name);
      }
      Class<?> zstd = loader.loadClass("com.github.luben.zstd.Zstd");
      byte[] original = "Minimal Intave cache loading".getBytes(StandardCharsets.UTF_8);
      byte[] compressed = (byte[]) zstd.getMethod("compress", byte[].class).invoke(null, original);
      assertArrayEquals(original, (byte[]) zstd.getMethod("decompress", byte[].class, int.class)
        .invoke(null, compressed, original.length));
    }
  }
}
