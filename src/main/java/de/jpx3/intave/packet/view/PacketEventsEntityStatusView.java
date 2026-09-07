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

package de.jpx3.intave.packet.view;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import org.bukkit.entity.Player;

/**
 * {@link EntityStatusView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction, mirroring {@link PacketEventsEntityGenericView}.
 * Neither accessor can be null here - PacketEvents decodes both fields as primitives - but they
 * stay boxed to satisfy the interface the ProtocolLib backend shapes.
 * <p>
 * The status is narrowed to a byte because that is its width on the wire; PacketEvents widens it
 * to an int purely for convenience, so the narrowing loses nothing.
 */
public final class PacketEventsEntityStatusView implements EntityStatusView {

  private final PacketSendEvent event;
  private final int entityId;
  private final byte status;

  private PacketEventsEntityStatusView(PacketSendEvent event, int entityId, byte status) {
    this.event = event;
    this.entityId = entityId;
    this.status = status;
  }

  /** @return a view over the event, or null when the packet is not an entity status. */
  public static PacketEventsEntityStatusView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS) {
      return null;
    }
    WrapperPlayServerEntityStatus wrapper = new WrapperPlayServerEntityStatus(event);
    return new PacketEventsEntityStatusView(
      event, wrapper.getEntityId(), (byte) wrapper.getStatus()
    );
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public Integer entityId() {
    return entityId;
  }

  @Override
  public Byte status() {
    return status;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction and never written to.
  }
}
