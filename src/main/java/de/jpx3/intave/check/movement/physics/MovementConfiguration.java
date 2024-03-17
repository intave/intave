package de.jpx3.intave.check.movement.physics;

final class MovementConfiguration {
  private static final int BOOLEANS = 4;
  private static final double BOOLEAN_POW_2 = Math.pow(BOOLEANS, 2);
  private static final MovementConfiguration[] UNIVERSE = new MovementConfiguration[(int) (Math.pow(4, 2) * Math.pow(2, BOOLEANS) * 4) + 1];

  private final int index;
  private final int forward, strafe;
  private final int attackReduceTicks;
  private final boolean attackReduce, sprinting, jumped, handActive;

  private MovementConfiguration(
    int index,
    int forward, int strafe,
    int attackReduceTicks, boolean sprinting,
    boolean jumped, boolean handActive
  ) {
    this.index = index;
    this.forward = forward;
    this.strafe = strafe;
    this.attackReduce = attackReduceTicks >= 1;
    this.attackReduceTicks = attackReduceTicks;
    this.sprinting = sprinting;
    this.jumped = jumped;
    this.handActive = handActive;
  }

  public int forward() {
    return forward;
  }

  public int strafe() {
    return strafe;
  }

  public boolean isReducing() {
    return attackReduceTicks > 0;
  }

  public int reduceTicks() {
    return attackReduceTicks;
  }

  public MovementConfiguration withReducing(int ticks) {
    ticks &= 0b11;
    if (ticks == 0) {
      return withoutReducing();
    } else if (attackReduceTicks == ticks) {
      return this;
    }
    return keyLookup(index | ticks << BOOLEANS + 4 | 1 << 3);
  }

  public MovementConfiguration withoutReducing() {
    if (attackReduceTicks == 0) {
      return this;
    }
    return keyLookup(index & ~(0b11 << BOOLEANS + 4) & ~(1 << 3));
  }

  public boolean isSprinting() {
    return sprinting;
  }

  public MovementConfiguration withSprinting() {
    if (sprinting) {
      return this;
    }
    return keyLookup(index | 1 << 2);
  }

  public MovementConfiguration withoutSprinting() {
    if (!sprinting) {
      return this;
    }
    return keyLookup(index & ~(1 << 2));
  }

  public boolean isJumping() {
    return jumped;
  }

  public MovementConfiguration withJump() {
    if (jumped) {
      return this;
    }
    return keyLookup(index | 1 << 1);
  }

  public MovementConfiguration withoutJump() {
    if (!jumped) {
      return this;
    }
    return keyLookup(index & ~(1 << 1));
  }

  public boolean isHandActive() {
    return handActive;
  }

  public MovementConfiguration withActiveHand() {
    if (handActive) {
      return this;
    }
    return keyLookup(index | 1);
  }

  public MovementConfiguration withoutActiveHand() {
    if (!handActive) {
      return this;
    }
    return keyLookup(index & ~1);
  }

  public MovementConfiguration withoutKeypress() {
    if (forward == 0 && strafe == 0) {
      return this;
    }
    return withKeyPress(0, 0);
  }

  public MovementConfiguration withKeyPress(int forward, int strafe) {
    if (Math.abs(forward) > 1 || Math.abs(strafe) > 1) {
      throw new IllegalStateException("That key can not exist (" + forward + "/" + strafe + ")");
    }
    int forwardSlot = (forward + 1 & 0x3) << BOOLEANS + 2;
    int strafeSlot = (strafe + 1 & 0x3) << BOOLEANS;
    return keyLookup(forwardSlot | strafeSlot | emptyKeypress().index);
  }

  private MovementConfiguration emptyKeypress() {
    return keyLookup(index & ~(0b11 << BOOLEANS + 2) & ~(0b11 << BOOLEANS));
  }

  public static MovementConfiguration keyLookup(int key) {
    MovementConfiguration config = UNIVERSE[key];
    if (config == null) {
      throw new IllegalStateException("Unable to lookup " + key);
    }
    return config;
  }

  private static final MovementConfiguration EMPTY_CONFIGURATION;

  public static MovementConfiguration empty() {
    return EMPTY_CONFIGURATION;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MovementConfiguration that = (MovementConfiguration) o;
    return index == that.index;
  }

  @Override
  public String toString() {
    return "(" + forward + "/" + strafe + ") " +
      (attackReduce ? "R" : "") +
      (sprinting ? "S" : "") +
      (jumped ? "J" : "") +
      (handActive ? "H" : "");
  }

  @Override
  public int hashCode() {
    return index;
  }

  public static MovementConfiguration select(
    int forward, int strafe,
    int attackReduceTicks, boolean sprinting,
    boolean jumped, boolean handActive
  ) {
    if (Math.abs(forward) > 1 || Math.abs(strafe) > 1) {
      throw new IllegalStateException("That key can not exist " + forward + " " + strafe);
    }
    int key = (attackReduceTicks & 0b11) << BOOLEANS + 4 |
      forward + 1 << BOOLEANS + 2 | strafe + 1 << BOOLEANS |
      (attackReduceTicks > 0 ? 8 : 0) | (sprinting ? 4 : 0) |
      (jumped ? 2 : 0) | (handActive ? 1 : 0);
    try {
      return keyLookup(key);
    } catch (IllegalStateException e) {
      throw new IllegalStateException("Unable to lookup " + key + " " + forward + " " + strafe + " " + attackReduceTicks + " " + sprinting + " " + jumped + " " + handActive);
    }
  }

  static {
    for (int forward = -1; forward <= 1; forward++) {
      for (int strafe = -1; strafe <= 1; strafe++) {
        for (int k = 0; k < Math.pow(BOOLEANS, 2); k++) {
          for (int attackReduceTicks = 0; attackReduceTicks < 4; attackReduceTicks++) {
            boolean reduceMark = ((k & 0b1000) > 0);
            boolean reduceTicks = attackReduceTicks > 0;
            if (reduceMark != reduceTicks) {
              continue;
            }
            int key = (attackReduceTicks & 0b11) << BOOLEANS + 4 | (forward + 1 & 0x3) << BOOLEANS + 2 | (strafe + 1 & 0x3) << BOOLEANS | k;
            UNIVERSE[key] = new MovementConfiguration(
              key, forward, strafe,
              attackReduceTicks, (k & 4) != 0,
              (k & 2) != 0, (k & 1) != 0
            );
          }
        }
      }
    }
    EMPTY_CONFIGURATION = select(0, 0, 0, false, false, false);
  }
}