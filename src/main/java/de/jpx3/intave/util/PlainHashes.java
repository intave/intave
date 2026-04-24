package de.jpx3.intave.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PlainHashes {
  private PlainHashes() {
  }

  public static String sha256(File file) throws IOException {
    try (InputStream inputStream = new FileInputStream(file)) {
      return sha256(inputStream);
    }
  }

  public static String sha256(InputStream inputStream) throws IOException {
    MessageDigest digest = sha256Digest();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = inputStream.read(buffer)) != -1) {
      digest.update(buffer, 0, read);
    }
    return hex(digest.digest());
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(String.format("%02x", value));
    }
    return builder.toString();
  }
}
