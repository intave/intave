package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;

public final class SimpleConfigurationLoader implements ConfigurationLoader {
  private final IntavePlugin plugin;

  public SimpleConfigurationLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public YamlConfiguration fetchConfiguration() {
    // use the config.yml file to build a advanced.yml config, and load that
    Resource simpleConfig = Resources.resourceFromFile(new File(plugin.dataFolder(), "config.yml"));
    Resource simpleConfigInClasspath = Resources.resourceFromJarOrBuild("config.yml");
    if (!simpleConfig.available()) {
      simpleConfig.write(simpleConfigInClasspath.read());
      simpleConfig = simpleConfigInClasspath;
    }
    Resource advancedConfig = Resources.resourceFromJarOrBuild("advanced.yml");
    Resource conversionData = Resources.resourceFromJarOrBuild("ctvs.mx");
    Resource cache = Resources.memoryResource();
    cache.write(advancedConfig);
    SimpleToAdvancedConfigConverter converter = new SimpleToAdvancedConfigConverter(
      simpleConfig, cache, conversionData
    );
    converter.convert();
    return YamlConfiguration.loadConfiguration(new InputStreamReader(cache.read()));
  }
}
