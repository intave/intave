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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.bukkit.entity.Player;

import java.util.function.IntConsumer;

/**
 * {@link EntityDestroyView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction into the id array, mirroring
 * {@link PacketEventsEntityStatusView}: every PacketEvents getter re-reads the packet buffer and
 * the destroy tracker walks the ids exactly once. {@code WrapperPlayServerDestroyEntities} carries
 * the same version table the ProtocolLib reader does - the single var int form on 1.17.0, the
 * length prefixed int form on 1.7.10 and the var int array everywhere else - so both backends
 * enumerate the same ids in the same order.
 */
public final class PacketEventsEntityDestroyView implements EntityDestroyView {

  private final PacketSendEvent event;
  private final int[] entityIds;

  private PacketEventsEntityDestroyView(PacketSendEvent event, int[] entityIds) {
    this.event = event;
    this.entityIds = entityIds;
  }

  /** @return a view over the event, or null when the packet is not an entity destroy packet. */
  public static PacketEventsEntityDestroyView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.DESTROY_ENTITIES) {
      return null;
    }
    WrapperPlayServerDestroyEntities wrapper = new WrapperPlayServerDestroyEntities(event);
    int[] entityIds = wrapper.getEntityIds();
    return new PacketEventsEntityDestroyView(event, entityIds == null ? EMPTY : entityIds);
  }

  private static final int[] EMPTY = new int[0];

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public void forEachEntityId(IntConsumer action) {
    for (int entityId : entityIds) {
      action.accept(entityId);
    }
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into an int array at construction and never written to.
  }
}
