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

public class ClientSettingsWithListingWirePacket extends ClientSettingsWithFilteringWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ClientSettingsWithListingWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(16), ClientSettingsWithoutHandWirePacket::locale,
			ByteBufStreamCodecs.SIGNED_BYTE, ClientSettingsWithoutHandWirePacket::viewDistance,
			ByteBufStreamCodecs.VARINT, ClientSettingsWithoutHandWirePacket::chatVisibility,
			ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithoutHandWirePacket::chatColors,
			ByteBufStreamCodecs.UNSIGNED_BYTE, ClientSettingsWithoutHandWirePacket::skinParts,
			ByteBufStreamCodecs.enumCodec(MainHand.values()), ClientSettingsWithHandWirePacket::mainHand,
			ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithFilteringWirePacket::filtering,
			ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithListingWirePacket::listing,
			ClientSettingsWithListingWirePacket::new
		);

	protected boolean listing;

	public ClientSettingsWithListingWirePacket() {
		this("en_us", 8, 0, true, 127, MainHand.RIGHT, false, true);
	}

	public ClientSettingsWithListingWirePacket(
			String locale,
			int viewDistance,
			int chatVisibility,
			boolean chatColors,
			int skinParts,
			MainHand mainHand,
			boolean filtering,
			boolean listing) {
		super(locale, viewDistance, chatVisibility, chatColors, skinParts, mainHand, filtering);
		this.listing = listing;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 757 && protocolVersion < 768;
	}

	public boolean listing() {
		return listing;
	}
}
