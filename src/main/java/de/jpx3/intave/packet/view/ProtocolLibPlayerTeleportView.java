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
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerTeleportReader;
import de.jpx3.intave.share.Motion;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * {@link PlayerTeleportView} backed by ProtocolLib. Wraps the pooled {@link PlayerTeleportReader}
 * so the existing reader pooling, flush and release semantics stay exactly as they were.
 */
public final class ProtocolLibPlayerTeleportView implements PlayerTeleportView {

  private final PacketEvent event;
  private final PacketContainer packet;
  private final PlayerTeleportReader reader;

  public ProtocolLibPlayerTeleportView(PacketEvent event) {
    this.event = event;
    this.packet = event.getPacket();
    this.reader = PacketReaders.readerOf(this.packet);
  }

  /** @return the wrapped reader, for teleport code that still needs ProtocolLib specifics. */
  public PlayerTeleportReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return event.getPlayer();
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
  public void setPositionX(double positionX) {
    reader.setPositionX(positionX);
  }

  @Override
  public void setPositionY(double positionY) {
    reader.setPositionY(positionY);
  }

  @Override
  public void setPositionZ(double positionZ) {
    reader.setPositionZ(positionZ);
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
  public Set<Relative> flags() {
    return reader.flags();
  }

  @Override
  public void setFlags(Set<Relative> flags) {
    reader.setFlags(flags);
  }

  @Override
  public Motion motion() {
    return reader.motion();
  }

  @Override
  public boolean dismountVehicle() {
    // Absent before 1.17; readSafely yields null there rather than throwing.
    Boolean dismountVehicle = packet.getBooleans().readSafely(0);
    return dismountVehicle != null && dismountVehicle;
  }

  @Override
  public int teleportId() {
    return packet.getIntegers().read(0);
  }

  @Override
  public void flush() {
    reader.flush();
  }

  @Override
  public void release() {
    reader.release();
  }
}
