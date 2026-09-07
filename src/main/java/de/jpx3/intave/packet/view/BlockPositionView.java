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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Engine neutral view of the packets the block tracker reads coordinates off.
 * <p>
 * Mirrors {@link MovementView} and {@link BlockInteractionView}. Two unrelated readers feed this
 * family, so the view carries a {@link Kind} discriminator and every accessor documents which kinds
 * it is defined for:
 * <ul>
 *   <li>{@code BlockPositionReader} (and its {@code BlockDigReader} subclass) behind the inbound
 *       kinds {@link Kind#BLOCK_DIG}, {@link Kind#BLOCK_PLACE} and {@link Kind#USE_ITEM}: the
 *       interaction target check reads the clicked block, the dig action and the clicked face;</li>
 *   <li>{@code ChunkCoordinateReader} behind {@link Kind#CHUNK_DATA}: the chunk load tracker reads
 *       nothing but the chunk coordinate pairs the packet announces.</li>
 * </ul>
 * The block position is handed out as the engine neutral {@link BlockPosition}; consumers that
 * still need the ProtocolLib wrapper can ask {@link ProtocolLibBlockPositionView} for it directly.
 * <p>
 * The block action packet is deliberately <i>not</i> part of this family - see
 * {@link PacketEventsBlockPositionView} for why it cannot be served from PacketEvents faithfully.
 */
public interface BlockPositionView {

  /** Which packet this view was built over. Every accessor below documents the kinds it serves. */
  enum Kind {
    /** Inbound player digging / block break packet. */
    BLOCK_DIG,
    /** Inbound block placement packet (the "use item on block" packet on modern versions). */
    BLOCK_PLACE,
    /** Inbound bare use item packet, which carries no block and no face. */
    USE_ITEM,
    /** Outbound chunk data packet, single column or the legacy bulk form. */
    CHUNK_DATA
  }

  /**
   * Engine neutral dig action, in the vanilla wire order. The names follow the ProtocolLib
   * spelling because that is the spelling the checks are written in; PacketEvents calls the first
   * three START_DIGGING, CANCELLED_DIGGING and FINISHED_DIGGING and the last one
   * SWAP_ITEM_WITH_OFFHAND, and those are mapped onto these by their wire id.
   */
  enum DigAction {
    START_DESTROY_BLOCK,
    ABORT_DESTROY_BLOCK,
    STOP_DESTROY_BLOCK,
    DROP_ALL_ITEMS,
    DROP_ITEM,
    RELEASE_USE_ITEM,
    SWAP_HELD_ITEMS,
    /** ProtocolLib only; no wire counterpart on the versions PacketEvents maps. */
    STAB
  }

  Player player();

  /** @return which of the packets in this family the view was built over, never null. */
  Kind kind();

  boolean cancelled();

  void setCancelled(boolean cancelled);

  /**
   * @return the block the packet points at, or null when it carries none - the bare use item
   * packet, a placement without a hit result, and every {@link Kind#CHUNK_DATA} packet.
   */
  @Nullable
  BlockPosition blockPosition();

  /**
   * @return the dig action for {@link Kind#BLOCK_DIG}, or null for every other kind and for a dig
   * packet whose action does not map onto a known one.
   */
  @Nullable
  DigAction digAction();

  /**
   * @return the clicked block face as its vanilla index for {@link Kind#BLOCK_PLACE}, or 255 when
   * the packet carries no face. 255 is what the checks treat as "empty interaction", so it is kept
   * rather than translated, and it is also what the kinds without a face report.
   */
  int enumDirection();

  /**
   * @return the block face the digging packet points at, as its vanilla D-U-N-S-W-E index (0-5),
   * or 255 when no face could be read. Only defined for {@link Kind#BLOCK_DIG}; every other kind
   * reports 255.
   * <p>
   * Deliberately a second accessor rather than a widening of {@link #enumDirection()}: that one
   * answers 255 for a digging packet on both engines today, and its consumers read it as the
   * placement's clicked face, so folding the digging face into it would change what they see.
   * <p>
   * The index is what both engines already carry, so nothing is converted in between. The server
   * decodes the packet's face byte into the {@code EnumDirection} ProtocolLib hands out, whose
   * ordinal is this index; PacketEvents decodes the same byte into a {@code BlockFace} whose face
   * value is the same index. Both resolve an out of range byte with the same modulo six, so the two
   * engines cannot disagree, not even on a malformed one.
   */
  int digFaceIndex();

  /**
   * @return the item stack the placement packet carries in its own payload, or null when it
   * carries none.
   * <p>
   * Only the pre-1.9 placement packet has this field on the wire; from 1.9 on the client no longer
   * sends the held item with the placement, so both engines report null there, exactly like the
   * ProtocolLib item modifier does on an empty field. An empty stack is reported as null as well,
   * because that is what ProtocolLib hands out for the "nothing in hand" encoding. Every kind other
   * than {@link Kind#BLOCK_PLACE} reports null.
   */
  @Nullable
  ItemStack placementItem();

  /**
   * @return the x coordinate of every chunk the packet announces, in packet order. Only defined for
   * {@link Kind#CHUNK_DATA}; other kinds throw.
   */
  int[] chunkXCoordinates();

  /**
   * @return the z coordinate of every chunk the packet announces, index aligned with
   * {@link #chunkXCoordinates()}. Only defined for {@link Kind#CHUNK_DATA}; other kinds throw.
   */
  int[] chunkZCoordinates();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
