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

package de.jpx3.intave.module.linker.packet.pe.reader;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;

/**
 * PacketEvents backed reader for the five movement packets Intave subscribes to:
 * FLYING, LOOK, POSITION, POSITION_LOOK and VEHICLE_MOVE.
 * <p>
 * Mirrors the surface of the ProtocolLib {@code PlayerMoveReader} so the movement logic can be
 * pointed at either engine. Two behaviours are deliberately copied from that reader rather than
 * taken from PacketEvents directly:
 * <ul>
 *   <li>a vehicle move always counts as carrying both position and rotation, because the client
 *   omits the "changed" flags on that packet;</li>
 *   <li>reads are done once and cached, since the movement path queries the same fields many times
 *   per packet and every PacketEvents getter re-reads the buffer.</li>
 * </ul>
 * Instances are per packet and not thread safe, matching how the ProtocolLib readers are used.
 */
public final class PeMoveReader {

  private final PacketReceiveEvent event;
  private final boolean vehicleMove;

  private WrapperPlayClientPlayerFlying flying;
  private WrapperPlayClientVehicleMove vehicle;

  private double x, y, z;
  private float yaw, pitch;
  private boolean onGround;
  private boolean hasMovement;
  private boolean hasRotation;
  private boolean dirty;

  private PeMoveReader(PacketReceiveEvent event, boolean vehicleMove) {
    this.event = event;
    this.vehicleMove = vehicleMove;
  }

  /** @return true when this packet type is one this reader understands. */
  public static boolean handles(PacketTypeCommon type) {
    return WrapperPlayClientPlayerFlying.isFlying(type) || type == PacketType.Play.Client.VEHICLE_MOVE;
  }

  /** @return a reader positioned on the event's packet, or null for unrelated packets. */
  public static PeMoveReader of(PacketReceiveEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.VEHICLE_MOVE) {
      PeMoveReader reader = new PeMoveReader(event, true);
      reader.readVehicle();
      return reader;
    }
    if (WrapperPlayClientPlayerFlying.isFlying(type)) {
      PeMoveReader reader = new PeMoveReader(event, false);
      reader.readFlying();
      return reader;
    }
    return null;
  }

  private void readFlying() {
    flying = new WrapperPlayClientPlayerFlying(event);
    Location location = flying.getLocation();
    x = location.getX();
    y = location.getY();
    z = location.getZ();
    yaw = location.getYaw();
    pitch = location.getPitch();
    onGround = flying.isOnGround();
    hasMovement = flying.hasPositionChanged();
    hasRotation = flying.hasRotationChanged();
  }

  private void readVehicle() {
    vehicle = new WrapperPlayClientVehicleMove(event);
    Vector3d position = vehicle.getPosition();
    x = position.getX();
    y = position.getY();
    z = position.getZ();
    yaw = vehicle.getYaw();
    pitch = vehicle.getPitch();
    // The client sends no flags here; the boat's own state is the movement.
    onGround = false;
    hasMovement = true;
    hasRotation = true;
  }

  public boolean isVehicleMove() {
    return vehicleMove;
  }

  public double positionX() {
    return x;
  }

  public double positionY() {
    return y;
  }

  public double positionZ() {
    return z;
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public boolean onGround() {
    return onGround;
  }

  public boolean hasMovement() {
    return hasMovement;
  }

  public boolean hasRotation() {
    return hasRotation;
  }

  /** @return true when any transmitted coordinate or angle is NaN or infinite. */
  public boolean anyNaNOrInfiniteValue() {
    if (hasMovement && (invalid(x) || invalid(y) || invalid(z))) {
      return true;
    }
    return hasRotation && (invalid(yaw) || invalid(pitch));
  }

  private static boolean invalid(double value) {
    return Double.isNaN(value) || Double.isInfinite(value);
  }

  public void setPositionX(double value) {
    x = value;
    dirty = true;
  }

  public void setPositionY(double value) {
    y = value;
    dirty = true;
  }

  public void setPositionZ(double value) {
    z = value;
    dirty = true;
  }

  public void setYaw(float value) {
    yaw = value;
    dirty = true;
  }

  public void setPitch(float value) {
    pitch = value;
    dirty = true;
  }

  public void setOnGround(boolean value) {
    onGround = value;
    dirty = true;
  }

  /**
   * Writes cached edits back into the packet. PacketEvents only re-encodes a packet when asked, so
   * a reader that never modified anything costs nothing here.
   */
  public void flush() {
    if (!dirty) {
      return;
    }
    if (vehicleMove) {
      vehicle.setPosition(new Vector3d(x, y, z));
      vehicle.setYaw(yaw);
      vehicle.setPitch(pitch);
    } else {
      flying.setLocation(new Location(x, y, z, yaw, pitch));
      flying.setOnGround(onGround);
    }
    event.markForReEncode(true);
    dirty = false;
  }
}
