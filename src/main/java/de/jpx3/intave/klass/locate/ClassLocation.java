package de.jpx3.intave.klass.locate;

import de.jpx3.intave.klass.Lookup;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public final class ClassLocation extends Location {
  private final static Reference<Class<?>> EMPTY_CLASS_REFERENCE = new WeakReference<>(null);
  private final String location;
  private Reference<Class<?>> classCache = EMPTY_CLASS_REFERENCE;

  public ClassLocation(String name, IntegerMatcher versionMatcher, String location) {
    super(name, versionMatcher);
    this.location = location;
  }

  public Class<?> access() {
    Class<?> klass = classCache.get();
    if (klass == null) {
      klass = compile();
      classCache = new WeakReference<>(klass);
    }
    return klass;
  }

  private Class<?> compile() {
    try {
      return Class.forName(compiledLocation());
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public String compiledLocation() {
    return location.replace("{version}", Lookup.version());
  }

  @Override
  public String toString() {
    return "ClassLocation{" +
      key() + " -> " + compiledLocation() +
      '}';
  }

  public static ClassLocation defaultFor(String name) {
    return new ClassLocation(name, IntegerMatcher.between(8, 16), "net.minecraft.server.{version}." + name);
  }
}
