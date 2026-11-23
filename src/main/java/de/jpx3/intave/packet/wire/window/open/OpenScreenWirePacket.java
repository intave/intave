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
import de.jpx3.intave.codec.transform.PentaFunction;
import de.jpx3.intave.packet.contract.immutable.OpenScreenPacket;
import de.jpx3.intave.packet.contract.mutable.MutableWindowAddressed;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.*;

public final class OpenScreenWirePacket {
	private OpenScreenWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol393.class,
			Protocol477.class,
			Protocol765.class
		));
	}

	static abstract class Base extends AbstractMutable implements OpenScreenPacket, MutableWindowAddressed {
		static final StreamCodec<ByteBuf, ByteBuf, String> JSON_TITLE = ByteBufStreamCodecs.stringCodec(262144);

		protected int containerId;

		Base(int containerId) {
			this.containerId = containerId;
		}

		@Override
		public final int containerId() {
			return containerId;
		}

		@Override
		public final void setContainerId(int containerId) {
			markMutated();
			this.containerId = containerId;
		}
	}

	static class Protocol47 extends Base {
		private static final StreamCodec<ByteBuf, ByteBuf, String> MENU_TYPE = ByteBufStreamCodecs.stringCodec(32);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			codec(ByteBufStreamCodecs.VARINT_STRING, Protocol47::new);

		private final String menuType;
		private final String titleJson;
		private final int slots;
		private final OptionalInt horseEntityId;

		public Protocol47() {
			this(0, "minecraft:container", "\"\"", 0, OptionalInt.empty());
		}

		public Protocol47(int containerId, String menuType, String titleJson, int slots, OptionalInt horseEntityId) {
			super(containerId);
			this.menuType = Objects.requireNonNull(menuType);
			this.titleJson = Objects.requireNonNull(titleJson);
			if ("EntityHorse".equals(menuType) != horseEntityId.isPresent()) {
				throw new IllegalArgumentException("Only EntityHorse windows carry a horse entity ID");
			}
			if (slots < 0 || slots > 255) throw new IllegalArgumentException("Invalid legacy slot count " + slots);
			this.slots = slots;
			this.horseEntityId = horseEntityId;
		}

		static <T extends Protocol47> StreamCodec<ByteBuf, ByteBuf, T> codec(
			StreamCodec<ByteBuf, ByteBuf, String> titleCodec,
			PentaFunction<Integer, String, String, Integer, OptionalInt, T> constructor
		) {
			return StreamCodec.of((output, packet) -> packet.write(output, titleCodec), input -> {
				int containerId = input.readUnsignedByte();
				String type = MENU_TYPE.decode(input);
				String title = titleCodec.decode(input);
				int slots = input.readUnsignedByte();
				OptionalInt entityId = "EntityHorse".equals(type) ? OptionalInt.of(input.readInt()) : OptionalInt.empty();
				return constructor.apply(containerId, type, title, slots, entityId);
			});
		}

		final void write(ByteBuf output, StreamCodec<ByteBuf, ByteBuf, String> titleCodec) {
			if (containerId() < 0 || containerId() > 255) throw new IllegalArgumentException("Invalid legacy window ID " + containerId());
			output.writeByte(containerId());
			MENU_TYPE.encode(output, menuType);
			titleCodec.encode(output, titleJson);
			output.writeByte(slots);
			horseEntityId.ifPresent(output::writeInt);
		}

		public String menuType() {
			return menuType;
		}

		public String titleJson() {
			return titleJson;
		}

		public int slots() {
			return slots;
		}

		public OptionalInt horseEntityId() {
			return horseEntityId;
		}

		@Override
		public boolean isBeaconMenu() {
			return "minecraft:beacon".equals(menuType);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 393;
		}
	}

	static final class Protocol393 extends OpenScreenWirePacket.Protocol47 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC =
			codec(JSON_TITLE, Protocol393::new);

		public Protocol393() {
		}

		public Protocol393(int containerId, String menuType, String titleJson, int slots, OptionalInt horseEntityId) {
			super(containerId, menuType, titleJson, slots, horseEntityId);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 393 && protocolVersion < 477;
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol477::containerId,
				ByteBufStreamCodecs.VARINT, Protocol477::menuId,
				JSON_TITLE, Protocol477::titleJson,
				Protocol477::new
			);

		private static final int BEACON_MENU_ID = 8;
		private final int menuId;
		private final String titleJson;

		public Protocol477() {
			this(0, 0, "\"\"");
		}

		public Protocol477(int containerId, int menuId, String titleJson) {
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

	static final class Protocol765 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol765::containerId,
				ByteBufStreamCodecs.VARINT, Protocol765::menuId,
				ByteBufStreamCodecs.TRUSTED_TAG, packet -> packet.title,
				Protocol765::new
			);

		// The crafter was inserted before the beacon in the 1.20.3 menu registry.
		private static final int BEACON_MENU_ID = 9;
		private final int menuId;
		private final Tag title;

		public Protocol765() {
			this(0, 0, new StringTag(""));
		}

		public Protocol765(int containerId, int menuId, Tag title) {
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
}
