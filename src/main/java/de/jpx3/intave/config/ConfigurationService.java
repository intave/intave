package de.jpx3.intave.config;

import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigurationService {
  private final ConfigSelectionResolver resolver = new ConfigSelectionResolver();
  private ConfigurationLoader loader;
  private YamlConfiguration configuration;

  public void init() {
    ConfigSelection selection = resolver.resolve();
    loader = selection.loader();
    configuration = loader.fetchConfiguration();
  }

  public YamlConfiguration configuration() {
    return configuration;
  }

  public void shutdown() {
    // load config again on shutdown
    // this will generate an advanced.yml file when
    // selection changed while Intave was still running
    loader = resolver.resolve().loader();
    configuration = loader.fetchConfiguration();
  }
}
