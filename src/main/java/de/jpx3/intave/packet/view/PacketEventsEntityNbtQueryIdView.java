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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientQueryEntityNBT;
import org.bukkit.entity.Player;

/**
 * {@link EntityInteractIdView} backed by PacketEvents, for the entity NBT query packet.
 * <p>
 * This is the second PacketEvents implementation of the family. On the ProtocolLib side the entity
 * id filter reaches the target of an interaction and the target of an NBT query through the very
 * same field - the packet's first integer - so one subscription covers both. PacketEvents has no
 * such shared accessor: interactions decode through {@code WrapperPlayClientInteractEntity} and
 * queries through {@code WrapperPlayClientQueryEntityNBT}, which is why the query gets its own
 * implementation of the existing interface rather than a new family.
 * <p>
 * Decoding, caching and the deferred re-encode mirror {@link PacketEventsEntityInteractIdView}
 * exactly: the id is read once at construction because every PacketEvents getter re-reads the
 * packet buffer, and a packet whose target was left alone is never re-encoded.
 */
public final class PacketEventsEntityNbtQueryIdView implements EntityInteractIdView {

  private final PacketReceiveEvent event;
  private final WrapperPlayClientQueryEntityNBT wrapper;

  private int entityId;
  private boolean dirty;

  private PacketEventsEntityNbtQueryIdView(
    PacketReceiveEvent event,
    WrapperPlayClientQueryEntityNBT wrapper,
    int entityId
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.entityId = entityId;
  }

  /** @return a view over the event, or null when the packet is not an entity NBT query. */
  public static PacketEventsEntityNbtQueryIdView of(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.QUERY_ENTITY_NBT) {
      return null;
    }
    WrapperPlayClientQueryEntityNBT wrapper = new WrapperPlayClientQueryEntityNBT(event);
    return new PacketEventsEntityNbtQueryIdView(event, wrapper, wrapper.getEntityId());
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
