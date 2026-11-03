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
import de.jpx3.intave.packet.contract.mutable.MutableClientSettingsPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import io.netty.buffer.ByteBuf;

public class ClientSettingsWithoutHandWirePacket extends MutableWirePacket implements MutableClientSettingsPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ClientSettingsWithoutHandWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(16), ClientSettingsWithoutHandWirePacket::locale,
			ByteBufStreamCodecs.SIGNED_BYTE, ClientSettingsWithoutHandWirePacket::viewDistance,
			ByteBufStreamCodecs.VARINT, ClientSettingsWithoutHandWirePacket::chatVisibility,
			ByteBufStreamCodecs.BOOLEAN, ClientSettingsWithoutHandWirePacket::chatColors,
			ByteBufStreamCodecs.UNSIGNED_BYTE, ClientSettingsWithoutHandWirePacket::skinParts,
			ClientSettingsWithoutHandWirePacket::new
		);

	protected String locale;
	protected int viewDistance;
	protected int chatVisibility;
	protected boolean chatColors;
	protected int skinParts;

	public ClientSettingsWithoutHandWirePacket() {
		this("en_us", 8, 0, true, 127);
	}

	public ClientSettingsWithoutHandWirePacket(
		String locale,
		int viewDistance,
		int chatVisibility,
		boolean chatColors,
		int skinParts
	) {
		this.locale = locale;
		this.viewDistance = viewDistance;
		this.chatVisibility = chatVisibility;
		this.chatColors = chatColors;
		this.skinParts = skinParts;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}

	@Override
	public String locale() {
		return this.locale;
	}

	@Override
	public void setLocale(String value) {
		markMutated();
		this.locale = value;
	}

	@Override
	public MainHand mainHand() {
		return null;
	}

	@Override
	public void setMainHand(MainHand value) {
		markMutated();
		throw new IllegalStateException("No main-hand preference before 1.9");
	}

	public int viewDistance() {
		return viewDistance;
	}

	public int chatVisibility() {
		return chatVisibility;
	}

	public boolean chatColors() {
		return chatColors;
	}

	public int skinParts() {
		return skinParts;
	}
}
