/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.linker.packet.pe;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.Plugin;

/**
 * Owns the PacketEvents lifecycle for Intave.
 * <p>
 * PacketEvents is a singleton per server: the standalone PacketEvents plugin, or any other plugin
 * shading it, may already have built and initialised the API. Building it a second time detaches
 * the first instance's channel injectors and silently breaks every other consumer, so this class
 * only ever creates an instance when none exists, and only terminates the instance it created
 * itself.
 */
public final class PacketEventsBootstrap {

  private static final String API_CLASS = "com.github.retrooper.packetevents.PacketEvents";

  private static boolean ownsApi = false;
  private static boolean initialised = false;

  private PacketEventsBootstrap() {
  }

  /** @return whether PacketEvents is on the classpath at all. */
  public static boolean available() {
    try {
      Class.forName(API_CLASS);
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  /** @return whether a usable, initialised PacketEvents API is present. */
  public static boolean ready() {
    if (!available()) {
      return false;
    }
    PacketEventsAPI<?> api = PacketEvents.getAPI();
    return api != null && api.isInitialized();
  }

  /**
   * Creates the API if nobody else did. Must run in the plugin's {@code onLoad}: PacketEvents
   * injects into the server's channel pipeline and has to be loaded before players can connect.
   *
   * @return true when PacketEvents is usable after this call.
   */
  public static boolean load(Plugin plugin) {
    if (!available()) {
      return false;
    }
    PacketEventsAPI<?> existing = PacketEvents.getAPI();
    if (existing != null) {
      // Another plugin owns the instance; reuse it and leave its lifecycle alone.
      ownsApi = false;
      return true;
    }
    PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
    PacketEventsAPI<?> api = PacketEvents.getAPI();
    PacketEventsSettings settings = api.getSettings();
    settings.reEncodeByDefault(false);
    settings.checkForUpdates(false);
    api.load();
    ownsApi = true;
    return true;
  }

  /** Starts packet delivery. Must run in the plugin's {@code onEnable}, after listeners registered. */
  public static boolean init() {
    if (!available()) {
      return false;
    }
    PacketEventsAPI<?> api = PacketEvents.getAPI();
    if (api == null) {
      return false;
    }
    if (!api.isInitialized()) {
      api.init();
    }
    initialised = true;
    return true;
  }

  /** Stops packet delivery, but only tears the API down when Intave created it. */
  public static void terminate() {
    if (!available() || !initialised) {
      return;
    }
    PacketEventsAPI<?> api = PacketEvents.getAPI();
    if (api == null) {
      return;
    }
    if (ownsApi && api.isInitialized()) {
      api.terminate();
    }
    initialised = false;
  }

  /** @return true when Intave created the API and is therefore responsible for terminating it. */
  public static boolean ownsApi() {
    return ownsApi;
  }
}
