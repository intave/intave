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
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTabComplete;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link TabCompleteView} backed by PacketEvents.
 * <p>
 * PacketEvents normalises both forms of the packet into one wrapper with a list of command
 * matches, so unlike ProtocolLib it would happily hand out the modern Brigadier suggestions too.
 * That would silently widen what the filters strip, so this view enforces the family's scope
 * itself: above 1.12 it reports no matches, which is what the ProtocolLib field read produces
 * there. See the scope note on {@link TabCompleteView}.
 * <p>
 * Below 1.13 a match is nothing but its text - the tooltip component the modern packet carries per
 * entry does not exist yet - so rebuilding the list from plain strings on write is lossless.
 */
public final class PacketEventsTabCompleteView implements TabCompleteView {

  /**
   * True while the server speaks the pre Brigadier tab complete packet, the only form the
   * ProtocolLib counterpart can read and therefore the only form this family acts on.
   */
  private static final boolean LEGACY_MATCH_ARRAY = !MinecraftVersions.VER1_13_0.atOrAbove();

  private final PacketSendEvent event;
  private final WrapperPlayServerTabComplete wrapper;

  private String[] matches;
  private boolean dirty;

  private PacketEventsTabCompleteView(
    PacketSendEvent event,
    WrapperPlayServerTabComplete wrapper,
    String[] matches
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.matches = matches;
  }

  /** @return a view over the event, or null when the packet is not a tab complete packet. */
  public static PacketEventsTabCompleteView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.TAB_COMPLETE) {
      return null;
    }
    if (!LEGACY_MATCH_ARRAY) {
      // Modern suggestions: out of this family's scope, so the view reports nothing to filter
      // rather than decoding a payload the ProtocolLib path never touched.
      return new PacketEventsTabCompleteView(event, null, null);
    }
    WrapperPlayServerTabComplete wrapper = new WrapperPlayServerTabComplete(event);
    return new PacketEventsTabCompleteView(event, wrapper, readMatches(wrapper));
  }

  private static String[] readMatches(WrapperPlayServerTabComplete wrapper) {
    List<WrapperPlayServerTabComplete.CommandMatch> read = wrapper.getCommandMatches();
    if (read == null) {
      return null;
    }
    String[] matches = new String[read.size()];
    for (int i = 0; i < matches.length; i++) {
      WrapperPlayServerTabComplete.CommandMatch match = read.get(i);
      matches[i] = match == null ? null : match.getText();
    }
    return matches;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public String[] matches() {
    return matches;
  }

  @Override
  public void setMatches(String[] matches) {
    if (wrapper == null) {
      // Nothing was read, so nothing can be written: staying a no-op keeps the modern packet
      // byte identical to what the server produced.
      return;
    }
    this.matches = matches;
    this.dirty = true;
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    List<WrapperPlayServerTabComplete.CommandMatch> rebuilt = new ArrayList<>(matches.length);
    for (String match : matches) {
      rebuilt.add(new WrapperPlayServerTabComplete.CommandMatch(match));
    }
    wrapper.setCommandMatches(rebuilt);
    event.markForReEncode(true);
    dirty = false;
  }
}
