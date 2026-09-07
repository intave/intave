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
 * Engine neutral view of a player abilities packet, in either direction.
 * <p>
 * Both directions share one interface because they are one family for the ability tracker: the
 * inbound packet carries the flight state the client asks for, the outbound packet carries the
 * flight state the server grants, and the tracker reconciles the two. Which direction a view
 * describes is answered by {@link #inbound()}; the accessors of the other direction throw
 * {@link IllegalStateException}, because there is no meaningful value to hand back.
 * <p>
 * Only the fields the ability tracker actually reads are exposed:
 * <ul>
 *   <li>inbound: the requested flying flag;</li>
 *   <li>outbound: fly speed, walk speed and the flight allowed flag.</li>
 * </ul>
 * The remaining ability flags (invulnerable, creative) are read by nothing and are left out on
 * purpose, so neither backend has to answer for them.
 */
public interface AbilityView {

  Player player();

  /** @return true for the client -> server abilities packet, false for the server -> client one. */
  boolean inbound();

  /** @return true for the server -> client abilities packet. */
  default boolean outbound() {
    return !inbound();
  }

  /**
   * @return the flying flag the client asked for.
   * @throws IllegalStateException when this view describes an outbound packet.
   */
  boolean requestedFlying();

  /**
   * @return the fly speed the server grants.
   * @throws IllegalStateException when this view describes an inbound packet.
   */
  float flyingSpeed();

  /**
   * @return the walk speed the server grants. This is the second float of the packet, which the
   * client applies as its field of view modifier.
   * @throws IllegalStateException when this view describes an inbound packet.
   */
  float walkingSpeed();

  /**
   * @return whether the server allows the player to fly.
   * @throws IllegalStateException when this view describes an inbound packet.
   */
  boolean flyingAllowed();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
