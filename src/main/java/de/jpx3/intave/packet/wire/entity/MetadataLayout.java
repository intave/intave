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

import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;

import java.util.*;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

/** The metadata fields and serializer registry used by one client protocol range. */
enum MetadataLayout {
	V1_8(47, 106),
	V1_9(107, 209),
	V1_10(210, 314),
	V1_11(315, 315),
	V1_11_1(316, 334),
	V1_12(335, 392),
	V1_13(393, 400),
	V1_13_1(401, 403),
	V1_13_2(404, 476),
	V1_14(477, 572),
	V1_15(573, 734),
	V1_16(735, 750),
	V1_16_2(751, 754),
	V1_17(755, 755),
	V1_17_1(756, 756),
	V1_18(757, 758),
	V1_19(759, 760),
	V1_19_3(761, 761),
	V1_19_4(762, 762),
	V1_20(763, 763),
	V1_20_2(764, 764),
	V1_20_3(765, 765),
	V1_20_5(766, 766),
	V1_21(767, 767),
	V1_21_2(768, 768),
	V1_21_4(769, 769),
	V1_21_5(770, 770),
	V1_21_6(771, 771),
	V1_21_7(772, 772),
	V1_21_9(773, 773),
	V1_21_11(774, 774),
	V26_1(775, 775),
	V26_2(776, 776),
	V26_3(777, Integer.MAX_VALUE);

	private final int protocol;
	private final int lastProtocol;
	private final List<Serializer> serializers;
	private final MetadataParticleCodec particles;

	MetadataLayout(int protocol, int lastProtocol) {
		this.protocol = protocol;
		this.lastProtocol = lastProtocol;
		this.serializers = Serializer.forProtocol(protocol);
		this.particles = new MetadataParticleCodec(protocol);
	}

	int protocol() {
		return protocol;
	}

	boolean appliesTo(int protocol) {
		return protocol >= this.protocol && protocol <= lastProtocol;
	}

	boolean usesPackedHeaders() {
		return protocol < 107;
	}

	int terminator() {
		return usesPackedHeaders() ? 127 : 255;
	}

	int fieldIndex(int header) {
		return usesPackedHeaders() ? header & 31 : header;
	}

	int readSerializerId(ByteBuf in, int header) {
		return usesPackedHeaders() ? header >> 5 : VARINT.decode(in);
	}

	void writeHeader(ByteBuf out, int index, int serializer) {
		if (usesPackedHeaders()) {
			out.writeByte(serializer << 5 | index);
		} else {
			out.writeByte(index);
			VARINT.encode(out, serializer);
		}
	}

	int livingFlagsIndex() {
		if (protocol < 107) return -1;
		if (protocol < 210) return 5;
		if (protocol < 477) return 6;
		if (protocol < 755) return 7;
		return 8;
	}

	int healthIndex() {
		return usesPackedHeaders() ? 6 : livingFlagsIndex() + 1;
	}

	int poseIndex() {
		return protocol < 477 ? -1 : 6;
	}

	int bedIndex() {
		if (protocol < 477) return -1;
		if (protocol < 573) return 12;
		if (protocol < 755) return 13;
		return 14;
	}

	int attachmentIndex() {
		if (protocol < 316) return -1;
		if (protocol < 477) return 7;
		if (protocol < 755) return 8;
		return 9;
	}

	ValueType attachmentType() {
		return protocol < 477 ? ValueType.VAR_INT : ValueType.OPTIONAL_INT;
	}

	OptionalInt decodeAttachment(int encoded) {
		if (encoded == 0) return OptionalInt.empty();
		return OptionalInt.of(protocol < 477 ? encoded : encoded - 1);
	}

	int encodeAttachment(OptionalInt entityId) {
		if (!entityId.isPresent()) return 0;
		int id = entityId.getAsInt();
		if (id < 0 || id == Integer.MAX_VALUE || (protocol < 477 && id == 0)) {
			throw new IllegalArgumentException("Unrepresentable attached entity ID");
		}
		return protocol < 477 ? id : id + 1;
	}

	ValueType babyType(EntityTypeData type) {
		return type.isArmorStand() || protocol < 107 ? ValueType.BYTE : ValueType.BOOLEAN;
	}

