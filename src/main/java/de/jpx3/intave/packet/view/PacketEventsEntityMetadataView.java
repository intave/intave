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
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * {@link EntityMetadataView} backed by PacketEvents.
 * <p>
 * Entity metadata is a server bound packet, so the factory takes a {@link PacketSendEvent}. The
 * wrapper is decoded once at construction into an index to value map: the trackers fetch several
 * indices from the same packet and every PacketEvents getter re-reads the packet buffer. The first
 * entry for a repeated index wins, matching the ProtocolLib reader's first match lookup.
 * <p>
 * Two values are normalised into the shapes the metadata consumers already pattern match on, so the
 * same consumer code reads either engine identically:
 * <ul>
 *   <li>PacketEvents' {@code EntityPose} becomes ProtocolLib's {@code EnumWrappers.EntityPose}
 *   (identical constant names), because the pose tracker tests for that type;</li>
 *   <li>an {@code Optional<Integer>} becomes an {@link OptionalInt}, which is what the firework
 *   attachment and health readers expect from the NMS side.</li>
 * </ul>
 * Everything else (bytes, booleans, floats) is already the same type on both engines and is passed
 * through untouched.
 */
public final class PacketEventsEntityMetadataView implements EntityMetadataView {

  private static final int BED_POSITION_INDEX = resolveBedPositionIndex();

  private final PacketSendEvent event;
  private final int entityId;
  private final Map<Integer, Object> values;

  private PacketEventsEntityMetadataView(PacketSendEvent event, int entityId, Map<Integer, Object> values) {
    this.event = event;
    this.entityId = entityId;
    this.values = values;
  }

  /** @return a view over the event, or null when the packet is not an entity metadata packet. */
  public static PacketEventsEntityMetadataView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) {
      return null;
    }
    WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
    List<EntityData<?>> metadata = wrapper.getEntityMetadata();
    Map<Integer, Object> values = new HashMap<>();
    if (metadata != null) {
      for (EntityData<?> entry : metadata) {
        if (entry != null) {
          values.putIfAbsent(entry.getIndex(), entry.getValue());
        }
      }
    }
    return new PacketEventsEntityMetadataView(event, wrapper.getEntityId(), values);
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
  public Object fetchRaw(int index) {
    return normalise(values.get(index));
  }

  @Override
  public Optional<BlockPosition> bedPosition() {
    Object raw = values.get(BED_POSITION_INDEX);
    if (raw == null) {
      return Optional.empty();
    }
    try {
      if (raw instanceof Optional) {
        Object present = ((Optional<?>) raw).orElse(null);
        return present instanceof Vector3i
          ? Optional.of(toBlockPosition((Vector3i) present))
          : Optional.empty();
      }
      if (raw instanceof Vector3i) {
        return Optional.of(toBlockPosition((Vector3i) raw));
      }
      return Optional.empty();
    } catch (Exception e) {
      System.err.println("Failed to read bed position from entity metadata, returning empty");
      System.err.println("Required index: " + BED_POSITION_INDEX);
      System.err.println("Target entityid: " + entityId);
      System.err.println("Entity metadata: " + values);
      e.printStackTrace();
      return Optional.empty();
    }
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into a value map at construction.
  }

  private static BlockPosition toBlockPosition(Vector3i vector) {
    return new BlockPosition(vector.getX(), vector.getY(), vector.getZ());
  }

  private static Object normalise(Object raw) {
    if (raw instanceof com.github.retrooper.packetevents.protocol.entity.pose.EntityPose) {
      return toProtocolLibPose((com.github.retrooper.packetevents.protocol.entity.pose.EntityPose) raw);
    }
    if (raw instanceof Optional) {
      Object present = ((Optional<?>) raw).orElse(null);
      if (present instanceof Integer) {
        return OptionalInt.of((Integer) present);
      }
      if (present == null) {
        // Only an empty optional of an unknown payload type; an empty OptionalInt is the shape the
        // consumers handle, and an empty optional carries no payload to misinterpret.
        return OptionalInt.empty();
      }
    }
    return raw;
  }

  private static Object toProtocolLibPose(
    com.github.retrooper.packetevents.protocol.entity.pose.EntityPose pose
  ) {
    try {
      return com.comphenix.protocol.wrappers.EnumWrappers.EntityPose.valueOf(pose.name());
    } catch (IllegalArgumentException | NoClassDefFoundError unknown) {
      // Pose this ProtocolLib build does not know; hand back the PacketEvents constant unchanged so
      // the consumer falls through to its "unknown pose" branch instead of throwing.
      return pose;
    }
  }

  private static int resolveBedPositionIndex() {
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      return 14;
    }
    if (MinecraftVersions.VER1_15_0.atOrAbove()) {
      return 13;
    }
    return 12;
  }
}
