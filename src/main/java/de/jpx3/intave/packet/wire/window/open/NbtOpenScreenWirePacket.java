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

import com.viaversion.nbt.tag.StringTag;
import com.viaversion.nbt.tag.Tag;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class NbtOpenScreenWirePacket extends OpenScreenWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, NbtOpenScreenWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, NbtOpenScreenWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, NbtOpenScreenWirePacket::menuId,
			ByteBufStreamCodecs.TRUSTED_TAG, packet -> packet.title,
			NbtOpenScreenWirePacket::new
		);

	// The crafter was inserted before the beacon in the 1.20.3 menu registry.
	private static final int BEACON_MENU_ID = 9;
	private final int menuId;
	private final Tag title;

	public NbtOpenScreenWirePacket() {
		this(0, 0, new StringTag(""));
	}

	public NbtOpenScreenWirePacket(int containerId, int menuId, Tag title) {
		super(containerId);
		this.menuId = menuId;
		this.title = title.copy();
	}

	public int menuId() {
		return menuId;
	}

	public Tag title() {
		return title.copy();
	}

	@Override
	public boolean isBeaconMenu() {
		return menuId == BEACON_MENU_ID;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 765;
	}
}
