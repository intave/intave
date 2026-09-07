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
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.share.Position;
import org.bukkit.entity.Player;

/**
 * {@link MovementView} backed by ProtocolLib. Wraps the pooled {@link PlayerMoveReader} so the
 * existing reader pooling and release semantics stay exactly as they were.
 */
public final class ProtocolLibMovementView implements MovementView {

  private final PacketEvent event;
  private final PlayerMoveReader reader;
  private final boolean positionLook;

  public ProtocolLibMovementView(PacketEvent event) {
    this.event = event;
    PacketContainer packet = event.getPacket();
    this.reader = PacketReaders.readerOf(packet);
    this.positionLook = packet.getType() == PacketType.Play.Client.POSITION_LOOK;
  }

  /** @return the wrapped reader, for movement code that still needs ProtocolLib specifics. */
  public PlayerMoveReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public boolean cancelled() {
    return event.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public boolean isVehicleMove() {
    return event.getPacketType() == PacketType.Play.Client.VEHICLE_MOVE;
  }

  @Override
  public boolean isPositionLook() {
    return positionLook;
  }

  @Override
  public boolean hasMovement() {
    return reader.hasMovement();
  }

  @Override
  public boolean hasRotation() {
    return reader.hasRotation();
  }

  @Override
  public double positionX() {
    return reader.positionX();
  }

  @Override
  public double positionY() {
    return reader.positionY();
  }

  @Override
  public double positionZ() {
    return reader.positionZ();
  }

  @Override
  public float yaw() {
    return reader.yaw();
  }

  @Override
  public float pitch() {
    return reader.pitch();
  }

  @Override
  public boolean onGround() {
    return reader.onGround();
  }

  @Override
  public boolean anyNaNOrInfiniteValue() {
    return reader.anyNaNOrInfiniteValue();
  }

  @Override
  public void setPosition(Position position) {
    reader.setPosition(position);
  }

  @Override
  public void setOnGround(boolean onGround) {
    reader.setOnGround(onGround);
  }

  @Override
  public void release() {
    reader.release();
  }
}
