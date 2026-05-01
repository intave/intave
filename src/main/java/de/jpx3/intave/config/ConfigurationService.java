package de.jpx3.intave.config;

import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigurationService {

  private final ConfigSelectionResolver resolver = new ConfigSelectionResolver();

  private ConfigurationLoader loader;
  private YamlConfiguration configuration;

  public void init() {
    loadResolvedConfiguration();
  }

  public YamlConfiguration configuration() {
    if (configuration == null) {
      throw new IllegalStateException("ConfigurationService not initialized or already shutdown");
    }
    return configuration;
  }

  public void reload() {
    loadResolvedConfiguration();
  }

  public void shutdown() {
    if (loader instanceof AutoCloseable) {
      try {
        ((AutoCloseable) loader).close();
      } catch (Exception ignored) {
        // intentionally ignored: shutdown must be best-effort
      }
    }

    loader = null;
    configuration = null;
  }

  private void loadResolvedConfiguration() {
    ConfigSelection selection = resolver.resolve();

    ConfigurationLoader resolvedLoader = selection.loader();
    YamlConfiguration resolvedConfiguration = resolvedLoader.fetchConfiguration();

    this.loader = resolvedLoader;
    this.configuration = resolvedConfiguration;
  }
}