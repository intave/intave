package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigSelectionResolver {
  public ConfigSelection resolve() {
    File dataFolder = IntavePlugin.singletonInstance().dataFolder();
    File settingsFile = new File(dataFolder, "settings.yml");
    if (settingsFile.exists()) {
      return ConfigSelection.LEGACY;
    }
    Resource configResource = Resources.resourceFromFile(new File(dataFolder, "config.yml"));
    Resource configResourceInClasspath = Resources.resourceFromJarOrBuild("config.yml");
    if (!configResource.available()) {
      try (InputStream read = configResourceInClasspath.read()) {
        configResource.write(read);
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
      configResource = configResourceInClasspath;
    }
    YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(configResource.read()));
    String configType = config.getString("config", "LEGACY");
    return ConfigSelection.from(configType);
  }

}
