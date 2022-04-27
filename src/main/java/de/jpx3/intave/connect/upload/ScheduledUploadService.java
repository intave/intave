package de.jpx3.intave.connect.upload;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.security.ContextSecrets;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.LicenseAccess;
import org.apache.commons.io.output.CountingOutputStream;
import org.bukkit.Bukkit;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class ScheduledUploadService {
  private static UUID temporaryId = null;

  private final static long STORAGE_BYTE_LIMIT = 1024 * 1024 * 64; // 64 MB
  private final Map<String, byte[]> storage = new HashMap<>();
  private long storageSize = 0;

  public void enable() {
    mergeFiles();
    ShutdownTasks.add(this::disable);
  }

  private void mergeFiles() {
    BackgroundExecutor.execute(this::uploadSessionFiles);
    // start timer for edge of next day
    Bukkit.getScheduler().scheduleSyncDelayedTask(IntavePlugin.singletonInstance(), this::mergeFiles, millisUntilNextDay() / 50);
  }

  public void scheduledUpload(String name, String data) throws IOException {
    scheduledUpload(name, new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
  }

  public void scheduledUpload(String name, byte[] data) throws IOException {
    scheduledUpload(name, new ByteArrayInputStream(data));
  }

  @Native
  public void scheduledUpload(String name, InputStream inputStream) throws IOException {
    // encrypt inputStream with public key
    if (storageSize + inputStream.available() > STORAGE_BYTE_LIMIT) {
      if (!temporaryFolderPresent()) {
        newTemporaryFolder();
      }
      storage.forEach((k, v) -> {
        try {
          copyToSession(k, v);
        } catch (IOException exception) {
          System.out.println("Failed to copy file to temp-directory: " + k);
          exception.printStackTrace();
        }
      });
      storage.clear();
      inputStream = compressAndEncrypt(inputStream);
      OutputStream outputStream = copyToSessionChannel(name);
      // copy inputStream to outputStream
      try (OutputStream output = new BufferedOutputStream(outputStream)) {
        byte[] buffer = new byte[2048];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
          output.write(buffer, 0, read);
        }
      }
      inputStream.close();
      storageSize = 0;
      return;
    }
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] copy = new byte[2048];
      int read;
      while ((read = inputStream.read(copy)) != -1) {
        buffer.write(copy, 0, read);
      }
      byte[] array = compressAndEncrypt(buffer.toByteArray());
      if (array == null) {
        return;
      }
      storage.put(name, array);
      storageSize += array.length;
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private InputStream compressAndEncrypt(InputStream data) {
    try {
      data = new DeflaterInputStream(data);
      String password = "Iloovestatistics";
      // encrypt data with AES key
      SecretKeySpec key = new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "AES");
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, key);
      return new CipherInputStream(data, cipher);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return null;
  }

  private byte[] compressAndEncrypt(byte[] data) {
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(buffer)) {
        deflaterOutputStream.write(data, 0, data.length);
        deflaterOutputStream.flush();
      }
      data = buffer.toByteArray();

      String password = "Iloovestatistics";
      // encrypt data with AES key
      SecretKeySpec key = new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "AES");
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, key);
      return cipher.doFinal(data);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return null;
  }

  public void disable() {
    mergeFilesToSessionFile();
    uploadSessionFiles();
  }

  private void mergeFilesToSessionFile() {
    File workingFolder = dataFolder();
    File sessionFile = new File(workingFolder, "X3-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
    sessionFile.getParentFile().mkdirs();
    boolean added = false;
    try {
      sessionFile.createNewFile();
    } catch (IOException exception) {
      exception.printStackTrace();
      return;
    }
    // create a zip file
    try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(sessionFile.toPath()))) {
      if (temporaryFolderPresent()) {
        File[] files = tempDirectory().listFiles();
        if (files != null) {
          for (File file : files) {
            zipOut.putNextEntry(new ZipEntry(file.getName()));
            try (FileInputStream in = new FileInputStream(file)) {
              int len;
              byte[] buffer = new byte[2048];
              while ((len = in.read(buffer)) != -1) {
                zipOut.write(buffer, 0, len);
              }
            }
            zipOut.closeEntry();
            file.delete();
            added = true;
          }
        }
        tempDirectory().delete();
        temporaryId = null;
      }
      for (Map.Entry<String, byte[]> entry : storage.entrySet()) {
        String k = entry.getKey();
        byte[] v = entry.getValue();
        zipOut.putNextEntry(new ZipEntry(k));
        zipOut.write(v);
        zipOut.closeEntry();
        added = true;
      }
      storage.clear();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (!added) {
      sessionFile.delete();
    }
  }

  private void uploadSessionFiles() {
    lock("X3-2-S");
    File workingFolder = dataFolder();
    File oldFile = new File(workingFolder, "X4-" + (currentDay() - 4));
    if (oldFile.exists()) {
      oldFile.delete();
    }
    File finalFile = new File(workingFolder, "X4-" + (currentDay() - 1));
    if (finalFile.exists()) {
      unlock("X3-2-S");
      return;
    } else {
      try {
        finalFile.createNewFile();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      URL url = new URL("https://service.intave.de/analytics/upload");
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/zip");
      connection.setRequestProperty("Identifier", LicenseAccess.rawLicense());
      connection.setRequestProperty("Hardware", HWIDVerification.publicHardwareIdentifier());
      OutputStream outputStream = connection.getOutputStream();
      CountingOutputStream filterOutputStream = new CountingOutputStream(outputStream);
      try (ZipOutputStream zipOut = new ZipOutputStream(filterOutputStream)) {
        File[] files = workingFolder.listFiles();
        if (files != null) {
          for (File file : files) {
            if (file.getName().startsWith("X3-")) {
              zipOut.putNextEntry(new ZipEntry(file.getName()));
              try (FileInputStream in = new FileInputStream(file)) {
                int len;
                byte[] buffer = new byte[2048];
                while ((len = in.read(buffer)) != -1) {
                  zipOut.write(buffer, 0, len);
                }
              }
              zipOut.closeEntry();
            }
          }
        }
      }
      long bytes = filterOutputStream.getByteCount();
      IntaveLogger.logger().info("Uploaded " + bytes + " bytes of analytics & usage data");
      InputStream inputStream = connection.getInputStream();
      StringBuilder response = new StringBuilder();
      Scanner scanner = new Scanner(inputStream);
      while (scanner.hasNextLine()) {
        response.append(scanner.nextLine());
      }
      scanner.close();
      if (!response.toString().equals("SUCCESS")) {
        throw new RuntimeException("Server error: " + response);
      }
    } catch (Exception e) {
      finalFile.delete();
      unlock("X3-2-S");
      throw new RuntimeException("Upload failed: " + e.getMessage());
    }
    File[] files = workingFolder.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.getName().startsWith("X3-")) {
          file.delete();
        }
      }
    }
    unlock("X3-2-S");
  }

  private final Map<String, File> lockFiles = new HashMap<>();
  private final Map<String, FileLock> locks = new HashMap<>();
  private final Map<String, FileChannel> lockChannels = new HashMap<>();

  public void lock(String key) {
    FileLock lock = null;
    FileChannel channel = null;
    try {
      File lockFile = new File(dataFolder(), "X0-" + key + ".lock");
      if (lockFile.exists() && System.currentTimeMillis() - lockFile.lastModified() > 5 * 60 * 1000) {
        try {
          lockFile.delete();
        } catch (Exception ignored) {}
      }
      int attemptsRemaining = 30 * 1000 / 50;
      while (lockFile.exists() && attemptsRemaining-- > 0) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException exception) {
          exception.printStackTrace();
        }
      }
      lockFile.delete();
      lockFile.createNewFile();
      lockFiles.put(key, lockFile);
      channel = new FileOutputStream(lockFile).getChannel();
      lock = channel.lock();
    } catch (IOException exception) {
      exception.printStackTrace();
    }
    locks.put(key, lock);
    lockChannels.put(key, channel);
  }

  public void unlock(String key) {
    File lockFile = lockFiles.remove(key);
    FileLock lock = locks.remove(key);
    if (lock != null) {
      try {
        lock.release();
      } catch (IOException exception) {
        exception.printStackTrace();
      }
    }
    FileChannel channel = lockChannels.remove(key);
    if (channel != null) {
      try {
        channel.close();
      } catch (IOException exception) {
        exception.printStackTrace();
      }
    }
    lockFile.delete();
  }

  private boolean temporaryFolderPresent() {
    return temporaryId != null;
  }

  private void newTemporaryFolder() {
    File file;
    do {
      temporaryId = UUID.randomUUID();
      file = tempDirectory();
    } while (file.exists());
    file.mkdir();
  }

  private void copyToSession(String name, byte[] data) throws IOException {
    File file = new File(tempDirectory(), name);
    file.createNewFile();
    file.setWritable(true);
    file.setReadable(true);
    try (java.io.FileOutputStream out = new FileOutputStream(file)) {
      out.write(data);
    }
  }

  private OutputStream copyToSessionChannel(String name) throws IOException {
    File file = new File(tempDirectory(), name);
    file.createNewFile();
    file.setWritable(true);
    file.setReadable(true);
    // push input stream to file output stream
    return new FileOutputStream(file);
  }

  private File tempDirectory() {
    // get the temp directory
    return new File(System.getProperty("java.io.tmpdir"), "intave-"+ temporaryId);
  }

  private long currentDay() {
    return System.currentTimeMillis() / (24 * 60 * 60 * 1000);
  }

  private long millisUntilNextDay() {
    return (24 * 60 * 60 * 1000) - (System.currentTimeMillis() % (24 * 60 * 60 * 1000));
  }

  public File dataFolder() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Data/";
    } else {
      if (GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory") + "data/";
      } else {
        filePath = System.getProperty("user.home") + "/.intave/data/";
      }
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    return workDirectory;
  }
}
