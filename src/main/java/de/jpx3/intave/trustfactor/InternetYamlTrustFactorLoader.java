package de.jpx3.intave.trustfactor;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.CachedResource;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public final class InternetYamlTrustFactorLoader implements TrustFactorLoader {
  @Override
  public TrustFactorConfiguration fetch() {
    CachedResource trustfactor = new CachedResource("trustfactor", "https://service.intave.de/trustfactor/" + IntavePlugin.version(), TimeUnit.DAYS.toMillis(7));
    trustfactor.prepareFile();
    InputStreamReader reader = new InputStreamReader(trustfactor.read());
    YamlConfiguration configuration = YamlConfiguration.loadConfiguration(reader);
    if (configuration.getConfigurationSection("physics") == null) {
      IntaveLogger.logger().error("Unable to download TXM file");
    }
    return new YamlTrustFactorConfiguration(configuration);
  }
}
