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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

final class LowPrecisionVectorCodec implements StreamCodec<ByteBuf, ByteBuf, RawVector3d> {
	static final LowPrecisionVectorCodec INSTANCE = new LowPrecisionVectorCodec();

	private LowPrecisionVectorCodec() {
	}

	@Override
	public void encode(ByteBuf output, RawVector3d value) {
		double x = sanitizeVector(value.x),
			y = sanitizeVector(value.y),
			z = sanitizeVector(value.z);
		double maximum = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
		if (maximum < 3.051944088384301E-5) {
			output.writeByte(0);
			return;
		}
		long scale = (long) Math.ceil(maximum);
		boolean extended = (scale & 3) != scale;
		long packed =
			(extended ? scale & 3 | 4 : scale)
				| packVector(x / scale) << 3
				| packVector(y / scale) << 18
				| packVector(z / scale) << 33;
		output.writeByte((int) packed);
		output.writeByte((int) (packed >> 8));
		output.writeInt((int) (packed >> 16));
		if (extended) ByteBufStreamCodecs.VARINT.encode(output, (int) (scale >> 2));
	}

	@Override
	public RawVector3d decode(ByteBuf input) {
		int first = input.readUnsignedByte();
		if (first == 0) return new RawVector3d(0, 0, 0);
		int second = input.readUnsignedByte();
		long packed = input.readUnsignedInt() << 16 | (long) second << 8 | first;
		long scale = first & 3;
		if ((first & 4) != 0) scale |= (ByteBufStreamCodecs.VARINT.decode(input) & 0xffffffffL) << 2;
		return new RawVector3d(
			unpackVector(packed >> 3) * scale,
			unpackVector(packed >> 18) * scale,
			unpackVector(packed >> 33) * scale);
	}

	private static double sanitizeVector(double value) {
		return Double.isNaN(value)
			? 0
			: Math.max(-1.7179869183E10, Math.min(1.7179869183E10, value));
	}

	private static long packVector(double value) {
		return Math.round((value * 0.5 + 0.5) * 32766);
	}

	private static double unpackVector(long value) {
		return Math.min(value & 32767, 32766) * 2.0 / 32766.0 - 1.0;
	}
}
