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

public enum ScoreboardObjectiveAction {
  CREATE,
  REMOVE,
  UPDATE;

  public int id() {
    return ordinal();
  }

  public static ScoreboardObjectiveAction byId(int id) {
    if (id < 0 || id >= values().length) {
      throw new IllegalArgumentException("Unknown scoreboard objective action " + id);
    }
    return values()[id];
  }
}
