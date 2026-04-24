package de.jpx3.intave.resource;

import de.jpx3.intave.IntavePlugin;

import java.io.*;
import java.util.Locale;
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

  public static Resource resourceFromFileWithLock(File file) {
    return resourceFromFile(file).locked(file);
  }

  public static Resource resourceFromFileWithHashAndLock(File file) {
    return resourceFromFile(file).locked(file);
  }

  public static Resource memoryResource() {
    return new MemoryResource();
  }

  static Resource withLockingFile(File targetFile, Resource resource) {
    return new LockingLayer(targetFile, resource);
  }

  static Resource refreshFileAccessDateOnRead(File targetFile, Resource resource) {
    return new FileAccessTimeRefreshLayer(resource, targetFile);
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
    return withFileSpread(cacheFileLocationOf(nameFrom(identifier)), Resources::resourceFromFileWithLock, 8);
  }

  public static Resource cacheResourceChain(
    String urlString,
    String identifier,
    long expires
  ) {
    Resource cache = withFileSpread(cacheFileLocationOf(nameFrom(identifier)), Resources::resourceFromFileWithLock, 8);
    ResourceRegistry.registerResource(identifier, cache);
    return cache;
  }

  public static Resource localServiceCacheResource(
    String localPath,
    String identifier,
    long expires
  ) {
    Resource cache = withFileSpread(cacheFileLocationOf(nameFrom(identifier)), Resources::resourceFromFileWithLock, 8);
    Resource local = resourceFromJar(localPath);
    Resource resourceCache = new ResourceCache(cache, local, expires).retryReads(2);
    ResourceRegistry.registerResource(identifier, resourceCache);
    return resourceCache;
  }

  private static String nameFrom(String identifier) {
    UUID uuid = UUID.nameUUIDFromBytes((IntavePlugin.version() + ":" + identifier).getBytes(UTF_8));
    return uuid.toString().replace("-", "")
      .replace("f", "r")
      .replace("e", "y")
      .replace("c", "i");
  }

  static InputStream subscribeToClose(InputStream initial, Runnable onClose) {
    return new FilterInputStream(initial) {
      boolean closed = false;

      @Override
      public void close() throws IOException {
        if (closed) {
          return;
        }
        closed = true;
        try {
          super.close();
        } finally {
          onClose.run();
        }
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
        if (closed) {
          return;
        }
        closed = true;
        try {
          super.close();
        } finally {
          onClose.run();
        }
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
