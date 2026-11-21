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

package de.jpx3.intave.packet.wire.window.open;

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.OptionalInt;

/** 1.13 kept named windows and raised the JSON component length limit. */
public final class LegacyOpenScreenProtocol393WirePacket extends LegacyOpenScreenWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, LegacyOpenScreenProtocol393WirePacket> STREAM_CODEC =
		codec(JSON_TITLE, LegacyOpenScreenProtocol393WirePacket::new);

	public LegacyOpenScreenProtocol393WirePacket() {
	}

	public LegacyOpenScreenProtocol393WirePacket(int containerId, String menuType, String titleJson, int slots, OptionalInt horseEntityId) {
		super(containerId, menuType, titleJson, slots, horseEntityId);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 393 && protocolVersion < 477;
	}
}
