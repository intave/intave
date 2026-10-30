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
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.math.MathHelper;
import io.netty.buffer.ByteBuf;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.codec.JsonStreamCodecs.floatField;
import static de.jpx3.intave.codec.JsonStreamCodecs.object;

public final class Rotation implements Serializable {
	private static final StreamCodec<ByteBuf, ByteBuf, Float> BYTE_ANGLE =
		ByteBufStreamCodecs.BYTE.beforeAndAfter(value -> value * (360.0f / 256), value -> (byte) (int) (value * (256.0f / 360)));
	public static final StreamCodec<ByteBuf, ByteBuf, Rotation> BYTE_STREAM_CODEC = StreamCodec.compound(
		BYTE_ANGLE, Rotation::yaw,
		BYTE_ANGLE, Rotation::pitch,
		Rotation::new
	);
	public static final StreamCodec<JsonReader, JsonWriter, Rotation> JSON_CODEC = object(
		floatField("yaw", Rotation::yaw),
		floatField("pitch", Rotation::pitch),
		Rotation::new
	);
	public static final StreamCodec<ByteBuf, ByteBuf, Rotation> STREAM_CODEC = StreamCodec.of(
		(buf, rotation) -> {
			buf.writeFloat(rotation.yaw);
			buf.writeFloat(rotation.pitch);
		},
		buf -> new Rotation(buf.readFloat(), buf.readFloat())
	);
	private float yaw, pitch;

	public Rotation(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	public float distanceTo(Rotation rotation) {
		if (rotation == null) {
			throw new IllegalArgumentException("Rotation must not be null");
		}
		float yawDistance = MathHelper.distanceInDegrees(yaw, rotation.yaw);
		float pitchDistance = MathHelper.distanceInDegrees(pitch, rotation.pitch);
		return yawDistance + pitchDistance;
	}

	public void setYaw(float yaw) {
		this.yaw = yaw;
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
	}

	public Rotation add(Rotation rotation) {
		return new Rotation(yaw + rotation.yaw, pitch + rotation.pitch);
	}

	public Rotation subtract(Rotation rotation) {
		return new Rotation(yaw - rotation.yaw, pitch - rotation.pitch);
	}

	public boolean isZero() {
		return yaw == 0 && pitch == 0;
	}

	public boolean isWithin(Rotation rotation, float threshold) {
		if (rotation == null) {
			throw new IllegalArgumentException("Rotation must not be null");
		}
		if (Float.isNaN(threshold)) {
			return true;
		}
		if (threshold < 0) {
			throw new IllegalArgumentException("Threshold must be non-negative");
		}
		float yawDistance = MathHelper.distanceInDegrees(yaw, rotation.yaw);
		float pitchDistance = MathHelper.distanceInDegrees(pitch, rotation.pitch);
		return yawDistance <= threshold && pitchDistance <= threshold;
	}

	@Override
	public String toString() {
		return "Rot{" + yaw + ", " + pitch + "}";
	}

	public Rotation filtered(Set<Relative> relativeSet) {
		return new Rotation(
			relativeSet.contains(Relative.X_ROT) ? yaw : 0,
			relativeSet.contains(Relative.Y_ROT) ? pitch : 0
		);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		Rotation rotation = (Rotation) obj;
		return Float.compare(rotation.yaw, yaw) == 0 && Float.compare(rotation.pitch, pitch) == 0;
	}

	@Override
	public int hashCode() {
		int result = Float.hashCode(yaw);
		result = 31 * result + Float.hashCode(pitch);
		return result;
	}

	public static Rotation zero() {
		return new Rotation(0, 0);
	}

	public static Rotation random() {
		ThreadLocalRandom current = ThreadLocalRandom.current();
		return new Rotation(current.nextFloat() * 360 - 180, current.nextFloat() * 180 - 90);
	}

	public static Rotation of(float yaw, float pitch) {
		return new Rotation(yaw, pitch);
	}
}
