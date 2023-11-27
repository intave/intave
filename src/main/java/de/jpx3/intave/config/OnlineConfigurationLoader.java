package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

public class OnlineConfigurationLoader implements ConfigurationLoader {
  private final IntavePlugin plugin;

  public OnlineConfigurationLoader(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public YamlConfiguration fetchConfiguration() {
    throw new UnsupportedOperationException("Cannot fetch online configuration");
  }
}
