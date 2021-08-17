package de.jpx3.intave.adapter;

import com.comphenix.protocol.ProtocolLibrary;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.viaversion.ViaVersion2Access;
import de.jpx3.intave.adapter.viaversion.ViaVersion3Access;
import de.jpx3.intave.adapter.viaversion.ViaVersion4Access;
import de.jpx3.intave.adapter.viaversion.ViaVersionAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.List;

/**
 * Created by Jpx3 on 27.07.2018.
 */

public class ViaVersionAdapter {
  private final static List<ViaVersionAccess> available = Lists.newArrayList();

  static {
    available.add(new ViaVersion2Access());
    available.add(new ViaVersion3Access());
    available.add(new ViaVersion4Access());
  }

  private static ViaVersionAccess access;

  public static void setup() {
    PluginManager pluginManager = Bukkit.getServer().getPluginManager();
    Plugin viaVersion = pluginManager.getPlugin("ViaVersion");
    if (viaVersion == null) {
      return;
    }
    String version = viaVersion.getDescription().getVersion();
    access = available.stream().filter(access -> access.available(version)).findFirst().orElse(null);
    available.clear();
    if (access != null) {
      access.setup();
    } else {
      IntaveLogger.logger().error("Unknown ViaVersion version, linkage failed (ViaVersion version: " + version + ")");
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
      return ProtocolLibrary.getProtocolManager().getProtocolVersion(player);
    }
  }

  public static boolean foundLinkage() {
    return access != null;
  }
}