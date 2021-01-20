package de.jpx3.intave.tools;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.tools.annotate.Native;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Locale;
import java.util.UUID;

public final class EncryptedResource {
  private final String name;
  private final boolean versionDependent;

  public EncryptedResource(String name, boolean versionDependent) {
    this.name = name;
    this.versionDependent = versionDependent;
  }

  @Native
  public InputStream read() {
    if(!fileStore().exists()) {
      throw new IllegalStateException();
    }
    try {
      FileInputStream fileInputStream = new FileInputStream(fileStore());
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int read;
      while((read = fileInputStream.read(buf)) != -1) {
        byteArrayOutputStream.write(buf, 0, read);
      }
      fileInputStream.close();
      byteArrayOutputStream.close();
      ByteBuffer byteBuffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
      byte[] iv = new byte[byteBuffer.getInt()];
      byteBuffer.get(iv);
      KeySpec spec = new PBEKeySpec("AES/GCM/NoPadding".toCharArray(), iv, 65536, 128); // AES-128
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
      SecretKey secretKey = new SecretKeySpec(key, "AES");
      byte[] cipherBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherBytes);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
      return new ByteArrayInputStream(cipher.doFinal(cipherBytes));
    } catch (Exception | Error e) {
      throw new IllegalStateException();
    }
  }

  @Native
  public boolean write(InputStream inputStream) {
    File file = fileStore();
    if(file.exists()) {
      file.delete();
    }
    try {
      file.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
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
      KeySpec spec = new PBEKeySpec("AES/GCM/NoPadding".toCharArray(), iv, 65536, 128); // AES-128
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
      ReadableByteChannel byteChannel = Channels.newChannel(new ByteArrayInputStream(byteBuffer.array()));
      FileOutputStream fileOutputStream = new FileOutputStream(fileStore());
      fileOutputStream.getChannel().transferFrom(byteChannel, 0, Long.MAX_VALUE);
      file.setLastModified(AccessHelper.now());
      fileOutputStream.close();
    } catch (Exception exception) {
//      exception.printStackTrace();
      return false;
    }
    return file.exists();
  }

  public boolean exists() {
    File file = fileStore();
    return file.exists();
  }

  private File fileStore() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if(operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      filePath = "/var/lib/intave/";
    }
    workDirectory = new File(filePath);
    if(!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return new File(workDirectory, resourceId());
  }

  private String resourceId() {
    return new UUID(~name.hashCode(), versionDependent ? ~intaveVersion().hashCode() : -391180952).toString() + "e";
  }

  private String intaveVersion() {
    return IntavePlugin.version();
  }
}
