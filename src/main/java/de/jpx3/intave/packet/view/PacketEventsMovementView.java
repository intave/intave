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

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import de.jpx3.intave.module.linker.packet.pe.reader.PeMoveReader;
import de.jpx3.intave.share.Position;
import org.bukkit.entity.Player;

/**
 * {@link MovementView} backed by PacketEvents.
 * <p>
 * Unlike the ProtocolLib view there is no reader pool here: {@link PeMoveReader} decodes the packet
 * once into primitives at construction, so {@link #release()} only has to flush pending writes.
 */
public final class PacketEventsMovementView implements MovementView {

  private final PacketReceiveEvent event;
  private final PeMoveReader reader;
  private final boolean positionLook;

  private PacketEventsMovementView(PacketReceiveEvent event, PeMoveReader reader) {
    this.event = event;
    this.reader = reader;
    this.positionLook = event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
  }

  /** @return a view over the event, or null when the packet is not a movement packet. */
  public static PacketEventsMovementView of(PacketReceiveEvent event) {
    PeMoveReader reader = PeMoveReader.of(event);
    return reader == null ? null : new PacketEventsMovementView(event, reader);
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
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
    return reader.isVehicleMove();
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
    reader.setPositionX(position.getX());
    reader.setPositionY(position.getY());
    reader.setPositionZ(position.getZ());
  }

  @Override
  public void setOnGround(boolean onGround) {
    reader.setOnGround(onGround);
  }

  @Override
  public void release() {
    reader.flush();
  }
}
