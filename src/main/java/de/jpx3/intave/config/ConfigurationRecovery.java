package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

final class ConfigurationRecovery {
  private static final String BUGGED_CONFIG_FOLDER = "bugged config";
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

  private ConfigurationRecovery() {
  }

  static YamlConfiguration loadConfiguration(File file, String defaultResource) {
    ensureConfigurationExists(file, defaultResource);
    try {
      YamlConfiguration configuration = loadFromFile(file);
      validate(configuration, file);
      return configuration;
    } catch (Exception exception) {
      recover(file, defaultResource, exception);
      try {
        YamlConfiguration configuration = loadFromFile(file);
        validate(configuration, file);
        return configuration;
      } catch (Exception recoveredException) {
        throw new RuntimeException("Unable to recover configuration " + file.getName(), recoveredException);
      }
    }
  }

  static void ensureConfigurationExists(File file, String defaultResource) {
    Resource configResource = Resources.resourceFromFile(file);
    if (configResource.available()) {
      return;
    }
    writeDefault(file, defaultResource);
  }

  static YamlConfiguration recoverConfiguration(File file, String defaultResource, Exception exception) {
    recover(file, defaultResource, exception);
    return loadConfiguration(file, defaultResource);
  }

  private static YamlConfiguration loadFromFile(File file) throws IOException, InvalidConfigurationException {
    YamlConfiguration configuration = new YamlConfiguration();
    configuration.load(file);
    return configuration;
  }

  private static void validate(YamlConfiguration configuration, File file) {
    if ("config.yml".equalsIgnoreCase(file.getName())) {
      validatePrefix(configuration, "prefix");
    } else if ("advanced.yml".equalsIgnoreCase(file.getName())) {
      validatePrefix(configuration, "layout.prefix");
    }
  }

  private static void validatePrefix(YamlConfiguration configuration, String path) {
    if (!configuration.isString(path)) {
      throw new IllegalArgumentException("Invalid or missing " + path);
    }
    String prefix = configuration.getString(path);
    if (prefix == null || prefix.trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid or empty " + path);
    }
  }

  private static void recover(File file, String defaultResource, Exception exception) {
    IntavePlugin.singletonInstance().logger().error("Invalid " + file.getName() + ", moving it to " + BUGGED_CONFIG_FOLDER + ": " + exception.getMessage());
    moveBuggedConfiguration(file);
    writeDefault(file, defaultResource);
  }

  private static void moveBuggedConfiguration(File file) {
    if (!file.exists()) {
      return;
    }
    File targetFolder = new File(file.getParentFile(), BUGGED_CONFIG_FOLDER);
    if (!targetFolder.exists() && !targetFolder.mkdirs()) {
      throw new IllegalStateException("Unable to create " + targetFolder.getAbsolutePath());
    }
    String timestamp = DATE_FORMAT.format(new Date());
    File target = new File(targetFolder, timestamp + "-" + file.getName());
    int copy = 1;
    while (target.exists()) {
      target = new File(targetFolder, timestamp + "-" + copy++ + "-" + file.getName());
    }
    if (!file.renameTo(target)) {
      throw new IllegalStateException("Unable to move bugged configuration to " + target.getAbsolutePath());
    }
  }

  private static void writeDefault(File file, String defaultResource) {
    Resource classpathResource = Resources.resourceFromJarOrBuild(defaultResource);
    Resource fileResource = Resources.resourceFromFile(file);
    try (InputStream read = classpathResource.read()) {
      fileResource.write(read);
    } catch (IOException exception) {
      throw new RuntimeException("Unable to write default " + defaultResource, exception);
    }
  }
}
