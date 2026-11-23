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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableClientSettingsPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ClientSettingsWirePacket {
	private ClientSettingsWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol755.class,
			Protocol757.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableClientSettingsPacket {
		protected String locale;
		protected int viewDistance;
		protected int chatVisibility;
		protected boolean chatColors;
		protected int skinParts;

		protected Base() {
			this("en_us", 8, 0, true, 127);
		}

		protected Base(
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
		public String locale() {
			return this.locale;
		}

		@Override
		public void setLocale(String value) {
			markMutated();
			this.locale = value;
		}

		@Override
		public abstract MainHand mainHand();

		@Override
		public abstract void setMainHand(MainHand value);

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

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(16), Protocol47::locale,
				ByteBufStreamCodecs.SIGNED_BYTE, Protocol47::viewDistance,
				ByteBufStreamCodecs.VARINT, Protocol47::chatVisibility,
				ByteBufStreamCodecs.BOOLEAN, Protocol47::chatColors,
				ByteBufStreamCodecs.UNSIGNED_BYTE, Protocol47::skinParts,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(
			String locale,
			int viewDistance,
			int chatVisibility,
			boolean chatColors,
			int skinParts
		) {
			super(locale, viewDistance, chatVisibility, chatColors, skinParts);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
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
	}

	static class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(16), Base::locale,
				ByteBufStreamCodecs.SIGNED_BYTE, Base::viewDistance,
				ByteBufStreamCodecs.VARINT, Base::chatVisibility,
				ByteBufStreamCodecs.BOOLEAN, Base::chatColors,
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::skinParts,
				ByteBufStreamCodecs.enumCodec(MainHand.values()), Protocol107::mainHand,
				Protocol107::new
			);

		protected MainHand mainHand;

		public Protocol107() {
			this("en_us", 8, 0, true, 127, MainHand.RIGHT);
		}

		public Protocol107(
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
			markMutated();
			mainHand = Objects.requireNonNull(value, "mainHand");
		}
	}

	static class Protocol755 extends ClientSettingsWirePacket.Protocol107 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(16), Base::locale,
				ByteBufStreamCodecs.SIGNED_BYTE, Base::viewDistance,
				ByteBufStreamCodecs.VARINT, Base::chatVisibility,
				ByteBufStreamCodecs.BOOLEAN, Base::chatColors,
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::skinParts,
				ByteBufStreamCodecs.enumCodec(MainHand.values()), ClientSettingsWirePacket.Protocol107::mainHand,
				ByteBufStreamCodecs.BOOLEAN, Protocol755::filtering,
				Protocol755::new
			);

		protected boolean filtering;

		public Protocol755() {
			this("en_us", 8, 0, true, 127, MainHand.RIGHT, false);
		}

		public Protocol755(
			String locale,
			int viewDistance,
			int chatVisibility,
			boolean chatColors,
			int skinParts,
			MainHand mainHand,
			boolean filtering) {
			super(locale, viewDistance, chatVisibility, chatColors, skinParts, mainHand);
			this.filtering = filtering;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 755 && protocolVersion < 757;
		}

		public boolean filtering() {
			return filtering;
		}
	}

	static class Protocol757 extends ClientSettingsWirePacket.Protocol755 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol757> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.stringCodec(16), Base::locale,
				ByteBufStreamCodecs.SIGNED_BYTE, Base::viewDistance,
				ByteBufStreamCodecs.VARINT, Base::chatVisibility,
				ByteBufStreamCodecs.BOOLEAN, Base::chatColors,
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::skinParts,
				ByteBufStreamCodecs.enumCodec(MainHand.values()), ClientSettingsWirePacket.Protocol107::mainHand,
				ByteBufStreamCodecs.BOOLEAN, ClientSettingsWirePacket.Protocol755::filtering,
				ByteBufStreamCodecs.BOOLEAN, Protocol757::listing,
				Protocol757::new
			);

		protected boolean listing;

		public Protocol757() {
			this("en_us", 8, 0, true, 127, MainHand.RIGHT, false, true);
		}

		public Protocol757(
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

	static class Protocol768 extends ClientSettingsWirePacket.Protocol757 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.stringCodec(16), Base::locale,
			ByteBufStreamCodecs.SIGNED_BYTE, Base::viewDistance,
			ByteBufStreamCodecs.VARINT, Base::chatVisibility,
			ByteBufStreamCodecs.BOOLEAN, Base::chatColors,
			ByteBufStreamCodecs.UNSIGNED_BYTE, Base::skinParts,
			ByteBufStreamCodecs.enumCodec(MainHand.values()), ClientSettingsWirePacket.Protocol107::mainHand,
			ByteBufStreamCodecs.BOOLEAN, ClientSettingsWirePacket.Protocol755::filtering,
			ByteBufStreamCodecs.BOOLEAN, ClientSettingsWirePacket.Protocol757::listing,
			ByteBufStreamCodecs.VARINT, Protocol768::particles,
			Protocol768::new
		);

		protected int particles;

		public Protocol768() {
			this(
				"en_us", 8, 0,
				true, 127, MainHand.RIGHT,
				false, true, 0
			);
		}

		public Protocol768(
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
}
