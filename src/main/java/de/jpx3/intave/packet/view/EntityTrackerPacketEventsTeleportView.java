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
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.Position;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * {@link EntityTrackerTeleportView} backed by PacketEvents.
 * <p>
 * There is no reader pool here: the wrapper is decoded once at construction into a position, a flag
 * set and two version answers. It has to be, because the delayed half of the teleport runs inside a
 * tick feedback callback and PacketEvents has released the packet's buffer by then.
 *
 * <h2>What 2.13.0 actually decodes</h2>
 * {@code WrapperPlayServerEntityTeleport#read} branches on its own {@link ServerVersion}: from
 * {@code V_1_21_2} it reads a varint id, an {@code EntityPositionData} - position, delta movement,
 * yaw, pitch, in that order - and an int relative mask; below that it reads the id, then the
 * position as three doubles from 1.9 or three ints divided by 32 below, a byte yaw and pitch, and
 * leaves the relative flag field untouched. So {@link #relativeFlags()} is empty below 1.21.2 for
 * two independent reasons: the wire carries no flags there, and the field the getter returns is
 * null. Both are handled; the null is never dereferenced.
 * <p>
 * An earlier revision of the entity tracker recorded this packet as unportable because PacketEvents
 * "decodes an absolute {@code Vector3d} and carries no relative flag set at all". That was true of
 * 2.4.0 and is not true of 2.13.0, which carries {@code getRelativeFlags()} and the full
 * {@code EntityPositionData} of the 1.21.2 format.
 *
 * <h2>Flag translation</h2>
 * PacketEvents models the flags as an int mask and Intave as an enum set, and the two agree bit for
 * bit: {@code X} is 1, {@code Y} 2, {@code Z} 4, {@code YAW} 8, {@code PITCH} 16, the three deltas
 * 32/64/128 and {@code ROTATE_DELTA} 256, which are exactly the shifts
 * {@link Relative}'s slots produce. The translation is written out by name anyway rather than
 * passing the mask through, so a future renumbering on either side is a compile error instead of a
 * silently mismapped axis - and a mismapped axis here is an entity placed at its own offset.
 */
public final class EntityTrackerPacketEventsTeleportView implements EntityTrackerTeleportView {

  private final PacketSendEvent event;
  private final int entityId;
  private final Position position;
  private final Set<Relative> relativeFlags;
  private final boolean resolvesRelatively;
  private final boolean fixedPointSince1_9;

  private EntityTrackerPacketEventsTeleportView(
    PacketSendEvent event,
    int entityId,
    Position position,
    Set<Relative> relativeFlags,
    boolean resolvesRelatively,
    boolean fixedPointSince1_9
  ) {
    this.event = event;
    this.entityId = entityId;
    this.position = position;
    this.relativeFlags = relativeFlags;
    this.resolvesRelatively = resolvesRelatively;
    this.fixedPointSince1_9 = fixedPointSince1_9;
  }

  /**
   * @return a view over the event, or null when the packet is not the entity teleport.
   * <p>
   * The type is compared against what {@link PacketEventsIdMapper} bound for
   * {@link PacketId.Server#ENTITY_TELEPORT} rather than against a named constant, so this view can
   * never be built over a packet the subscription only received through a fallback binding.
   */
  public static EntityTrackerPacketEventsTeleportView of(PacketSendEvent event) {
    PacketTypeCommon packetType = event.getPacketType();
    if (!PacketEventsIdMapper.typesOf(PacketId.Server.ENTITY_TELEPORT).contains(packetType)) {
      return null;
    }
    WrapperPlayServerEntityTeleport wrapper = new WrapperPlayServerEntityTeleport(event);
    ServerVersion serverVersion = wrapper.getServerVersion();
    boolean resolvesRelatively = serverVersion != null
      && serverVersion.isNewerThanOrEquals(ServerVersion.V_1_21_2);
    boolean fixedPointSince1_9 = serverVersion == null
      || serverVersion.isNewerThanOrEquals(ServerVersion.V_1_9);
    Vector3d payload = wrapper.getPosition();
    return new EntityTrackerPacketEventsTeleportView(
      event,
      wrapper.getEntityId(),
      new Position(payload.getX(), payload.getY(), payload.getZ()),
      resolvesRelatively ? translate(wrapper.getRelativeFlags()) : Collections.emptySet(),
      resolvesRelatively,
      fixedPointSince1_9
    );
  }

  /** @return the Intave flag set of a PacketEvents relative mask; empty when there is none. */
  private static Set<Relative> translate(RelativeFlag flags) {
    if (flags == null) {
      return Collections.emptySet();
    }
    Set<Relative> translated = EnumSet.noneOf(Relative.class);
    if (flags.has(RelativeFlag.X)) {
      translated.add(Relative.X);
    }
    if (flags.has(RelativeFlag.Y)) {
      translated.add(Relative.Y);
    }
    if (flags.has(RelativeFlag.Z)) {
      translated.add(Relative.Z);
    }
    if (flags.has(RelativeFlag.YAW)) {
      translated.add(Relative.Y_ROT);
    }
    if (flags.has(RelativeFlag.PITCH)) {
      translated.add(Relative.X_ROT);
    }
    if (flags.has(RelativeFlag.DELTA_X)) {
      translated.add(Relative.DELTA_X);
    }
    if (flags.has(RelativeFlag.DELTA_Y)) {
      translated.add(Relative.DELTA_Y);
    }
    if (flags.has(RelativeFlag.DELTA_Z)) {
      translated.add(Relative.DELTA_Z);
    }
    if (flags.has(RelativeFlag.ROTATE_DELTA)) {
      translated.add(Relative.ROTATE_DELTA);
    }
    return translated;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public @Nullable Integer entityId() {
    return entityId;
  }

  @Override
  public Position position() {
    return position;
  }

  @Override
  public Set<Relative> relativeFlags() {
    return relativeFlags;
  }

  @Override
  public boolean resolvesRelatively() {
    return resolvesRelatively;
  }

  @Override
  public long toWirePosition(double coordinate) {
    // Below 1.9 the wrapper produced this coordinate as wireInt / 32.0, an exact power of two
    // quotient, so multiplying back returns the original integer bit for bit rather than re-rounding.
    return fixedPointSince1_9
      ? ClientMath.positionLong(coordinate)
      : Math.round(coordinate * 32d);
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded at construction and never written to.
  }
}
