package de.jpx3.intave.check.movement.physics;

public final class MovementConfiguration {
  private final static int BOOLEANS = 4;
  private final static MovementConfiguration[] UNIVERSE = new MovementConfiguration[(int) (Math.pow(4, 2) * Math.pow(2, BOOLEANS)) + 1];

  private final int index;
  private final int forward, strafe;
  private final boolean attackReduce, sprinting, jumped, handActive;

  private MovementConfiguration(
    int index,
    int forward, int strafe,
    boolean attackReduce, boolean sprinting,
    boolean jumped, boolean handActive
  ) {
    this.index = index;
    this.forward = forward;
    this.strafe = strafe;
    this.attackReduce = attackReduce;
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
    return attackReduce;
  }

  public MovementConfiguration withReducing() {
    return keyLookup(index | 1 << 3);
  }

  public MovementConfiguration withoutReducing() {
    return keyLookup(index & ~(1 << 3));
  }

  public boolean isSprinting() {
    return sprinting;
  }

  public MovementConfiguration withSprinting() {
    return keyLookup(index | 1 << 2);
  }

  public MovementConfiguration withoutSprinting() {
    return keyLookup(index & ~(1 << 2));
  }

  public boolean isJumping() {
    return jumped;
  }

  public MovementConfiguration withJump() {
    return keyLookup(index | 1 << 1);
  }

  public MovementConfiguration withoutJump() {
    return keyLookup(index & ~(1 << 1));
  }

  public boolean isHandActive() {
    return handActive;
  }

  public MovementConfiguration withActiveHand() {
    return keyLookup(index | 1);
  }

  public MovementConfiguration withoutActiveHand() {
    return keyLookup(index & ~1);
  }

  public MovementConfiguration withoutKeypress() {
    return withKeyPress(0, 0);
  }

  public MovementConfiguration withKeyPress(int forward, int strafe) {
    if (Math.abs(forward) > 1 || Math.abs(strafe) > 1) {
      throw new IllegalStateException("That key can not exist (" + forward + "/" + strafe+")");
    }
    int forwardSlot = (forward + 1 & 0x3) << BOOLEANS + 2;
    int strafeSlot = (strafe + 1 & 0x3) << BOOLEANS;
    return keyLookup(forwardSlot | strafeSlot | emptyKeypress().index);
  }

  private MovementConfiguration emptyKeypress() {
    return keyLookup(index & (int) (Math.pow(BOOLEANS, 2) - 1));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MovementConfiguration that = (MovementConfiguration) o;
    return index == that.index;
  }

  @Override
  public int hashCode() {
    return index;
  }

  public static MovementConfiguration keyLookup(int key) {
    MovementConfiguration config = UNIVERSE[key];
    if (config == null) {
      throw new IllegalStateException("Unable to lookup " + key);
    }
    return config;
  }

  @Override
  public String toString() {
    return "MovementConfiguration{" +
      "index=" + index +
      ", forward=" + forward +
      ", strafe=" + strafe +
      ", attackReduce=" + attackReduce +
      ", sprinting=" + sprinting +
      ", jumped=" + jumped +
      ", handActive=" + handActive +
      '}';
  }

  public static MovementConfiguration empty() {
    return select(0, 0, false, false, false, false);
  }

  public static MovementConfiguration select(
    int forward, int strafe,
    boolean attackReduce, boolean sprinting,
    boolean jumped, boolean handActive
  ) {
    if (Math.abs(forward) > 1 || Math.abs(strafe) > 1) {
      throw new IllegalStateException("That key can not exist " + forward + " " + strafe);
    }
    int key =
      forward + 1 << BOOLEANS + 2 | strafe + 1 << BOOLEANS |
      (attackReduce ? 8 : 0) | (sprinting ? 4 : 0) |
      (jumped ? 2 : 0) | (handActive ? 1 : 0);
    return keyLookup(key);
  }

  static {
    for (int forward = -1; forward <= 1; forward++) {
      for (int strafe = -1; strafe <= 1; strafe++) {
        for (int k = 0; k < Math.pow(BOOLEANS, 2); k++) {
          int key = (forward + 1 & 0x3) << BOOLEANS + 2 | (strafe + 1 & 0x3) << BOOLEANS | k;
          UNIVERSE[key] = new MovementConfiguration(
            key, forward, strafe,
            (k & 8) != 0, (k & 4) != 0,
            (k & 2) != 0, (k & 1) != 0
          );
        }
      }
    }
  }
}