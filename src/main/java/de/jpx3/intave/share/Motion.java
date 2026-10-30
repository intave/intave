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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.math.Hypot;
import io.netty.buffer.ByteBuf;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.codec.JsonStreamCodecs.doubleField;
import static de.jpx3.intave.codec.JsonStreamCodecs.object;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.math.MathHelper.hypot3d;

public final class Motion {
	public static final StreamCodec<ByteBuf, ByteBuf, Motion> FLOAT_STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Motion::motionX,
		ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Motion::motionY,
		ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Motion::motionZ,
		Motion::new
	);
	public static final StreamCodec<ByteBuf, ByteBuf, Motion> LOW_PRECISION_STREAM_CODEC =
		RawVector3d.LOW_PRECISION_STREAM_CODEC.beforeAndAfter(RawVector3d::toMotion, Motion::toRawVector3d);

	public static final StreamCodec<JsonReader, JsonWriter, Motion> JSON_CODEC = object(
		doubleField("x", Motion::motionX),
		doubleField("y", Motion::motionY),
		doubleField("z", Motion::motionZ),
		Motion::new
	);
	public static final StreamCodec<JsonReader, JsonWriter, List<Motion>> LIST_JSON_CODEC =
		JsonStreamCodecs.listCodecOf(JSON_CODEC);
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

	public Motion copiedOverrideIfNotNaN(
		double newMotionX,
		double newMotionY,
		double newMotionZ
	) {
		return new Motion(
			Double.isNaN(newMotionX) ? this.motionX : newMotionX,
			Double.isNaN(newMotionY) ? this.motionY : newMotionY,
			Double.isNaN(newMotionZ) ? this.motionZ : newMotionZ
		);
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
		return Math.abs(motionX - other.motionX) < 1E-10 &&
			Math.abs(motionY - other.motionY) < 1E-10 &&
			Math.abs(motionZ - other.motionZ) < 1E-10;
	}

	public Motion reversed() {
		return new Motion(-this.motionX, -this.motionY, -this.motionZ);
	}

	public Motion rotated(Rotation rotation) {
		double pitch = Math.toRadians(rotation.pitch());
		double pitchCos = Math.cos(pitch);
		double pitchSin = Math.sin(pitch);
		double rotatedY = motionY * pitchCos - motionZ * pitchSin;
		double rotatedZ = motionY * pitchSin + motionZ * pitchCos;
		double yaw = Math.toRadians(rotation.yaw());
		double yawCos = Math.cos(yaw);
		double yawSin = Math.sin(yaw);
		double rotatedX = motionX * yawCos + rotatedZ * yawSin;
				   rotatedZ = -motionX * yawSin + rotatedZ * yawCos;
		return new Motion(rotatedX, rotatedY, rotatedZ);
	}

	public RawVector3d toRawVector3d() {
		return new RawVector3d(motionX, motionY, motionZ);
	}

	public RawVector3d furthestCorner() {
		RawVector3d thisAsVec = this.toRawVector3d();
		double crossX = Math.abs(RawVector3d.X_AXIS.dot(thisAsVec));
		double crossY = Math.abs(RawVector3d.Y_AXIS.dot(thisAsVec));
		double crossZ = Math.abs(RawVector3d.Z_AXIS.dot(thisAsVec));
		int stepX = this.motionX < 0.0 ? -1 : 1;
		int stepY = this.motionY < 0.0 ? -1 : 1;
		int stepZ = this.motionZ < 0.0 ? -1 : 1;
		if (crossX <= crossY && crossX <= crossZ) {
			return new RawVector3d(-stepX, -stepZ, stepY);
		} else if (crossY <= crossZ) {
			return new RawVector3d(stepZ, -stepY, -stepX);
		} else {
			return new RawVector3d(-stepY, stepX, -stepZ);
		}
	}

	public boolean almostIdentical(Motion motion) {
		return Math.abs(motionX - motion.motionX) < 1E-5 &&
			Math.abs(motionY - motion.motionY) < 1E-5 &&
			Math.abs(motionZ - motion.motionZ) < 1E-5;
	}

	public long almostIdenticalHash() {
		long x = (long) (motionX * 10000);
		long y = (long) (motionY * 10000);
		long z = (long) (motionZ * 10000);
		return x ^ y ^ z;
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

	public String shortString() {
		int digits = 5;
		return "" + (motionX < 0 ? "-" : "+") + (Math.abs(motionX) > 0.99 ? (int) motionX : "") + "." + formatDouble(motionX, digits).split("\\.")[1]
			+ "," + (motionY < 0 ? "-" : "+") + (Math.abs(motionY) > 0.99 ? (int) motionY : "") + "." + formatDouble(motionY, digits).split("\\.")[1]
			+ "," + (motionZ < 0 ? "-" : "+") + (Math.abs(motionZ) > 0.99 ? (int) motionZ : "") + "." + formatDouble(motionZ, digits).split("\\.")[1]
			+ "";
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
