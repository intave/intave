package de.jpx3.intave.trustfactor;

import de.jpx3.intave.IntavePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;

final class DebugYamlTrustFactorLoader implements TrustFactorLoader {
  @Override
  public TrustFactorConfiguration fetch() {
    String fileName = "/" + IntavePlugin.version().replace(".", "-") + ".yml";
    InputStream resourceAsStream = getClass().getResourceAsStream(fileName);
    if (resourceAsStream == null) {
      resourceAsStream = getClass().getResourceAsStream("/14-0-0.yml");
    }
    return new YamlTrustFactorConfiguration(YamlConfiguration.loadConfiguration(new InputStreamReader(resourceAsStream)));
  }
}
