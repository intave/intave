package de.jpx3.intave.library;

import java.net.URL;
import java.net.URLClassLoader;

public final class LibraryClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  public LibraryClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  public LibraryClassLoader(URL[] urls) {
    super(urls);
  }

  @Override
  protected void addURL(URL url) {
    super.addURL(url);
  }
}
