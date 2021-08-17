package de.jpx3.intave.security;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Native;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.IntaveControl.GOMME_MODE;

public final class ContextSecrets {
  private static final Map<String, String> mapping = new HashMap<>();

  @Native
  public static void setup() {
    if (GOMME_MODE) {
      IntavePlugin plugin = IntavePlugin.singletonInstance();
      File dataFolder = plugin.getDataFolder();
      if (!dataFolder.exists()) {
        dataFolder.mkdirs();
      }
      File configFile = new File(dataFolder, "secrets.yml");
      if (!configFile.exists()) {
        throw new IntaveInternalException("Secret file required");
      }
      YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(configFile);
      for (Map.Entry<String, Object> entry : yamlConfiguration.getValues(true).entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        mapping.put(key, String.valueOf(value));
      }
    }
  }

  @Native
  public static String secret(String key) {
    if (GOMME_MODE) {
      return mapping.getOrDefault(key.toLowerCase(), "null");
    }
    return "null";
  }
}
