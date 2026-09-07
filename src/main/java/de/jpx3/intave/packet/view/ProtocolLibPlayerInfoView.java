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
import com.comphenix.protocol.wrappers.PlayerInfoData;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerInfoReader;
import de.jpx3.intave.packet.reader.PlayerInfoRemoveReader;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * {@link PlayerInfoView} backed by ProtocolLib.
 * <p>
 * Wraps the pooled {@link PlayerInfoReader} and {@link PlayerInfoRemoveReader} so their existing
 * pooling and release semantics stay exactly as they were. Which of the two applies is decided by
 * the packet type, mirroring the reader registry.
 * <p>
 * The entry list is read once at construction and mutated in place, then handed back to
 * {@link PlayerInfoReader#writePlayerInfoData(List)} on {@link #release()} - the same order the
 * vanish filter used before this seam existed. That write is a no-op on 1.18 and above, where the
 * list read out of the packet is already the live one.
 */
public final class ProtocolLibPlayerInfoView implements PlayerInfoView {

  private final PacketEvent event;
  private final boolean removal;

  /** Non-null for the player info packet. */
  private final PlayerInfoReader infoReader;
  private final List<PlayerInfoData> entries;
  private final Set<Action> actions;

  /** Non-null for the player info remove packet. */
  private final PlayerInfoRemoveReader removeReader;
  private final List<UUID> removedIds;

  public ProtocolLibPlayerInfoView(PacketEvent event) {
    this.event = event;
    PacketContainer packet = event.getPacket();
    this.removal = packet.getType() == PacketType.Play.Server.PLAYER_INFO_REMOVE;
    if (removal) {
      this.removeReader = PacketReaders.readerOf(packet);
      this.removedIds = removeReader.playersToRemove();
      this.infoReader = null;
      this.entries = null;
      this.actions = Collections.emptySet();
    } else {
      this.infoReader = PacketReaders.readerOf(packet);
      this.entries = infoReader.playerInfoData();
      this.actions = translate(infoReader.playerInfoActions());
      this.removeReader = null;
      this.removedIds = null;
    }
  }

  private static Set<Action> translate(Set<EnumWrappers.PlayerInfoAction> read) {
    if (read == null || read.isEmpty()) {
      return Collections.emptySet();
    }
    // Insertion ordered so the action order the packet carried survives the translation.
    Set<Action> actions = new LinkedHashSet<>(read.size());
    for (EnumWrappers.PlayerInfoAction action : read) {
      actions.add(translate(action));
    }
    return actions;
  }

  private static Action translate(EnumWrappers.PlayerInfoAction action) {
    switch (action) {
      case ADD_PLAYER:
        return Action.ADD_PLAYER;
      case UPDATE_GAME_MODE:
        return Action.UPDATE_GAME_MODE;
      case UPDATE_LATENCY:
        return Action.UPDATE_LATENCY;
      case REMOVE_PLAYER:
        return Action.REMOVE_PLAYER;
      default:
        // Display name, listed flag, chat initialisation, list order, hat: nothing branches on
        // these, and newer ProtocolLib builds keep adding constants.
        return Action.OTHER;
    }
  }

  /** @return the wrapped reader, for tab list code that still needs ProtocolLib specifics. */
  public PlayerInfoReader reader() {
    return infoReader;
  }

  /** @return the wrapped removal reader, for tab list code that still needs ProtocolLib specifics. */
  public PlayerInfoRemoveReader removeReader() {
    return removeReader;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public boolean isRemoval() {
    return removal;
  }

  @Override
  public Set<Action> actions() {
    return actions;
  }

  @Override
  public List<UUID> entryIds() {
    if (removal) {
      return Collections.unmodifiableList(removedIds);
    }
    List<UUID> ids = new ArrayList<>(entries.size());
    for (PlayerInfoData entry : entries) {
      ids.add(entry.getProfile().getUUID());
    }
    return ids;
  }

  @Override
  public void removeEntriesIf(Predicate<UUID> filter) {
    if (removal) {
      removedIds.removeIf(filter);
    } else {
      entries.removeIf(entry -> filter.test(entry.getProfile().getUUID()));
    }
  }

  @Override
  public void shuffleEntries() {
    Collections.shuffle(removal ? removedIds : entries);
  }

  @Override
  public boolean entriesEmpty() {
    return removal ? removedIds.isEmpty() : entries.isEmpty();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public void release() {
    if (removal) {
      // The removal reader hands out the packet's live list, so the removals are already applied.
      removeReader.releaseSafe();
      return;
    }
    infoReader.writePlayerInfoData(entries);
    // releaseSafe rather than release so an early release here stays idempotent.
    infoReader.releaseSafe();
  }
}
