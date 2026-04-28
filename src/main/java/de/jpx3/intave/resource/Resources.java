package de.jpx3.intave.resource;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.IntaveDomains;
import de.jpx3.intave.library.asm.ByteVector;
import de.jpx3.intave.security.HashAccess;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class Resources {
  public static Resource resourceFromFile(File file) {
    return new FileResource(file);
  }

  public static Resource resourceFromJar(String path) {
    return new ClasspathResource(path);
  }

  public static Resource resourceFromJarWithFallback(String path, Resource fallback) {
    return new ClasspathResource(path, fallback);
  }

  public static Resource resourceFromJarOrBuild(String path) {
    return resourceFromJarWithFallback(path, resourceFromFile(new File("src/main/java/resources/" + path)));
  }

  public static Resource hashProtected(String path, Resource target) {
    Resource hashResource = Resources.resourceFromFile(new File(path + ".hash"));
    return new HashProtectedLayer(path, target, hashResource);
  }

  public static Resource resourceFromFileWithLock(File file) {
    return resourceFromFile(file).locked(file);
  }

  public static Resource resourceFromFileWithHashAndLock(File file) {
    return resourceFromFile(file).hashProtected(file).locked(file);
  }

  public static Resource resourceFromWeb(URL url) {
    return new WebResource(url);
  }

  public static Resource resourceFromWebWithFallback(URL url, Resource fallback) {
    return new WebResource(url, fallback);
  }

  public static Resource memoryResource() {
    return new MemoryResource();
  }

  public static Resource resourceFromOneOf(URL[] urls) {
    Resource previous = null;
    for (int i = urls.length - 1; i >= 0; i--) {
      previous = resourceFromWebWithFallback(urls[i], previous);
    }
    return previous;
  }

  static Resource withLockingFile(File targetFile, Resource resource) {
    return new LockingLayer(targetFile, resource);
  }

  static Resource refreshFileAccessDateOnRead(File targetFile, Resource resource) {
    return new FileAccessTimeRefreshLayer(resource, targetFile);
  }

  static Resource withEncryption(Resource resource) {
    return new EncryptionLayer(resource);
  }

  static Resource withCompression(Resource resource) {
    // add later
    return new CompressionLayer(resource);
  }

  static Resource retryRead(Resource resource, int retries) {
    return new RetryReadLayer(resource, retries);
  }

  static Resource withFileSpread(File file, Function<File, Resource> resourcer, int spreads) {
    return new FileSpreadLayer(file, resourcer, spreads);
  }

  public static Resource fileCache(
    String identifier
  ) {
    try {
      String name = nameFrom(new URL("https://google.com"), identifier, Long.MAX_VALUE);
      return withFileSpread(cacheFileLocationOf(name), Resources::resourceFromFileWithLock, 8).encrypted();
    } catch (MalformedURLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static Resource cacheResourceChain(
    String urlString,
    String identifier,
    long expires
  ) {
    URL url;
    try {
      url = new URL(urlString);
    } catch (MalformedURLException exception) {
      throw new IllegalStateException(exception);
    }
    File initialFile = cacheFileLocationOf(nameFrom(url, identifier, expires));
    Resource cache = withFileSpread(initialFile, Resources::resourceFromFileWithLock, 8).encrypted();
    Resource access = resourceFromWeb(url);
    Resource resourceCache = new ResourceCache(cache, access, expires).retryReads(2);
    ResourceRegistry.registerResource(identifier, resourceCache);
    return resourceCache;
  }

  public static Resource localServiceCacheResource(
    String localPath,
    String identifier,
    long expires
  ) {
    return cacheResourceChainWithMultipleDomains(
      "https://{DOMAIN}/" + localPath,
      IntaveDomains.serviceDomains(),
      identifier, expires
    );
  }

  private static Resource cacheResourceChainWithMultipleDomains(
    String pattern,
    List<String> domains,
    String identifier,
    long expires
  ) {
    URL[] urls = new URL[domains.size()];
    for (int i = 0; i < urls.length; i++) {
      try {
        urls[i] = new URL(pattern.replace("{DOMAIN}", domains.get(i)));
      } catch (MalformedURLException exception) {
        throw new IllegalStateException(exception);
      }
    }
    File initialFile = cacheFileLocationOf(nameFrom(urls[0], identifier, expires));
    Resource cache = withFileSpread(initialFile, Resources::resourceFromFileWithLock, 8).encrypted();
    Resource access = resourceFromOneOf(urls);
    Resource resourceCache = new ResourceCache(cache, access, expires).retryReads(2);
    ResourceRegistry.registerResource(identifier, resourceCache);
    return resourceCache;
  }

  private static String nameFrom(URL url, String identifier, long expires) {
    long seed = expires % (1L << 32);
    seed *= 31;
    seed += identifier.hashCode();
    seed *= 31;
    seed += url.hashCode();
    seed *= 31;
    Random random = new Random();
    random.setSeed(seed);
    int lastInt = random.nextInt();
    for (int i = 0; i < identifier.length(); i++) {
      lastInt = Math.abs(random.nextInt(Math.abs(url.hashCode() ^ lastInt) + 1)) + 1;
    }
    random.nextInt(Math.abs(lastInt) + 1);
    random.nextInt(IntavePlugin.version().hashCode());
    long mostSigBits = ((long) Math.abs(identifier.hashCode()) ^ Math.abs(random.nextInt(Byte.MAX_VALUE))) | versionResourceKey();
    long leastSigBits = ((long) Math.abs(IntavePlugin.version().hashCode()) ^ Math.abs(random.nextInt(Short.MAX_VALUE))) << 32 | random.nextInt();
    UUID uuid = new UUID(mostSigBits, leastSigBits);
    return uuid.toString().replace("-", "")
      .replace("f", "r")
      .replace("e", "y")
      .replace("c", "i");
  }

  private static int fileHashCode = 0;

  private static long versionResourceKey() {
    long quarterYearsSinceEpoch = ByteVector.startTime / (1000L * 60 * 60 * 24 * 365 / 4);
    String asString = String.valueOf(quarterYearsSinceEpoch);
    Random random = new Random(quarterYearsSinceEpoch);
    // compute the hash of the string
    MessageDigest messageDigest;
    try {
      messageDigest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    // shuffle the string using the random
    byte[] bytes = asString.getBytes(UTF_8);
    for (int i = 0; i < bytes.length; i++) {
      int index = random.nextInt(bytes.length);
      byte temp = bytes[i];
      bytes[i] = bytes[index];
      bytes[index] = temp;
    }
    // insert random bytes into the string, using the random
    byte[] randomBytes = new byte[bytes.length];
    for (int i = 0; i < randomBytes.length; i++) {
      randomBytes[i] = (byte) random.nextInt();
    }
    messageDigest.update(randomBytes);
    messageDigest.update(bytes);
    byte[] digest = messageDigest.digest();
    StringBuilder stringBuilder = new StringBuilder();
    for (byte b : digest) {
      stringBuilder.append(String.format("%02x", b));
    }
    String quarterHash = stringBuilder.toString();
    return ((long) (short) fileHashCode & Math.abs(quarterHash.hashCode())) << Integer.SIZE | quarterYearsSinceEpoch << Integer.SIZE + Short.SIZE;
  }

  static InputStream subscribeToClose(InputStream initial, Runnable onClose) {
    return new FilterInputStream(initial) {
      boolean closed = false;

      @Override
      public void close() throws IOException {
        super.close();
        onClose.run();
        closed = true;
      }

      @Override
      protected void finalize() {
        if (!closed) {
          throw new IllegalStateException("InputStream was not closed");
        }
      }
    };
  }

  static OutputStream subscribeToClose(OutputStream initial, Runnable onClose) {
    return new FilterOutputStream(initial) {
      boolean closed = false;

      @Override
      public synchronized void close() throws IOException {
        super.close();
        if (closed) {
          return;
        }
        onClose.run();
        closed = true;
      }

      @Override
      protected void finalize() {
        if (!closed) {
          throw new IllegalStateException("OutputStream was not closed");
        }
      }
    };
  }

  private static File cacheFileLocationOf(String resourceId) {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Cache/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/cache/";
    }
    File workDirectory = new File(filePath + "/" + (resourceId.length() > 4 ? resourceId.substring(0, 4) : "????") + "/");
    if (!workDirectory.exists()) {
      workDirectory.mkdirs();
    }
    return new File(workDirectory, resourceId.length() > 4 ? resourceId.substring(4) : resourceId);
  }
}
