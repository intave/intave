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

import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

/**
 * {@link TabCompleteView} backed by ProtocolLib.
 * <p>
 * The legacy string array is read and written straight through the structure modifier, exactly the
 * way the filters did before this seam existed. On 1.13 and above the packet has no string array
 * field, {@code readSafely} hands back null and the filters skip - which is the behaviour this
 * family's scope note pins down.
 */
public final class ProtocolLibTabCompleteView implements TabCompleteView {

  private static final int MATCHES_FIELD = 0;

  private final PacketEvent event;

  public ProtocolLibTabCompleteView(PacketEvent event) {
    this.event = event;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public String[] matches() {
    return event.getPacket().getStringArrays().readSafely(MATCHES_FIELD);
  }

  @Override
  public void setMatches(String[] matches) {
    event.getPacket().getStringArrays().writeSafely(MATCHES_FIELD, matches);
  }

  @Override
  public void release() {
    // Writes go straight into the live packet container: nothing to flush, no pooled reader held.
  }
}
