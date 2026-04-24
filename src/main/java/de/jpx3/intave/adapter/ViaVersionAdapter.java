package de.jpx3.intave.adapter;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.viaversion.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.List;

/**
 * Created by Jpx3 on 27.07.2018.
 */

public class ViaVersionAdapter {
  private static final List<ViaVersionAccess> available = Lists.newArrayList();

  static {
    available.add(new ViaVersion2Access());
    available.add(new ViaVersion3Access());
    available.add(new ViaVersion4Access());
    available.add(new ViaVersion5Access());
  }

  private static ViaVersionAccess access;

  public static void setup() {
    PluginManager pluginManager = Bukkit.getServer().getPluginManager();
    Plugin viaVersion = pluginManager.getPlugin("ViaVersion");
    if (viaVersion == null) {
      return;
    }
    String version = viaVersion.getDescription().getVersion();
    ViaVersionAccess found = null;
    for (ViaVersionAccess viaVersionAccess : available) {
      if (viaVersionAccess.available(version)) {
        found = viaVersionAccess;
        break;
      }
    }
    access = found;
    available.clear();
    if (access != null) {
      access.setup();
    } else {
      IntaveLogger.logger().error("Unknown ViaVersion version, using backup linkage (ViaVersion version: " + version + ")");
      access = new ViaVersion5Access();
      access.setup();
    }
  }

  public static void patchConfiguration() {
    if (foundLinkage()) {
      access.patchConfiguration();
    }
  }

  public static boolean ignoreBlocking(Player player) {
    return foundLinkage() && access.ignoreBlocking(player);
  }

  public static int protocolVersionOf(Player player) {
    if (foundLinkage()) {
      return access.protocolVersionOf(player);
    } else {
      return PacketEvents.getAPI().getPlayerManager().getClientVersion(player).getProtocolVersion();
    }
  }

  public static void decrementReceivedPackets(Player player, int amount) {
    if (foundLinkage()) {
      access.decrementReceivedPackets(player, amount);
    }
  }

  public static boolean foundLinkage() {
    return access != null;
  }

  public static String version() {
    return access != null ? access.version() : "unknown";
  }
}
