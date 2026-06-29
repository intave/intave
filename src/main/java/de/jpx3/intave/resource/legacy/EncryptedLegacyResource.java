package de.jpx3.intave.resource.legacy;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.klass.trace.Caller;
import de.jpx3.intave.klass.trace.PluginInvocation;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Deprecated
public final class EncryptedLegacyResource implements LegacyResource {
  private static final int CLASS_VERSION = 4;
  private final String name;
  private final boolean versionDependent;

  private FileLock lock;
  private FileChannel lockChannel;

  public EncryptedLegacyResource(String name, boolean versionDependent) {
    this.name = name;
    this.versionDependent = versionDependent;
  }

  public InputStream read() {
    if (!fileStore().exists()) {
      throw new IllegalStateException();
    }
    fileStore().setLastModified(System.currentTimeMillis());
    PluginInvocation pluginInvocation = Caller.pluginInfo(false);
    if (pluginInvocation != null && !"Intave".equals(pluginInvocation.pluginName())) {
      throw new IllegalStateException("Unable to access resource file \"" + resourceId() + "\", is it corrupted?");
    }
    FileChannel fileInputStream = acquireInputFileChannel();
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      fileInputStream.transferTo(0, Long.MAX_VALUE, Channels.newChannel(byteArrayOutputStream));
      ByteBuffer byteBuffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
      byte[] iv = new byte[byteBuffer.getInt()];
      byteBuffer.get(iv);
      KeySpec spec = new PBEKeySpec("adXUOhsZW7H5m4dlOyrNV7ZvHBBB071Sy2jCiuUZ91QMAcYyexjxwDQmXL1LR1nV".toCharArray(), iv, 65536, 128); // AES-128
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
      SecretKey secretKey = new SecretKeySpec(key, "AES");
      byte[] cipherBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherBytes);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
      return new ByteArrayInputStream(cipher.doFinal(cipherBytes));
    } catch (Exception | Error throwable) {
      fileStore().delete();
      throw new IntaveInternalException("Unable to access resource file \"" + resourceId() + "\" (\"" + name + "\"), is it corrupted?", throwable);
    } finally {
      removeFileLock(fileInputStream);
    }
  }

  public boolean write(InputStream inputStream) {
    File file = fileStore();
    if (file.exists()) {
      file.delete();
    }
    try {
      file.createNewFile();
    } catch (IOException exception) {
      exception.printStackTrace();
      return false;
    }
    PluginInvocation pluginInvocation = Caller.pluginInfo(false);
    if (pluginInvocation == null || !"Intave".equals(pluginInvocation.pluginName())) {
      throw new IllegalStateException("Unable to access resource file \"" + resourceId() + "\", is it corrupted?");
    }
    // lock file early
    FileChannel fileChannel = acquireOutputFileChannel();
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
      KeySpec spec = new PBEKeySpec("adXUOhsZW7H5m4dlOyrNV7ZvHBBB071Sy2jCiuUZ91QMAcYyexjxwDQmXL1LR1nV".toCharArray(), iv, 65536, 128); // AES-128
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
      fileChannel.transferFrom(byteChannel, 0, Long.MAX_VALUE);
      file.setLastModified(System.currentTimeMillis());
    } catch (Exception exception) {
//      exception.printStackTrace();
      return false;
    } finally {
      removeFileLock(fileChannel);
    }
    return file.exists();
  }

  public void write(byte[] bytes) {
    write(new ByteArrayInputStream(bytes));
  }

  public void write(String string) {
    write(string.getBytes(StandardCharsets.UTF_8));
  }

  public void write(Collection<String> lines) {
    write(String.join(System.lineSeparator(), lines));
  }

  private FileChannel acquireInputFileChannel() {
    acquireFileChannel();
    FileInputStream in;
    try {
      in = new FileInputStream(fileStore());
      return in.getChannel();
    } catch (FileNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private FileChannel acquireOutputFileChannel() {
    acquireFileChannel();
    FileOutputStream in;
    try {
      in = new FileOutputStream(fileStore());
      return in.getChannel();
    } catch (FileNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private void acquireFileChannel() {
    File file = fileStore();
    File lockFile = new File(file + ".sig");
    try {
      lockFile.createNewFile();
      RandomAccessFile accessFile = new RandomAccessFile(lockFile, "rw");
      lockChannel = accessFile.getChannel();
      String hash = String.valueOf(ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE));
      lockChannel.write(ByteBuffer.wrap(hash.getBytes(StandardCharsets.UTF_8)));
      lock = lockChannel.lock();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void removeFileLock(FileChannel channel) {
    File file = fileStore();
    File lockFile = new File(file + ".sig");
    try {
      channel.close();
      lock.close();
      lockChannel.close();
      if (!lockFile.delete()) {
        lockFile.deleteOnExit();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public boolean exists() {
    File file = fileStore();
    return file.exists();
  }

  public File fileStore() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/";
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return new File(workDirectory, resourceId());
  }

  private String resourceId() {
    return new UUID(~name.hashCode() | (CLASS_VERSION | CLASS_VERSION << 2), versionDependent ? ~intaveVersion().hashCode() : -391180952) + "e";
  }

  private String intaveVersion() {
    return IntavePlugin.fullVersion();
  }
}
