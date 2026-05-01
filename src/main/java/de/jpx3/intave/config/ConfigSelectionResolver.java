package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigSelectionResolver {

  public ConfigSelection resolve() {
    File dataFolder = IntavePlugin.singletonInstance().dataFolder();

    // legacy override check (safe version)
    File legacyFile = new File(dataFolder, "settings.yml");
    if (legacyFile.exists() && legacyFile.isFile()) {
      return ConfigSelection.LEGACY;
    }

    File configFile = new File(dataFolder, "config.yml");

    YamlConfiguration config = ConfigurationRecovery.loadConfiguration(configFile, "config.yml");

    ConfigSelection selection = parseSelection(config.getString("config"));

    if (selection != null) {
      return selection;
    }

    // fallback safe default (no recursion recovery here)
    return ConfigSelection.LEGACY;
  }

  private ConfigSelection parseSelection(String raw) {
    if (raw == null) {
      return null;
    }

    try {
      return ConfigSelection.from(raw.trim().toUpperCase());
    } catch (Exception ignored) {
      return null;
    }
  }
}