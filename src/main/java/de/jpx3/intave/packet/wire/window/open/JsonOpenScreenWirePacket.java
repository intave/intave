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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public final class JsonOpenScreenWirePacket extends OpenScreenWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, JsonOpenScreenWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, JsonOpenScreenWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, JsonOpenScreenWirePacket::menuId,
			JSON_TITLE, JsonOpenScreenWirePacket::titleJson,
			JsonOpenScreenWirePacket::new
		);

	private static final int BEACON_MENU_ID = 8;
	private final int menuId;
	private final String titleJson;

	public JsonOpenScreenWirePacket() {
		this(0, 0, "\"\"");
	}

	public JsonOpenScreenWirePacket(int containerId, int menuId, String titleJson) {
		super(containerId);
		this.menuId = menuId;
		this.titleJson = Objects.requireNonNull(titleJson);
	}

	public int menuId() {
		return menuId;
	}

	public String titleJson() {
		return titleJson;
	}

	@Override
	public boolean isBeaconMenu() {
		return menuId == BEACON_MENU_ID;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 477 && protocolVersion < 765;
	}
}
