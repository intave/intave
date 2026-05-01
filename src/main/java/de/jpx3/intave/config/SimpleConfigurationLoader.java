package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class SimpleConfigurationLoader implements ConfigurationLoader {
  private final IntavePlugin plugin;

  public SimpleConfigurationLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public YamlConfiguration fetchConfiguration() {
    // use the config.yml file to build a advanced.yml config, and load that
    File simpleConfigFile = new File(plugin.dataFolder(), "config.yml");
    ConfigurationRecovery.loadConfiguration(simpleConfigFile, "config.yml");
    Resource simpleConfig = Resources.resourceFromFile(simpleConfigFile);
    Resource advancedConfig = Resources.resourceFromJarOrBuild("advanced.yml");
    Resource conversionData = Resources.resourceFromJarOrBuild("ctvs.mx");
    Resource cache = Resources.memoryResource();
    cache.write(advancedConfig);
    SimpleToAdvancedConfigConverter converter = new SimpleToAdvancedConfigConverter(
      simpleConfig, cache, conversionData
    );
    converter.convert();
    YamlConfiguration configuration = new YamlConfiguration();
    try {
      configuration.loadFromString(cache.readAsString());
    } catch (InvalidConfigurationException exception) {
      throw new RuntimeException("Unable to convert simple configuration", exception);
    }
    return configuration;
  }
}
