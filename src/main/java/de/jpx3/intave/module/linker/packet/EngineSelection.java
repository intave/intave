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

package de.jpx3.intave.module.linker.packet;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsBootstrap;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decides which packet engine's subscriptions are live for this run.
 * <p>
 * Most checks now carry two subscriptions for the same packet - a ProtocolLib one and a PacketEvents
 * twin. They are two paths through the same check, not two halves of it: each reads the packet, each
 * advances the same per-user metadata and each raises violations. Registering both on a server that
 * has both libraries installed would therefore run every check twice per packet, doubling packet
 * counters and violation levels. Exactly one engine may be live, and this class is what picks it.
 * <p>
 * The default is {@link Engine#PROTOCOLLIB}, deliberately. The PacketEvents path is verified to
 * compile and is reviewed field by field, but it has not yet been run against a live server, and
 * defaulting to it would swap the engine under every existing installation on upgrade. It is opt-in
 * through {@code compatibility.packet-engine: packetevents} until that verification exists.
 *
 * @see #active()
 */
public final class EngineSelection {

  /** Config key; accepts {@code protocollib} or {@code packetevents}. */
  private static final String SETTING = "compatibility.packet-engine";

  private static Engine active;

  private static final List<String> DARK_SUBSCRIPTIONS = new CopyOnWriteArrayList<>();

  private EngineSelection() {
  }

  /**
   * @return the engine whose subscriptions are registered. Resolved once, on first use, because
   * subscriptions are linked throughout startup and the answer must not change midway.
   */
  public static synchronized Engine active() {
    if (active == null) {
      active = resolve();
    }
    return active;
  }

  /**
   * @return true when a subscription declared for this engine should be registered.
   * {@link Engine#INTERNAL} is always live: it is not an engine choice but Intave's own priority
   * slot on the ProtocolLib pipeline, and it has no PacketEvents counterpart.
   */
  public static boolean isActive(Engine engine) {
    return engine == Engine.INTERNAL || engine == active();
  }

  /**
   * Reports a subscription that will not be registered because it belongs to the other engine.
   * <p>
   * Only the ProtocolLib-only ones are worth saying out loud, and only while PacketEvents is the
   * live engine: those are checks with no PacketEvents twin, so they are simply inactive this run
   * and an operator has no other way to find that out. The reverse case - PacketEvents twins skipped
   * while ProtocolLib is live - is the normal state of every twinned check and would print hundreds
   * of lines saying nothing.
   */
  public static void recordSkipped(Engine engine, String subscription) {
    if (engine == Engine.PROTOCOLLIB && active() == Engine.PACKETEVENTS) {
      DARK_SUBSCRIPTIONS.add(subscription);
      IntaveLogger.logger().warning(
        "No PacketEvents implementation for " + subscription + "; this check is inactive on the PacketEvents engine"
      );
    }
  }

  /** @return the subscriptions that have no twin on the live engine and are therefore not running. */
  public static List<String> darkSubscriptions() {
    return Collections.unmodifiableList(DARK_SUBSCRIPTIONS);
  }

  private static Engine resolve() {
    String configured = configuredEngine();
    if ("packetevents".equalsIgnoreCase(configured)) {
      if (PacketEventsBootstrap.available()) {
        IntaveLogger.logger().info("Packet engine: PacketEvents (configured)");
        return Engine.PACKETEVENTS;
      }
      IntaveLogger.logger().warning(
        "Packet engine 'packetevents' was requested but PacketEvents is not available; falling back to ProtocolLib"
      );
      return Engine.PROTOCOLLIB;
    }
    if (configured != null && !"protocollib".equalsIgnoreCase(configured)) {
      IntaveLogger.logger().warning(
        "Unknown value '" + configured + "' for " + SETTING + "; using ProtocolLib"
      );
    }
    return Engine.PROTOCOLLIB;
  }

  private static String configuredEngine() {
    try {
      IntavePlugin plugin = IntavePlugin.singletonInstance();
      return plugin == null ? null : plugin.settings().getString(SETTING, null);
    } catch (RuntimeException exception) {
      // Settings not loaded yet, or no config at all: the default answers for both cases.
      return null;
    }
  }
}
