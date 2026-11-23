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

import com.viaversion.nbt.tag.Tag;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/** The blank, styled and fixed number formats introduced in protocol 765. */
public final class ScoreboardNumberFormat {
	public static final StreamCodec<ByteBuf, ByteBuf, ScoreboardNumberFormat> OPTIONAL_CODEC =
		ByteBufStreamCodecs.optionalCodecOf(StreamCodec.of((output, format) -> {
			ByteBufStreamCodecs.VARINT.encode(output, format.type);
			if (format.type != 0) ByteBufStreamCodecs.TRUSTED_TAG.encode(output, format.value);
		}, input -> {
			int type = ByteBufStreamCodecs.VARINT.decode(input);
			if (type < 0 || type > 2) throw new IllegalArgumentException("Unknown number format " + type);
			return new ScoreboardNumberFormat(type, type == 0 ? null : ByteBufStreamCodecs.TRUSTED_TAG.decode(input));
		}));

	private final int type;
	private final Tag value;

	private ScoreboardNumberFormat(int type, Tag value) {
		this.type = type;
		this.value = value;
	}
}
