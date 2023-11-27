package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public enum ConfigSelection {
  SIMPLE(SimpleConfigurationLoader::new,"this", "simple"),
  ADVANCED(AdvancedConfigurationLoader::new, "advanced", "adv"),
  ONLINE(OnlineConfigurationLoader::new, "online", "on", "cloud", "cloud-based"),
  LEGACY(LegacyConfigurationLoader::new, "legacy", "old");


  final List<String> alia;
  final Function<IntavePlugin, ? extends ConfigurationLoader> loader;

  ConfigSelection(String... alia) {
    this.alia = Arrays.asList(alia);
    this.loader = null;
  }

  ConfigSelection(Function<IntavePlugin, ? extends ConfigurationLoader> loader, String... alia) {
    this.alia = Arrays.asList(alia);
    this.loader = loader;
  }

  public ConfigurationLoader loader() {
    return loader.apply(IntavePlugin.singletonInstance());
  }

  public static ConfigSelection from(String name) {
    if (name.contains("/")) {
      name = name.split("/")[0];
    }
    for (ConfigSelection value : values()) {
      if (value.name().equalsIgnoreCase(name)) {
        return value;
      }
      if (value.alia.contains(name.toLowerCase())) {
        return value;
      }
    }
    return SIMPLE;
  }
}
