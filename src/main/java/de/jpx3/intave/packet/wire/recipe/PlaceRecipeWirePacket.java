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

package de.jpx3.intave.packet.wire.recipe;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutablePlaceRecipePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PlaceRecipeWirePacket {
	private PlaceRecipeWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol335.class,
			Protocol393.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutablePlaceRecipePacket {
		protected int containerId = 0;
		protected boolean placeAll = false;
		protected int recipeId;

		protected Base() {
			this(0, 0, false);
		}

		protected Base(int containerId, int recipeId, boolean placeAll) {
			this.containerId = containerId;
			this.recipeId = recipeId;
			this.placeAll = placeAll;
		}

		@Override
		public int containerId() {
			return this.containerId;
		}

		@Override
		public void setContainerId(int value) {
			markMutated();
			this.containerId = value;
		}

		@Override
		public boolean placeAll() {
			return this.placeAll;
		}

		@Override
		public void setPlaceAll(boolean value) {
			markMutated();
			this.placeAll = value;
		}

		public int recipeId() {
			return recipeId;
		}
	}

	static final class Protocol335 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol335> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.SIGNED_BYTE, Base::containerId,
				ByteBufStreamCodecs.VARINT, Base::recipeId,
				ByteBufStreamCodecs.BOOLEAN, Base::placeAll,
				Protocol335::new
			);

		public Protocol335() {
			super();
		}

		public Protocol335(int containerId, int recipeId, boolean placeAll) {
			super(containerId, recipeId, placeAll);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 335 && protocolVersion < 393;
		}
	}

	static final class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.SIGNED_BYTE, Base::containerId,
				ByteBufStreamCodecs.VARINT_STRING, Protocol393::recipeKey,
				ByteBufStreamCodecs.BOOLEAN, Base::placeAll,
				Protocol393::new
			);

		private String recipeKey = "minecraft:air";

		public Protocol393() {
			super();
		}

		public Protocol393(int containerId, String recipeKey, boolean placeAll) {
			super(containerId, 0, placeAll);
			this.recipeKey = recipeKey;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 393 && protocolVersion < 768;
		}

		public String recipeKey() {
			return recipeKey;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol768::containerId,
				ByteBufStreamCodecs.VARINT, Protocol768::recipeId,
				ByteBufStreamCodecs.BOOLEAN, Protocol768::placeAll,
				Protocol768::new
			);

		public Protocol768() {
			super();
		}

		public Protocol768(int containerId, int recipeId, boolean placeAll) {
			super(containerId, recipeId, placeAll);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768;
		}
	}
}
