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

public enum PlayerAction {
  @Deprecated
  PRESS_SHIFT_KEY,
  @Deprecated
  RELEASE_SHIFT_KEY,
  @Deprecated
  START_SNEAKING,
  @Deprecated
  STOP_SNEAKING,
  STOP_SLEEPING,
  START_SPRINTING,
  STOP_SPRINTING,
  START_RIDING_JUMP,
  STOP_RIDING_JUMP,
  OPEN_INVENTORY,
  START_FALL_FLYING,
  RIDING_JUMP;

  public boolean isStartSneak() {
    switch (this) {
      case START_SNEAKING:
      case PRESS_SHIFT_KEY:
        return true;
    }
    return false;
  }

  public boolean isStopSneak() {
    switch (this) {
      case STOP_SNEAKING:
      case RELEASE_SHIFT_KEY:
        return true;
    }
    return false;
  }

  public boolean isSneakRelated() {
    switch (this) {
      case STOP_SNEAKING:
      case START_SNEAKING:
      case PRESS_SHIFT_KEY:
      case RELEASE_SHIFT_KEY:
        return true;
      default:
        return false;
    }
  }
}
