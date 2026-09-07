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
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper;
import de.jpx3.intave.share.Position;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link EntityTrackerPositionSyncView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction. It has to be, because the delayed half of the sync
 * runs inside a tick feedback callback and PacketEvents has released the packet's buffer by then.
 *
 * <h2>What 2.13.0 actually decodes</h2>
 * 2.13.0 declares {@code PacketType.Play.Server.ENTITY_POSITION_SYNC} and ships
 * {@code WrapperPlayServerEntityPositionSync}, whose {@code read} takes a varint entity id, an
 * {@code EntityPositionData} and an on-ground flag; {@code EntityPositionData.read} takes position,
 * delta movement, yaw and pitch in that order - the same four fields, in the same order, that the
 * NMS {@code PositionMoveRotation} record carries and that the ProtocolLib backend converts out of
 * the container. An earlier revision of the entity tracker recorded this packet as unportable
 * because 2.4.0 declared neither; that is a statement about 2.4.0 only.
 *
 * <h2>Why the packet type is checked against two ids</h2>
 * {@link PacketEventsIdMapper} lists {@code ENTITY_TELEPORT} as the fallback candidate of
 * {@code ENTITY_POSITION_SYNC}, because a packet id that resolves to nothing registers no
 * subscription at all. On a server below 1.21.2 - where the position sync packet does not exist -
 * that fallback is what the subscription binds, so this view would be handed plain entity teleports
 * and would apply them a second time, on top of the teleport subscription that already handled
 * them. Declining every type that the teleport id also bound closes that: below 1.21.2 the factory
 * returns null and the teleport twin stays the only handler, and from 1.21.2 the two ids resolve to
 * two different constants and both twins see only their own packet.
 * <p>
 * The check also keeps the wrapper class out of the picture on a runtime whose PacketEvents
 * predates the packet: the factory returns before the {@code new} that would have to resolve it.
 */
public final class EntityTrackerPacketEventsPositionSyncView implements EntityTrackerPositionSyncView {

  private final PacketSendEvent event;
  private final int entityId;
  private final Position position;

  private EntityTrackerPacketEventsPositionSyncView(
    PacketSendEvent event,
    int entityId,
    Position position
  ) {
    this.event = event;
    this.entityId = entityId;
    this.position = position;
  }

  /**
   * @return a view over the event, or null when the packet is not the entity position sync - which
   * includes the case where that id could only be bound through its teleport fallback.
   */
  public static EntityTrackerPacketEventsPositionSyncView of(PacketSendEvent event) {
    PacketTypeCommon packetType = event.getPacketType();
    List<PacketTypeCommon> positionSyncTypes =
      PacketEventsIdMapper.typesOf(PacketId.Server.ENTITY_POSITION_SYNC);
    if (!positionSyncTypes.contains(packetType)) {
      return null;
    }
    if (PacketEventsIdMapper.typesOf(PacketId.Server.ENTITY_TELEPORT).contains(packetType)) {
      return null;
    }
    WrapperPlayServerEntityPositionSync wrapper = new WrapperPlayServerEntityPositionSync(event);
    Vector3d synchronised = wrapper.getValues().getPosition();
    return new EntityTrackerPacketEventsPositionSyncView(
      event,
      wrapper.getId(),
      new Position(synchronised.getX(), synchronised.getY(), synchronised.getZ())
    );
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
  public void release() {
    // Nothing held: the wrapper was decoded at construction and never written to.
  }
}
