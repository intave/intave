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

import com.github.retrooper.packetevents.PacketEvents;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsBootstrap;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsLinkage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the packet layer actually bound at runtime.
 * <p>
 * A packet subscription has a failure mode that raises nothing and logs nothing: it registers, it is
 * healthy, and it never fires - because the packet id resolved to a constant this server version
 * does not put on the wire, or because it belongs to the engine that is not live. On an anticheat
 * that reads as "the check found nothing", which is indistinguishable from "there was nothing to
 * find" right up until someone cheats past it.
 * <p>
 * This report exists to make that state observable. {@link #startupSummary()} is the compact form
 * printed once at boot; {@link #fullReport()} adds the per-subscription traffic, which only becomes
 * meaningful after the server has seen real players - a subscription still at zero hits then is
 * either dead or bound to a packet nobody sends.
 */
public final class PacketEngineReport {

  private PacketEngineReport() {
  }

  /** Short form for the boot log: which engine, and whether anything failed to bind. */
  public static List<String> startupSummary() {
    List<String> lines = new ArrayList<>();
    Engine engine = EngineSelection.active();
    lines.add("Packet engine: " + engine);

    if (engine != Engine.PACKETEVENTS) {
      lines.add("PacketEvents subscriptions are registered but idle; set compatibility.packet-engine to 'packetevents' to run them");
      return lines;
    }

    lines.add("PacketEvents runtime: " + runtimeVersion());
    lines.add("Subscriptions bound: " + PacketEventsLinkage.linkage().subscriptionCount());

    List<String> unresolved = new ArrayList<>(PacketEventsLinkage.unresolvedPackets());
    if (!unresolved.isEmpty()) {
      lines.add("Packet ids with no constant on this PacketEvents version (" + unresolved.size() + "): " + String.join(", ", unresolved));
    }

    List<String> dark = EngineSelection.darkSubscriptions();
    if (!dark.isEmpty()) {
      lines.add("Checks inactive for lack of a PacketEvents twin (" + dark.size() + "): " + String.join(", ", dark));
    }

    int unbound = 0;
    for (PacketEventsLinkage.SubscriptionStat stat : PacketEventsLinkage.linkage().statistics()) {
      if (stat.boundTypes == 0) {
        unbound++;
      }
    }
    if (unbound > 0) {
      lines.add("Subscriptions that bound no packet type at all: " + unbound);
    }
    return lines;
  }

  /**
   * Full form, for the diagnostics command. Adds the two tables a live test actually needs: which
   * constant every ambiguous packet id bound on this server version, and how much traffic each
   * subscription has seen.
   */
  public static List<String> fullReport() {
    List<String> lines = new ArrayList<>(startupSummary());
    if (EngineSelection.active() != Engine.PACKETEVENTS) {
      return lines;
    }

    Map<String, String> bound = PacketEventsIdMapper.boundNames();
    Set<String> ambiguous = PacketEventsIdMapper.ambiguousIds();
    lines.add("");
    lines.add("Packet ids that had more than one candidate name, and what they bound here:");
    boolean anyAmbiguous = false;
    for (Map.Entry<String, String> entry : bound.entrySet()) {
      if (ambiguous.contains(entry.getKey())) {
        lines.add("  " + entry.getKey() + " -> " + entry.getValue());
        anyAmbiguous = true;
      }
    }
    if (!anyAmbiguous) {
      lines.add("  (none of them is in use)");
    }

    List<PacketEventsLinkage.SubscriptionStat> stats = PacketEventsLinkage.linkage().statistics();
    List<String> silent = new ArrayList<>();
    long dispatched = 0;
    for (PacketEventsLinkage.SubscriptionStat stat : stats) {
      dispatched += stat.hits;
      if (stat.hits == 0) {
        silent.add(stat.identifier + " [" + stat.priority + ", " + stat.boundTypes + " types]");
      }
    }
    lines.add("");
    lines.add("Traffic: " + dispatched + " dispatches over " + stats.size() + " subscriptions");
    if (silent.isEmpty()) {
      lines.add("Every subscription has fired at least once.");
    } else {
      lines.add("Never fired (" + silent.size() + ") - after real traffic, each of these is either dead or bound to a packet nobody sends:");
      for (String entry : silent) {
        lines.add("  " + entry);
      }
    }
    return lines;
  }

  private static String runtimeVersion() {
    try {
      if (!PacketEventsBootstrap.available() || PacketEvents.getAPI() == null) {
        return "unavailable";
      }
      return String.valueOf(PacketEvents.getAPI().getVersion());
    } catch (RuntimeException | LinkageError throwable) {
      return "unknown";
    }
  }
}
