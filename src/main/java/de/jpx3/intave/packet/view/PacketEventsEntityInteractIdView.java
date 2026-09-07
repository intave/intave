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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;

/**
 * {@link EntityInteractIdView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction and the target id cached, mirroring
 * {@link PacketEventsAttackView}: every PacketEvents getter re-reads the packet buffer. The
 * redirect is cached as a dirty flag and only flushed in {@link #release()}, so a packet whose
 * target was left alone - which is every interaction outside the decoy path - is never re-encoded.
 */
public final class PacketEventsEntityInteractIdView implements EntityInteractIdView {

  private final PacketReceiveEvent event;
  private final WrapperPlayClientInteractEntity wrapper;

  private int entityId;
  private boolean dirty;

  private PacketEventsEntityInteractIdView(
    PacketReceiveEvent event,
    WrapperPlayClientInteractEntity wrapper,
    int entityId
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.entityId = entityId;
  }

  /** @return a view over the event, or null when the packet is not an entity interaction. */
  public static PacketEventsEntityInteractIdView of(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
      return null;
    }
    WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
    return new PacketEventsEntityInteractIdView(event, wrapper, wrapper.getEntityId());
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public Integer entityId() {
    return entityId;
  }

  @Override
  public void setEntityId(int entityId) {
    this.entityId = entityId;
    wrapper.setEntityId(entityId);
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
