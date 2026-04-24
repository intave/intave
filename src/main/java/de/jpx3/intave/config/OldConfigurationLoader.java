package de.jpx3.intave.config;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveBootFailureException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;

public final class OldConfigurationLoader {
  private static final String CONF_CACHE_FILE_SUFFIX = ".yml";
  private static final Collector<String, Map<String, Integer>, Map<String, Integer>> STATE_COLLECTOR = Collector.of(HashMap::new, (map, line) -> {
    if (line.contains(":")) {
      String[] split = line.split(":");
      map.put(split[0], Integer.parseInt(split[1]));
    }
  }, (map1, map2) -> {
    map1.putAll(map2);
    return map1;
  });

  private final String configurationKey;
  private YamlConfiguration configuration;

  public OldConfigurationLoader(String configurationKey) {
    this.configurationKey = configurationKey;
  }

  public int latestState() {
    return stateMappings().getOrDefault(configurationKey.toLowerCase(Locale.ROOT), 0);
  }

  public void saveState(int state) {
    Map<String, Integer> mappings = stateMappings();
    mappings.put(configurationKey.toLowerCase(Locale.ROOT), state);
    StringBuilder content = new StringBuilder();
    mappings.forEach((key, value) -> content.append(key).append(":").append(value).append(System.lineSeparator()));
    try {
      Files.write(stateFile().toPath(), content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public void loadConfigurationUpdatedForcefully() {
    loadConfiguration();
  }

  public void loadConfiguration() {
    File settingFile = new File(IntavePlugin.singletonInstance().dataFolder(), "settings.yml");
    if (!settingFile.exists()) {
      if (getResource("settings.yml") != null) {
        saveResource("settings.yml", false);
      } else if (getResource("advanced.yml") != null) {
        saveResource("advanced.yml", false);
        settingFile = new File(IntavePlugin.singletonInstance().dataFolder(), "advanced.yml");
      } else {
        throw new IntaveBootFailureException("No embedded configuration file is available");
      }
    }
    configuration = YamlConfiguration.loadConfiguration(new InputStreamReader(read(settingFile)));
    saveConfiguration(configuration);
  }

  public void saveResource(String resourcePath, boolean replace) {
    if (resourcePath == null || resourcePath.isEmpty()) {
      throw new IllegalArgumentException("ResourcePath cannot be null or empty");
    }
    resourcePath = resourcePath.replace('\\', '/');
    InputStream in = getResource(resourcePath);
    if (in == null) {
      throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found");
    }
    File dataFolder = IntavePlugin.singletonInstance().dataFolder();
    File outFile = new File(dataFolder, resourcePath);
    File outDir = outFile.getParentFile();
    if (!outDir.exists()) {
      outDir.mkdirs();
    }
    if (outFile.exists() && !replace) {
      return;
    }
    try (InputStream input = in) {
      Files.copy(input, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public InputStream getResource(String filename) {
    if (filename == null) {
      throw new IllegalArgumentException("Filename cannot be null");
    }
    return getClass().getClassLoader().getResourceAsStream(filename);
  }

  private InputStream read(File file) {
    try {
      return Files.newInputStream(file.toPath());
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void saveConfiguration(YamlConfiguration configuration) {
    try {
      configuration.save(configurationCache());
      saveState(configuration.getInt("variant", latestState()));
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public void deleteCaches() {
    configurationCache().delete();
  }

  public boolean configurationCacheExists() {
    return configurationCache().exists();
  }

  public File configurationCache() {
    String fileName = new UUID(((long) configurationKey.length() << 8) | (configurationKey.hashCode() >>> 1), ~configurationKey.hashCode()) + CONF_CACHE_FILE_SUFFIX;
    return new File(intaveTempDirectory(), fileName);
  }

  private File stateFile() {
    return new File(intaveTempDirectory(), "configuration-states.txt");
  }

  private Map<String, Integer> stateMappings() {
    File stateFile = stateFile();
    if (!stateFile.exists()) {
      return new HashMap<>();
    }
    try {
      return Files.lines(stateFile.toPath()).collect(STATE_COLLECTOR);
    } catch (IOException exception) {
      return new HashMap<>();
    }
  }

  private File intaveTempDirectory() {
    File workDirectory;
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("win")) {
      workDirectory = new File(System.getenv("APPDATA") + "/Intave");
    } else {
      workDirectory = new File(System.getProperty("user.home") + "/.intave/");
    }
    if (!workDirectory.exists()) {
      workDirectory.mkdirs();
    }
    return workDirectory;
  }

  public YamlConfiguration configuration() {
    return configuration;
  }
}
