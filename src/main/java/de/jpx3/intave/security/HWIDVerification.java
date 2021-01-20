package de.jpx3.intave.security;

import de.jpx3.intave.tools.EncryptedResource;
import de.jpx3.intave.tools.annotate.Native;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

public final class HWIDVerification {
  private static EncryptedResource encryptedResource;
  private static String identifier;

  @Native
  public static String publicHardwareIdentifier() {
    if(encryptedResource == null) {
      encryptedResource = new EncryptedResource("hardware-id", false);
    }
    if (!encryptedResource.exists()) {
      identifier = randomString();
      ByteArrayInputStream inputStream = new ByteArrayInputStream(identifier.getBytes(StandardCharsets.UTF_8));
      encryptedResource.write(inputStream);
    }

    if(identifier == null) {
      identifier = new Scanner(new InputStreamReader(encryptedResource.read())).next();
    }
    return identifier;
  }

  @Native
  private static String randomString() {
    char[] available = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz01234567890_-$@?".toCharArray();
    StringBuilder str = new StringBuilder();
    for (int i = 0; i < 128; i++) {
      str.append(available[ThreadLocalRandom.current().nextInt(0, available.length - 1)]);
    }
    return str.toString();
  }
}
