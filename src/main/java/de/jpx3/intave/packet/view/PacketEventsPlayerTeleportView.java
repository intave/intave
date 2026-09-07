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
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.share.Motion;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link PlayerTeleportView} backed by PacketEvents.
 * <p>
 * There is no reader pool here: the wrapper is decoded once at construction into primitives, since
 * the teleport path reads every field repeatedly and each PacketEvents getter would otherwise
 * re-read the packet buffer. Writes are cached and pushed back in {@link #flush()}, so an
 * untouched packet is never re-encoded.
 * <p>
 * PacketEvents models the relative flags as a bit mask rather than an enum set, so
 * {@link #flags()} translates the mask into Intave's {@link Relative} constants and
 * {@link #setFlags(Set)} translates back. Only the five positional and rotational flags exist in
 * that mask; the 1.21.3 delta flags have no representation, which is also why {@link #motion()}
 * reports a zero motion - matching the ProtocolLib reader's behaviour on every protocol below
 * 1.21.3.
 */
public final class PacketEventsPlayerTeleportView implements PlayerTeleportView {

  private final PacketSendEvent event;
  private final WrapperPlayServerPlayerPositionAndLook wrapper;

  private double positionX;
  private double positionY;
  private double positionZ;
  private final float yaw;
  private final float pitch;
  private byte relativeMask;
  private final int teleportId;
  private final boolean dismountVehicle;

  private boolean dirty;

  private PacketEventsPlayerTeleportView(
    PacketSendEvent event,
    WrapperPlayServerPlayerPositionAndLook wrapper
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.positionX = wrapper.getX();
    this.positionY = wrapper.getY();
    this.positionZ = wrapper.getZ();
    this.yaw = wrapper.getYaw();
    this.pitch = wrapper.getPitch();
    this.relativeMask = wrapper.getRelativeMask();
    this.teleportId = wrapper.getTeleportId();
    this.dismountVehicle = wrapper.isDismountVehicle();
  }

  /** @return a view over the event, or null when the packet is not a player position packet. */
  public static PacketEventsPlayerTeleportView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
      return null;
    }
    return new PacketEventsPlayerTeleportView(
      event,
      new WrapperPlayServerPlayerPositionAndLook(event)
    );
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public double positionX() {
    return positionX;
  }

  @Override
  public double positionY() {
    return positionY;
  }

  @Override
  public double positionZ() {
    return positionZ;
  }

  @Override
  public void setPositionX(double positionX) {
    this.positionX = positionX;
    this.dirty = true;
  }

  @Override
  public void setPositionY(double positionY) {
    this.positionY = positionY;
    this.dirty = true;
  }

  @Override
  public void setPositionZ(double positionZ) {
    this.positionZ = positionZ;
    this.dirty = true;
  }

  @Override
  public float yaw() {
    return yaw;
  }

  @Override
  public float pitch() {
    return pitch;
  }

  @Override
  public Set<Relative> flags() {
    // A fresh mutable set per call: the consumer strips the flags it resolved before writing back.
    Set<Relative> flags = new HashSet<>();
    if (RelativeFlag.X.isSet(relativeMask)) {
      flags.add(Relative.X);
    }
    if (RelativeFlag.Y.isSet(relativeMask)) {
      flags.add(Relative.Y);
    }
    if (RelativeFlag.Z.isSet(relativeMask)) {
      flags.add(Relative.Z);
    }
    if (RelativeFlag.YAW.isSet(relativeMask)) {
      flags.add(Relative.Y_ROT);
    }
    if (RelativeFlag.PITCH.isSet(relativeMask)) {
      flags.add(Relative.X_ROT);
    }
    return flags;
  }

  @Override
  public void setFlags(Set<Relative> flags) {
    byte mask = 0;
    mask = RelativeFlag.X.set(mask, flags.contains(Relative.X));
    mask = RelativeFlag.Y.set(mask, flags.contains(Relative.Y));
    mask = RelativeFlag.Z.set(mask, flags.contains(Relative.Z));
    mask = RelativeFlag.YAW.set(mask, flags.contains(Relative.Y_ROT));
    mask = RelativeFlag.PITCH.set(mask, flags.contains(Relative.X_ROT));
    this.relativeMask = mask;
    this.dirty = true;
  }

  @Override
  public Motion motion() {
    // The delta relative flags cannot be encoded in this wrapper's byte mask, so they are never
    // reported by flags() and this motion is never consulted. Zero mirrors the ProtocolLib
    // reader's answer on the protocols that carry no delta.
    return new Motion(0, 0, 0);
  }

  @Override
  public boolean dismountVehicle() {
    return dismountVehicle;
  }

  @Override
  public int teleportId() {
    return teleportId;
  }

  @Override
  public void flush() {
    if (!dirty) {
      return;
    }
    wrapper.setX(positionX);
    wrapper.setY(positionY);
    wrapper.setZ(positionZ);
    wrapper.setRelativeMask(relativeMask);
    event.markForReEncode(true);
    dirty = false;
  }

  @Override
  public void release() {
    flush();
  }
}
