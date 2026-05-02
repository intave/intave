package de.jpx3.classloader;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

public final class NativeLibrary {
  private final String name;
  private final int version;
  private final File tempDirectory;
  private final String baseDownloadURL;
  private final List<String> allowedHashes;

  public NativeLibrary(
    String name, int version,
    File tempDirectory,
    String baseDownloadURL,
    List<String> allowedHashes
  ) {
    this.name = name;
    this.version = version;
    this.tempDirectory = tempDirectory;
    this.baseDownloadURL = baseDownloadURL;
    this.allowedHashes = allowedHashes;
  }

  public void load() {
    try {
      prepareCache();
      File tempFile = copyCacheToTempFile();
      System.load(tempFile.getAbsolutePath());
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to load library " + name, exception);
    }
  }

  private File copyCacheToTempFile() throws IOException {
    File tempFile;
    int i = 0;
    do {
      tempFile = new File(tempDirectory, name + i + suffix());
    } while (tempFile.exists() && i++ < 100);
    tempFile.createNewFile();
    InputStream resourceAsStream = Files.newInputStream(cacheFile().toPath());
    FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
    int read;
    byte[] array = new byte[512];
    while ((read = resourceAsStream.read(array)) != -1) {
      fileOutputStream.write(array, 0, read);
    }
    fileOutputStream.close();
    resourceAsStream.close();
    return tempFile;
  }

  private void prepareCache() throws IOException, IllegalAccessException {
    if (!cacheFile().exists()) {
      // download
      tryDownload();
    }
    hashCheck();
  }

  private void hashCheck() throws IllegalAccessException {
    String hash = hashOf(cacheFile());
    boolean fileValid = allowedHashes.stream().anyMatch(hash::equalsIgnoreCase);
    if (!fileValid) {
      cacheFile().delete();
      throw new IllegalAccessException("Unknown " + name + " library (" + hash + ")");
    }
  }

  private String hashOf(File file) {
    StringBuilder jarChecksum = new StringBuilder();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");// MD5
      FileInputStream fis = new FileInputStream(file);
      byte[] dataBytes = new byte[1024];
      int nread;
      while ((nread = fis.read(dataBytes)) != -1) {
        md.update(dataBytes, 0, nread);
      }
      fis.close();
      byte[] mdbytes = md.digest();
      for (byte mdbyte : mdbytes) {
        jarChecksum.append(Integer.toString((mdbyte & 0xff) + 0x100, 16).substring(1));
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
    return jarChecksum.toString();
  }

  private void tryDownload() throws IOException {
    URL url = new URL(downloadUrl());
    URLConnection connection = url.openConnection();
    connection.addRequestProperty("User-Agent", "Intave/$VERSION$");
    connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
    connection.addRequestProperty("Pragma", "no-cache");
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(8000);
    connection.connect();
    InputStream inputStream = connection.getInputStream();
    ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
    cacheFile().getParentFile().mkdirs();
    cacheFile().createNewFile();
    FileChannel fileChannel = new FileOutputStream(cacheFile()).getChannel();
    fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
  }

  private String targetURL() {
    URLConnection con;
    try {
      URL url = new URL("https://raw.githubusercontent.com/intave/domains/main/service2");
      con = url.openConnection();
      con.setConnectTimeout(5000);
      con.setReadTimeout(5000);
      con.setUseCaches(false);
      con.setDefaultUseCaches(false);
    } catch (Exception exception) {
      return "service.intave.de";
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
      return reader.readLine();
    } catch (IOException e) {
      return "service.intave.de";
    }
  }

  public String downloadUrl() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("win")) {
      return baseDownloadURL + "classloader-" + suffix();
    }
    return baseDownloadURL + "libclassloader-" + suffix();
  }

  public String suffix() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT).replace("amd64", "x86_64");

    if (operatingSystem.contains("win")) {
      return "windows-" + arch + ".dll";
    } else if (operatingSystem.contains("mac")) {
      return "macos-" + arch + ".dylib";
    } else {
      return "linux-" + arch + ".so";
    }
  }

  private File cacheFile() {
    return new File(intaveFolder(), "classloader." + version + suffix());
  }

  private File intaveFolder() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/";
    }
    return new File(filePath);
  }
}
