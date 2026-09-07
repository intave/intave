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
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.bukkit.entity.Player;

/**
 * PacketEvents counterpart of {@link de.jpx3.intave.packet.PacketSender}.
 * <p>
 * Deliberately a separate class rather than a branch inside that one: {@code PacketSender} resolves
 * ProtocolLib's manager in a static initialiser, so merely loading it on a PacketEvents-only server
 * would fail.
 */
public final class PacketEventsSender {

  private PacketEventsSender() {
  }

  /**
   * Hands an inbound packet back to the server, the twin of
   * {@link de.jpx3.intave.packet.PacketSender#receiveClientPacketFrom}.
   * <p>
   * The replay <em>does</em> re-enter the listener chain, exactly as ProtocolLib's
   * {@code receiveClientPacket} does, so the caller has to hide it from Intave the same way: call
   * {@code user.ignoreNextInboundPacket()} first. {@code PacketEventsLinkage} consumes that flag
   * once for the replayed packet, which is what keeps the packet from re-entering the subscription
   * that parked it. Skipping the flag here would loop; using {@link #receiveClientPacketSilentlyFrom}
   * with the flag set would leave the flag standing and swallow the player's next real packet.
   */
  public static void receiveClientPacketFrom(Player receiver, PacketWrapper<?> packet) {
    PacketEventsAPI<?> api = api();
    if (api == null || receiver == null || packet == null) {
      return;
    }
    api.getPlayerManager().receivePacket(receiver, packet);
  }

  /**
   * Replays an inbound packet past every PacketEvents listener, Intave's included.
   * <p>
   * For callers that do not set the ignore flag. No ProtocolLib equivalent exists, so a check that
   * has to behave identically on both engines wants {@link #receiveClientPacketFrom} instead.
   */
  public static void receiveClientPacketSilentlyFrom(Player receiver, PacketWrapper<?> packet) {
    PacketEventsAPI<?> api = api();
    if (api == null || receiver == null || packet == null) {
      return;
    }
    api.getPlayerManager().receivePacketSilently(receiver, packet);
  }

  /** Twin of {@link de.jpx3.intave.packet.PacketSender#sendServerPacket}. */
  public static void sendServerPacket(Player receiver, PacketWrapper<?> packet) {
    PacketEventsAPI<?> api = api();
    if (api == null || receiver == null || packet == null) {
      return;
    }
    api.getPlayerManager().sendPacket(receiver, packet);
  }

  /** Twin of {@link de.jpx3.intave.packet.PacketSender#sendServerPacketWithoutEvent}. */
  public static void sendServerPacketWithoutEvent(Player receiver, PacketWrapper<?> packet) {
    PacketEventsAPI<?> api = api();
    if (api == null || receiver == null || packet == null) {
      return;
    }
    api.getPlayerManager().sendPacketSilently(receiver, packet);
  }

  /**
   * Writes an already encoded packet straight onto the wire, past every PacketEvents listener.
   * <p>
   * This is the twin of {@link de.jpx3.intave.packet.PacketSender#sendServerPacketWithoutEvent} for
   * a buffer rather than a container, and the piece the packet sandwich needs: it re-emits the exact
   * bytes of a cancelled outbound packet without re-entering the encoder, so the subscription that
   * cancelled it does not see it a second time. {@code buffer} is what
   * {@code ProtocolPacketEvent#getFullBufferClone()} handed out - a detached copy that outlives the
   * listener call, unlike the event's own buffer.
   * <p>
   * Deliberately not an overload of {@link #sendServerPacketWithoutEvent(Player, PacketWrapper)}:
   * one takes a decoded wrapper and the other raw bytes, and an {@code Object} overload next to a
   * {@code PacketWrapper} one is exactly the pair that picks the wrong method by accident.
   */
  public static void sendServerBufferWithoutEvent(Player receiver, Object buffer) {
    PacketEventsAPI<?> api = api();
    if (api == null || receiver == null || buffer == null) {
      return;
    }
    Object channel = api.getPlayerManager().getChannel(receiver);
    if (channel == null) {
      return;
    }
    api.getProtocolManager().sendPacketSilently(channel, buffer);
  }

  private static PacketEventsAPI<?> api() {
    return PacketEventsBootstrap.available() ? PacketEvents.getAPI() : null;
  }
}
