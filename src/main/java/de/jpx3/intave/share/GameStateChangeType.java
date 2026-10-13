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

package de.jpx3.intave.share;

import de.jpx3.intave.annotate.Nullable;

public enum GameStateChangeType {
  INVALID_BED(0),
  END_RAIN(1),
  BEGIN_RAIN(2),
  CHANGE_GAME_MODE(3),
  ENTER_CREDITS(4),
  DEMO_MESSAGE(5),
  ARROW_HITTING_PLAYER(6),
  RAIN_LEVEL_CHANGE(7),
  THUNDER_LEVEL_CHANGE(8),
  PLAY_MOB_APPEARANCE(9),
  PLAY_MOB2_APPEARANCE(10),
  ENABLE_RESPAWN_SCREEN(11);

  private final int id;

  GameStateChangeType(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  public static @Nullable GameStateChangeType byId(int id) {
    return id >= 0 && id < values().length ? values()[id] : null;
  }
}
