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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

/**
 * {@link EntityStatusView} backed by ProtocolLib.
 * <p>
 * Reads the same two structure fields the entity tracker read before the view existed: the entity
 * id from the first integer and the status from the first byte.
 */
public final class ProtocolLibEntityStatusView implements EntityStatusView {

  private final PacketEvent event;
  private final PacketContainer packet;

  public ProtocolLibEntityStatusView(PacketEvent event) {
    this.event = event;
    this.packet = event.getPacket();
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public Integer entityId() {
    return packet.getIntegers().read(0);
  }

  @Override
  public Byte status() {
    return packet.getBytes().read(0);
  }

  @Override
  public void release() {
    // Nothing held: this family only reads, so there is no write to flush.
  }
}
