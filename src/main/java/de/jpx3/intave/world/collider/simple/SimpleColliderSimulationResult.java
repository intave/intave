package de.jpx3.intave.world.collider.simple;

public class SimpleColliderSimulationResult {
  private final double motionX, motionY, motionZ;
  private final boolean onGround, collidedVertically;

  public SimpleColliderSimulationResult(double motionX, double motionY, double motionZ, boolean onGround, boolean collidedVertically) {
    this.motionX = motionX;
    this.motionY = motionY;
    this.motionZ = motionZ;
    this.onGround = onGround;
    this.collidedVertically = collidedVertically;
  }

  public double motionX() {
    return motionX;
  }

  public double motionY() {
    return motionY;
  }

  public double motionZ() {
    return motionZ;
  }

  public boolean onGround() {
    return onGround;
  }

  public boolean collidedVertically() {
    return collidedVertically;
  }
}
