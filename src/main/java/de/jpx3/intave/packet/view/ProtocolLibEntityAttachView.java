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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

/**
 * {@link EntityAttachView} backed by ProtocolLib.
 * <p>
 * Field indices are the ones the entity tracker used before the view existed, unchanged: the mount
 * packet keeps the vehicle in the first integer and the passenger list in the first integer array,
 * while the legacy attach packet declares its fields as leash flag, passenger, vehicle in that
 * order, which is the order ProtocolLib's integer modifier exposes them in.
 */
public final class ProtocolLibEntityAttachView implements EntityAttachView {

  private final PacketEvent event;
  private final PacketContainer packet;
  private final boolean mount;
  private final boolean legacyAttach;

  public ProtocolLibEntityAttachView(PacketEvent event) {
    this.event = event;
    this.packet = event.getPacket();
    PacketType type = event.getPacketType();
    this.mount = type == PacketType.Play.Server.MOUNT;
    this.legacyAttach = type == PacketType.Play.Server.ATTACH_ENTITY;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public boolean isMount() {
    return mount;
  }

  @Override
  public boolean isLegacyAttach() {
    return legacyAttach;
  }

  @Override
  public int vehicleId() {
    return mount ? packet.getIntegers().read(0) : packet.getIntegers().read(2);
  }

  @Override
  public int[] passengers() {
    return packet.getIntegerArrays().read(0);
  }

  @Override
  public boolean isLeash() {
    return packet.getIntegers().read(0) != 0;
  }

  @Override
  public int passengerId() {
    return packet.getIntegers().read(1);
  }

  @Override
  public void release() {
    // Nothing held: this family only reads, so there is no write to flush.
  }
}
