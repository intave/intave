package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class AdvancedConfigurationLoader implements ConfigurationLoader {
  private final IntavePlugin plugin;

  public AdvancedConfigurationLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public YamlConfiguration fetchConfiguration() {
    File simpleConfigFile = new File(plugin.dataFolder(), "config.yml");
    ConfigurationRecovery.loadConfiguration(simpleConfigFile, "config.yml");
    Resource simpleConfig = Resources.resourceFromFile(simpleConfigFile);
    File advancedConfigFile = new File(plugin.dataFolder(), "advanced.yml");
    Resource advancedConfig = Resources.resourceFromFile(advancedConfigFile);
    Resource advancedConfigInClasspath = Resources.resourceFromJarOrBuild("advanced.yml");
    if (!advancedConfig.available()) {
      try (InputStream read = advancedConfigInClasspath.read()) {
        advancedConfig.write(read);
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
      Resource conversionData = Resources.resourceFromJarOrBuild("ctvs.mx");
      SimpleToAdvancedConfigConverter converter = new SimpleToAdvancedConfigConverter(
        simpleConfig, advancedConfig, conversionData
      );
      converter.convert();
    }
    return ConfigurationRecovery.loadConfiguration(advancedConfigFile, "advanced.yml");
  }
}
