package de.jpx3.intave.klass.locate;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_16_2;
import static de.jpx3.intave.adapter.MinecraftVersions.VER1_8_0;

final class ClassLocation extends Location {
  private static final Reference<Class<?>> EMPTY_CLASS_REFERENCE = new SoftReference<>(null);
  private final String location;
  private Reference<Class<?>> classCache = EMPTY_CLASS_REFERENCE;

  public ClassLocation(String name, VersionMatcher versionMatcher, String location) {
    super(name, versionMatcher);
    this.location = location;
  }

  public Class<?> access() {
    Class<?> klass = classCache.get();
    if (klass == null) {
      klass = compile();
      classCache = new SoftReference<>(klass);
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
    return "{" +
      key() + " -> " + compiledLocation() +
      '}';
  }

  public static ClassLocation defaultFor(String name) {
    return new ClassLocation(
      name, VersionMatcher.between(VER1_8_0, VER1_16_2),
      "net.minecraft.server.{version}." + name
    );
  }
}
