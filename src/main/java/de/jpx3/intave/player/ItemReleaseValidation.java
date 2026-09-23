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

package de.jpx3.intave.player;

import com.comphenix.protocol.wrappers.EnumWrappers;

/**
 * validation for item release packets.
 *
 * <p>the vanilla client always releases the active item with the same
 * hardcoded values, so any deviation proves the packet was spoofed.
 * cheats abuse this to silently drop the server-side item state while
 * keeping the item in use on their side, which removes the movement
 * slowdown the simulation expects.
 */
public final class ItemReleaseValidation {
  private ItemReleaseValidation() {
  }

  /**
   * checks whether a release_use_item packet carries a facing the vanilla
   * client would never send.
   *
   * <p>a null facing cannot be proven spoofed, so it is accepted here and
   * left to the remaining checks.
   *
   * @param face the facing sent with the release packet, may be null
   * @return true when the packet is a spoofed release
   */
  public static boolean isSpoofedRelease(EnumWrappers.Direction face) {
    return face != null && face != EnumWrappers.Direction.DOWN;
  }
}
