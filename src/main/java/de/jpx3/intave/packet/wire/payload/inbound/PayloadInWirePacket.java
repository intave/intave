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

package de.jpx3.intave.packet.wire.payload.inbound;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutablePayloadInPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.bytes;

public class PayloadInWirePacket extends MutableWirePacket implements MutablePayloadInPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PayloadInWirePacket> STREAM_CODEC =
		StreamCodec.of(
			(output, packet) -> {
				ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.tag);
				output.writeBytes(packet.payload);
			},
			input -> {
				String tag = ByteBufStreamCodecs.VARINT_STRING.decode(input);
				byte[] payload = bytes(input, input.readableBytes());
				return new PayloadInWirePacket(tag, payload);
			});

	protected String tag;
	protected byte[] payload;

	public PayloadInWirePacket() {
		this("", new byte[0]);
	}

	public PayloadInWirePacket(String tag, byte[] payload) {
		this.tag = tag;
		this.payload = payload;
	}

	@Override
	public String tag() {
		return this.tag;
	}

	@Override
	public void setTag(String value) {
		markMutated();
		this.tag = value;
	}

	@Override
	public byte[] payload() {
		markMutated();
		return this.payload;
	}

	@Override
	public void setPayload(byte[] value) {
		markMutated();
		this.payload = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}
}
