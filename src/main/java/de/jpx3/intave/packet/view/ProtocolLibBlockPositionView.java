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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.BlockPositionReader;
import de.jpx3.intave.packet.reader.ChunkCoordinateReader;
import de.jpx3.intave.packet.reader.PacketReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;

/**
 * {@link BlockPositionView} backed by ProtocolLib. Wraps the pooled readers so the existing reader
 * pooling and release semantics stay exactly as they were.
 * <p>
 * Two entry points exist because the subscriptions in this family are declared both ways: the raw
 * {@link PacketEvent} form, and the form where the linker injects the reader plus a
 * {@link Cancellable}. The chunk subscription gets no cancellable at all, which is why one
 * constructor leaves it out.
 */
public final class ProtocolLibBlockPositionView implements BlockPositionView {

  private final Player player;
  private final PacketContainer packet;
  private final PacketReader reader;
  private final Cancellable cancellable;
  private final Kind kind;

  public ProtocolLibBlockPositionView(PacketEvent event) {
    this(event.getPlayer(), event.getPacket(), PacketReaders.readerOf(event.getPacket()), event);
  }

  /** Entry point for the chunk subscription, which is never handed a cancellable. */
  public ProtocolLibBlockPositionView(Player player, PacketContainer packet, PacketReader reader) {
    this(player, packet, reader, null);
  }

  public ProtocolLibBlockPositionView(
    Player player, PacketContainer packet, PacketReader reader, @Nullable Cancellable cancellable
  ) {
    this.player = player;
    this.packet = packet;
    this.reader = reader;
    this.cancellable = cancellable;
    this.kind = kindOf(packet.getType());
  }

  private static Kind kindOf(PacketType packetType) {
    if (packetType == PacketType.Play.Client.BLOCK_DIG) {
      return Kind.BLOCK_DIG;
    }
    // BLOCK_PLACE and USE_ITEM_ON are the same packet; which constant a given ProtocolLib build
    // hands out depends on the server version, so both map onto the placement kind.
    if (packetType == PacketType.Play.Client.BLOCK_PLACE || packetType == PacketType.Play.Client.USE_ITEM_ON) {
      return Kind.BLOCK_PLACE;
    }
    if (packetType == PacketType.Play.Client.USE_ITEM) {
      return Kind.USE_ITEM;
    }
    if (packetType == PacketType.Play.Server.MAP_CHUNK || packetType == PacketType.Play.Server.MAP_CHUNK_BULK) {
      return Kind.CHUNK_DATA;
    }
    throw new IllegalArgumentException("Not a block position packet: " + packetType.name());
  }

  /** @return the wrapped reader, for code that still needs ProtocolLib specifics. */
  public PacketReader reader() {
    return reader;
  }

  /** @return the backing packet, for code that still clones or forwards it. */
  public PacketContainer packet() {
    return packet;
  }

  /** @return the targeted block as the ProtocolLib wrapper, for code that has not been converted yet. */
  @Nullable
  public com.comphenix.protocol.wrappers.BlockPosition protocolLibBlockPosition() {
    return reader instanceof BlockPositionReader
      ? ((BlockPositionReader) reader).blockPosition()
      : null;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public Kind kind() {
    return kind;
  }

  @Override
  public boolean cancelled() {
    return cancellable != null && cancellable.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    if (cancellable == null) {
      throw new IllegalStateException("This packet was delivered without a cancellable: " + packet.getType().name());
    }
    cancellable.setCancelled(cancelled);
  }

  @Override
  public @Nullable BlockPosition blockPosition() {
    return reader instanceof BlockPositionReader
      ? ((BlockPositionReader) reader).nativeBlockPosition()
      : null;
  }

  @Override
  public @Nullable DigAction digAction() {
    if (kind != Kind.BLOCK_DIG) {
      return null;
    }
    EnumWrappers.PlayerDigType digType = reader instanceof BlockDigReader
      ? ((BlockDigReader) reader).action()
      : packet.getPlayerDigTypes().readSafely(0);
    return digActionOf(digType);
  }

  /**
   * Public because the digging handler in {@code PlayerHandTracker} maps the very same enum on the
   * ProtocolLib side of its engine independent body and there is no reason for a second copy of the
   * table. Widened visibility only; the mapping itself is untouched.
   */
  public static @Nullable DigAction digActionOf(@Nullable EnumWrappers.PlayerDigType digType) {
    if (digType == null) {
      return null;
    }
    switch (digType) {
      case START_DESTROY_BLOCK:
        return DigAction.START_DESTROY_BLOCK;
      case ABORT_DESTROY_BLOCK:
        return DigAction.ABORT_DESTROY_BLOCK;
      case STOP_DESTROY_BLOCK:
        return DigAction.STOP_DESTROY_BLOCK;
      case DROP_ALL_ITEMS:
        return DigAction.DROP_ALL_ITEMS;
      case DROP_ITEM:
        return DigAction.DROP_ITEM;
      case RELEASE_USE_ITEM:
        return DigAction.RELEASE_USE_ITEM;
      case SWAP_HELD_ITEMS:
        return DigAction.SWAP_HELD_ITEMS;
      case STAB:
        return DigAction.STAB;
      default:
        return null;
    }
  }

  /**
   * Read straight off the packet's direction modifier rather than through a reader, because that is
   * how the digging face has always been read: {@code BlockDigReader} exposes only the action, and
   * {@code BlockInteractionReader#enumDirection()} resolves the placement packet's hit result,
   * which the digging packet does not carry. {@code readSafely} rather than {@code read} so a build
   * without the field reports the 255 marker instead of throwing.
   */
  @Override
  public int digFaceIndex() {
    if (kind != Kind.BLOCK_DIG) {
      return 255;
    }
    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    return direction == null ? 255 : direction.ordinal();
  }

  @Override
  public int enumDirection() {
    return reader instanceof BlockInteractionReader
      ? ((BlockInteractionReader) reader).enumDirection()
      : 255;
  }

  /**
   * Read straight off the packet rather than through a reader: no reader in this family ever
   * exposed the placement item, and the consumer that needs it read the item modifier itself.
   */
  @Override
  public @Nullable ItemStack placementItem() {
    return kind == Kind.BLOCK_PLACE ? packet.getItemModifier().readSafely(0) : null;
  }

  @Override
  public int[] chunkXCoordinates() {
    return chunkReader().xCoordinates();
  }

  @Override
  public int[] chunkZCoordinates() {
    return chunkReader().zCoordinates();
  }

  private ChunkCoordinateReader chunkReader() {
    if (kind != Kind.CHUNK_DATA || !(reader instanceof ChunkCoordinateReader)) {
      throw new IllegalStateException("Chunk coordinates are only carried by the chunk data packets");
    }
    return (ChunkCoordinateReader) reader;
  }

  @Override
  public void release() {
    reader.release();
  }
}
