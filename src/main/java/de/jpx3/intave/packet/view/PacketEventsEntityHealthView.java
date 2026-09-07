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
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import de.jpx3.intave.entity.EntityLookup;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link EntityHealthView} backed by PacketEvents.
 * <p>
 * Far less ceremony than the ProtocolLib counterpart: PacketEvents hands out the metadata list as
 * mutable {@link EntityData} objects, so the health entry is edited in place and the packet is
 * flagged for re-encoding. There is no deep clone to perform and no watchable to rebuild - both of
 * those exist on the other side only to work around ProtocolLib's packet container semantics.
 * <p>
 * The wrapper is decoded once at construction, mirroring
 * {@link PacketEventsEntityMetadataView}: every PacketEvents getter re-reads the packet buffer,
 * and both the entity id and the metadata list are needed on every packet of this family.
 */
public final class PacketEventsEntityHealthView implements EntityHealthView {

  /** Legacy protocol index of the health metadata entry; see {@link EntityHealthView}. */
  private static final int HEALTH_INDEX = 6;

  private final PacketSendEvent event;
  private final WrapperPlayServerEntityMetadata wrapper;
  private final int entityId;
  private final List<EntityData> metadata;

  private boolean dirty;

  private PacketEventsEntityHealthView(
    PacketSendEvent event,
    WrapperPlayServerEntityMetadata wrapper,
    int entityId,
    List<EntityData> metadata
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.entityId = entityId;
    this.metadata = metadata;
  }

  /** @return a view over the event, or null when the packet is not an entity metadata packet. */
  public static PacketEventsEntityHealthView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) {
      return null;
    }
    WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
    return new PacketEventsEntityHealthView(
      event, wrapper, wrapper.getEntityId(), wrapper.getEntityMetadata()
    );
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public @Nullable Entity entity() {
    Player player = player();
    // The ProtocolLib reader resolves against the receiving player's world; without a Bukkit
    // player there is no world to resolve against.
    return player == null ? null : EntityLookup.findEntity(player.getWorld(), entityId);
  }

  @Override
  public void obscureHealth(float health) {
    if (metadata == null) {
      return;
    }
    for (EntityData entry : metadata) {
      if (entry == null || entry.getIndex() != HEALTH_INDEX) {
        continue;
      }
      Object value = entry.getValue();
      if (!(value instanceof Float) || (Float) value == 0.0F) {
        continue;
      }
      entry.setValue(health);
      dirty = true;
    }
  }

  @Override
  public void discard() {
    // Nothing held: the wrapper was decoded at construction and nothing was written.
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    wrapper.setEntityMetadata(metadata);
    event.markForReEncode(true);
    dirty = false;
  }
}
