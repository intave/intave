package de.jpx3.intave.reflect.converters;

import de.jpx3.intave.annotate.KeepEnumInternalNames;

@KeepEnumInternalNames
public enum PlayerAction {
  PRESS_SHIFT_KEY("PRESS_SHIFT_KEY"),
  RELEASE_SHIFT_KEY("RELEASE_SHIFT_KEY"),
  START_SNEAKING("START_SNEAKING"),
  STOP_SNEAKING("STOP_SNEAKING"),
  STOP_SLEEPING("STOP_SLEEPING"),
  START_SPRINTING("START_SPRINTING"),
  STOP_SPRINTING("STOP_SPRINTING"),
  START_RIDING_JUMP("START_RIDING_JUMP"),
  STOP_RIDING_JUMP("STOP_RIDING_JUMP"),
  OPEN_INVENTORY("OPEN_INVENTORY"),
  START_FALL_FLYING("START_FALL_FLYING"),
  RIDING_JUMP("RIDING_JUMP");

  private final String action;

  PlayerAction(String action) {
    this.action = action;
  }

  public String action() {
    return action;
  }
}
