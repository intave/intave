package de.jpx3.intave.library;

import de.jpx3.intave.security.ContextSecrets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class Library {
  private final String path;
  private final String name;
  private final String version;
  private final String repository;

  public Library(String path, String name, String version, String repository) {
    this.path = path;
    this.name = name;
    this.version = version;
    this.repository = repository;
  }

  public boolean isInCache() {
    return cacheFile().exists();
  }

  public void downloadToCache() {
    try {
      // download via maven
      URL url = new URL(String.format("%s/%s/%s/%s-%s.jar", repository, (path + "/" + name).replace(".", "/"), version, name, version));
      InputStream inputStream = url.openStream();
      File file = cacheFile();
      if (!file.exists()) {
        file.createNewFile();
      }

      MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
      MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
      MessageDigest md5Digest = MessageDigest.getInstance("MD5");

      List<String> extensions = Arrays.asList("sha256", "sha1", "md5");
      List<MessageDigest> digests = Arrays.asList(sha256Digest, sha1Digest, md5Digest);
      FileOutputStream fileStream = new FileOutputStream(file);
      int length;
      byte[] buffer = new byte[4096];
      while ((length = inputStream.read(buffer)) > 0) {
        fileStream.write(buffer, 0, length);
        for (MessageDigest digest : digests) {
          digest.update(buffer, 0, length);
        }
      }

      boolean matchingHash = false;
      for (MessageDigest digest : digests) {
        if (matchingHash) {
          break;
        }
        HashResult hashResult = verifyHash(extensions.get(digests.indexOf(digest)), digest);
        System.out.println(hashResult);
        switch (hashResult) {
          case MATCH:
            matchingHash = true;
            break;
          case NO_MATCH:
            String string = String.format("Hash mismatch for %s %s in %s (%s/%s)", name, version, repository, digest.getAlgorithm(), extensions.get(digests.indexOf(digest)));
            System.out.println(string);
            inputStream.close();
            fileStream.close();
            file.delete();
            return;
          case NO_HASH:
            break;
        }
      }

      if (!matchingHash) {
        System.out.println("No matching hash found for " + name + " " + version + " in " + repository + "");
        inputStream.close();
        fileStream.close();
        file.delete();
        return;
      }

      inputStream.close();
      fileStream.close();
    } catch (Exception exception) {
      exception.printStackTrace();
      cacheFile().delete();
    }
  }

  private HashResult verifyHash(String extension, MessageDigest digest) {
    try {
      URL url = new URL(String.format("%s/%s/%s/%s-%s.jar.%s", repository, (path + "/" + name).replace(".", "/"), version, name, version, extension));
      InputStream inputStream = url.openStream();
      byte[] hashBuffer = new byte[4096];
      int length;
      StringBuilder stringBuilder = new StringBuilder();
      while ((length = inputStream.read(hashBuffer)) > 0) {
        stringBuilder.append(new String(hashBuffer, 0, length));
      }
      String sha1 = stringBuilder.toString();
      String sha1Hash = String.format("%040x", new java.math.BigInteger(1, digest.digest()));
      if (!sha1.equals(sha1Hash)) {
        return HashResult.NO_MATCH;
      }
    } catch (Exception exception) {
//      exception.printStackTrace();
      return HashResult.NO_HASH;
    }
    return HashResult.MATCH;
  }

  public enum HashResult {
    MATCH,
    NO_HASH,
    NO_MATCH
  }

  public InputStream read(String path) {
    try {
      if (!isInCache()) {
        downloadToCache();
      }
      return Files.newInputStream(new File(cacheFile(), path).toPath());
    } catch (Exception exception) {
      exception.printStackTrace();
      return null;
    }
  }

  public File cacheFile() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    File workDirectory;
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Libraries";
    } else {
      if (GOMME_MODE) {
        filePath = ContextSecrets.secret("cache-directory");
      } else {
        filePath = System.getProperty("user.home") + "/.intave/libraries";
      }
    }
    workDirectory = new File(filePath);
    if (!workDirectory.exists()) {
      workDirectory.mkdir();
    }
    File file = new File(workDirectory, String.format("/%s/%s/%s/%s.jar", path, name, version, name));
    file.getParentFile().mkdirs();
    return file;
  }

  public String version() {
    return version;
  }

  public String path() {
    return path;
  }


}
