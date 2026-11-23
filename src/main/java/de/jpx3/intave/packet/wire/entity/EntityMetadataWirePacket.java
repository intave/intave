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

package de.jpx3.intave.packet.wire.entity;

import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.packet.contract.mutable.MutableEntityMetadataPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.packet.wire.entity.MetadataLayout.ValueType;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;

public final class EntityMetadataWirePacket {
	private EntityMetadataWirePacket() {}

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

	static abstract class Base extends AbstractMutable implements MutableEntityMetadataPacket {
		private int entityId;
		private final MetadataLayout layout;
		// The client applies a list, including repeated indexes, in wire order.
		// Preserve every value's encoding even when an earlier update is superseded.
		private final List<Entry> entries = new ArrayList<>();

		Base(MetadataLayout layout) {
			this.layout = layout;
		}

		@Override
		public final boolean appliesTo(int protocol) {
			return layout.appliesTo(protocol);
		}

		@Override
		public int entityId() {
			return entityId;
		}

		@Override
		public void setEntityId(int entityId) {
			markMutated();
			this.entityId = entityId;
		}

		private Object value(int index, ValueType type) {
			int last = lastEntryIndex(index);
			if (last < 0) return null;
			Entry entry = entries.get(last);
			return layout.valueType(entry.serializer) == type ? entry.value : null;
		}

		private int lastEntryIndex(int index) {
			for (int i = entries.size() - 1; i >= 0; i--) {
				if (entries.get(i).index == index) return i;
			}
			return -1;
		}

		@Override
		public Byte entityFlags() {
			return (Byte) value(0, ValueType.BYTE);
		}

		@Override
		public Byte livingFlags() {
			return (Byte) value(layout.livingFlagsIndex(), ValueType.BYTE);
		}

		@Override
		public Float health() {
			return (Float) value(layout.healthIndex(), ValueType.FLOAT);
		}

