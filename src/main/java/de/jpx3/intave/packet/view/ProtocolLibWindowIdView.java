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

import ac.intave.samples.event.InventoryActionEvent;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.packet.reader.WindowIdReader;
import org.bukkit.entity.Player;

/**
 * {@link WindowIdView} backed by ProtocolLib.
 * <p>
 * Three subscription shapes feed this family today and each gets its own factory: the server close
 * window subscription receives a pooled {@link WindowIdReader} injected by the subscription linker,
 * the client close window subscription receives the raw {@link PacketEvent} and reads the integer
 * itself, and the block dig subscription receives a pooled {@link BlockDigReader}. Every read is a
 * straight delegation, so the legacy path stays byte for byte what it was - including the raw path
 * keeping {@code readSafely}, whose null the caller unboxes exactly as before.
 */
public final class ProtocolLibWindowIdView implements WindowIdView {

  private final Player player;
  private final WindowIdReader windowIdReader;
  private final BlockDigReader blockDigReader;
  private final boolean rawContainerId;
  private final Integer rawContainerIdValue;
  private final InventoryActionEvent.Action heldItemAction;

  private ProtocolLibWindowIdView(
    Player player,
    WindowIdReader windowIdReader,
    BlockDigReader blockDigReader,
    boolean rawContainerId,
    Integer rawContainerIdValue,
    InventoryActionEvent.Action heldItemAction
  ) {
    this.player = player;
    this.windowIdReader = windowIdReader;
    this.blockDigReader = blockDigReader;
    this.rawContainerId = rawContainerId;
    this.rawContainerIdValue = rawContainerIdValue;
    this.heldItemAction = heldItemAction;
  }

  /** @return a view over a close window packet whose reader the linker already injected. */
  public static ProtocolLibWindowIdView ofWindowClose(Player player, WindowIdReader reader) {
    return new ProtocolLibWindowIdView(player, reader, null, false, null, null);
  }

  /** @return a view over a close window packet delivered as a raw event. */
  public static ProtocolLibWindowIdView ofWindowClose(PacketEvent event) {
    Integer containerId = event.getPacket().getIntegers().readSafely(0);
    return new ProtocolLibWindowIdView(event.getPlayer(), null, null, true, containerId, null);
  }

  /**
   * @return a view over a block dig packet, or null when the packet carries a dig action that is
   * not one of the three held item actions the inventory samples describe.
   */
  public static ProtocolLibWindowIdView ofHeldItemAction(Player player, BlockDigReader reader) {
    InventoryActionEvent.Action action = convert(reader.action());
    return action == null ? null : new ProtocolLibWindowIdView(player, null, reader, false, null, action);
  }

  private static InventoryActionEvent.Action convert(EnumWrappers.PlayerDigType digType) {
    if (digType == EnumWrappers.PlayerDigType.DROP_ITEM) {
      return InventoryActionEvent.Action.DROP_HELD_ONE;
    } else if (digType == EnumWrappers.PlayerDigType.DROP_ALL_ITEMS) {
      return InventoryActionEvent.Action.DROP_HELD_STACK;
    } else if (digType == EnumWrappers.PlayerDigType.SWAP_HELD_ITEMS) {
      return InventoryActionEvent.Action.SWAP_HANDS;
    } else {
      return null;
    }
  }

  /** @return the wrapped window id reader, for code that still needs ProtocolLib specifics. */
  public WindowIdReader windowIdReader() {
    return windowIdReader;
  }

  /** @return the wrapped block dig reader, for code that still needs ProtocolLib specifics. */
  public BlockDigReader blockDigReader() {
    return blockDigReader;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public int containerId() {
    if (windowIdReader != null) {
      return windowIdReader.containerId();
    }
    if (rawContainerId) {
      // Unboxes, and so throws on an absent field exactly like the legacy raw event path did.
      return rawContainerIdValue;
    }
    throw new IllegalStateException("this view wraps a block dig packet, which carries no window id");
  }

  @Override
  public InventoryActionEvent.Action heldItemAction() {
    if (heldItemAction == null) {
      throw new IllegalStateException("this view wraps a close window packet, which carries no dig action");
    }
    return heldItemAction;
  }

  @Override
  public void release() {
    if (windowIdReader != null) {
      windowIdReader.release();
    }
    if (blockDigReader != null) {
      blockDigReader.release();
    }
  }
}
