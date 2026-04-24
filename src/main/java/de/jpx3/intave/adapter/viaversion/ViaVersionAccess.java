package de.jpx3.intave.adapter.viaversion;

import de.jpx3.intave.IntaveLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public interface ViaVersionAccess {
  void setup();

  void patchConfiguration();

  int protocolVersionOf(Player player);

  boolean ignoreBlocking(Player player);

  default void decrementReceivedPackets(Player player, int amount) {
  }

  default void warnPatchConfigurationFailure() {
    IntaveLogger logger = IntaveLogger.logger();
    if (logger != null) {
      logger.warn("Could not automatically patch ViaVersion PPS limits");
    } else {
      Bukkit.getLogger().warning("[Intave] WARNING: Could not automatically patch ViaVersion PPS limits");
    }
  }

  boolean available(String version);

  String version();
}