	int babyIndex(EntityTypeData type) {
		if (!type.isLivingEntity()) return -1;
		// AbstractPiglin inserted its immunity flag before Piglin's baby flag in 1.16.2.
		if (type.name().equalsIgnoreCase("Piglin") && protocol >= 751) {
			return protocol < 755 ? 16 : 17;
		}
		if (type.isArmorStand()) {
			if (protocol < 210) return 10;
			if (protocol < 477) return 11;
			if (protocol < 573) return 13;
			if (protocol < 755) return 14;
			return 15;
		}
		if (protocol < 107) return 12;
		if (protocol < 210) return 11;
		if (protocol < 477) return 12;
		if (protocol < 573) return 14;
		if (protocol < 755) return 15;
		return 16;
	}

	ValueType valueType(int serializer) {
		if (serializer < 0 || serializer >= serializers.size()) {
			throw new IllegalArgumentException("Unknown metadata serializer " + serializer + " in " + protocol);
		}
		Serializer entry = serializers.get(serializer);
		// 1.21 added inline painting variants alongside registry references.
		if (entry == Serializer.PAINTING_VARIANT && protocol >= 767) return ValueType.PAINTING;
		return entry.type;
	}

	int serializerId(ValueType type) {
		for (int id = 0; id < serializers.size(); id++) {
			if (valueType(id) == type) return id;
		}
		return -1;
	}

	/**
	 * Reads fields exposed by the packet contract and consumes the other fields.
	 * The packet retains every original value's bytes for lossless re-encoding.
	 */
	Object readValue(ByteBuf in, ValueType type) {
		switch (type) {
			case BYTE: return in.readByte();
			case FLOAT: return in.readFloat();
			case BOOLEAN: return in.readBoolean();
			case VAR_INT:
			case OPTIONAL_INT:
			case POSE: return VARINT.decode(in);
			case OPTIONAL_POSITION: return readOptionalPosition(in);
			case SHORT: in.skipBytes(2); break;
			case INT: in.skipBytes(4); break;
			case STRING: VARINT_STRING.decode(in); break;
			case VAR_LONG: VARLONG.decode(in); break;
			case ITEM: ItemStack.codecFor(protocol).decode(in); break;
			case LEGACY_POSITION:
			case ROTATIONS:
			case VECTOR: in.skipBytes(12); break;
			case QUATERNION: in.skipBytes(16); break;
			case POSITION: in.skipBytes(8); break;
			case OPTIONAL_UUID:
				if (in.readBoolean()) in.skipBytes(16);
				break;
			case COMPONENT: readComponent(in); break;
			case OPTIONAL_COMPONENT: readOptionalComponent(in); break;
			case NBT:
				(protocol < 764 ? NAMED_COMPOUND_TAG : COMPOUND_TAG).decode(in);
				break;
			case PARTICLE: particles.read(in); break;
			case PARTICLES: readParticles(in); break;
			case VILLAGER:
				VARINT.decode(in); // Type.
				VARINT.decode(in); // Profession.
				VARINT.decode(in); // Level.
				break;
			case GLOBAL_POSITION: readOptionalGlobalPosition(in); break;
			case PAINTING: readPainting(in); break;
			case PROFILE: readProfile(in); break;
			default: throw new IllegalArgumentException("Unsupported metadata kind " + type);
		}
		return null;
	}

	private Optional<BlockPosition> readOptionalPosition(ByteBuf in) {
		if (!in.readBoolean()) return Optional.empty();
		return Optional.of((protocol < 477 ? BlockPosition.LEGACY_STREAM_CODEC
			: BlockPosition.WIRE_STREAM_CODEC).decode(in));
	}

	private void readComponent(ByteBuf in) {
		if (protocol < 765) stringCodec(262144).decode(in);
		else TRUSTED_TAG.decode(in);
	}

	private void readOptionalComponent(ByteBuf in) {
		if (in.readBoolean()) readComponent(in);
	}

	private int readCount(ByteBuf in) {
		int count = VARINT.decode(in);
		if (count < 0 || count > in.readableBytes()) {
			throw new IllegalArgumentException("Invalid metadata list length " + count);
		}
		return count;
	}

	private void readParticles(ByteBuf in) {
		int count = readCount(in);
		for (int i = 0; i < count; i++) particles.read(in);
	}

	private void readOptionalGlobalPosition(ByteBuf in) {
		if (in.readBoolean()) {
			VARINT_STRING.decode(in); // Dimension key.
			in.skipBytes(8); // Block position.
		}
	}

