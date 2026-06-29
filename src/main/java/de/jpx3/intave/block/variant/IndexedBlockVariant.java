package de.jpx3.intave.block.variant;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class IndexedBlockVariant implements BlockVariant {
  private final Material type;
  private final int variantIndex;
  private final Map<? extends Setting<?>, Comparable<?>> nativeConfig;
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
    for (Map.Entry<? extends Setting<?>, Comparable<?>> entry : nativeConfig.entrySet()) {
      Setting<?> setting = entry.getKey();
      Comparable<?> value = entry.getValue();
      String name = setting.name().toLowerCase(Locale.ROOT);
      namedSettings.put(name, setting);
      namedConfig.put(name, value);
    }
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
    String enumFieldName = (String) namedConfig.get(name);
    if (setting == null || enumFieldName == null) {
      return null;
    }
    if (!(setting instanceof EnumSetting)) {
      throw new IllegalStateException(type + "/" + name + " is not a enum property");
    }
    return ((EnumSetting) setting).enumType(klass, enumFieldName);
  }

  @Override
  public int index() {
    return variantIndex;
  }

  @Override
  public void dumpStates() {
	  for (Map.Entry<? extends Setting<?>, Comparable<?>> entry : nativeConfig.entrySet()) {
		  Setting<?> setting = entry.getKey();
		  Comparable<?> comparable = entry.getValue();
		  System.out.println("  " + setting.name() + ": " + comparable);
	  }
  }
}
