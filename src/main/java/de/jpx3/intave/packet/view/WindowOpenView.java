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

/**
 * Engine neutral view of an outbound "server opened a container" packet.
 * <p>
 * Two packet shapes share this family, exactly the two the sample dispatcher subscribes to: the
 * open window packet and the separate open horse window packet 1.14 introduced. Only two fields are
 * ever read off either - the container id, which drives the assumed window state machine, and the
 * menu type string that travels into the inventory samples - so those are the only two operations
 * this interface offers, mirroring {@link WindowItemView} and {@link WindowIdView}.
 */
public interface WindowOpenView {

  /**
   * The value {@code WindowOpenReader} yields when the menu type cannot be named. It is part of the
   * ProtocolLib path's own vocabulary, not a marker this family invented, so a consumer that has to
   * cope with it on one backend already copes with it on the other.
   */
  String UNKNOWN_MENU_TYPE = "unknown";

  Player player();

  /** @return the container id the packet assigns to the window it opens. */
  int containerId();

  /**
   * @return the namespaced menu type key, for example {@code minecraft:generic_9x3}, or
   * {@link #UNKNOWN_MENU_TYPE} when the backend cannot name the type. Never null.
   */
  String menuType();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