	private void readPainting(ByteBuf in) {
		if (VARINT.decode(in) != 0) return; // Registry reference; zero introduces an inline variant.
		VARINT.decode(in); // Width.
		VARINT.decode(in); // Height.
		VARINT_STRING.decode(in); // Asset key.
		if (protocol >= 768) {
			readOptionalComponent(in); // Title.
			readOptionalComponent(in); // Author.
		}
	}

	private void readProfile(ByteBuf in) {
		boolean complete = in.readBoolean();
		if (complete) {
			in.skipBytes(16); // UUID.
			stringCodec(16).decode(in); // Player name.
		} else {
			if (in.readBoolean()) stringCodec(16).decode(in);
			if (in.readBoolean()) in.skipBytes(16);
		}
		readProfileProperties(in);
		readSkinPatch(in);
	}

	private void readProfileProperties(ByteBuf in) {
		int count = readCount(in);
		for (int i = 0; i < count; i++) {
			stringCodec(64).decode(in); // Name.
			VARINT_STRING.decode(in); // Value.
			if (in.readBoolean()) stringCodec(1024).decode(in); // Signature.
		}
	}

	private void readSkinPatch(ByteBuf in) {
		// Optional body, cape and elytra textures, followed by an optional player model.
		for (int i = 0; i < 3; i++) {
			if (in.readBoolean()) VARINT_STRING.decode(in);
		}
		if (in.readBoolean()) in.skipBytes(1);
	}

	enum ValueType {
		BYTE, SHORT, INT, FLOAT, STRING, ITEM, LEGACY_POSITION, ROTATIONS,
		VAR_INT, VAR_LONG, COMPONENT, OPTIONAL_COMPONENT, BOOLEAN, POSITION,
		OPTIONAL_POSITION, OPTIONAL_UUID, NBT, PARTICLE, PARTICLES, VILLAGER,
		OPTIONAL_INT, POSE, GLOBAL_POSITION, VECTOR, QUATERNION, PROFILE, PAINTING
	}

	/** Names mirror EntityDataSerializers; distinct registry entries may share a wire value type. */
	private enum Serializer {
		BYTE(ValueType.BYTE),
		SHORT(ValueType.SHORT),
		FIXED_INT(ValueType.INT),
		INT(ValueType.VAR_INT),
		LONG(ValueType.VAR_LONG),
		FLOAT(ValueType.FLOAT),
		STRING(ValueType.STRING),
		COMPONENT(ValueType.COMPONENT),
		OPTIONAL_COMPONENT(ValueType.OPTIONAL_COMPONENT),
		ITEM_STACK(ValueType.ITEM),
		BOOLEAN(ValueType.BOOLEAN),
		ROTATIONS(ValueType.ROTATIONS),
		LEGACY_BLOCK_POS(ValueType.LEGACY_POSITION),
		BLOCK_POS(ValueType.POSITION),
		OPTIONAL_BLOCK_POS(ValueType.OPTIONAL_POSITION),
		DIRECTION(ValueType.VAR_INT),
		OPTIONAL_UUID(ValueType.OPTIONAL_UUID),
		BLOCK_STATE(ValueType.VAR_INT),
		OPTIONAL_BLOCK_STATE(ValueType.VAR_INT),
		COMPOUND_TAG(ValueType.NBT),
		PARTICLE(ValueType.PARTICLE),
		PARTICLES(ValueType.PARTICLES),
		VILLAGER_DATA(ValueType.VILLAGER),
		OPTIONAL_UNSIGNED_INT(ValueType.OPTIONAL_INT),
		POSE(ValueType.POSE),
		CAT_VARIANT(ValueType.VAR_INT),
		CAT_SOUND_VARIANT(ValueType.VAR_INT),
		COW_VARIANT(ValueType.VAR_INT),
		COW_SOUND_VARIANT(ValueType.VAR_INT),
		WOLF_VARIANT(ValueType.VAR_INT),
		WOLF_SOUND_VARIANT(ValueType.VAR_INT),
		FROG_VARIANT(ValueType.VAR_INT),
		PIG_VARIANT(ValueType.VAR_INT),
		PIG_SOUND_VARIANT(ValueType.VAR_INT),
		CHICKEN_VARIANT(ValueType.VAR_INT),
		CHICKEN_SOUND_VARIANT(ValueType.VAR_INT),
		ZOMBIE_NAUTILUS_VARIANT(ValueType.VAR_INT),
		OPTIONAL_GLOBAL_POS(ValueType.GLOBAL_POSITION),
		PAINTING_VARIANT(ValueType.VAR_INT),
		SNIFFER_STATE(ValueType.VAR_INT),
		ARMADILLO_STATE(ValueType.VAR_INT),
		COPPER_GOLEM_STATE(ValueType.VAR_INT),
		WEATHERING_COPPER_STATE(ValueType.VAR_INT),
		VECTOR3(ValueType.VECTOR),
		QUATERNION(ValueType.QUATERNION),
		RESOLVABLE_PROFILE(ValueType.PROFILE),
		HUMANOID_ARM(ValueType.VAR_INT),
		DYE_COLOR(ValueType.VAR_INT);

