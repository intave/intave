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
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerInitializeWorldBorder;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorder;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderCenter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderSize;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayWorldBorderLerpSize;
import de.jpx3.intave.packet.reader.WorldBorderReader;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.entity.Player;

/**
 * {@link WorldBorderView} backed by PacketEvents.
 * <p>
 * The packet is decoded into primitives at construction. That is not just the usual "every getter
 * re-reads the buffer" concern the other PacketEvents views have: the consumer folds the border a
 * tick later, from a feedback callback, and by then the PacketEvents buffer is long gone. Decoding
 * up front and folding later is what makes the deferred consumer safe here.
 * <p>
 * The fold in {@link #updated(WorldBorder)} is the same four step sequence
 * {@link WorldBorderReader#updated(WorldBorder)} performs, in the same order, driven by the same
 * {@link WorldBorderReader.UpdateType} flags, so both engines produce the same border for the same
 * packet. The float narrowing on the three size fields mirrors the reader's {@code (float)(double)}
 * casts, which are load bearing: they are what makes a border the server sends as a double compare
 * equal to the one the client rounds to a float.
 */
public final class PacketEventsWorldBorderView implements WorldBorderView {

  private final PacketSendEvent event;
  private final WorldBorderReader.UpdateType type;
  private final double centerX;
  private final double centerZ;
  private final double rawSize;
  private final double oldSize;
  private final double newSize;
  private final long lerpTime;
  private final int absoluteMaxSize;

  private PacketEventsWorldBorderView(
    PacketSendEvent event,
    WorldBorderReader.UpdateType type,
    double centerX,
    double centerZ,
    double rawSize,
    double oldSize,
    double newSize,
    long lerpTime,
    int absoluteMaxSize
  ) {
    this.event = event;
    this.type = type;
    this.centerX = centerX;
    this.centerZ = centerZ;
    this.rawSize = rawSize;
    this.oldSize = oldSize;
    this.newSize = newSize;
    this.lerpTime = lerpTime;
    this.absoluteMaxSize = absoluteMaxSize;
  }

  /**
   * @return a view over the event, or null when the packet is not one of the five world border
   * packets this family covers.
   */
  public static PacketEventsWorldBorderView of(PacketSendEvent event) {
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.WORLD_BORDER) {
      return ofLegacy(event);
    }
    if (packetType == PacketType.Play.Server.INITIALIZE_WORLD_BORDER) {
      WrapperPlayServerInitializeWorldBorder wrapper =
        new WrapperPlayServerInitializeWorldBorder(event);
      return new PacketEventsWorldBorderView(
        event,
        WorldBorderReader.UpdateType.INITIALIZE,
        wrapper.getX(),
        wrapper.getZ(),
        // The ProtocolLib reader takes the raw size of this packet from double index 0, which on
        // the initialize packet is the center X. The value is dead either way - the lerp step
        // right after it replaces the size outright - so it is mirrored rather than corrected,
        // keeping the two engines bit for bit identical.
        (float) wrapper.getX(),
        (float) wrapper.getOldDiameter(),
        (float) wrapper.getNewDiameter(),
        wrapper.getSpeed(),
        wrapper.getPortalTeleportBoundary()
      );
    }
    if (packetType == PacketType.Play.Server.WORLD_BORDER_CENTER) {
      WrapperPlayServerWorldBorderCenter wrapper = new WrapperPlayServerWorldBorderCenter(event);
      return new PacketEventsWorldBorderView(
        event,
        WorldBorderReader.UpdateType.SET_CENTER,
        wrapper.getX(), wrapper.getZ(),
        0, 0, 0, 0L, 0
      );
    }
    if (packetType == PacketType.Play.Server.WORLD_BORDER_SIZE) {
      WrapperPlayServerWorldBorderSize wrapper = new WrapperPlayServerWorldBorderSize(event);
      return new PacketEventsWorldBorderView(
        event,
        WorldBorderReader.UpdateType.SET_SIZE,
        0, 0,
        (float) wrapper.getDiameter(),
        0, 0, 0L, 0
      );
    }
    if (packetType == PacketType.Play.Server.WORLD_BORDER_LERP_SIZE) {
      WrapperPlayWorldBorderLerpSize wrapper = new WrapperPlayWorldBorderLerpSize(event);
      return new PacketEventsWorldBorderView(
        event,
        WorldBorderReader.UpdateType.LERP_SIZE,
        0, 0, 0,
        (float) wrapper.getOldDiameter(),
        (float) wrapper.getNewDiameter(),
        wrapper.getSpeed(),
        0
      );
    }
    return null;
  }

  /**
   * The pre 1.17 packet carries every action in one type, so the action decides which of its
   * fields were written on the wire; the field indices below are the ones the ProtocolLib reader
   * takes for the same action off the NMS packet.
   */
  private static PacketEventsWorldBorderView ofLegacy(PacketSendEvent event) {
    WrapperPlayServerWorldBorder wrapper = new WrapperPlayServerWorldBorder(event);
    WrapperPlayServerWorldBorder.WorldBorderAction action = wrapper.getAction();
    if (action == null) {
      return null;
    }
    switch (action) {
      case SET_SIZE:
        return new PacketEventsWorldBorderView(
          event, WorldBorderReader.UpdateType.SET_SIZE,
          0, 0, (float) wrapper.getRadius(), 0, 0, 0L, 0
        );
      case LERP_SIZE:
        return new PacketEventsWorldBorderView(
          event, WorldBorderReader.UpdateType.LERP_SIZE,
          0, 0, 0,
          (float) wrapper.getOldRadius(), (float) wrapper.getNewRadius(),
          wrapper.getSpeed(), 0
        );
      case SET_CENTER:
        return new PacketEventsWorldBorderView(
          event, WorldBorderReader.UpdateType.SET_CENTER,
          wrapper.getCenterX(), wrapper.getCenterZ(),
          0, 0, 0, 0L, 0
        );
      case INITIALIZE:
        return new PacketEventsWorldBorderView(
          event, WorldBorderReader.UpdateType.INITIALIZE,
          wrapper.getCenterX(), wrapper.getCenterZ(),
          // Same dead raw size as on the modern initialize packet; on the legacy one the index
          // the reader uses happens to hold the new radius instead of the center X.
          (float) wrapper.getNewRadius(),
          (float) wrapper.getOldRadius(), (float) wrapper.getNewRadius(),
          wrapper.getSpeed(), wrapper.getPortalTeleportBoundary()
        );
      case SET_WARNING_TIME:
        return new PacketEventsWorldBorderView(
          event, WorldBorderReader.UpdateType.SET_WARNING_TIME,
          0, 0, 0, 0, 0, 0L, 0
        );
      case SET_WARNING_BLOCKS:
        return new PacketEventsWorldBorderView(
          event, WorldBorderReader.UpdateType.SET_WARNING_BLOCKS,
          0, 0, 0, 0, 0, 0L, 0
        );
      default:
        return null;
    }
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public WorldBorder updated(WorldBorder border) {
    if (type.updatesCenter()) {
      border = border.withCenterAt(new Position(centerX, 0, centerZ));
    }
    if (type.updatesRawSize()) {
      border = border.withSize(rawSize);
    }
    if (type.updatesLerpSize()) {
      border = border.withLerpingSize(oldSize, newSize, lerpTime);
    }
    if (type.updatesAbsoluteMaxSize()) {
      border = border.withAbsoluteMaxSize(absoluteMaxSize);
    }
    return border;
  }
}