		@Override
		public Pose pose() {
			Integer id = (Integer) value(layout.poseIndex(), ValueType.POSE);
			if (id == null) return null;
			// Pose's simulation enum order differs from Minecraft's wire order.
			switch (id) {
				case 0: return Pose.STANDING;
				case 1: return Pose.FALL_FLYING;
				case 2: return Pose.SLEEPING;
				case 3: return Pose.SWIMMING;
				case 5: return Pose.CROUCHING;
				default: return null;
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public Optional<BlockPosition> bedPosition() {
			Optional<BlockPosition> position = (Optional<BlockPosition>) value(layout.bedIndex(), ValueType.OPTIONAL_POSITION);
			return position == null ? null : position.map(p -> new BlockPosition(p.x(), p.y(), p.z()));
		}

		@Override
		public OptionalInt attachedEntityId() {
			int index = layout.attachmentIndex();
			Integer id = (Integer) value(index, layout.attachmentType());
			if (id == null) return null;
			return layout.decodeAttachment(id);
		}

		@Override
		public Boolean baby(EntityTypeData entityType) {
			int index = layout.babyIndex(entityType);
			if (index < 0) return null;
			if (layout.babyType(entityType) == ValueType.BYTE) {
				Byte flags = (Byte) value(index, ValueType.BYTE);
				if (flags == null) return null;
				if (entityType.isArmorStand()) return (flags & 1) != 0;
				return isZombie(entityType) ? flags == 1 : flags < 0;
			}
			return (Boolean) value(index, ValueType.BOOLEAN);
		}

		@Override
		public void setEntityFlags(byte flags) {
			set(0, ValueType.BYTE, out -> out.writeByte(flags));
		}

		@Override
		public void setLivingFlags(byte flags) {
			set(layout.livingFlagsIndex(), ValueType.BYTE, out -> out.writeByte(flags));
		}

		@Override
		public void setHealth(float health) {
			set(layout.healthIndex(), ValueType.FLOAT, out -> out.writeFloat(health));
		}

		@Override
		public void setPose(Pose pose) {
			Objects.requireNonNull(pose);
			int id;
			switch (pose) {
				case STANDING: id = 0; break;
				case FALL_FLYING: id = 1; break;
				case SLEEPING: id = 2; break;
				case SWIMMING: id = 3; break;
				case CROUCHING: id = 5; break;
				default: throw new IllegalArgumentException("Unsupported pose " + pose);
			}
			set(layout.poseIndex(), ValueType.POSE, out -> VARINT.encode(out, id));
		}

		@Override
		public void setBedPosition(Optional<BlockPosition> position) {
			Objects.requireNonNull(position);
			set(layout.bedIndex(), ValueType.OPTIONAL_POSITION, out -> {
				out.writeBoolean(position.isPresent());
				position.ifPresent(p -> BlockPosition.WIRE_STREAM_CODEC.encode(out, p));
			});
		}

		@Override
		public void setAttachedEntityId(OptionalInt entityId) {
			Objects.requireNonNull(entityId);
			int encoded = layout.encodeAttachment(entityId);
			set(layout.attachmentIndex(), layout.attachmentType(), out -> VARINT.encode(out, encoded));
		}

		@Override
		public void setBaby(EntityTypeData entityType, boolean baby) {
			int index = layout.babyIndex(entityType);
			if (entityType.isArmorStand()) {
				Byte previous = (Byte) value(index, ValueType.BYTE);
				int flags = previous == null ? 0 : previous;
				set(index, ValueType.BYTE, out -> out.writeByte(baby ? flags | 1 : flags & ~1));
			} else if (layout.babyType(entityType) == ValueType.BYTE) {
				int age = baby ? (isZombie(entityType) ? 1 : -1) : 0;
				set(index, ValueType.BYTE, out -> out.writeByte(age));
			} else {
				set(index, ValueType.BOOLEAN, out -> out.writeBoolean(baby));
			}
		}

		private boolean isZombie(EntityTypeData entityType) {
			return entityType.name().toLowerCase(Locale.ROOT).contains("zombie");
		}

		private void set(int index, ValueType type, Consumer<ByteBuf> write) {
			int serializer = layout.serializerId(type);
			if (index < 0 || serializer < 0) {
				throw new UnsupportedOperationException("Metadata field unavailable in protocol " + layout.protocol());
			}
			ByteBuf buffer = Unpooled.buffer();
			try {
				write.accept(buffer);
				Object value = layout.readValue(buffer, type);
				byte[] bytes = new byte[buffer.writerIndex()];
				buffer.getBytes(0, bytes);
				Entry replacement = new Entry(index, serializer, value, bytes);
				int last = lastEntryIndex(index);
				// Replace the effective update without reordering or discarding earlier entries.
				if (last < 0) entries.add(replacement);
				else entries.set(last, replacement);
				markMutated();
			} finally {
				buffer.release();
			}
		}

		final void write(ByteBuf out) {
			VARINT.encode(out, entityId);
			for (Entry entry : entries) {
				layout.writeHeader(out, entry.index, entry.serializer);
				out.writeBytes(entry.bytes);
			}
			out.writeByte(layout.terminator());
		}

		final void read(ByteBuf in) {
			entityId = VARINT.decode(in);
			int header;
			while ((header = in.readUnsignedByte()) != layout.terminator()) {
				int index = layout.fieldIndex(header);
				int serializer = layout.readSerializerId(in, header);
				entries.add(Entry.read(in, layout, index, serializer));
			}
			if (in.isReadable()) {
				throw new IllegalArgumentException("Trailing entity metadata bytes");
			}
		}

		protected static <P extends Base> StreamCodec<ByteBuf, ByteBuf, P> codec(Supplier<P> factory) {
			return StreamCodec.of((out, packet) -> packet.write(out), in -> {
				P packet = factory.get();
				packet.read(in);
				return packet;
			});
		}
	}

	private static final class Entry {
		final int index;
		final int serializer;
		final Object value;
		final byte[] bytes;

		private Entry(int index, int serializer, Object value, byte[] bytes) {
			this.index = index;
			this.serializer = serializer;
			this.value = value;
			this.bytes = bytes;
		}

		static Entry read(ByteBuf in, MetadataLayout layout, int index, int serializer) {
			int start = in.readerIndex();
			Object value = layout.readValue(in, layout.valueType(serializer));
			byte[] bytes = new byte[in.readerIndex() - start];
			in.getBytes(start, bytes);
			return new Entry(index, serializer, value, bytes);
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = codec(Protocol47::new);

		Protocol47() {
			super(MetadataLayout.V1_8);
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC = codec(Protocol107::new);

		Protocol107() {
			super(MetadataLayout.V1_9);
		}
	}

	static final class Protocol210 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol210> STREAM_CODEC = codec(Protocol210::new);

		Protocol210() {
			super(MetadataLayout.V1_10);
		}
	}

	static final class Protocol315 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol315> STREAM_CODEC = codec(Protocol315::new);

		Protocol315() {
			super(MetadataLayout.V1_11);
		}
	}

	static final class Protocol316 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol316> STREAM_CODEC = codec(Protocol316::new);

		Protocol316() {
			super(MetadataLayout.V1_11_1);
		}
	}

	static final class Protocol335 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol335> STREAM_CODEC = codec(Protocol335::new);

