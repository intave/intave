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

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of a PacketEvents packet event.
 * <p>
 * Subscribers get this instead of the raw PacketEvents event so that a subscription reads the same
 * whether the packet arrives inbound or outbound, and so that Intave code does not have to branch
 * on {@code PacketReceiveEvent} versus {@code PacketSendEvent} for the operations both share
 * (player, cancellation, packet type).
 */
public final class PacketEventWrapper {

  private final ProtocolPacketEvent event;
  private final boolean inbound;

  PacketEventWrapper(PacketReceiveEvent event) {
    this.event = event;
    this.inbound = true;
  }

  PacketEventWrapper(PacketSendEvent event) {
    this.event = event;
    this.inbound = false;
  }

  /** @return true for client -> server packets. */
  public boolean inbound() {
    return inbound;
  }

  /** @return true for server -> client packets. */
  public boolean outbound() {
    return !inbound;
  }

  public PacketTypeCommon packetType() {
    return event.getPacketType();
  }

  /** @return the Bukkit player, or null before the connection reaches the play phase. */
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  /** @return PacketEvents' connection object; valid during login and configuration too. */
  public com.github.retrooper.packetevents.protocol.player.User user() {
    return event.getUser();
  }

  public boolean cancelled() {
    return event.isCancelled();
  }

  public void cancel() {
    event.setCancelled(true);
  }

  public void uncancel() {
    event.setCancelled(false);
  }

  /** The underlying event, for the rare check that needs a PacketEvents specific wrapper. */
  public ProtocolPacketEvent nativeEvent() {
    return event;
  }

  /** @return the underlying receive event, or null when this packet is outbound. */
  public PacketReceiveEvent receiveEvent() {
    return inbound ? (PacketReceiveEvent) event : null;
  }

  /** @return the underlying send event, or null when this packet is inbound. */
  public PacketSendEvent sendEvent() {
    return inbound ? null : (PacketSendEvent) event;
  }
}
