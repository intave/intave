package de.jpx3.intave.share;

import de.jpx3.intave.check.movement.physics.SimulationEnvironment;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.share.Relative;
import io.netty.buffer.ByteBuf;
import org.bukkit.util.Vector;

import java.util.Set;

import static de.jpx3.intave.math.MathHelper.hypot3d;

public final class Motion {
  public static final StreamCodec<ByteBuf, ByteBuf, Motion> STREAM_CODEC = StreamCodec.of(
    (buf, motion) -> {
      buf.writeDouble(motion.motionX);
      buf.writeDouble(motion.motionY);
      buf.writeDouble(motion.motionZ);
    },
    buf -> new Motion(buf.readDouble(), buf.readDouble(), buf.readDouble())
  );
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

  public void setTo(double x, double y, double z) {
    this.motionX = x;
    this.motionY = y;
    this.motionZ = z;
  }

  public void setTo(Vector velocity) {
    setTo(velocity.getX(), velocity.getY(), velocity.getZ());
  }

  public void setNull() {
    this.motionX = 0.0;
    this.motionY = 0.0;
    this.motionZ = 0.0;
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

  public Motion multiply(double factor) {
    motionX *= factor;
    motionY *= factor;
    motionZ *= factor;
    return this;
  }

  public Motion multiplyXZByFactor(double factor) {
    motionX *= factor;
    motionZ *= factor;
    return this;
  }

  public Motion multiplyYByFactor(double factor) {
    motionY *= factor;
    return this;
  }

  public Motion multiply(double x, double y, double z) {
    motionX *= x;
    motionY *= y;
    motionZ *= z;
    return this;
  }

  public void setMotionX(double x) {
    this.motionX = x;
  }

  public void setMotionY(double y) {
    this.motionY = y;
  }

  public void setMotionZ(double z) {
    this.motionZ = z;
  }

  public Motion normalize() {
    double length = length();
    if (length != 0.0) {
      motionX /= length;
      motionY /= length;
      motionZ /= length;
    }
    return this;
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

  public double horizontalLength() {
    return Math.sqrt(motionX * motionX + motionZ * motionZ);
  }

  public double horizontalLengthSqr() {
    return motionX * motionX + motionZ * motionZ;
  }

  public Motion filtered(Set<Relative> relativeSet) {
    return new Motion(
      relativeSet.contains(Relative.DELTA_X) ? motionX : 0,
      relativeSet.contains(Relative.DELTA_Y) ? motionY : 0,
      relativeSet.contains(Relative.DELTA_Z) ? motionZ : 0
    );
  }

  public Motion add(double x, double y, double z) {
    motionX += x;
    motionY += y;
    motionZ += z;
    return this;
  }

  public Motion add(Motion other) {
    return add(other.motionX, other.motionY, other.motionZ);
  }

  public void setTo(Motion motion) {
    setTo(motion.motionX, motion.motionY, motion.motionZ);
  }

  public void setToBaseMotionFrom(SimulationEnvironment data) {
    setTo(data.baseMotionX(), data.baseMotionY(), data.baseMotionZ());
  }

  public double length() {
    return hypot3d(motionX, motionY, motionZ);
  }

  public Vector toBukkitVector() {
    return new Vector(this.motionX, this.motionY, this.motionZ);
  }

  public static Motion copyFrom(Motion context) {
    return new Motion(context.motionX, context.motionY, context.motionZ);
  }

  @Override
  public String toString() {
    return "(" + motionX + ", " + motionY + ", " + motionZ + ")";
  }
}
