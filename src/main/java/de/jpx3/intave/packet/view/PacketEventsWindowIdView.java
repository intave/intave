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
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import org.bukkit.entity.Player;

/**
 * {@link WindowIdView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction because every PacketEvents getter re-reads the packet
 * buffer, so {@link #release()} has nothing to hand back. Three factories mirror the three
 * ProtocolLib subscription shapes: the client and the server close window packets, which differ
 * only in which wrapper reads the same field, and the block dig packet.
 */
public final class PacketEventsWindowIdView implements WindowIdView {

  private final ProtocolPacketEvent event;
  private final Integer containerId;
  private final InventoryActionEvent.Action heldItemAction;

  private PacketEventsWindowIdView(
    ProtocolPacketEvent event, Integer containerId, InventoryActionEvent.Action heldItemAction
  ) {
    this.event = event;
    this.containerId = containerId;
    this.heldItemAction = heldItemAction;
  }

  /** @return a view over the event, or null when the packet is not the client close window packet. */
  public static PacketEventsWindowIdView ofWindowClose(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CLOSE_WINDOW) {
      return null;
    }
    return new PacketEventsWindowIdView(
      event, new WrapperPlayClientCloseWindow(event).getWindowId(), null
    );
  }

  /** @return a view over the event, or null when the packet is not the server close window packet. */
  public static PacketEventsWindowIdView ofWindowClose(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.CLOSE_WINDOW) {
      return null;
    }
    return new PacketEventsWindowIdView(
      event, new WrapperPlayServerCloseWindow(event).getWindowId(), null
    );
  }

  /**
   * @return a view over the event, or null when the packet is not a block dig packet or carries a
   * dig action that is not one of the three held item actions the inventory samples describe.
   */
  public static PacketEventsWindowIdView ofHeldItemAction(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
      return null;
    }
    InventoryActionEvent.Action action = convert(new WrapperPlayClientPlayerDigging(event).getAction());
    return action == null ? null : new PacketEventsWindowIdView(event, null, action);
  }

  // PacketEvents' DROP_ITEM / DROP_ITEM_STACK carry the vanilla ids 4 and 3, so they line up with
  // ProtocolLib's DROP_ITEM and DROP_ALL_ITEMS respectively.
  private static InventoryActionEvent.Action convert(DiggingAction action) {
    if (action == DiggingAction.DROP_ITEM) {
      return InventoryActionEvent.Action.DROP_HELD_ONE;
    } else if (action == DiggingAction.DROP_ITEM_STACK) {
      return InventoryActionEvent.Action.DROP_HELD_STACK;
    } else if (action == DiggingAction.SWAP_ITEM_WITH_OFFHAND) {
      return InventoryActionEvent.Action.SWAP_HANDS;
    } else {
      return null;
    }
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public int containerId() {
    if (containerId == null) {
      throw new IllegalStateException("this view wraps a block dig packet, which carries no window id");
    }
    return containerId;
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
    // Nothing held: the wrapper was decoded into primitives at construction.
  }
}
