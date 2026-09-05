package de.jpx3.classloader;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class NativeLibraryTest {
  private static final String RESOURCE_DIRECTORY = "/de/jpx3/classloader/native/v2/";

  private static final String[][] LIBRARIES = {
    {
      "classloader-windows-aarch64.dll",
      "508e43c8dc46a73c9caaa8aae79acfb7e4a93916d155215cb2142403b0f66f45"
    },
    {
      "classloader-windows-x86_64.dll",
      "29411bd5e4d1f22190e9d2f592240c7a6d3a3ec2113f63d056bc726b41f443fa"
    },
    {
      "libclassloader-macos-aarch64.dylib",
      "4d6929ac47b20dbcb04e972920bd576ec1fb4449c0026c1f866c2c72bf64f991"
    },
    {
      "libclassloader-macos-x86_64.dylib",
      "45bd9c97466ed0012f4857b174ef49ac3e22b25dfd3cf9a8633ffc27012587b0"
    },
    {
      "libclassloader-linux-aarch64.so",
      "4900df5b06bfdd40de92dc93141f158ed7abb12143df89e1e5aa617fdac95bad"
    },
    {
      "libclassloader-linux-x86_64.so",
      "9bb42f8f9d9a526c2be70256d0d7d0e2efc4eb2550c919745f0b36257596f563"
    }
  };

  @Test
  void resolvesPublishedNativeNamesAndArm64Aliases() {
    String originalOs = System.getProperty("os.name");
    String originalArch = System.getProperty("os.arch");
    String[][] platforms = {
      {"Windows 11", "amd64", "classloader-windows-x86_64.dll"},
      {"Windows 11", "aarch64", "classloader-windows-aarch64.dll"},
      {"Linux", "amd64", "libclassloader-linux-x86_64.so"},
      {"Linux", "aarch64", "libclassloader-linux-aarch64.so"},
      {"Mac OS X", "x86_64", "libclassloader-macos-x86_64.dylib"},
      {"Mac OS X", "aarch64", "libclassloader-macos-aarch64.dylib"},
      {"Mac OS X", "arm64", "libclassloader-macos-aarch64.dylib"}
    };
    try {
      for (String[] platform : platforms) {
        System.setProperty("os.name", platform[0]);
        System.setProperty("os.arch", platform[1]);
        NativeLibrary library = new NativeLibrary("classloader", 2, new File("."),
          "https://example.invalid/", Collections.emptyList());
        assertEquals("https://example.invalid/" + platform[2], library.downloadUrl());
      }
    } finally {
      System.setProperty("os.name", originalOs);
      System.setProperty("os.arch", originalArch);
    }
  }

  @Test
  void bundlesTrustedNativeLibraries() throws Exception {
    for (String[] library : LIBRARIES) {
      String resourcePath = RESOURCE_DIRECTORY + library[0];
      String bundledJar = System.getProperty("intave.test.shadedJar");
      if (bundledJar != null) {
        URL resource = NativeLibrary.class.getResource(resourcePath);
        assertNotNull(resource, resourcePath);
        assertEquals("jar", resource.getProtocol(), "Native must come from the distribution JAR");
        assertEquals(new File(bundledJar).getCanonicalFile(),
          new File(((JarURLConnection) resource.openConnection()).getJarFileURL().toURI()).getCanonicalFile());
      }
      try (InputStream inputStream = NativeLibrary.class.getResourceAsStream(resourcePath)) {
        assertNotNull(inputStream, "Missing native library " + resourcePath);
        assertEquals(library[1], sha256(inputStream), "Unexpected native library " + resourcePath);
      }
    }
  }

  private static String sha256(InputStream inputStream) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[8192];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      digest.update(buffer, 0, read);
    }

    StringBuilder hash = new StringBuilder();
    for (byte value : digest.digest()) {
      hash.append(String.format("%02x", value & 0xff));
    }
    return hash.toString();
  }
}
