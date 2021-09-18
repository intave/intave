package de.jpx3.intave.block.variant;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class IndexedBlockVariant implements BlockVariant {
  private final Material type;
  private final Map<Setting<?>, Comparable<?>> nativeConfig;
  private final Map<String, Comparable<?>> namedConfig = new HashMap<>();
  private final Map<String, Setting<?>> namedSettings = new HashMap<>();

  IndexedBlockVariant(Material type, Map<Setting<?>, Comparable<?>> nativeConfig) {
    this.type = type;
    this.nativeConfig = nativeConfig;
    nativeConfig.keySet().forEach(setting -> namedSettings.put(setting.name().toLowerCase(Locale.ROOT), setting));
    nativeConfig.forEach((setting, comparable) -> namedConfig.put(setting.name().toLowerCase(Locale.ROOT), comparable));
  }

  public Comparable<?> propertyOf(String name) {
    return namedConfig.get(name);
  }

  @Override
  public <T extends Enum<T>> T enumProperty(Class<T> clazz, String name) {
    name = name.toLowerCase(Locale.ROOT);
    return ((UnknownEnumSetting) namedSettings.get(name)).enumType(clazz, (Integer) namedConfig.get(name));
  }
}
