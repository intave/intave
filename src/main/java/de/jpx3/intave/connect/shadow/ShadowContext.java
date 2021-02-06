package de.jpx3.intave.connect.shadow;

public final class ShadowContext {
  private final long time;
  private final double moveForward;
  private final double moveStrafe;
  private final boolean jump;
  private final boolean sneak;
  private final double x;
  private final double y;
  private final double z;
  private final float yaw;
  private final float pitch;
  private final boolean sprinting;
  private final int packetCounter;

  public ShadowContext(
    long time,
    double moveForward, double moveStrafe,
    boolean jump, boolean sneak,
    double x, double y, double z,
    float yaw, float pitch,
    boolean sprinting,
    int packetCounter
  ) {
    this.time = time;
    this.moveForward = moveForward;
    this.moveStrafe = moveStrafe;
    this.jump = jump;
    this.sneak = sneak;
    this.x = x;
    this.y = y;
    this.z = z;
    this.yaw = yaw;
    this.pitch = pitch;
    this.sprinting = sprinting;
    this.packetCounter = packetCounter;
  }

  public long time() {
    return time;
  }

  public double moveForward() {
    return moveForward;
  }

  public double moveStrafe() {
    return moveStrafe;
  }

  public boolean isJump() {
    return jump;
  }

  public boolean isSneak() {
    return sneak;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public double z() {
    return z;
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public boolean isSprinting() {
    return sprinting;
  }

  public int packetCounter() {
    return packetCounter;
  }
}
