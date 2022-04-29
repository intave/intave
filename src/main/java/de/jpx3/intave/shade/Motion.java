package de.jpx3.intave.shade;

import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.util.Vector;

import static de.jpx3.intave.math.MathHelper.hypot3d;

public final class Motion {
  public double motionX;
  public double motionY;
  public double motionZ;

  public Motion() {
    this(0.0, 0.0, 0.0);
  }

  public Motion(double motionX, double motionY, double motionZ) {
    this.motionX = motionX;
    this.motionY = motionY;
    this.motionZ = motionZ;
  }

  public static Motion fromVector(Vector velocity) {
    return new Motion(velocity.getX(), velocity.getY(), velocity.getZ());
  }

  public void reset(double x, double y, double z) {
    this.motionX = x;
    this.motionY = y;
    this.motionZ = z;
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

  public Motion copy() {
    return copyFrom(this);
  }

  public double distance(Motion other) {
    return hypot3d(motionX - other.motionX, motionY - other.motionY, motionZ - other.motionZ);
  }

  public double horizontalDistance(Motion other) {
    return Hypot.fast(motionX - other.motionX, motionZ - other.motionZ);
  }

  public Motion add(double x, double y, double z) {
    motionX += x;
    motionY += y;
    motionZ += z;
    return this;
  }

  public void resetTo(Motion motion) {
    reset(motion.motionX, motion.motionY, motion.motionZ);
  }

  public void resetTo(MovementMetadata data) {
    reset(data.physicsMotionX, data.physicsMotionY, data.physicsMotionZ);
  }

  public double length() {
    return hypot3d(motionX, motionY, motionZ);
  }

  public Vector toBukkitVector() {
    return new Vector(this.motionX, this.motionY, this.motionZ);
  }

  @Override
  public String toString() {
    return "Motion{" +
      "x=" + motionX +
      ", y=" + motionY +
      ", z=" + motionZ +
      '}';
  }

  public static Motion copyFrom(Motion context) {
    return new Motion(context.motionX, context.motionY, context.motionZ);
  }

  public static Motion zero() {
    return new Motion();
  }
}
