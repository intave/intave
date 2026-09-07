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
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkDataBulk;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@link BlockPositionView} backed by PacketEvents.
 * <p>
 * Unlike the ProtocolLib view there is no reader pool here: the wrapper is decoded once into
 * primitives at construction, so {@link #release()} has nothing to do.
 * <p>
 * Field mapping against the ProtocolLib readers this replaces:
 * <ul>
 *   <li>{@code BlockPositionReader.blockPosition()} - PacketEvents decodes the block position for
 *       every version into {@code Vector3i}, so the 1.14 hit-result branch the reader carries
 *       disappears here;</li>
 *   <li>{@code BlockDigReader.action()} - PacketEvents' {@code DiggingAction}, mapped onto
 *       {@link DigAction} by its wire id rather than by name, because the two libraries spell the
 *       first three and the last action differently;</li>
 *   <li>the digging packet's own face, which no ProtocolLib reader in this family ever exposed -
 *       PacketEvents decodes it into a {@code BlockFace} whose face value is the vanilla
 *       D-U-N-S-W-E index, the same number the {@code EnumDirection} on the ProtocolLib side has as
 *       its ordinal, so {@link #digFaceIndex()} needs no conversion on either engine;</li>
 *   <li>{@code BlockInteractionReader.enumDirection()} - the raw face id, which is already the
 *       vanilla D-U-N-S-W-E ordering that {@link Direction} uses, so it lines up with the ordinals
 *       the ProtocolLib reader produces. A packet without a face reports 255, the same "empty
 *       interaction" marker the ProtocolLib reader uses;</li>
 *   <li>{@code ChunkCoordinateReader.xCoordinates()} / {@code zCoordinates()} - the column
 *       coordinates of the chunk data packet, or the coordinate arrays of the legacy bulk packet.</li>
 * </ul>
 * <b>Cost note for the chunk kind:</b> ProtocolLib reads the two chunk coordinates straight off the
 * packet, while PacketEvents has no partial decode - constructing {@link WrapperPlayServerChunkData}
 * decodes the whole column (sections, biomes, light) even though only the coordinates are used.
 * PacketEvents caches that wrapper on the event, so a second listener decoding the same packet is
 * free, but the first decode is not. Weigh that before moving the chunk subscription over.
 * <p>
 * <b>Not covered:</b> the outbound block action packet, which the ProtocolLib
 * {@code BlockActionReader} also serves. Its consumer switches on the block's {@link
 * org.bukkit.Material}, and PacketEvents' {@code WrapperPlayServerBlockAction.getBlockType()}
 * resolves the packet's block <i>type</i> id through {@code WrappedBlockState.getByGlobalId}, which
 * is the block <i>state</i> id lookup - a different number space, so it yields the wrong block. The
 * material therefore cannot be reproduced faithfully here and that packet stays on ProtocolLib.
 */
public final class PacketEventsBlockPositionView implements BlockPositionView {

  private final ProtocolPacketEvent event;
  private final Kind kind;
  private final BlockPosition blockPosition;
  private final DigAction digAction;
  private final int enumDirection;
  private final int[] chunkXCoordinates;
  private final int[] chunkZCoordinates;
  /**
   * Filled in by the placement branch of the factory instead of being threaded through the
   * constructor, because it is the only kind that can ever carry an item and only does so on the
   * pre-1.9 wire format.
   */
  private ItemStack placementItem;
  /**
   * Filled in by the digging branch of the factory for the same reason as {@link #placementItem}:
   * it is the only kind that carries a digging face. 255 is the "no face" marker every other kind
   * reports.
   */
  private int digFaceIndex = 255;

  private PacketEventsBlockPositionView(
    ProtocolPacketEvent event,
    Kind kind,
    @Nullable BlockPosition blockPosition,
    @Nullable DigAction digAction,
    int enumDirection,
    @Nullable int[] chunkXCoordinates,
    @Nullable int[] chunkZCoordinates
  ) {
    this.event = event;
    this.kind = kind;
    this.blockPosition = blockPosition;
    this.digAction = digAction;
    this.enumDirection = enumDirection;
    this.chunkXCoordinates = chunkXCoordinates;
    this.chunkZCoordinates = chunkZCoordinates;
  }

  /** @return a view over the event, or null when the packet is not an inbound member of this family. */
  public static PacketEventsBlockPositionView of(PacketReceiveEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.PLAYER_DIGGING) {
      WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
      PacketEventsBlockPositionView view = new PacketEventsBlockPositionView(
        event,
        Kind.BLOCK_DIG,
        positionOf(wrapper.getBlockPosition()),
        digActionOf(wrapper.getAction()),
        // The dig packet carries a face, but enumDirection() is the placement's clicked face and
        // reports the "empty interaction" marker for every other kind, here included; the digging
        // face is handed out by digFaceIndex() instead.
        255,
        null,
        null
      );
      view.digFaceIndex = digFaceIndexOf(wrapper.getBlockFace());
      return view;
    }
    if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
      WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
      PacketEventsBlockPositionView view = new PacketEventsBlockPositionView(
        event,
        Kind.BLOCK_PLACE,
        positionOf(wrapper.getBlockPosition()),
        null,
        faceIndexOf(wrapper.getFaceId()),
        null,
        null
      );
      view.placementItem = placementItemOf(wrapper);
      return view;
    }
    if (type == PacketType.Play.Client.USE_ITEM) {
      // The bare use item packet carries no block and no face, so nothing has to be decoded.
      return new PacketEventsBlockPositionView(event, Kind.USE_ITEM, null, null, 255, null, null);
    }
    return null;
  }

  /** @return a view over the event, or null when the packet is not an outbound member of this family. */
  public static PacketEventsBlockPositionView of(PacketSendEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Server.CHUNK_DATA) {
      Column column = new WrapperPlayServerChunkData(event).getColumn();
      if (column == null) {
        return null;
      }
      return new PacketEventsBlockPositionView(
        event,
        Kind.CHUNK_DATA,
        null,
        null,
        255,
        new int[]{column.getX()},
        new int[]{column.getZ()}
      );
    }
    if (type == PacketType.Play.Server.MAP_CHUNK_BULK) {
      WrapperPlayServerChunkDataBulk wrapper = new WrapperPlayServerChunkDataBulk(event);
      int[] xCoordinates = wrapper.getX();
      int[] zCoordinates = wrapper.getZ();
      if (xCoordinates == null || zCoordinates == null) {
        return null;
      }
      return new PacketEventsBlockPositionView(
        event,
        Kind.CHUNK_DATA,
        null,
        null,
        255,
        // Cloned like the ProtocolLib bulk reader does, so the consumer cannot write into the packet.
        xCoordinates.clone(),
        zCoordinates.clone()
      );
    }
    return null;
  }

  private static @Nullable BlockPosition positionOf(@Nullable Vector3i position) {
    return position == null ? null : new BlockPosition(position.getX(), position.getY(), position.getZ());
  }

  private static int faceIndexOf(int faceId) {
    return faceId < 0 || faceId >= Direction.values().length ? 255 : faceId;
  }

  /**
   * PacketEvents resolves the digging packet's face byte through
   * {@code BlockFace#getBlockFaceByValue}, which indexes the six cartesian faces modulo six - the
   * same wrap the server applies when it turns that byte into the {@code EnumDirection} the
   * ProtocolLib view reads, so both engines land on the same face for the same byte. For those six
   * the face value is the ordinal, which is the vanilla D-U-N-S-W-E index; anything else - only
   * {@code OTHER}, which this packet cannot decode to - falls back to the 255 marker.
   */
  private static int digFaceIndexOf(@Nullable BlockFace face) {
    return face == null ? 255 : faceIndexOf(face.getFaceValue());
  }

  /**
   * PacketEvents only decodes an item stack out of the placement packet on the pre-1.9 wire format,
   * where the client still sent the held item along; on every later version the optional is empty,
   * which is the same "no such field" answer ProtocolLib's item modifier gives there. An empty
   * stack becomes null because that is the value ProtocolLib produces for the empty-hand encoding.
   */
  private static @Nullable ItemStack placementItemOf(WrapperPlayClientPlayerBlockPlacement wrapper) {
    com.github.retrooper.packetevents.protocol.item.ItemStack item = wrapper.getItemStack().orElse(null);
    if (item == null || item.isEmpty()) {
      return null;
    }
    return SpigotConversionUtil.toBukkitItemStack(item);
  }

  /**
   * Maps by wire id rather than by name: PacketEvents calls the first three actions START_DIGGING,
   * CANCELLED_DIGGING and FINISHED_DIGGING and the seventh SWAP_ITEM_WITH_OFFHAND, but the ids are
   * the vanilla ones the ProtocolLib enum is ordered by.
   */
  private static @Nullable DigAction digActionOf(@Nullable DiggingAction action) {
    if (action == null) {
      return null;
    }
    switch (action.getId()) {
      case 0:
        return DigAction.START_DESTROY_BLOCK;
      case 1:
        return DigAction.ABORT_DESTROY_BLOCK;
      case 2:
        return DigAction.STOP_DESTROY_BLOCK;
      case 3:
        return DigAction.DROP_ALL_ITEMS;
      case 4:
        return DigAction.DROP_ITEM;
      case 5:
        return DigAction.RELEASE_USE_ITEM;
      case 6:
        return DigAction.SWAP_HELD_ITEMS;
      default:
        return null;
    }
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public Kind kind() {
    return kind;
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
  public @Nullable DigAction digAction() {
    return digAction;
  }

  @Override
  public int enumDirection() {
    return enumDirection;
  }

  @Override
  public int digFaceIndex() {
    return digFaceIndex;
  }

  @Override
  public @Nullable ItemStack placementItem() {
    return placementItem;
  }

  @Override
  public int[] chunkXCoordinates() {
    return requireChunkCoordinates(chunkXCoordinates);
  }

  @Override
  public int[] chunkZCoordinates() {
    return requireChunkCoordinates(chunkZCoordinates);
  }

  private int[] requireChunkCoordinates(@Nullable int[] coordinates) {
    if (kind != Kind.CHUNK_DATA || coordinates == null) {
      throw new IllegalStateException("Chunk coordinates are only carried by the chunk data packets");
    }
    return coordinates;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction.
  }
}
