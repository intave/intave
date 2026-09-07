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
import com.github.retrooper.packetevents.protocol.chat.ChatType;
import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import org.bukkit.entity.Player;

/**
 * {@link ChatOutView} backed by PacketEvents.
 * <p>
 * PacketEvents does not hand out the raw position byte the ProtocolLib path reads. Its legacy chat
 * message processor already translates that byte through {@code ChatTypes.getById}, so position 2
 * arrives as {@link ChatTypes#GAME_INFO} and the single type check covers both encodings the
 * ProtocolLib view has to test separately. From 1.19 on the action bar travels as the system chat
 * packet instead, where the same flag is the overlay bit.
 * <p>
 * The answer is computed once at construction: both wrappers decode the packet buffer on every
 * getter call and this runs for every chat packet the server sends.
 */
public final class PacketEventsChatOutView implements ChatOutView {

  private final PacketSendEvent event;
  private final boolean actionBar;

  private PacketEventsChatOutView(PacketSendEvent event, boolean actionBar) {
    this.event = event;
    this.actionBar = actionBar;
  }

  /** @return a view over the event, or null when the packet is not an outgoing chat packet. */
  public static PacketEventsChatOutView of(PacketSendEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Server.CHAT_MESSAGE) {
      ChatMessage message = new WrapperPlayServerChatMessage(event).getMessage();
      ChatType chatType = message == null ? null : message.getType();
      return new PacketEventsChatOutView(event, chatType == ChatTypes.GAME_INFO);
    }
    if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
      return new PacketEventsChatOutView(
        event, new WrapperPlayServerSystemChatMessage(event).isOverlay()
      );
    }
    return null;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean isActionBar() {
    return actionBar;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }
}
