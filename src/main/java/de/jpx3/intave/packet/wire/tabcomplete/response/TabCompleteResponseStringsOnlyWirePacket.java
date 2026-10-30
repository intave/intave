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

package de.jpx3.intave.packet.wire.tabcomplete.response;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.packet.wire.WireCodecs.count;

public final class TabCompleteResponseStringsOnlyWirePacket extends TabCompleteResponseWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TabCompleteResponseStringsOnlyWirePacket>
			STREAM_CODEC =
					StreamCodec.of(
							(output, packet) -> {
								ByteBufStreamCodecs.VARINT.encode(output, packet.suggestions.size());
								for (int i = 0; i < packet.suggestions.size(); i++) {
									ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.suggestions.get(i));
								}
							},
							input -> {
								int count = count(input, input.readableBytes());
								List<String> suggestions = new ArrayList<>(count);
								List<String> tooltips = new ArrayList<>(count);
								for (int i = 0; i < count; i++) {
									suggestions.add(ByteBufStreamCodecs.VARINT_STRING.decode(input));
									String tooltip = null;

									tooltips.add(tooltip);
								}
								return new TabCompleteResponseStringsOnlyWirePacket(
										suggestions, tooltips);
							});

	public TabCompleteResponseStringsOnlyWirePacket() {
		super();
	}

	public TabCompleteResponseStringsOnlyWirePacket(
			List<String> suggestions, List<String> tooltips) {
		super(0, 0, 0, suggestions, tooltips);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 393;
	}
}
