package de.jpx3.intave.resource;

import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.lib.asm.ByteVector;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;

final class EncryptionLayer implements Resource {
  private final Resource target;

  public EncryptionLayer(Resource target) {
    this.target = target;
  }

  @Override
  public boolean available() {
    return target.available();
  }

  @Override
  public long lastModified() {
    return target.lastModified();
  }

  @Override
  @Native
  public void write(InputStream inputStream) {
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int i;
      while ((i = inputStream.read(buf)) != -1) {
        byteArrayOutputStream.write(buf, 0, i);
      }
      inputStream.close();
      SecureRandom secureRandom = new SecureRandom();
      byte[] iv = new byte[12];
      secureRandom.nextBytes(iv);
      long quarterYearsSinceEpoch = ByteVector.startTime / (1000L * 60 * 60 * 24 * 365 / 4);
      String asString = String.valueOf(quarterYearsSinceEpoch);
      Random random = new Random(quarterYearsSinceEpoch);
      // compute the hash of the string
      MessageDigest messageDigest;
      try {
        messageDigest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException(exception);
      }
      // shuffle the string using the random
      byte[] bytes = asString.getBytes(UTF_8);
      for (int j = 0; j < bytes.length; j++) {
        int index = random.nextInt(bytes.length);
        byte temp = bytes[j];
        bytes[j] = bytes[index];
        bytes[index] = temp;
      }
      messageDigest.update(bytes);
      // insert random bytes into the string, using the random
      byte[] randomBytes = new byte[bytes.length];
      for (int j = 0; j < randomBytes.length; j++) {
        randomBytes[j] = (byte) random.nextInt();
      }
      messageDigest.update(randomBytes);
      byte[] digest = messageDigest.digest();
      StringBuilder stringBuilder = new StringBuilder();
      for (byte b : digest) {
        stringBuilder.append(String.format("%02x", b));
      }
      String quarterHash = stringBuilder.toString();
      String password = "adXUOhsZW7H5m4dlOyrNV7ZvHBBB071Sy2jCiuUZ91QMAcYyexjxwDQmXL1LR1nV";
      // xor the password with the quarterHash
      byte[] passwordBytes = password.getBytes(UTF_8);
      byte[] quarterHashBytes = quarterHash.getBytes(UTF_8);
      for (int j = 0; j < passwordBytes.length; j++) {
        passwordBytes[j] ^= quarterHashBytes[j % quarterHash.length()];
      }
      // shuffle the password bytes using the random
      for (int j = 0; j < passwordBytes.length; j++) {
        int index = random.nextInt(passwordBytes.length);
        byte temp = passwordBytes[j];
        passwordBytes[j] = passwordBytes[index];
        passwordBytes[index] = temp;
      }
      KeySpec spec = new PBEKeySpec(new String(passwordBytes, UTF_8).toCharArray(), iv, 65536, 128); // AES-128
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
      SecretKey secretKey = new SecretKeySpec(key, "AES");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
      byte[] encryptedData = cipher.doFinal(byteArrayOutputStream.toByteArray());
      byteArrayOutputStream.close();
      ByteBuffer byteBuffer = ByteBuffer.allocate(4 + iv.length + encryptedData.length);
      byteBuffer.putInt(iv.length);
      byteBuffer.put(iv);
      byteBuffer.put(encryptedData);
      target.write(new ByteArrayInputStream(byteBuffer.array()));
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  @Override
  @Native
  public InputStream read() {
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      InputStream inputStream = target.read();
      if (inputStream == null) {
        return null;
      }
      byte[] buf = new byte[4096];
      int i;
      while ((i = inputStream.read(buf)) != -1) {
        byteArrayOutputStream.write(buf, 0, i);
      }
      inputStream.close();
      byte[] byteArray = byteArrayOutputStream.toByteArray();
      if (byteArray.length == 0) {
        return new ByteArrayInputStream(new byte[0]);
      }
      ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray);
      byte[] iv = new byte[byteBuffer.getInt()];
      byteBuffer.get(iv);

      long quarterYearsSinceEpoch = ByteVector.startTime / (1000L * 60 * 60 * 24 * 365 / 4);
      String asString = String.valueOf(quarterYearsSinceEpoch);
      Random random = new Random(quarterYearsSinceEpoch);
      // compute the hash of the string
      MessageDigest messageDigest;
      try {
        messageDigest = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException(exception);
      }
      // shuffle the string using the random
      byte[] bytes = asString.getBytes(UTF_8);
      for (int j = 0; j < bytes.length; j++) {
        int index = random.nextInt(bytes.length);
        byte temp = bytes[j];
        bytes[j] = bytes[index];
        bytes[index] = temp;
      }
      messageDigest.update(bytes);
      // insert random bytes into the string, using the random
      byte[] randomBytes = new byte[bytes.length];
      for (int j = 0; j < randomBytes.length; j++) {
        randomBytes[j] = (byte) random.nextInt();
      }
      messageDigest.update(randomBytes);
      byte[] digest = messageDigest.digest();
      StringBuilder stringBuilder = new StringBuilder();
      for (byte b : digest) {
        stringBuilder.append(String.format("%02x", b));
      }
      String quarterHash = stringBuilder.toString();
      String password = "adXUOhsZW7H5m4dlOyrNV7ZvHBBB071Sy2jCiuUZ91QMAcYyexjxwDQmXL1LR1nV";

      // xor the password with the quarterHash
      byte[] passwordBytes = password.getBytes(UTF_8);
      byte[] quarterHashBytes = quarterHash.getBytes(UTF_8);
      for (int j = 0; j < passwordBytes.length; j++) {
        passwordBytes[j] ^= quarterHashBytes[j % quarterHash.length()];
      }
      // shuffle the password bytes using the random
      for (int j = 0; j < passwordBytes.length; j++) {
        int index = random.nextInt(passwordBytes.length);
        byte temp = passwordBytes[j];
        passwordBytes[j] = passwordBytes[index];
        passwordBytes[index] = temp;
      }
      KeySpec spec = new PBEKeySpec(new String(passwordBytes, UTF_8).toCharArray(), iv, 65536, 128); // AES-128
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
      SecretKey secretKey = new SecretKeySpec(key, "AES");
      byte[] cipherBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherBytes);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
      return new ByteArrayInputStream(cipher.doFinal(cipherBytes));
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return new ByteArrayInputStream(new byte[0]);
  }

  @Override
  public void delete() {
    target.delete();
  }
}
