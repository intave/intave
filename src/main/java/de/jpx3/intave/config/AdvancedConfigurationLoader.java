package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class AdvancedConfigurationLoader implements ConfigurationLoader {
  private final IntavePlugin plugin;

  public AdvancedConfigurationLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public YamlConfiguration fetchConfiguration() {
    Resource simpleConfig = Resources.resourceFromFile(new File(plugin.dataFolder(), "config.yml"));
    Resource simpleConfigInClasspath = Resources.resourceFromJarOrBuild("config.yml");
    if (!simpleConfig.available()) {
      try (InputStream read = simpleConfigInClasspath.read()) {
        simpleConfig.write(read);
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
      simpleConfig = simpleConfigInClasspath;
    }
    Resource advancedConfig = Resources.resourceFromFile(new File(plugin.dataFolder(), "advanced.yml"));
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
      advancedConfig = advancedConfigInClasspath;
    }
    return YamlConfiguration.loadConfiguration(new InputStreamReader(advancedConfig.read()));
  }
}
