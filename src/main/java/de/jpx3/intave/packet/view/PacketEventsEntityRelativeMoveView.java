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
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EntityRelativeMoveView} backed by PacketEvents.
 * <p>
 * The three packets of this family have three separate wrappers here, unlike ProtocolLib where they
 * share one packet class, so the factory dispatches on the packet type and normalises them back
 * into one shape. The rotation only packet contributes no movement and reports zero deltas, which
 * is the same value the ProtocolLib backend reads out of the inherited, unset delta fields of the
 * shared server side packet class.
 *
 * <h2>Reconstructing the wire units</h2>
 * PacketEvents hands out the deltas already divided into blocks, while
 * {@link de.jpx3.intave.module.tracker.entity.Entity} accumulates them in wire units; see
 * {@link EntityRelativeMoveView} for why. Multiplying back is lossless rather than a re-rounding:
 * the wrapper produced the double as {@code raw / divisor} with a divisor of 4096 or 32, both exact
 * powers of two, so for every value the wire can carry - a short from 1.9 on, a byte below it - the
 * quotient is exactly representable and {@code Math.round(delta * divisor)} returns the original
 * integer bit for bit.
 * <p>
 * The divisor is taken from the wrapper's own {@link ServerVersion} rather than from Intave's
 * version table, so the multiplication always uses the very divisor the wrapper divided by.
 */
public final class PacketEventsEntityRelativeMoveView implements EntityRelativeMoveView {

  private final PacketSendEvent event;
  private final int entityId;
  private final long deltaX;
  private final long deltaY;
  private final long deltaZ;
  private final double divisor;

  private PacketEventsEntityRelativeMoveView(
    PacketSendEvent event,
    int entityId,
    long deltaX,
    long deltaY,
    long deltaZ,
    double divisor
  ) {
    this.event = event;
    this.entityId = entityId;
    this.deltaX = deltaX;
    this.deltaY = deltaY;
    this.deltaZ = deltaZ;
    this.divisor = divisor;
  }

  /**
   * @return a view over the event, or null when the packet is not a member of the relative move
   * family.
   */
  public static PacketEventsEntityRelativeMoveView of(PacketSendEvent event) {
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
      WrapperPlayServerEntityRelativeMove wrapper = new WrapperPlayServerEntityRelativeMove(event);
      double divisor = divisorOf(wrapper);
      return new PacketEventsEntityRelativeMoveView(
        event,
        wrapper.getEntityId(),
        toWireUnits(wrapper.getDeltaX(), divisor),
        toWireUnits(wrapper.getDeltaY(), divisor),
        toWireUnits(wrapper.getDeltaZ(), divisor),
        divisor
      );
    }
    if (packetType == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
      WrapperPlayServerEntityRelativeMoveAndRotation wrapper =
        new WrapperPlayServerEntityRelativeMoveAndRotation(event);
      double divisor = divisorOf(wrapper);
      return new PacketEventsEntityRelativeMoveView(
        event,
        wrapper.getEntityId(),
        toWireUnits(wrapper.getDeltaX(), divisor),
        toWireUnits(wrapper.getDeltaY(), divisor),
        toWireUnits(wrapper.getDeltaZ(), divisor),
        divisor
      );
    }
    if (packetType == PacketType.Play.Server.ENTITY_ROTATION) {
      WrapperPlayServerEntityRotation wrapper = new WrapperPlayServerEntityRotation(event);
      // Rotation only: no deltas on the wire, so the entity does not move. The ProtocolLib backend
      // reads the same zeroes out of the shared packet class' unset delta fields.
      return new PacketEventsEntityRelativeMoveView(
        event, wrapper.getEntityId(), 0L, 0L, 0L, divisorOf(wrapper)
      );
    }
    return null;
  }

  private static double divisorOf(PacketWrapper<?> wrapper) {
    ServerVersion serverVersion = wrapper.getServerVersion();
    return serverVersion != null && serverVersion.isNewerThanOrEquals(ServerVersion.V_1_9)
      ? 4096d
      : 32d;
  }

  private static long toWireUnits(double delta, double divisor) {
    return Math.round(delta * divisor);
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
  public long deltaX() {
    return deltaX;
  }

  @Override
  public long deltaY() {
    return deltaY;
  }

  @Override
  public long deltaZ() {
    return deltaZ;
  }

  @Override
  public double divisor() {
    return divisor;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction and never written to.
  }
}
