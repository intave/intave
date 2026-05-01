package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigSelectionResolver {
  public ConfigSelection resolve() {
    File dataFolder = IntavePlugin.singletonInstance().dataFolder();
    File settingsFile = new File(dataFolder, "settings.yml");
    if (settingsFile.exists()) {
      return ConfigSelection.LEGACY;
    }
    File configFile = new File(dataFolder, "config.yml");
    YamlConfiguration config = ConfigurationRecovery.loadConfiguration(configFile, "config.yml");
    String configType = config.getString("config", "LEGACY");
    ConfigSelection from = ConfigSelection.from(configType);
    if (from == null) {
      config = ConfigurationRecovery.recoverConfiguration(configFile, "config.yml", new IllegalArgumentException("Invalid config type: " + configType));
      from = ConfigSelection.from(config.getString("config", "LEGACY"));
      if (from == null) {
        throw new RuntimeException("Invalid default config type");
      }
    }
    return from;
  }

}
