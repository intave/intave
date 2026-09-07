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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import de.jpx3.intave.entity.EntityLookup;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EntityGenericView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction and the entity id cached, mirroring
 * {@link PacketEventsEntityVelocityView}: every PacketEvents getter re-reads the packet buffer, and
 * the entity id is read on every packet of this family. The destroy stage write is cached as a
 * dirty flag and only flushed in {@link #release()}, so an untouched packet is never re-encoded.
 */
public final class PacketEventsEntityGenericView implements EntityGenericView {

  private final PacketSendEvent event;
  private final WrapperPlayServerBlockBreakAnimation wrapper;
  private final int entityId;

  private boolean dirty;

  private PacketEventsEntityGenericView(
    PacketSendEvent event,
    WrapperPlayServerBlockBreakAnimation wrapper,
    int entityId
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.entityId = entityId;
  }

  /** @return a view over the event, or null when the packet is not a block break animation. */
  public static PacketEventsEntityGenericView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
      return null;
    }
    WrapperPlayServerBlockBreakAnimation wrapper = new WrapperPlayServerBlockBreakAnimation(event);
    return new PacketEventsEntityGenericView(event, wrapper, wrapper.getEntityId());
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
  public @Nullable Entity entity() {
    Player player = player();
    // The ProtocolLib reader resolves against the receiving player's world; without a Bukkit
    // player - an early login packet, for instance - there is no world to resolve against.
    return player == null ? null : EntityLookup.findEntity(player.getWorld(), entityId);
  }

  @Override
  public void setDestroyStage(int destroyStage) {
    wrapper.setDestroyStage((byte) destroyStage);
    dirty = true;
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    event.markForReEncode(true);
    dirty = false;
  }
}
