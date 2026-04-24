package de.jpx3.intave.adapter;

import de.jpx3.intave.version.MinecraftVersion;
import com.github.retrooper.packetevents.PacketEvents;
import de.jpx3.intave.access.InvalidDependencyException;
import org.bukkit.Bukkit;

public final class PacketEventsAdapter {
  private static final String PACKET_EVENTS_MISSING = "PacketEvents is required and must be loaded before Intave";

  public static MinecraftVersion serverVersion() {
    return MinecraftVersion.getCurrentVersion();
  }

  public static boolean packetEventsAvailable() {
    return Bukkit.getPluginManager().getPlugin("packetevents") != null;
  }

  public static void checkIfOutdated() {
    if (!packetEventsAvailable() || PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
      throw new InvalidDependencyException(PACKET_EVENTS_MISSING);
    }
  }
}

