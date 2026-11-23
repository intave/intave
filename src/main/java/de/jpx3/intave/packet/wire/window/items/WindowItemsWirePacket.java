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

package de.jpx3.intave.packet.wire.window.items;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.contract.immutable.WindowBulkItemsPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Supplier;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;

public final class WindowItemsWirePacket {
	private WindowItemsWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class, Protocol107.class, Protocol210.class, Protocol315.class,
			Protocol316.class, Protocol335.class, Protocol393.class, Protocol401.class,
			Protocol404.class, Protocol477.class, Protocol573.class, Protocol735.class,
			Protocol751.class, Protocol755.class, Protocol756.class, Protocol757.class,
			Protocol759.class, Protocol761.class, Protocol762.class, Protocol763.class,
			Protocol764.class, Protocol765.class, Protocol766.class, Protocol767.class,
			Protocol768.class, Protocol769.class, Protocol770.class, Protocol771.class,
			Protocol772.class, Protocol773.class, Protocol774.class, Protocol775.class,
			Protocol776.class, Protocol777.class
		));
	}

	static abstract class Base extends AbstractMutable implements WindowBulkItemsPacket {
		protected int containerId;
		protected int stateId;
		protected List<ItemStack> slots = Collections.emptyList();
		protected ItemStack carriedItem;
		private final boolean hasStateId;

		Base(int protocol) {
			hasStateId = protocol >= 756;
		}

		@Override
		public int containerId() {
			return containerId;
		}

		@Override
		public OptionalInt revision() {
			return hasStateId ? OptionalInt.of(stateId) : OptionalInt.empty();
		}

		@Override
		public List<ItemStack> items() {
			List<ItemStack> snapshot = new ArrayList<>(slots.size());
			for (ItemStack stack : slots) {
				snapshot.add(stack == null ? null : stack.copy());
			}
			return Collections.unmodifiableList(snapshot);
		}

		@Override
		public Map<Integer, ItemStack> itemMap() {
			Map<Integer, ItemStack> result = new LinkedHashMap<>();
			for (int i = 0; i < slots.size(); i++) {
				ItemStack stack = slots.get(i);
				result.put(i, stack == null ? null : stack.copy());
			}
			return Collections.unmodifiableMap(result);
		}

		@Override
		public boolean hasCarriedItem() {
			return hasStateId;
		}

		@Override
		public ItemStack carriedItem() {
			return carriedItem == null ? null : carriedItem.copy();
		}

		protected static <P extends Base> StreamCodec<ByteBuf, ByteBuf, P> codec(int protocol, Supplier<P> factory) {
			StreamCodec<ByteBuf, ByteBuf, ItemStack> items = ItemStack.codecFor(protocol);
			return StreamCodec.of((output, packet) -> {
				if (protocol < 768) {
					output.writeByte(packet.containerId);
				} else {
					VARINT.encode(output, packet.containerId);
				}
				if (protocol < 756) {
					output.writeShort(packet.slots.size());
				} else {
					VARINT.encode(output, packet.stateId);
					VARINT.encode(output, packet.slots.size());
				}
				for (ItemStack slot : packet.slots) {
					items.encode(output, slot);
				}
				if (packet.hasCarriedItem()) {
					items.encode(output, packet.carriedItem);
				}
			}, input -> {
				P packet = factory.get();
				packet.containerId = protocol < 768 ? input.readUnsignedByte() : VARINT.decode(input);
				if (protocol >= 756) {
					packet.stateId = VARINT.decode(input);
				}
				int size = protocol < 756 ? input.readShort() : VARINT.decode(input);
				if (size < 0 || size > input.readableBytes()) {
					throw new IllegalArgumentException("Invalid window slot count " + size);
				}
				packet.slots = new ArrayList<>(Math.min(size, 65536));
				for (int slot = 0; slot < size; slot++) {
					packet.slots.add(items.decode(input));
				}
				if (packet.hasCarriedItem()) {
					packet.carriedItem = items.decode(input);
				}
				if (input.isReadable()) {
					throw new IllegalArgumentException("Trailing bytes after window contents");
				}
				return packet;
			});
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = codec(47, Protocol47::new);

		Protocol47() {
			super(47);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 47 && protocol < 107;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC = codec(107, Protocol107::new);

		Protocol107() {
			super(107);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 107 && protocol < 210;
		}
	}

	static final class Protocol210 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol210> STREAM_CODEC = codec(210, Protocol210::new);

		Protocol210() {
			super(210);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 210 && protocol < 315;
		}
	}

	static final class Protocol315 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol315> STREAM_CODEC = codec(315, Protocol315::new);

		Protocol315() {
			super(315);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 315;
		}
	}

	static final class Protocol316 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol316> STREAM_CODEC = codec(316, Protocol316::new);

		Protocol316() {
			super(316);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 316 && protocol < 335;
		}
	}

	static final class Protocol335 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol335> STREAM_CODEC = codec(335, Protocol335::new);

		Protocol335() {
			super(335);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 335 && protocol < 393;
		}
	}

	static final class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC = codec(393, Protocol393::new);

		Protocol393() {
			super(393);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 393 && protocol < 401;
		}
	}

	static final class Protocol401 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol401> STREAM_CODEC = codec(401, Protocol401::new);

		Protocol401() {
			super(401);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 401 && protocol < 404;
		}
	}

	static final class Protocol404 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol404> STREAM_CODEC = codec(404, Protocol404::new);

		Protocol404() {
			super(404);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 404 && protocol < 477;
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC = codec(477, Protocol477::new);

		Protocol477() {
			super(477);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 477 && protocol < 573;
		}
	}

	static final class Protocol573 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol573> STREAM_CODEC = codec(573, Protocol573::new);

		Protocol573() {
			super(573);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 573 && protocol < 735;
		}
	}

	static final class Protocol735 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC = codec(735, Protocol735::new);

		Protocol735() {
			super(735);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 735 && protocol < 751;
		}
	}

	static final class Protocol751 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol751> STREAM_CODEC = codec(751, Protocol751::new);

		Protocol751() {
			super(751);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 751 && protocol < 755;
		}
	}

	static final class Protocol755 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC = codec(755, Protocol755::new);

		Protocol755() {
			super(755);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 755;
		}
	}

	static final class Protocol756 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol756> STREAM_CODEC = codec(756, Protocol756::new);

		Protocol756() {
			super(756);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 756;
		}
	}

	static final class Protocol757 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol757> STREAM_CODEC = codec(757, Protocol757::new);

		Protocol757() {
			super(757);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 757 && protocol < 759;
		}
	}

	static final class Protocol759 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC = codec(759, Protocol759::new);

		Protocol759() {
			super(759);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol >= 759 && protocol < 761;
		}
	}

	static final class Protocol761 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol761> STREAM_CODEC = codec(761, Protocol761::new);

		Protocol761() {
			super(761);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 761;
		}
	}

	static final class Protocol762 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol762> STREAM_CODEC = codec(762, Protocol762::new);

		Protocol762() {
			super(762);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 762;
		}
	}

	static final class Protocol763 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC = codec(763, Protocol763::new);

		Protocol763() {
			super(763);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 763;
		}
	}

	static final class Protocol764 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC = codec(764, Protocol764::new);

		Protocol764() {
			super(764);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 764;
		}
	}

	static final class Protocol765 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC = codec(765, Protocol765::new);

		Protocol765() {
			super(765);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 765;
		}
	}

	static final class Protocol766 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol766> STREAM_CODEC = codec(766, Protocol766::new);

		Protocol766() {
			super(766);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 766;
		}
	}

	static final class Protocol767 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol767> STREAM_CODEC = codec(767, Protocol767::new);

		Protocol767() {
			super(767);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 767;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC = codec(768, Protocol768::new);

		Protocol768() {
			super(768);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 768;
		}
	}

	static final class Protocol769 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol769> STREAM_CODEC = codec(769, Protocol769::new);

		Protocol769() {
			super(769);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 769;
		}
	}

	static final class Protocol770 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC = codec(770, Protocol770::new);

		Protocol770() {
			super(770);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 770;
		}
	}

	static final class Protocol771 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol771> STREAM_CODEC = codec(771, Protocol771::new);

		Protocol771() {
			super(771);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 771;
		}
	}

	static final class Protocol772 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol772> STREAM_CODEC = codec(772, Protocol772::new);

		Protocol772() {
			super(772);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 772;
		}
	}

	static final class Protocol773 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol773> STREAM_CODEC = codec(773, Protocol773::new);

		Protocol773() {
			super(773);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 773;
		}
	}

	static final class Protocol774 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol774> STREAM_CODEC = codec(774, Protocol774::new);

		Protocol774() {
			super(774);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 774;
		}
	}

	static final class Protocol775 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol775> STREAM_CODEC = codec(775, Protocol775::new);

		Protocol775() {
			super(775);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 775;
		}
	}

	static final class Protocol776 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol776> STREAM_CODEC = codec(776, Protocol776::new);

		Protocol776() {
			super(776);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 776;
		}
	}

	static final class Protocol777 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC = codec(777, Protocol777::new);

		Protocol777() {
			super(777);
		}

		@Override
		public boolean appliesTo(int protocol) {
			return protocol == 777;
		}
	}
}
