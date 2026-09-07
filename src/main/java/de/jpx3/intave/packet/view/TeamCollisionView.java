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

/**
 * Engine neutral view of the collision rule carried by an outbound scoreboard team packet.
 * <p>
 * Unlike the other view families this one exposes no getters. Its single consumer never inspects
 * the packet, it only forces the rule to "never" so that entity pushing cannot displace a player
 * behind Intave's back; how the rule is located in the packet differs enough between the engines -
 * and between protocol versions on ProtocolLib alone - that reproducing the lookup in the consumer
 * would leak the whole layout into it. So the operation, rather than the fields, is what crosses
 * the seam.
 */
public interface TeamCollisionView {

  /**
   * Rewrites the team's collision rule to "never".
   * <p>
   * A packet that carries no collision rule - a team removal, or a members only update - is left
   * untouched.
   */
  void disableCollisions();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}
