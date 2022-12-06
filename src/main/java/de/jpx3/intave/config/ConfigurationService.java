package de.jpx3.intave.config;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveBootFailureException;
import de.jpx3.intave.annotate.HighOrderService;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;

@HighOrderService
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
    File dataFolder = plugin.dataFolder();
    if (!dataFolder.exists()) {
      dataFolder.mkdirs();
    }
    File configFile;
    File oldConfigFile = new File(dataFolder, "config.yml");
    File newConfigFile = new File(dataFolder, "sources.yml");
    if (oldConfigFile.exists() && !newConfigFile.exists()) {
      configFile = oldConfigFile;
    } else if (!oldConfigFile.exists() && newConfigFile.exists()) {
      configFile = newConfigFile;
    } else if (!oldConfigFile.exists() && !newConfigFile.exists()) {
      saveResource("sources.yml", false);
      configFile = newConfigFile;
    } else {
      throw new IntaveBootFailureException("Both config.yml and sources.yml exist. Please delete one of them.");
    }
    try {
      FileInputStream fileInputStream = new FileInputStream(configFile);
      YamlConfiguration configuration = new YamlConfiguration();
      InputStreamReader reader = new InputStreamReader(fileInputStream);
      configuration.load(reader);
      fileInputStream.close();
      reader.close();
      String configurationIdentifier = configuration.getString("config-identifier");
      if (configurationIdentifier == null) {
        throw new IntaveBootFailureException("It seems like you are using an old/invalid configuration");
      }
      return configurationIdentifier;
    } catch (FileNotFoundException exception) {
      throw new IntaveBootFailureException("It seems like we are unable to create the default configuration file");
    } catch (InvalidConfigurationException | IOException exception) {
      throw new IntaveBootFailureException("It seems like your configuration is invalid", exception);
    }
  }

  // stolen from bukkit
  public void saveResource(String resourcePath, boolean replace) {
    if (resourcePath != null && !resourcePath.equals("")) {
      resourcePath = resourcePath.replace('\\', '/');
      InputStream in = this.getResource(resourcePath);
      if (in == null) {
        throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found");
      } else {
        File outFile = new File(plugin.dataFolder(), resourcePath);
        int lastIndex = resourcePath.lastIndexOf(47);
        File outDir = new File(plugin.dataFolder(), resourcePath.substring(0, Math.max(lastIndex, 0)));
        if (!outDir.exists()) {
          outDir.mkdirs();
        }
        try {
          if (!outFile.exists() || replace) {
            OutputStream out = Files.newOutputStream(outFile.toPath());
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) != -1) {
              out.write(buf, 0, len);
            }
            out.close();
            in.close();
          }
        } catch (IOException ignored) {
        }
      }
    } else {
      throw new IllegalArgumentException("ResourcePath cannot be null or empty");
    }
  }

  public InputStream getResource(String filename) {
    if (filename == null) {
      throw new IllegalArgumentException("Filename cannot be null");
    } else {
      try {
        URL url = this.getClass().getClassLoader().getResource(filename);
        if (url == null) {
          return null;
        } else {
          URLConnection connection = url.openConnection();
          connection.setUseCaches(false);
          return connection.getInputStream();
        }
      } catch (IOException var4) {
        return null;
      }
    }
  }

  //  @Native
  public void setupConfiguration(String requiredState) {
//    boolean enterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
//    boolean partner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;

    boolean useExternalConfigurationFile = (/*enterprise && */configurationKey.equalsIgnoreCase("file")) || IntaveControl.USE_EXTERNAL_CONFIGURATION_FILE;
    boolean configurationCacheOutdated = System.currentTimeMillis() - loader().configurationCache().lastModified() > 1000 * 60 * 60 * 2;

    if (useExternalConfigurationFile || configurationCacheOutdated) {
      loader.loadConfigurationUpdatedForcefully();
      return;
    }

//    String hash = loader.precomputeConfigurationHash();
    int latestKnownState = loader().latestState();
    if (requiredState.equals("") || /* no connection to our servers */
      requiredState.equalsIgnoreCase(String.valueOf(latestKnownState)) /* configuration is up to date */
    ) {
//      IntaveLogger.logger().info("Loading configuration from cache");
      loader.loadConfiguration();
    } else {
//      IntaveLogger.logger().info("Loading configuration from server");
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