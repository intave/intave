package de.jpx3.intave.reflect.patchy;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.access.IntaveInternalException;

import java.util.*;
import java.util.function.Consumer;

public final class PatchyClassSwitchLoader<T> {
  private final String baseClassName;
  private final List<MinecraftVersion> minecraftVersions;
  private final MinecraftVersion requiredVersion;
  private final MinecraftVersion maximumVersion;

  private PatchyClassSwitchLoader(String baseClassName, MinecraftVersion requiredVersion, MinecraftVersion maximumVersion, MinecraftVersion... versions) {
    this.baseClassName = baseClassName;
    this.requiredVersion = requiredVersion;
    this.maximumVersion = maximumVersion;
    this.minecraftVersions = Arrays.asList(versions);
  }

  public boolean available() {
    return (requiredVersion == null || MinecraftVersion.atOrAbove(requiredVersion)) &&
      (maximumVersion == null || maximumVersion.compareTo(MinecraftVersion.getCurrentVersion()) <= 0);
  }

  public void loadIfAvailable() {
    if (available()) {
      load();
    }
  }

  public void loadIfAvailable(Consumer<Class<?>> callback) {
    if (available()) {
      callback.accept(load());
    }
  }

  public Class<T> load() {
    ClassLoader classLoader = PatchyClassSwitchLoader.class.getClassLoader();
    return PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, className());
  }

  public void instantiateIfAvailable(Consumer<T> callback) {
    if (available()) {
      callback.accept(newInstance());
    }
  }

  public T newInstance() {
    if (!available()) {
      throw new IllegalStateException("Class from switch not available");
    }
    Class<?> load = load();
    try {
      //noinspection unchecked
      return (T) load.newInstance();
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new IntaveInternalException("Failed to load " + className(), exception);
    }
  }

  private String className() {
    if (!baseClassName.contains("{ver}")) {
      throw new IllegalStateException("Base class name does not contain ver placeholder");
    }
    String version = nameOf(selectedVersion().orElseThrow(MissingVersionTranslationException::new));
    String className = baseClassName.replace("{ver}", version);
    return className;
  }

  private Optional<MinecraftVersion> selectedVersion() {
    return minecraftVersions.stream().filter(version -> version.atOrAbove()).max(Comparator.naturalOrder());
  }

  private String nameOf(MinecraftVersion version) {
    int major = version.getMajor();
    int minor = version.getMinor();
    int build = version.getBuild();
    StringBuilder builder = new StringBuilder();
    builder.append(minor);
    if (build > 0) {
      builder.append("b").append(build);
    }
    return builder.toString();
  }

  public static <K> Builder<K> builderFor(String classScheme) {
    return new Builder<>(classScheme);
  }

  public static <K> Builder<K> builderFor(Class<K> klass, String classScheme) {
    return new Builder<>(classScheme);
  }

  public static class Builder<K> {
    private final String classScheme;
    private final List<MinecraftVersion> versions = new ArrayList<>();
    private MinecraftVersion requiredVersion, maximumVersion;

    public Builder(String classScheme) {
      this.classScheme = classScheme;
    }

    public Builder<K> withVersions(MinecraftVersion... versions) {
      Arrays.stream(versions).forEach(this::withVersion);
      return this;
    }

    public Builder<K> withVersion(MinecraftVersion version) {
      versions.add(version);
      return this;
    }

    public Builder<K> requires(MinecraftVersion version) {
      this.requiredVersion = version;
      return this;
    }

    public Builder<K> ignoreFrom(MinecraftVersion version) {
      this.maximumVersion = version;
      return this;
    }

    public PatchyClassSwitchLoader<K> complete() {
      return new PatchyClassSwitchLoader<K>(classScheme, requiredVersion, maximumVersion, versions.toArray(new MinecraftVersion[0]));
    }
  }
}
