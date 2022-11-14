package de.jpx3.intave.block.variant;

import org.bukkit.Material;

import java.util.*;

final class IndexedBlockVariant implements BlockVariant {
  private final Material type;
  private final int variantIndex;
  private final Map<? extends Setting<?>, Comparable<?>> nativeConfig;
  private final Map<String, Integer> baselineIndices = new HashMap<>();
  private final Map<String, Comparable<?>> namedConfig = new HashMap<>();
  private final Map<String, Setting<?>> namedSettings = new HashMap<>();

  IndexedBlockVariant(
    Material type,
    Map<? extends Setting<?>, Comparable<?>> nativeConfig,
    int variantIndex
  ) {
    this.type = type;
    this.variantIndex = variantIndex;
    this.nativeConfig = nativeConfig;
    nativeConfig.keySet().forEach(setting -> namedSettings.put(setting.name().toLowerCase(Locale.ROOT), setting));
    nativeConfig.forEach((setting, comparable) -> namedConfig.put(setting.name().toLowerCase(Locale.ROOT), comparable));
  }

  @Override
  public Set<String> propertyNames() {
    return namedConfig.keySet();
  }

  public <T> T propertyOf(String name) {
    //noinspection unchecked
    return (T) namedConfig.get(name);
  }

  @Override
  public <T extends Enum<T>> T enumProperty(Class<T> klass, String name) {
    name = name.toLowerCase(Locale.ROOT);
    Setting<?> setting = namedSettings.get(name);
    Integer enumIndex = (Integer) namedConfig.get(name);
    if (setting == null || enumIndex == null) {
      return null;
    }
    if (!(setting instanceof EnumSetting)) {
      throw new IllegalStateException(type + "/" + name + " is not a enum property");
    }
    return ((EnumSetting) setting).enumType(klass, enumIndex);
  }

  @Override
  public int index() {
    return variantIndex;
  }

  @Override
  public void dumpStates() {
    nativeConfig.forEach((setting, comparable) -> System.out.println("  " + setting.name() + ": " + comparable));
  }
}
