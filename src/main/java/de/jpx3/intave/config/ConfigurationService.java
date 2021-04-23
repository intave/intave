package de.jpx3.intave.config;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveException;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.user.UserMetaClientData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;

public final class ConfigurationService {
  private final IntavePlugin plugin;
  private final ConfigurationLoader loader;
  private final String configurationKey;

  public ConfigurationService(IntavePlugin plugin) {
    this.plugin = plugin;
    this.configurationKey = resolveConfigurationKey();
    this.loader = new ConfigurationLoader(configurationKey);
  }

  private String resolveConfigurationKey() {
    File dataFolder = plugin.getDataFolder();
    if (!dataFolder.exists()) {
      dataFolder.mkdirs();
    }
    File configFile = new File(dataFolder, "config.yml");
    if (!configFile.exists()) {
      plugin.saveResource("config.yml", false);
    }
    try {
      FileInputStream fileInputStream = new FileInputStream(configFile);
      YamlConfiguration configuration = new YamlConfiguration();
      InputStreamReader reader = new InputStreamReader(fileInputStream);
      configuration.load(reader);
      fileInputStream.close();
      reader.close();
      String configurationIdentifier = configuration.getString("config-identifier");
      if(configurationIdentifier == null) {
        throw new IntaveException("It seems like you are using an old/invalid configuration");
      }
      return configurationIdentifier;
    } catch (FileNotFoundException exception) {
      throw new IntaveException("It seems like Intave is unable to create the default configuration file");
    } catch (InvalidConfigurationException | IOException exception) {
      throw new IntaveException("It seems like your configuration is invalid", exception);
    }
  }

  @Native
  public void setupConfiguration(String requiredState) {
    boolean enterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;

    boolean useExternalConfigurationFile = (enterprise && configurationKey.equalsIgnoreCase("file")) || IntaveControl.USE_EXTERNAL_CONFIGURATION_FILE;
    boolean configurationCacheOutdated = AccessHelper.now() - loader().configurationCache().lastModified() > 1000 * 60 * 60 * 2;

    if (useExternalConfigurationFile || configurationCacheOutdated) {
      loader.loadConfigurationUpdatedForcefully();
      return;
    }

//    String hash = loader.precomputeConfigurationHash();
    int latestKnownState = loader().latestState();
    if(requiredState == null ||  /* no connection to our servers */
      requiredState.equalsIgnoreCase(String.valueOf(latestKnownState)) /* configuration is up to date */
    ) {
      loader.loadConfiguration();
    } else {
      loader.loadConfigurationUpdatedForcefully();
    }
  }

  public void deleteCache() {
    loader.deleteCaches();
  }

  public ConfigurationLoader loader() {
    return loader;
  }

  public YamlConfiguration configuration() {
    return loader.configuration();
  }

  public String configurationKey() {
    return configurationKey;
  }
}