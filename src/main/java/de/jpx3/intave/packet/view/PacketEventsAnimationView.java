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
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUseBed;
import de.jpx3.intave.packet.reader.AnimationReader;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.entity.Player;

/**
 * {@link AnimationView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction, mirroring {@link PacketEventsEntityVelocityView}:
 * every PacketEvents getter re-reads the packet buffer, and the consumers read the entity id plus
 * one payload field. Nothing is written back, so {@link #release()} has nothing to flush.
 * <p>
 * PacketEvents normalises the per-version animation ids, so the constants map one to one onto the
 * ones the ProtocolLib reader produces. The use bed packet only exists on protocols up to 1.13;
 * on newer ones the server sends the sleeping position as entity metadata instead, which the
 * metadata family already covers on both engines.
 */
public final class PacketEventsAnimationView implements AnimationView {

  private final PacketSendEvent event;
  private final int entityId;
  private final AnimationReader.Animation animation;
  private final BlockPosition bedPosition;

  private PacketEventsAnimationView(
    PacketSendEvent event,
    int entityId,
    AnimationReader.Animation animation,
    BlockPosition bedPosition
  ) {
    this.event = event;
    this.entityId = entityId;
    this.animation = animation;
    this.bedPosition = bedPosition;
  }

  /**
   * @return a view over the event, or null when the packet is neither an entity animation nor a
   * use bed packet, or carries an animation Intave has no equivalent for.
   */
  public static PacketEventsAnimationView of(PacketSendEvent event) {
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.ENTITY_ANIMATION) {
      WrapperPlayServerEntityAnimation wrapper = new WrapperPlayServerEntityAnimation(event);
      AnimationReader.Animation animation = convert(wrapper.getType());
      if (animation == null) {
        return null;
      }
      return new PacketEventsAnimationView(event, wrapper.getEntityId(), animation, null);
    }
    if (packetType == PacketType.Play.Server.USE_BED) {
      WrapperPlayServerUseBed wrapper = new WrapperPlayServerUseBed(event);
      Vector3i position = wrapper.getPosition();
      if (position == null) {
        return null;
      }
      return new PacketEventsAnimationView(
        event,
        wrapper.getEntityId(),
        null,
        new BlockPosition(position.getX(), position.getY(), position.getZ())
      );
    }
    return null;
  }

  private static AnimationReader.Animation convert(
    WrapperPlayServerEntityAnimation.EntityAnimationType type
  ) {
    if (type == null) {
      return null;
    }
    switch (type) {
      case SWING_MAIN_ARM:
        return AnimationReader.Animation.SWING;
      case HURT:
        return AnimationReader.Animation.HURT;
      case WAKE_UP:
        return AnimationReader.Animation.WAKEUP;
      case SWING_OFF_HAND:
        return AnimationReader.Animation.SWING_OFFHAND;
      case CRITICAL_HIT:
        return AnimationReader.Animation.CRIT;
      case MAGIC_CRITICAL_HIT:
        return AnimationReader.Animation.CRIT_MAGIC;
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
  public int entityId() {
    return entityId;
  }

  @Override
  public boolean isBedUse() {
    return bedPosition != null;
  }

  @Override
  public AnimationReader.Animation animation() {
    return animation;
  }

  @Override
  public BlockPosition bedPosition() {
    return bedPosition;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction and never written to.
  }
}
