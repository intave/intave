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

package de.jpx3.intave.packet.wire.client.settings;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public class ClientSettingsWithHandWirePacket extends ClientSettingsWithoutHandWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ClientSettingsWithHandWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(16), ClientSettingsWithoutHandWirePacket::locale,
			ByteBufStreamCodecs.SIGNED_BYTE, ClientSettingsWithoutHandWirePacket::viewDistance,
			ByteBufStreamCodecs.VARINT, ClientSettingsWithoutHandWirePacket::chatVisibility,
			ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithoutHandWirePacket::chatColors,
			ByteBufStreamCodecs.UNSIGNED_BYTE, ClientSettingsWithoutHandWirePacket::skinParts,
			ByteBufStreamCodecs.enumCodec(MainHand.values()), ClientSettingsWithHandWirePacket::mainHand,
			ClientSettingsWithHandWirePacket::new
		);

	protected MainHand mainHand;

	public ClientSettingsWithHandWirePacket() {
		this("en_us", 8, 0, true, 127, MainHand.RIGHT);
	}

	public ClientSettingsWithHandWirePacket(
			String locale,
			int viewDistance,
			int chatVisibility,
			boolean chatColors,
			int skinParts,
			MainHand mainHand) {
		super(locale, viewDistance, chatVisibility, chatColors, skinParts);
		this.mainHand = mainHand;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 755;
	}

	@Override
	public MainHand mainHand() {
		return mainHand;
	}

	@Override
	public void setMainHand(MainHand value) {
		mainHand = Objects.requireNonNull(value, "mainHand");
	}
}
