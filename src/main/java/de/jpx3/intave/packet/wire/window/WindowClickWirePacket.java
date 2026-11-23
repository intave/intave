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

package de.jpx3.intave.packet.wire.window;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.item.HashedItemStack;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.contract.immutable.HashedWindowClickPacket;
import de.jpx3.intave.packet.contract.immutable.WindowClickHeaderPacket;
import de.jpx3.intave.packet.contract.immutable.WindowClickPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.WindowClickType;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Supplier;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

public final class WindowClickWirePacket {
	private WindowClickWirePacket() { }

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol755.class,
			Protocol756.class,
			Protocol757.class,
			Protocol759.class,
			Protocol761.class,
			Protocol762.class,
			Protocol763.class,
			Protocol764.class,
			Protocol765.class,
			Protocol766.class,
			Protocol767.class,
			Protocol768.class,
			Protocol769.class,
			Protocol770.class,
			Protocol771.class,
			Protocol772.class,
			Protocol773.class,
			Protocol774.class,
			Protocol775.class,
			Protocol776.class,
			Protocol777.class
		));
	}

	static abstract class Base extends AbstractMutable implements WindowClickHeaderPacket {
		private static final WindowClickType[] CLICK_TYPES = WindowClickType.values();
		protected int containerId, slot, button, clickId;
		protected OptionalInt revision = OptionalInt.of(0);
		protected byte[] originalItemPayload;

		@Override public int containerId() { return containerId; }
		@Override public int slot() { return slot; }
		@Override public int button() { return button; }
		@Override public OptionalInt revision() { return revision; }
		@Override public WindowClickType clickType() {
			return clickId >= 0 && clickId < CLICK_TYPES.length ? CLICK_TYPES[clickId] : WindowClickType.PICKUP;
		}

		protected void readHeader(ByteBuf input, int protocol) {
			containerId = protocol >= 768 ? VARINT.decode(input) : input.readByte();
			revision = protocol == 755 ? OptionalInt.empty() : OptionalInt.of(VARINT.decode(input));
			slot = input.readShort();
			button = input.readByte();
			clickId = VARINT.decode(input);
			// ClickType's idMapper (1.21.5+) maps unknown IDs to PICKUP. Retain the wire ID.
			if (protocol < 770) enumValue(CLICK_TYPES, clickId);
		}

		protected void writeHeader(ByteBuf output, int protocol) {
			if (protocol >= 768) VARINT.encode(output, containerId);
			else output.writeByte(containerId);
			if (protocol != 755) VARINT.encode(output, revision.getAsInt());
			output.writeShort(slot);
			output.writeByte(button);
			VARINT.encode(output, clickId);
		}

		protected void retainPayload(ByteBuf input, int start) {
			if (input.isReadable()) throw new IllegalArgumentException("Trailing bytes after click payload");
			// Preserve duplicate map entries and other vanilla normalization when forwarding unchanged.
			originalItemPayload = new byte[input.readerIndex() - start];
			input.getBytes(start, originalItemPayload);
		}
	}

	static abstract class StackClick extends Base implements WindowClickPacket {
		protected Map<Integer, ItemStack> changedSlots = Collections.emptyMap();
		protected ItemStack carriedItem;

		@Override public boolean hasClickedItem() { return false; }
		@Override public ItemStack clickedItem() { return null; }
		@Override public boolean hasSlotPredictions() { return true; }
		@Override public boolean hasCarriedItem() { return true; }
		@Override public ItemStack carriedItem() { return carriedItem == null ? null : carriedItem.copy(); }
		@Override public Map<Integer, ItemStack> itemMap() {
			Map<Integer, ItemStack> copy = new LinkedHashMap<>();
			changedSlots.forEach((slot, item) -> copy.put(slot, item == null ? null : item.copy()));
			return Collections.unmodifiableMap(copy);
		}

		protected static <P extends StackClick> StreamCodec<ByteBuf, ByteBuf, P> codec(int protocol, Supplier<P> factory) {
			StreamCodec<ByteBuf, ByteBuf, ItemStack> items = ItemStack.codecFor(protocol);
			return StreamCodec.of((output, packet) -> {
				packet.writeHeader(output, protocol);
				if (packet.originalItemPayload != null) {
					output.writeBytes(packet.originalItemPayload);
					return;
				}
				VARINT.encode(output, packet.changedSlots.size());
				packet.changedSlots.forEach((slot, item) -> {
					output.writeShort(slot);
					items.encode(output, item);
				});
				items.encode(output, packet.carriedItem);
			}, input -> {
				P packet = factory.get();
				packet.readHeader(input, protocol);
				int start = input.readerIndex();
				// 1.17 uses readMap directly; 1.17.1 adds limitValue(..., 128).
				int size = count(input, protocol == 755 ? Integer.MAX_VALUE : 128);
				Map<Integer, ItemStack> slots = new LinkedHashMap<>();
				for (int i = 0; i < size; i++) slots.put((int) input.readShort(), items.decode(input));
				packet.changedSlots = Collections.unmodifiableMap(slots);
				packet.carriedItem = items.decode(input);
				packet.retainPayload(input, start);
				return packet;
			});
		}
	}

	static abstract class HashedClick extends Base implements HashedWindowClickPacket {
		protected Map<Integer, HashedItemStack> changedSlots = Collections.emptyMap();
		protected HashedItemStack carriedItem;

		@Override public Map<Integer, HashedItemStack> hashedItemMap() { return changedSlots; }
		@Override public HashedItemStack hashedCarriedItem() { return carriedItem; }

		protected static <P extends HashedClick> StreamCodec<ByteBuf, ByteBuf, P> codec(int protocol, Supplier<P> factory) {
			StreamCodec<ByteBuf, ByteBuf, HashedItemStack> items = HashedItemStack.codecFor(protocol);
			return StreamCodec.of((output, packet) -> {
				packet.writeHeader(output, protocol);
				if (packet.originalItemPayload != null) {
					output.writeBytes(packet.originalItemPayload);
					return;
				}
				VARINT.encode(output, packet.changedSlots.size());
				packet.changedSlots.forEach((slot, item) -> {
					output.writeShort(slot);
					items.encode(output, item);
				});
				items.encode(output, packet.carriedItem);
			}, input -> {
				P packet = factory.get();
				packet.readHeader(input, protocol);
				int start = input.readerIndex();
				int size = count(input, 128);
				Map<Integer, HashedItemStack> slots = new LinkedHashMap<>();
				for (int i = 0; i < size; i++) slots.put((int) input.readShort(), items.decode(input));
				packet.changedSlots = Collections.unmodifiableMap(slots);
				packet.carriedItem = items.decode(input);
				packet.retainPayload(input, start);
				return packet;
			});
		}
	}

	static final class Protocol755 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC = codec(755, Protocol755::new);
		public Protocol755() { revision = OptionalInt.empty(); }
		@Override public boolean appliesTo(int protocol) { return protocol >= 755 && protocol < 756; }
	}

	static final class Protocol756 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol756> STREAM_CODEC = codec(756, Protocol756::new);
		public Protocol756() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 756 && protocol < 757; }
	}

	static final class Protocol757 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol757> STREAM_CODEC = codec(757, Protocol757::new);
		public Protocol757() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 757 && protocol < 759; }
	}

	static final class Protocol759 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC = codec(759, Protocol759::new);
		public Protocol759() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 759 && protocol < 761; }
	}

	static final class Protocol761 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol761> STREAM_CODEC = codec(761, Protocol761::new);
		public Protocol761() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 761 && protocol < 762; }
	}

	static final class Protocol762 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol762> STREAM_CODEC = codec(762, Protocol762::new);
		public Protocol762() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 762 && protocol < 763; }
	}

	static final class Protocol763 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC = codec(763, Protocol763::new);
		public Protocol763() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 763 && protocol < 764; }
	}

	static final class Protocol764 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC = codec(764, Protocol764::new);
		public Protocol764() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 764 && protocol < 765; }
	}

	static final class Protocol765 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC = codec(765, Protocol765::new);
		public Protocol765() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 765 && protocol < 766; }
	}

	static final class Protocol766 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol766> STREAM_CODEC = codec(766, Protocol766::new);
		public Protocol766() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 766 && protocol < 767; }
	}

	static final class Protocol767 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol767> STREAM_CODEC = codec(767, Protocol767::new);
		public Protocol767() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 767 && protocol < 768; }
	}

	static final class Protocol768 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC = codec(768, Protocol768::new);
		public Protocol768() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 768 && protocol < 769; }
	}

	static final class Protocol769 extends StackClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol769> STREAM_CODEC = codec(769, Protocol769::new);
		public Protocol769() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 769 && protocol < 770; }
	}

	static final class Protocol770 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC = codec(770, Protocol770::new);
		public Protocol770() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 770 && protocol < 771; }
	}

	static final class Protocol771 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol771> STREAM_CODEC = codec(771, Protocol771::new);
		public Protocol771() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 771 && protocol < 772; }
	}

	static final class Protocol772 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol772> STREAM_CODEC = codec(772, Protocol772::new);
		public Protocol772() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 772 && protocol < 773; }
	}

	static final class Protocol773 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol773> STREAM_CODEC = codec(773, Protocol773::new);
		public Protocol773() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 773 && protocol < 774; }
	}

	static final class Protocol774 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol774> STREAM_CODEC = codec(774, Protocol774::new);
		public Protocol774() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 774 && protocol < 775; }
	}

	static final class Protocol775 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol775> STREAM_CODEC = codec(775, Protocol775::new);
		public Protocol775() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 775 && protocol < 776; }
	}

	static final class Protocol776 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol776> STREAM_CODEC = codec(776, Protocol776::new);
		public Protocol776() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 776 && protocol < 777; }
	}

	static final class Protocol777 extends HashedClick {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC = codec(777, Protocol777::new);
		public Protocol777() { }
		@Override public boolean appliesTo(int protocol) { return protocol >= 777 && protocol < 778; }
	}
}
