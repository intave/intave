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

public class ClientSettingsWirePacket extends ClientSettingsWithListingWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ClientSettingsWirePacket> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.stringCodec(16), ClientSettingsWithoutHandWirePacket::locale,
		ByteBufStreamCodecs.SIGNED_BYTE, ClientSettingsWithoutHandWirePacket::viewDistance,
		ByteBufStreamCodecs.VARINT, ClientSettingsWithoutHandWirePacket::chatVisibility,
		ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithoutHandWirePacket::chatColors,
		ByteBufStreamCodecs.UNSIGNED_BYTE, ClientSettingsWithoutHandWirePacket::skinParts,
		ByteBufStreamCodecs.enumCodec(MainHand.values()), ClientSettingsWithHandWirePacket::mainHand,
		ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithFilteringWirePacket::filtering,
		ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithListingWirePacket::listing,
		ByteBufStreamCodecs.VARINT, ClientSettingsWirePacket::particles,
		ClientSettingsWirePacket::new
	);

	protected int particles;

	public ClientSettingsWirePacket() {
		this(
			"en_us", 8, 0,
			true, 127, MainHand.RIGHT,
			false, true, 0
		);
	}

	public ClientSettingsWirePacket(
		String locale, int viewDistance, int chatVisibility,
		boolean chatColors, int skinParts, MainHand mainHand,
		boolean filtering, boolean listing, int particles
	) {
		super(
			locale, viewDistance, chatVisibility, chatColors,
			skinParts, mainHand, filtering, listing
		);
		this.particles = particles;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}

	public int particles() {
		return particles;
	}
}