		final ValueType type;

		Serializer(ValueType type) {
			this.type = type;
		}

		static List<Serializer> forProtocol(int protocol) {
			if (protocol < 107) {
				return Arrays.asList(BYTE, SHORT, FIXED_INT, FLOAT, STRING, ITEM_STACK, LEGACY_BLOCK_POS, ROTATIONS);
			}
			List<Serializer> types = new ArrayList<>(Arrays.asList(
				BYTE, INT, FLOAT, STRING, COMPONENT, ITEM_STACK, BOOLEAN, ROTATIONS,
				BLOCK_POS, OPTIONAL_BLOCK_POS, DIRECTION, OPTIONAL_UUID, BLOCK_STATE
			));
			if (protocol >= 335) types.add(COMPOUND_TAG);
			if (protocol >= 393) {
				insertBefore(types, ITEM_STACK, OPTIONAL_COMPONENT);
				types.add(PARTICLE);
			}
			if (protocol >= 477) types.addAll(Arrays.asList(VILLAGER_DATA, OPTIONAL_UNSIGNED_INT, POSE));
			if (protocol >= 759) {
				types.addAll(Arrays.asList(CAT_VARIANT, FROG_VARIANT, OPTIONAL_GLOBAL_POS, PAINTING_VARIANT));
			}
			if (protocol >= 761) insertBefore(types, FLOAT, LONG);
			if (protocol >= 762) {
				insertBefore(types, COMPOUND_TAG, OPTIONAL_BLOCK_STATE);
				types.addAll(Arrays.asList(SNIFFER_STATE, VECTOR3, QUATERNION));
			}
			if (protocol >= 766) {
				insertBefore(types, VILLAGER_DATA, PARTICLES);
				insertBefore(types, FROG_VARIANT, WOLF_VARIANT);
				insertBefore(types, VECTOR3, ARMADILLO_STATE);
			}
			if (protocol >= 770) {
				insertBefore(types, WOLF_VARIANT, COW_VARIANT);
				insertBefore(types, FROG_VARIANT, WOLF_SOUND_VARIANT);
				insertBefore(types, OPTIONAL_GLOBAL_POS, PIG_VARIANT, CHICKEN_VARIANT);
			}
			if (protocol >= 773) {
				types.remove(COMPOUND_TAG);
				insertBefore(types, VECTOR3, COPPER_GOLEM_STATE, WEATHERING_COPPER_STATE);
				types.add(RESOLVABLE_PROFILE);
			}
			if (protocol >= 774) {
				insertBefore(types, OPTIONAL_GLOBAL_POS, ZOMBIE_NAUTILUS_VARIANT);
				types.add(HUMANOID_ARM);
			}
			if (protocol >= 775) {
				insertBefore(types, COW_VARIANT, CAT_SOUND_VARIANT);
				insertBefore(types, WOLF_VARIANT, COW_SOUND_VARIANT);
				insertBefore(types, CHICKEN_VARIANT, PIG_SOUND_VARIANT);
				insertBefore(types, ZOMBIE_NAUTILUS_VARIANT, CHICKEN_SOUND_VARIANT);
			}
			if (protocol >= 777) types.add(DYE_COLOR);
			return Collections.unmodifiableList(types);
		}

		private static void insertBefore(List<Serializer> types, Serializer next, Serializer... added) {
			types.addAll(types.indexOf(next), Arrays.asList(added));
		}
	}
}

