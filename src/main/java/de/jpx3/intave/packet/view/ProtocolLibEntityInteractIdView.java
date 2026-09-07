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
 * {@link EntityInteractIdView} backed by ProtocolLib.
 * <p>
 * The target id is the packet's first integer on every protocol version. It is read safely because
 * that is what the entity tracker did before the view existed, and written straight through: a
 * ProtocolLib packet is mutated in place, so no flush is needed in {@link #release()}.
 */
public final class ProtocolLibEntityInteractIdView implements EntityInteractIdView {

  private final PacketEvent event;
  private final PacketContainer packet;

  public ProtocolLibEntityInteractIdView(PacketEvent event) {
    this.event = event;
    this.packet = event.getPacket();
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public Integer entityId() {
    return packet.getIntegers().readSafely(0);
  }

  @Override
  public void setEntityId(int entityId) {
    packet.getIntegers().write(0, entityId);
  }

  @Override
  public void release() {
    // Nothing held: ProtocolLib packets are mutated in place, so the write is already visible.
  }
}