		Protocol335() {
			super(MetadataLayout.V1_12);
		}
	}

	static final class Protocol393 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol393> STREAM_CODEC = codec(Protocol393::new);

		Protocol393() {
			super(MetadataLayout.V1_13);
		}
	}

	static final class Protocol401 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol401> STREAM_CODEC = codec(Protocol401::new);

		Protocol401() {
			super(MetadataLayout.V1_13_1);
		}
	}

	static final class Protocol404 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol404> STREAM_CODEC = codec(Protocol404::new);

		Protocol404() {
			super(MetadataLayout.V1_13_2);
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC = codec(Protocol477::new);

		Protocol477() {
			super(MetadataLayout.V1_14);
		}
	}

	static final class Protocol573 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol573> STREAM_CODEC = codec(Protocol573::new);

		Protocol573() {
			super(MetadataLayout.V1_15);
		}
	}

	static final class Protocol735 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC = codec(Protocol735::new);

		Protocol735() {
			super(MetadataLayout.V1_16);
		}
	}

	static final class Protocol751 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol751> STREAM_CODEC = codec(Protocol751::new);

		Protocol751() {
			super(MetadataLayout.V1_16_2);
		}
	}

	static final class Protocol755 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC = codec(Protocol755::new);

		Protocol755() {
			super(MetadataLayout.V1_17);
		}
	}

	static final class Protocol756 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol756> STREAM_CODEC = codec(Protocol756::new);

		Protocol756() {
			super(MetadataLayout.V1_17_1);
		}
	}

	static final class Protocol757 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol757> STREAM_CODEC = codec(Protocol757::new);

		Protocol757() {
			super(MetadataLayout.V1_18);
		}
	}

	static final class Protocol759 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC = codec(Protocol759::new);

		Protocol759() {
			super(MetadataLayout.V1_19);
		}
	}

	static final class Protocol761 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol761> STREAM_CODEC = codec(Protocol761::new);

		Protocol761() {
			super(MetadataLayout.V1_19_3);
		}
	}

	static final class Protocol762 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol762> STREAM_CODEC = codec(Protocol762::new);

		Protocol762() {
			super(MetadataLayout.V1_19_4);
		}
	}

	static final class Protocol763 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC = codec(Protocol763::new);

		Protocol763() {
			super(MetadataLayout.V1_20);
		}
	}

	static final class Protocol764 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC = codec(Protocol764::new);

		Protocol764() {
			super(MetadataLayout.V1_20_2);
		}
	}

	static final class Protocol765 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC = codec(Protocol765::new);

		Protocol765() {
			super(MetadataLayout.V1_20_3);
		}
	}

	static final class Protocol766 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol766> STREAM_CODEC = codec(Protocol766::new);

		Protocol766() {
			super(MetadataLayout.V1_20_5);
		}
	}

	static final class Protocol767 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol767> STREAM_CODEC = codec(Protocol767::new);

		Protocol767() {
			super(MetadataLayout.V1_21);
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC = codec(Protocol768::new);

		Protocol768() {
			super(MetadataLayout.V1_21_2);
		}
	}

	static final class Protocol769 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol769> STREAM_CODEC = codec(Protocol769::new);

		Protocol769() {
			super(MetadataLayout.V1_21_4);
		}
	}

	static final class Protocol770 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC = codec(Protocol770::new);

		Protocol770() {
			super(MetadataLayout.V1_21_5);
		}
	}

	static final class Protocol771 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol771> STREAM_CODEC = codec(Protocol771::new);

		Protocol771() {
			super(MetadataLayout.V1_21_6);
		}
	}

	static final class Protocol772 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol772> STREAM_CODEC = codec(Protocol772::new);

		Protocol772() {
			super(MetadataLayout.V1_21_7);
		}
	}

	static final class Protocol773 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol773> STREAM_CODEC = codec(Protocol773::new);

		Protocol773() {
			super(MetadataLayout.V1_21_9);
		}
	}

	static final class Protocol774 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol774> STREAM_CODEC = codec(Protocol774::new);

		Protocol774() {
			super(MetadataLayout.V1_21_11);
		}
	}

	static final class Protocol775 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol775> STREAM_CODEC = codec(Protocol775::new);

		Protocol775() {
			super(MetadataLayout.V26_1);
		}
	}

	static final class Protocol776 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol776> STREAM_CODEC = codec(Protocol776::new);

		Protocol776() {
			super(MetadataLayout.V26_2);
		}
	}

	static final class Protocol777 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC = codec(Protocol777::new);

		Protocol777() {
			super(MetadataLayout.V26_3);
		}
	}
}

