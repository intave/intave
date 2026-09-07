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

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Engine neutral view of an outbound player info packet.
 * <p>
 * This family covers both tab list packets the vanish filter touches: the player info packet
 * (a single action plus a list of profiles on legacy protocols, a set of actions plus a list of
 * entries since 1.19.3) and the separate player info remove packet 1.19.3 introduced, which is
 * nothing but a list of profile ids.
 * <p>
 * Neither backend's entry type is exposed. The only thing the vanish filter reads off an entry is
 * the profile id, and the only things it does to the entry list are dropping entries, shuffling
 * them and asking whether anything is left - so that is exactly what this interface offers.
 * Everything else about an entry (display name, game mode, latency, chat session, texture
 * properties) is left untouched by the engine specific implementation, which keeps the original
 * entry objects and writes the surviving ones back verbatim.
 */
public interface PlayerInfoView {

  /**
   * The entry actions both backends agree on. Actions Intave does not branch on - display name,
   * listed flag, chat initialisation, list order, hat - collapse into {@link #OTHER} rather than
   * being enumerated, because the protocol keeps adding them and no consumer looks at them.
   */
  enum Action {
    ADD_PLAYER,
    UPDATE_GAME_MODE,
    UPDATE_LATENCY,
    REMOVE_PLAYER,
    OTHER
  }

  Player player();

  /**
   * @return true when this is the standalone player info remove packet rather than the player
   * info packet. The remove packet carries no action field on the wire, so {@link #actions()} is
   * empty for it and consumers have to branch on this instead of on the action set.
   */
  boolean isRemoval();

  /**
   * @return the actions this packet applies to its entries, in packet order. Empty for a removal
   * packet. Legacy protocols carry exactly one action, 1.19.3 and above carry a set.
   */
  Set<Action> actions();

  /**
   * @return the profile ids of the current entries, in packet order. The returned list is a
   * snapshot: mutate the packet through {@link #removeEntriesIf(Predicate)} and
   * {@link #shuffleEntries()}, not through this list.
   */
  List<UUID> entryIds();

  /** Drops every entry whose profile id matches the predicate. */
  void removeEntriesIf(Predicate<UUID> filter);

  /** Randomises entry order, so the tab list cannot be used to infer the original ordering. */
  void shuffleEntries();

  boolean entriesEmpty();

  void setCancelled(boolean cancelled);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
