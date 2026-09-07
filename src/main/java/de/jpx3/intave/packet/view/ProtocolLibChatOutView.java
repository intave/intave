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
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Player;

/**
 * {@link ChatOutView} backed by ProtocolLib.
 * <p>
 * The two ways the action bar slot is encoded are checked exactly as the display did before the
 * view existed: the legacy position byte first (read safely, it is absent on newer protocols) and
 * the chat type enum second.
 */
public final class ProtocolLibChatOutView implements ChatOutView {

  private final PacketEvent event;

  public ProtocolLibChatOutView(PacketEvent event) {
    this.event = event;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public boolean isActionBar() {
    PacketContainer packet = event.getPacket();
    Byte position = packet.getBytes().readSafely(0);
    EnumWrappers.ChatType type = packet.getChatTypes().read(0);
    return (position != null && position.intValue() == 2)
      || type == EnumWrappers.ChatType.GAME_INFO;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }
}
