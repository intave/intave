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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableTabCompleteResponsePacket;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.packet.wire.WireCodecs.count;

public class TabCompleteResponseWirePacket implements MutableTabCompleteResponsePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TabCompleteResponseWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT.encode(output, packet.id);
						ByteBufStreamCodecs.VARINT.encode(output, packet.start);
						ByteBufStreamCodecs.VARINT.encode(output, packet.length);

						ByteBufStreamCodecs.VARINT.encode(output, packet.suggestions.size());
						for (int i = 0; i < packet.suggestions.size(); i++) {
							ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.suggestions.get(i));

							String tooltip =
									i < packet.tooltips.size() ? packet.tooltips.get(i) : null;
							output.writeBoolean(tooltip != null);
							if (tooltip != null) ByteBufStreamCodecs.stringCodec(262144).encode(output, tooltip);
						}
					},
					input -> {
						int id = ByteBufStreamCodecs.VARINT.decode(input);
						int start = ByteBufStreamCodecs.VARINT.decode(input);
						int length = ByteBufStreamCodecs.VARINT.decode(input);

						int count = count(input, input.readableBytes());
						List<String> suggestions = new ArrayList<>(count);
						List<String> tooltips = new ArrayList<>(count);
						for (int i = 0; i < count; i++) {
							suggestions.add(ByteBufStreamCodecs.VARINT_STRING.decode(input));
							String tooltip = null;
							if ((input.readBoolean())) {
								tooltip = ByteBufStreamCodecs.stringCodec(262144).decode(input);
							}
							tooltips.add(tooltip);
						}
						return new TabCompleteResponseWirePacket(
								id, start, length, suggestions, tooltips);
					});

	protected int id, start, length;

	protected List<String> suggestions = new ArrayList<>();

	protected List<String> tooltips = new ArrayList<>();

	public TabCompleteResponseWirePacket() {
		this(0, 0, 0, new ArrayList<>(), new ArrayList<>());
	}

	public TabCompleteResponseWirePacket(
			int id, int start, int length, List<String> suggestions, List<String> tooltips) {
		this.id = id;
		this.start = start;
		this.length = length;
		this.suggestions = suggestions;
		this.tooltips = tooltips;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("TAB_COMPLETE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 393 && protocolVersion < 765;
	}

	@Override
	public List<String> suggestions() {
		return Collections.unmodifiableList(suggestions);
	}

	@Override
	public void setSuggestions(List<String> value) {
		suggestions = new ArrayList<>(value);
		tooltips.clear();
	}
}
