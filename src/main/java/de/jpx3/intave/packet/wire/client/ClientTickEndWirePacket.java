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

package de.jpx3.intave.packet.wire.client;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.immutable.ClientTickEndPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

public final class ClientTickEndWirePacket extends AbstractMutable implements ClientTickEndPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ClientTickEndWirePacket> STREAM_CODEC =
		StreamCodec.of((output, packet) -> {}, input -> new ClientTickEndWirePacket());

	public ClientTickEndWirePacket() {
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}
}
