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

package de.jpx3.intave.packet.wire.window.click;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.contract.mutable.MutableWindowClickPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.WindowClickType;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Supplier;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

public final class LegacyWindowClickWirePacket {
	private LegacyWindowClickWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol210.class,
			Protocol315.class,
			Protocol316.class,
			Protocol335.class,
			Protocol393.class,
			Protocol401.class,
			Protocol404.class,
			Protocol477.class,
			Protocol573.class,
			Protocol735.class,
			Protocol751.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableWindowClickPacket {
		protected static <P extends Base> StreamCodec<ByteBuf, ByteBuf, P> codec(
			StreamCodec<ByteBuf, ByteBuf, ItemStack> items, StreamCodec<ByteBuf, ByteBuf, Integer> clickModes, Supplier<P> factory
		) {
			StreamCodec<ByteBuf, ByteBuf, WindowClickType> clicks = enumCodec(WindowClickType.values(), clickModes);
			return StreamCodec.of((output, value) -> {
				Base packet = value;
				output.writeByte(packet.containerId);
				output.writeShort(packet.slot);
				output.writeByte(packet.button);
				output.writeShort(packet.actionNumber);
				clicks.encode(output, packet.clickType);
				if (packet.originalItemPayload != null) {
					output.writeBytes(packet.originalItemPayload);
				} else {
					items.encode(output, packet.clickedItem);
				}
			}, input -> {
				P value = factory.get();
				value.containerId = input.readByte();
				value.slot = input.readShort();
				value.button = input.readByte();
				value.actionNumber = input.readShort();
				value.clickType = clicks.decode(input);
				int itemStart = input.readerIndex();
				value.clickedItem = items.decode(input);
				value.originalItemPayload = new byte[input.readerIndex() - itemStart];
				input.getBytes(itemStart, value.originalItemPayload);
				if (input.isReadable()) throw new IllegalArgumentException("Trailing bytes after clicked item");
				return value;
			});
		}

		protected int containerId, slot, button, actionNumber;
		protected WindowClickType clickType = WindowClickType.PICKUP;
		protected ItemStack clickedItem;
		protected byte[] originalItemPayload;

		@Override
		public int containerId() { return containerId; }

		@Override
		public int slot() { return slot; }

		@Override
		public int button() { return button; }

		@Override
		public OptionalInt revision() { return OptionalInt.of(actionNumber); }

		@Override
		public WindowClickType clickType() { return clickType; }

		@Override
		public boolean hasClickedItem() { return true; }

		@Override
		public ItemStack clickedItem() { return clickedItem == null ? null : clickedItem.copy(); }

		@Override
		public boolean hasSlotPredictions() { return false; }

		@Override
		public Map<Integer, ItemStack> itemMap() { return Collections.emptyMap(); }

		@Override
		public boolean hasCarriedItem() { return false; }

		@Override
		public ItemStack carriedItem() { return null; }

		@Override
		public void setContainerId(int value) {
			markMutated();
			requireRange(value, Byte.MIN_VALUE, Byte.MAX_VALUE);
			containerId = value;
		}

		@Override
		public void setSlot(int value) {
			markMutated();
			requireRange(value, Short.MIN_VALUE, Short.MAX_VALUE);
			slot = value;
		}

		@Override
		public void setButton(int value) {
			markMutated();
			requireRange(value, Byte.MIN_VALUE, Byte.MAX_VALUE);
			button = value;
		}

		@Override
		public void setRevision(int value) {
			markMutated();
			requireRange(value, Short.MIN_VALUE, Short.MAX_VALUE);
			actionNumber = value;
		}

		@Override
		public void setClickType(WindowClickType value) {
			markMutated();
			clickType = Objects.requireNonNull(value, "clickType");
		}

		@Override
		public void setClickedItem(ItemStack value) {
			markMutated();
			clickedItem = value == null ? null : value.copy();
			originalItemPayload = null;
		}

		@Override
		public void setItemMap(Map<Integer, ItemStack> items) {
			markMutated();
			if (!items.isEmpty()) throw new UnsupportedOperationException("Legacy clicks have no slot predictions");
		}

		@Override
		public void setCarriedItem(ItemStack item) {
			markMutated();
			if (item != null) throw new UnsupportedOperationException("Legacy clicks have no carried item");
		}

		protected static void requireRange(int value, int minimum, int maximum) {
			if (value < minimum || value > maximum) throw new IllegalArgumentException("Value out of wire range: " + value);
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			codec(ItemStack.codecFor(47), SIGNED_BYTE, Protocol47::new);

		public Protocol47() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			codec(ItemStack.codecFor(107), VARINT, Protocol107::new);

		public Protocol107() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 210;
		}
	}

	static final class Protocol210 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol210> STREAM_CODEC =
			codec(ItemStack.codecFor(210), VARINT, Protocol210::new);

		public Protocol210() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 210 && protocolVersion < 315;
		}
	}

	static final class Protocol315 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol315> STREAM_CODEC =
			codec(ItemStack.codecFor(315), VARINT, Protocol315::new);

		public Protocol315() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 315 && protocolVersion < 316;
		}
	}

	static final class Protocol316 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol316> STREAM_CODEC =
			codec(ItemStack.codecFor(316), VARINT, Protocol316::new);

		public Protocol316() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 316 && protocolVersion < 335;
		}
	}

	static final class Protocol335 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol335> STREAM_CODEC =
			codec(ItemStack.codecFor(335), VARINT, Protocol335::new);

		public Protocol335() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 335 && protocolVersion < 393;
		}
	}

	static final class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC =
			codec(ItemStack.codecFor(393), VARINT, Protocol393::new);

		public Protocol393() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 393 && protocolVersion < 401;
		}
	}

	static final class Protocol401 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol401> STREAM_CODEC =
			codec(ItemStack.codecFor(401), VARINT, Protocol401::new);

		public Protocol401() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 401 && protocolVersion < 404;
		}
	}

	static final class Protocol404 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol404> STREAM_CODEC =
			codec(ItemStack.codecFor(404), VARINT, Protocol404::new);

		public Protocol404() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 404 && protocolVersion < 477;
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC =
			codec(ItemStack.codecFor(477), VARINT, Protocol477::new);

		public Protocol477() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 477 && protocolVersion < 573;
		}
	}

	static final class Protocol573 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol573> STREAM_CODEC =
			codec(ItemStack.codecFor(573), VARINT, Protocol573::new);

		public Protocol573() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 573 && protocolVersion < 735;
		}
	}

	static final class Protocol735 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC =
			codec(ItemStack.codecFor(735), VARINT, Protocol735::new);

		public Protocol735() {
			super();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 735 && protocolVersion < 751;
		}
	}

	static final class Protocol751 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol751> STREAM_CODEC =
			codec(ItemStack.codecFor(751), VARINT, Protocol751::new);

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 751 && protocolVersion < 755;
		}
	}
}
