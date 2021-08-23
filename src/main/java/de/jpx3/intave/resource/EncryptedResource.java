package de.jpx3.intave.resource;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.reflect.caller.CallerResolver;
import de.jpx3.intave.reflect.caller.PluginInvocation;
import de.jpx3.intave.security.ContextSecrets;
import de.jpx3.intave.security.HashAccess;
import de.jpx3.intave.tool.AccessHelper;

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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class EncryptedResource implements Resource {
  private final static int CLASS_VERSION = 4;
  private final String name;
  private final boolean versionDependent;

  private FileLock lock;
  private FileChannel lockChannel;

  public EncryptedResource(String name, boolean versionDependent) {
    this.name = name;
    this.versionDependent = versionDependent;
  }

  @Native
  public InputStream read() {
    if (!fileStore().exists()) {
      throw new IllegalStateException();
    }
    fileStore().setLastModified(AccessHelper.now());
    PluginInvocation pluginInvocation = CallerResolver.callerPluginInfo();
    if (pluginInvocation != null && !pluginInvocation.pluginName().equals("Intave")) {
      throw new IllegalStateException("Unable to access resource file \"" + resourceId() + "\", is it corrupted?");
    }
    try {
      FileChannel fileInputStream = acquireInputFileChannel();
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
      fileInputStream.close();
      removeFileLock(fileInputStream);
      return new ByteArrayInputStream(cipher.doFinal(cipherBytes));
    } catch (Exception | Error throwable) {
      throw new IntaveInternalException("Unable to access resource file \"" + resourceId() + "\" (\"" + name + "\"), is it corrupted?", throwable);
    }
  }

  @Native
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
    PluginInvocation pluginInvocation = CallerResolver.callerPluginInfo();
    if (pluginInvocation == null || !pluginInvocation.pluginName().equals("Intave")) {
      throw new IllegalStateException("Unable to access resource file \"" + resourceId() + "\", is it corrupted?");
    }
    try {
      // lock file early
      FileChannel fileChannel = acquireOutputFileChannel();
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
      file.setLastModified(AccessHelper.now());
      fileChannel.close();
      removeFileLock(fileChannel);
    } catch (Exception exception) {
//      exception.printStackTrace();
      return false;
    }
    return file.exists();
  }

  @Native
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

  @Native
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

  @Native
  private void acquireFileChannel() {
    File file = fileStore();
    File lockFile = new File(file + ".sig");
    try {
      lockFile.createNewFile();
      RandomAccessFile accessFile = null;
      Exception exceptionReserve = null;
      int k = 4 * 8;
      while (k-- > 0) { // god why
        try {
          accessFile = new RandomAccessFile(lockFile, "rw");
          break;
        } catch (Exception exception) {
          exceptionReserve = exception;
          try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(125, 350));
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
      if (accessFile == null) {
        throw new IllegalStateException(exceptionReserve);
      }
      lockChannel = accessFile.getChannel();
      String hash = HashAccess.hashOf(file);
      lockChannel.write(ByteBuffer.wrap(hash.getBytes(StandardCharsets.UTF_8)));
      lock = lockChannel.lock();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Native
  private void removeFileLock(FileChannel channel) {
    File file = fileStore();
    File lockFile = new File(file + ".sig");
    try {
      channel.close();
      lock.close();
      lockChannel.close();
      lockFile.delete();
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
      if (GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory");
      } else {
        filePath = System.getProperty("user.home") + "/.intave/";
      }
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return new File(workDirectory, resourceId());
  }

  @Native
  private String resourceId() {
    return new UUID(~name.hashCode() | (CLASS_VERSION | CLASS_VERSION << 2), versionDependent ? ~intaveVersion().hashCode() : -391180952).toString() + "e";
  }

  private String intaveVersion() {
    return IntavePlugin.version();
  }
}
