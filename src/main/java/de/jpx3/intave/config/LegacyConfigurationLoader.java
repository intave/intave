package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;

public class LegacyConfigurationLoader implements ConfigurationLoader {
  private final IntavePlugin plugin;

  public LegacyConfigurationLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public YamlConfiguration fetchConfiguration() {
    File dataFolder = IntavePlugin.singletonInstance().dataFolder();
    File settingsFile = new File(dataFolder, "settings.yml");
    Resource config = Resources.resourceFromFile(settingsFile);
    return YamlConfiguration.loadConfiguration(new InputStreamReader(config.read()));
  }
}
