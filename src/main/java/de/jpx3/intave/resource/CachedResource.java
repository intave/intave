package de.jpx3.intave.resource;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.security.ContextSecrets;
import de.jpx3.intave.security.HashAccess;
import de.jpx3.intave.tools.AccessHelper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

@SuppressWarnings({"UnusedReturnValue", "ResultOfMethodCallIgnored"})
public final class CachedResource {
  private final static String KEY = "AES/GCM/NoPadding";

  private final String name;
  private final String uri;
  private final long expireDuration;

  private FileLock lock;
  private FileChannel lockChannel;

  public CachedResource(
    String name, String uri,
    long expireDuration
  ) {
    this.name = name;
    this.uri = uri;
    this.expireDuration = expireDuration;
    this.prepareFile();
  }

  public boolean prepareFile() {
    File file = fileStore();
    long fileLastModified = AccessHelper.now() - file.lastModified();
    boolean invalidFile = !file.exists() || fileLastModified > expireDuration || file.length() == 0;

    if (invalidFile) {
      refreshFile();
    }
    return file.exists();
  }

  public List<String> readLines() {
    InputStream inputStream;
    try {
      inputStream = read();
    } catch (IllegalStateException exception) {
      refreshFile();
      inputStream = read();
    }
    Scanner scanner = new Scanner(inputStream, "UTF-8");
    List<String> lines = new ArrayList<>();
    while (scanner.hasNext()) {
      lines.add(scanner.next());
    }
    try {
      inputStream.close();
    } catch (IOException ignored) {}
    return lines;
  }

  public boolean available() {
    try {
      return read().available() > 0;
    } catch (IOException exception) {
      return false;
    }
  }

  @Native
  public InputStream read() {
    if (!fileStore().exists()) {
      return new ByteArrayInputStream(new byte[0]);
    }
    try {
      FileChannel fileInputChannel = acquireInputFileChannel();
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      fileInputChannel.transferTo(0, Long.MAX_VALUE, Channels.newChannel(byteArrayOutputStream));
      removeFileLock(fileInputChannel);
      fileInputChannel.close();
      ByteBuffer byteBuffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
      byte[] iv = new byte[byteBuffer.getInt()];
      byteBuffer.get(iv);
      KeySpec spec = new PBEKeySpec(KEY.toCharArray(), iv, 65536, 128); // AES-128
      SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      byte[] key = secretKeyFactory.generateSecret(spec).getEncoded();
      SecretKey secretKey = new SecretKeySpec(key, "AES");
      byte[] cipherBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(cipherBytes);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
      return new ByteArrayInputStream(cipher.doFinal(cipherBytes));
    } catch (Exception | Error exception) {
      exception.printStackTrace();
      fileStore().delete();
      return new ByteArrayInputStream(new byte[0]);
    }
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

  @Native
  private boolean refreshFile() {
    File file = fileStore();

    // try download
    try {
      URL remoteFileAddress = new URL(uri);
      URLConnection connection = remoteFileAddress.openConnection();
      connection.addRequestProperty("User-Agent", "Intave/" + IntavePlugin.version());
      connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
      connection.addRequestProperty("Pragma", "no-cache");
      connection.setConnectTimeout(2000);
      connection.setReadTimeout(1000);
      InputStream inputStream = connection.getInputStream();
      // connection was successful
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
      KeySpec spec = new PBEKeySpec(KEY.toCharArray(), iv, 65536, 128); // AES-128
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
      try {
        file.delete();
        file.createNewFile();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      FileChannel outputChannel = acquireOutputFileChannel();//new FileOutputStream(fileStore());
      outputChannel.transferFrom(byteChannel, 0, Long.MAX_VALUE);
      file.setLastModified(AccessHelper.now());
      removeFileLock(outputChannel);
      outputChannel.close();
    } catch (Exception exception) {
//      exception.printStackTrace();
      return false;
    }
    return file.exists();
  }

  private File fileStore() {
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
    return new UUID(~name.hashCode(), ~intaveVersion().hashCode()).toString() + "e";
  }

  private String intaveVersion() {
    return IntavePlugin.version();
  }
}
