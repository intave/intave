package de.jpx3.intave.share;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.packet.Relative;
import io.netty.buffer.ByteBuf;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.math.MathHelper.hypot3d;

public final class Motion {
	public static final StreamCodec<ByteBuf, ByteBuf, Motion> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.DOUBLE, Motion::motionX,
		ByteBufStreamCodecs.DOUBLE, Motion::motionY,
		ByteBufStreamCodecs.DOUBLE, Motion::motionZ,
		Motion::new
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

	public double lengthSquared() {
		return motionX * motionX + motionY * motionY + motionZ * motionZ;
	}

	public Vector toBukkitVector() {
		return new Vector(this.motionX, this.motionY, this.motionZ);
	}

	public double partialMotionIn(Direction.Axis axis) {
		switch (axis) {
			case X_AXIS:
				return motionX;
			case Y_AXIS:
				return motionY;
			case Z_AXIS:
				return motionZ;
		}
		throw new IllegalArgumentException("Unknown axis: " + axis);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		Motion other = (Motion) obj;
		return Double.compare(other.motionX, motionX) == 0 &&
			Double.compare(other.motionY, motionY) == 0 &&
			Double.compare(other.motionZ, motionZ) == 0;
	}

	@Override
	public int hashCode() {
		int result = Double.hashCode(motionX);
		result = 31 * result + Double.hashCode(motionY);
		result = 31 * result + Double.hashCode(motionZ);
		return result;
	}

	@Override
	public String toString() {
		return "(" + formatDouble(motionX, 4) + ", " + formatDouble(motionY, 4) + ", " + formatDouble(motionZ, 4) + ")";
	}

	public boolean isZero() {
		return motionX == 0.0 && motionY == 0.0 && motionZ == 0.0;
	}

	public static Motion newEmpty() {
		return new Motion(0.0, 0.0, 0.0);
	}

	public static Motion of(double motionX, double motionY, double motionZ) {
		return new Motion(motionX, motionY, motionZ);
	}

	public static Motion copyFrom(Motion context) {
		return new Motion(context.motionX, context.motionY, context.motionZ);
	}

	public static Motion fromVector(Vector velocity) {
		return new Motion(velocity.getX(), velocity.getY(), velocity.getZ());
	}

	public static Motion random() {
		ThreadLocalRandom current = ThreadLocalRandom.current();
		return new Motion(
			current.nextGaussian() * 0.33,
			current.nextGaussian() * 0.33,
			current.nextGaussian() * 0.33
		);
	}
}
