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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * {@link EntityTrackerTeleportView} backed by ProtocolLib.
 * <p>
 * Reads the very fields {@code Entity#immediateEntityTeleport} and {@code Entity#handleEntityTeleport}
 * read inline before the view existed, from the same converters and in the same version order: the
 * {@code PositionMoveRotation} record plus {@code Relative.flagsFrom} from 1.21.3, double slots zero
 * to two from 1.9, integer slots one to three below that - slot zero being the entity id on every
 * generation.
 * <p>
 * The payload is read lazily and then cached. Lazily, because the tracker drops most of these
 * packets on the entity id alone; cached, because the delayed half of the teleport runs inside a
 * tick feedback callback, long after this listener returned, and re-reading the container there
 * would observe whatever later listeners did to the packet rather than what was decoded when the
 * teleport was applied. The immediate half always runs first, so the cache is filled while the
 * packet is still the one this subscription saw.
 */
public final class EntityTrackerProtocolLibTeleportView implements EntityTrackerTeleportView {

  private static final boolean POSITION_PROCESSING_1_9 = MinecraftVersions.VER1_9_0.atOrAbove();
  private static final boolean POSITION_PROCESSING_1_21_3 = MinecraftVersions.VER1_21_3.atOrAbove();

  private final PacketEvent event;

  private boolean payloadRead;
  private Position position;
  private Set<Relative> relativeFlags;

  public EntityTrackerProtocolLibTeleportView(PacketEvent event) {
    this.event = event;
  }

  /** @return the wrapped event, for teleport code that still needs ProtocolLib specifics. */
  public PacketEvent event() {
    return event;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public @Nullable Integer entityId() {
    return event.getPacket().getIntegers().readSafely(0);
  }

  @Override
  public Position position() {
    readPayload();
    return position;
  }

  @Override
  public Set<Relative> relativeFlags() {
    readPayload();
    return relativeFlags;
  }

  @Override
  public boolean resolvesRelatively() {
    return POSITION_PROCESSING_1_21_3;
  }

  @Override
  public long toWirePosition(double coordinate) {
    // 1/4096 of a block from 1.9 on; this is the conversion the entity's own accumulators use.
    return POSITION_PROCESSING_1_9
      ? ClientMath.positionLong(coordinate)
      : Math.round(coordinate * 32d);
  }

  private void readPayload() {
    if (payloadRead) {
      return;
    }
    PacketContainer packet = event.getPacket();
    if (POSITION_PROCESSING_1_21_3) {
      position = PositionMoveRotation.firstFrom(packet).position();
      relativeFlags = Relative.flagsFrom(packet);
    } else if (POSITION_PROCESSING_1_9) {
      StructureModifier<Double> doubles = packet.getDoubles();
      position = new Position(doubles.read(0), doubles.read(1), doubles.read(2));
      relativeFlags = Collections.emptySet();
    } else {
      // 1.8: three fixed point integers of 1/32 of a block, the unit the accumulators keep too.
      StructureModifier<Integer> integers = packet.getIntegers();
      position = new Position(
        integers.read(1) / 32.0,
        integers.read(2) / 32.0,
        integers.read(3) / 32.0
      );
      relativeFlags = Collections.emptySet();
    }
    payloadRead = true;
  }

  @Override
  public void release() {
    // Nothing held: this view reads converters straight off the event's packet and never borrows a
    // pooled reader.
  }
}
