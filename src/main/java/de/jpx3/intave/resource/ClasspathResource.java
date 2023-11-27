package de.jpx3.intave.resource;

import java.io.InputStream;

public class ClasspathResource implements Resource {
  private final String path;
  private final Resource fallback;

  public ClasspathResource(String path, Resource fallback) {
    this.path = path;
    this.fallback = fallback;
  }

  public ClasspathResource(String path) {
    this(path, null);
  }

  @Override
  public boolean available() {
    ClassLoader classLoader = ClasspathResource.class.getClassLoader();
    return classLoader.getResource(path) != null || fallback != null && fallback.available();
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
    ClassLoader classLoader = ClasspathResource.class.getClassLoader();
    InputStream stream = classLoader.getResourceAsStream(path);
    return stream == null && fallback != null ? fallback.read() : stream;
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException("Cannot delete classpath resource");
  }
}
