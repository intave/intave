package de.jpx3.intave.trustfactor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;

final class InternetYamlTrustFactorLoader implements TrustFactorLoader {
  @Override
  public TrustFactorConfiguration fetch() {
    Resource trustfactor = Resources.resourceFromJarOrBuild("trustfactor.yml");
    InputStreamReader reader = new InputStreamReader(trustfactor.read());
    YamlConfiguration configuration = YamlConfiguration.loadConfiguration(reader);
    if (configuration.getConfigurationSection("physics") == null) {
      IntaveLogger.logger().error("Unable to download TXM file");
    }
    try {
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new YamlTrustFactorConfiguration(configuration);
  }
}
