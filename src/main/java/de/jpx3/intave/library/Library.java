package de.jpx3.intave.library;

import de.jpx3.intave.security.ContextSecrets;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class Library {
  private final String path;
  private final String name;
  private final String version;
  private final String repository;

  private final String suffix;

  public Library(String path, String name, String version, String repository) {
    this.path = path;
    this.name = name;
    this.version = version;
    this.repository = repository;
    this.suffix = "";
  }

  public Library(String path, String name, String version, String repository, String suffix) {
    this.path = path;
    this.name = name;
    this.version = version;
    this.repository = repository;
    this.suffix = suffix;
  }

  public boolean isInCache() {
    return cacheFile().exists();
  }

  public void downloadToCache() {
    try {
      // download via maven
      String inputURL = String.format(
        "%s/%s/%s/%s-%s.jar", repository,
        (path + "/" + name).replace(".", "/"),
        version, name,
        version + (suffix.isEmpty() ? "" :(suffix.startsWith("-") ? suffix : "-" + suffix))
      );
      URL url = new URL(inputURL);
      InputStream inputStream = url.openStream();
      File file = cacheFile();
      if (!file.exists()) {
        file.createNewFile();
      }

      List<String> hashAlgorithms = Arrays.asList("SHA-256", "SHA-1", "MD5");
      List<String> extensions = hashAlgorithms.stream().map(s -> s.replace("-", "").toLowerCase()).collect(Collectors.toList());
      List<MessageDigest> digests = hashAlgorithms.stream().map(s -> {
        try {
          return MessageDigest.getInstance(s);
        } catch (Exception noSuchAlgorithm) {
          return null;
        }
      }).filter(Objects::nonNull).collect(Collectors.toList());

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
//        System.out.println(hashResult);
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
        System.out.println("No matching hash found for " + name + " " + version + " in " + repository);
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

  private boolean reflectionsFailed = false;

  public void pushToClasspath() {
    try {
      if (!isInCache()) {
        throw new IllegalStateException("Library is not in cache");
      }
      File cacheFile = cacheFile();
      try {
        if (reflectionsFailed) {
          throw new Exception();
        }
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        addURL.setAccessible(true);
        addURL.invoke(systemClassLoader, cacheFile.toURI().toURL());
      } catch (Exception exception) {
        reflectionsFailed = true;
        // use unsafe
        Unsafe unsafe = prepareUnsafe();
        if (unsafe == null) {
          throw new IllegalStateException("Unsafe not found");
        }
        ClassLoader classLoader = Library.class.getClassLoader();
        Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
        long ucpOffset = unsafe.objectFieldOffset(ucpField);
        Object urlClassPath = unsafe.getObject(classLoader, ucpOffset);
        Field urlsField = urlClassPath.getClass().getDeclaredField("unopenedUrls");
        long urlsOffset = unsafe.objectFieldOffset(urlsField);
        //noinspection unchecked
        Queue<URL> urls = (Queue<URL>) unsafe.getObject(urlClassPath, urlsOffset);
        Field pathField = urlClassPath.getClass().getDeclaredField("path");
        long pathOffset = unsafe.objectFieldOffset(pathField);
        //noinspection unchecked
        List<String> paths = (List<String>) unsafe.getObject(urlClassPath, pathOffset);
        urls.add(cacheFile.toURI().toURL());
        paths.add(cacheFile.getAbsolutePath());
      }
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private Unsafe theUnsafe;

  private Unsafe prepareUnsafe() {
    if (theUnsafe == null) {
      try {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        theUnsafe = (Unsafe) f.get(null);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return theUnsafe;
  }

  private HashResult verifyHash(String extension, MessageDigest digest) {
    try {
      String inputURL = String.format(
        "%s/%s/%s/%s-%s.jar.%s", repository,
        (path + "/" + name).replace(".", "/"),
        version, name, version + (suffix.isEmpty() ? "" :(suffix.startsWith("-") ? suffix : "-" + suffix)), extension
      );
      URL url = new URL(inputURL);
      InputStream inputStream = url.openStream();
      byte[] hashBuffer = new byte[4096];
      int length;
      StringBuilder stringBuilder = new StringBuilder();
      while ((length = inputStream.read(hashBuffer)) > 0) {
        stringBuilder.append(new String(hashBuffer, 0, length));
      }
      String expectedHash = stringBuilder.toString();
      String calculatedHash = String.format("%040x", new java.math.BigInteger(1, digest.digest()));
      if (!expectedHash.equalsIgnoreCase(calculatedHash)) {
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
    File file = new File(workDirectory, String.format("/%s/%s/%s/%s.jar", path, name, version, name + (suffix.isEmpty() ? "" : (suffix.startsWith("-") ? suffix : "-" + suffix))));
    file.getParentFile().mkdirs();
    return file;
  }

  public String name() {
    return name;
  }

  public String suffix() {
    return suffix;
  }

  public String version() {
    return version;
  }

  public String path() {
    return path;
  }
}
