package de.jpx3.intave.resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClasspathResource implements Resource {

  private final String path;
  private final Resource fallback;

  private final Object lock = new Object();
  private volatile byte[] cachedBytes;

  public ClasspathResource(String path, Resource fallback) {
    this.path = path;
    this.fallback = fallback;
  }

  public ClasspathResource(String path) {
    this(path, null);
  }

  @Override
  public boolean available() {
    if (cachedBytes != null) {
      return true;
    }

    ClassLoader classLoader = ClasspathResource.class.getClassLoader();

    if (classLoader.getResource(path) != null) {
      return true;
    }

    return fallback != null && fallback.available();
  }

  @Override
  public long lastModified() {
    return 0;
  }

  @Override
  public void write(InputStream inputStream) {
    throw new UnsupportedOperationException("Cannot write to classpath resource");
  }

  @Override
  public InputStream read() {
    byte[] bytes = cachedBytes;

    if (bytes == null) {
      synchronized (lock) {
        bytes = cachedBytes;
        if (bytes == null) {
          bytes = loadBytes();
          cachedBytes = bytes;
        }
      }
    }

    return new ByteArrayInputStream(bytes);
  }

  private byte[] loadBytes() {
    ClassLoader classLoader = ClasspathResource.class.getClassLoader();

    InputStream stream = classLoader.getResourceAsStream(path);

    if (stream == null && fallback != null) {
      if (!fallback.available()) {
        throw new IllegalStateException(
            "Classpath resource missing and fallback unavailable: " + path
        );
      }
      stream = fallback.read();
    }

    if (stream == null) {
      throw new IllegalStateException("Classpath resource is missing: " + path);
    }

    try (InputStream inputStream = stream) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      byte[] buffer = new byte[4096];
      int read;

      while ((read = inputStream.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }

      return out.toByteArray();

    } catch (IOException e) {
      throw new IllegalStateException(
          "Unable to read classpath resource " + path,
          e
      );
    }
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException("Cannot delete classpath resource");
  }
}