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
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * {@link BlockInteractionView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction: the placement checks query the clicked block and
 * the clicked face several times per packet and every PacketEvents getter re-reads the buffer.
 * <p>
 * The face index is taken from the raw face id, which is already the vanilla D-U-N-S-W-E ordering
 * that {@link Direction} uses, so it lines up with the ordinals the ProtocolLib reader produces.
 * A packet without a face (the bare use item packet) reports 255, the same "empty interaction"
 * marker the ProtocolLib reader uses.
 */
public final class PacketEventsBlockInteractionView implements BlockInteractionView {

  /** Resolved per instance, exactly like the ProtocolLib reader, so no version lookup happens at class load. */
  private final boolean HAS_SEQUENCE_NUMBER = MinecraftVersions.VER1_19_2.atOrAbove();

  private final PacketReceiveEvent event;
  private final BlockPosition blockPosition;
  private final int enumDirection;
  private final Vector facingVector;
  private final int packetSequenceNumber;

  private int sequenceNumber = 0;
  private boolean hasArtificialSequenceNumber = false;

  private PacketEventsBlockInteractionView(
    PacketReceiveEvent event,
    @Nullable BlockPosition blockPosition,
    int enumDirection,
    @Nullable Vector facingVector,
    int packetSequenceNumber
  ) {
    this.event = event;
    this.blockPosition = blockPosition;
    this.enumDirection = enumDirection;
    this.facingVector = facingVector;
    this.packetSequenceNumber = packetSequenceNumber;
  }

  /** @return a view over the event, or null when the packet is not a block interaction. */
  public static PacketEventsBlockInteractionView of(PacketReceiveEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
      WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
      Vector3i position = wrapper.getBlockPosition();
      Vector3f cursor = wrapper.getCursorPosition();
      return new PacketEventsBlockInteractionView(
        event,
        position == null ? null : new BlockPosition(position.getX(), position.getY(), position.getZ()),
        faceIndexOf(wrapper.getFaceId()),
        cursor == null ? null : new Vector(cursor.getX(), cursor.getY(), cursor.getZ()),
        wrapper.getSequence()
      );
    }
    if (type == PacketType.Play.Client.USE_ITEM) {
      WrapperPlayClientUseItem wrapper = new WrapperPlayClientUseItem(event);
      // The bare use item packet carries no block, no face and no cursor position.
      return new PacketEventsBlockInteractionView(event, null, 255, null, wrapper.getSequence());
    }
    return null;
  }

  private static int faceIndexOf(int faceId) {
    return faceId < 0 || faceId >= Direction.values().length ? 255 : faceId;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean cancelled() {
    return event.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public @Nullable BlockPosition blockPosition() {
    return blockPosition;
  }

  @Override
  public int enumDirection() {
    return enumDirection;
  }

  @Override
  public @Nullable Direction direction() {
    Direction[] directions = Direction.values();
    if (enumDirection < 0 || enumDirection >= directions.length) {
      return null;
    }
    return directions[enumDirection];
  }

  @Override
  public @Nullable Vector facingVector() {
    return facingVector;
  }

  @Override
  public int sequenceNumber(User user) {
    if (HAS_SEQUENCE_NUMBER) {
      return packetSequenceNumber;
    } else if (hasArtificialSequenceNumber) {
      return sequenceNumber;
    } else {
      hasArtificialSequenceNumber = true;
      return sequenceNumber = user.meta().connection().simulatedBlockAckNum++;
    }
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction.
  }
}
