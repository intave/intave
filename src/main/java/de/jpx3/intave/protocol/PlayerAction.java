package de.jpx3.intave.protocol;

public enum PlayerAction {
  START_SNEAKING,
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
    return this == START_SNEAKING;
  }

  public boolean isStopSneak() {
    return this == STOP_SNEAKING;
  }

  public boolean isSneakRelated() {
    return isStartSneak() || isStopSneak();
  }
}
