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
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.contract.mutable.MutableEntityMetadataPacket;
import de.jpx3.intave.share.BlockPosition;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.ENTITY_METADATA;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class EntityMetadataWirePacketTest {
	@TestFactory
	Stream<DynamicTest> sparseUpdatesKeepAbsentFieldsAbsentAndMutableFlagsRoundTrip() {
		return Stream.of("47", "107", "210", "315", "316", "335", "393", "401", "404", "477", "573", "735", "751", "755", "756", "757", "758", "759", "760", "761", "762", "763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774", "775", "776", "777").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			sparseUpdatesKeepAbsentFieldsAbsentAndMutableFlagsRoundTripCase(Integer.parseInt(values[0]));
		}));
	}

	private void sparseUpdatesKeepAbsentFieldsAbsentAndMutableFlagsRoundTripCase(int protocol) {
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
			VARINT.encode(out, 312);
			out.writeByte(0);
			if (protocol >= 107) VARINT.encode(out, 0);
			out.writeByte(0x80);
			out.writeByte(protocol < 107 ? 127 : 255);
		});
		assertEquals(312, packet.entityId());
		assertEquals((byte) 0x80, packet.entityFlags());
		assertNull(packet.livingFlags());
		assertNull(packet.health());
		assertNull(packet.pose());
		assertNull(packet.bedPosition());
		assertNull(packet.attachedEntityId());
		assertFalse(packet.couldHaveBeenMutated());
		packet.setEntityId(10);
		packet.setEntityFlags((byte) 1);
		packet.setHealth(8.5F);
		if (protocol >= 107) packet.setLivingFlags((byte) 3);
		if (protocol >= 477) {
			packet.setPose(Pose.SLEEPING);
			packet.setBedPosition(new BlockPosition(-3, 65, 7));
		}
		var decoded = reencode(packet);
		assertEquals(10, decoded.entityId());
		assertEquals((byte) 1, decoded.entityFlags());
		assertEquals(8.5F, decoded.health());
		assertEquals(protocol >= 107 ? (byte) 3 : null, decoded.livingFlags());
		if (protocol >= 477) {
			assertEquals(Pose.SLEEPING, decoded.pose());
			assertEquals(Optional.of(new BlockPosition(-3, 65, 7)), decoded.bedPosition());
		} else {
			assertThrows(UnsupportedOperationException.class, () -> packet.setPose(Pose.STANDING));
			assertThrows(UnsupportedOperationException.class, packet::clearBedPosition);
		}
	}

	// IDs are from the corresponding client EntityDataSerializers and LivingEntity declarations.
	@TestFactory
	Stream<DynamicTest> readsClientFieldIndicesAndSerializerIds() {
		return Stream.of("107,5,2,-1,-1,-1", "210,6,2,-1,-1,-1", "393,6,2,-1,-1,-1", "477,7,2,18,12,10", "573,7,2,18,13,10", "755,8,2,18,14,10", "759,8,2,18,14,10", "761,8,3,19,14,11", "762,8,3,20,14,11", "765,8,3,20,14,11", "766,8,3,21,14,11", "770,8,3,21,14,11", "773,8,3,20,14,11", "774,8,3,20,14,11", "775,8,3,20,14,11", "776,8,3,20,14,11", "777,8,3,20,14,11").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			readsClientFieldIndicesAndSerializerIdsCase(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[3]), Integer.parseInt(values[4]), Integer.parseInt(values[5]));
		}));
	}

	private void readsClientFieldIndicesAndSerializerIdsCase(int protocol, int living, int floatType, int poseType, int bed, int bedType) {
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
			VARINT.encode(out, 12);
			header(out, living, 0); out.writeByte(3);
			header(out, living + 1, floatType); out.writeFloat(17.5F);
			if (poseType >= 0) {
				header(out, 6, poseType); VARINT.encode(out, 2); // Minecraft SLEEPING, not our enum ordinal 2.
				header(out, bed, bedType); out.writeBoolean(true);
				BlockPosition.WIRE_STREAM_CODEC.encode(out, new BlockPosition(-10, -64, 30));
			}
			out.writeByte(255);
		});
		assertEquals((byte) 3, packet.livingFlags());
		assertEquals(17.5F, packet.health());
		if (poseType >= 0) {
			assertEquals(Pose.SLEEPING, packet.pose());
			assertEquals(Optional.of(new BlockPosition(-10, -64, 30)), packet.bedPosition());
			packet.clearBedPosition();
			assertEquals(Optional.empty(), reencode(packet).bedPosition());
		}
	}

	@TestFactory
	Stream<DynamicTest> mapsPoseWireIdsWithoutUsingSimulationOrdinals() {
		return Stream.of("0,STANDING", "1,FALL_FLYING", "2,SLEEPING", "3,SWIMMING", "5,CROUCHING").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			mapsPoseWireIdsWithoutUsingSimulationOrdinalsCase(Integer.parseInt(values[0]), Pose.valueOf(values[1]));
		}));
	}

	private void mapsPoseWireIdsWithoutUsingSimulationOrdinalsCase(int id, Pose pose) {
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, 777, out -> {
			VARINT.encode(out, 1); header(out, 6, 20); VARINT.encode(out, id); out.writeByte(255);
		});
		assertEquals(pose, packet.pose());
		packet.setPose(pose);
		assertEquals(pose, reencode(packet).pose());
	}

	@Test
	void unsupportedPoseRemainsOpaqueAndDoesNotBecomeAPhysicalPose() {
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, 777, out -> {
			VARINT.encode(out, 1); header(out, 6, 20); VARINT.encode(out, 19); out.writeByte(255);
		});
		assertNull(packet.pose());
		packet.setEntityFlags((byte) 0x80);
		assertNull(reencode(packet).pose());
	}

	@TestFactory
	Stream<DynamicTest> retainsFireworkAttachmentAddressing() {
		return Stream.of("316,7,1", "393,7,1", "477,8,17", "755,9,17", "761,9,18", "762,9,19", "766,9,20", "773,9,19", "777,9,19").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			retainsFireworkAttachmentAddressingCase(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
		}));
	}

	private void retainsFireworkAttachmentAddressingCase(int protocol, int index, int serializer) {
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
			VARINT.encode(out, 1); header(out, index, serializer);
			VARINT.encode(out, protocol < 477 ? 300 : 301); out.writeByte(255);
		});
		assertEquals(OptionalInt.of(300), packet.attachedEntityId());
		assertEquals(2, packet.entityCount());
		assertEquals(300, packet.entityId(1));
		packet.setEntityId(1, 301);
		assertEquals(OptionalInt.of(301), reencode(packet).attachedEntityId());
		packet.clearAttachedEntityId();
		assertEquals(OptionalInt.empty(), reencode(packet).attachedEntityId());
	}

	@TestFactory
	Stream<DynamicTest> unrelatedItemParticlesBeforePlayerFlagsArePreserved() {
		return Stream.of("393,15,27", "477,15,32", "735,15,34", "755,15,36", "757,15,35", "759,15,39", "761,16,39", "762,17,42", "763,17,40", "764,17,40", "765,17,42", "766,17,44", "767,17,44", "768,17,44", "769,17,45", "770,17,46", "771,17,46", "772,17,46", "773,16,47", "774,16,47", "775,16,47", "776,16,54", "777,16,57").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			unrelatedItemParticlesBeforePlayerFlagsArePreservedCase(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
		}));
	}

	private void unrelatedItemParticlesBeforePlayerFlagsArePreservedCase(int protocol, int particleType, int itemParticle) {
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
			VARINT.encode(out, 1);
			header(out, 30, particleType); VARINT.encode(out, itemParticle);
			ItemStack.codecFor(protocol).encode(out, null);
			header(out, 0, 0); out.writeByte(0x80);
			out.writeByte(255);
		});
		packet.setEntityId(2);
		assertEquals((byte) 0x80, reencode(packet).entityFlags());
	}

	@TestFactory
	Stream<DynamicTest> skipsBothVibrationDestinationLayouts() {
		return Stream.of("755,15,37", "757,15,36", "759,15,40", "761,16,40", "762,17,43", "764,17,41", "765,17,43", "766,17,45", "777,16,58").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			skipsBothVibrationDestinationLayoutsCase(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
		}));
	}

	private void skipsBothVibrationDestinationLayoutsCase(int protocol, int serializer, int particle) {
		for (int destination : new int[]{0, 1}) {
			roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
				VARINT.encode(out, 1); header(out, 30, serializer); VARINT.encode(out, particle);
				if (protocol < 759) out.writeLong(0);
				if (protocol >= 765) VARINT.encode(out, destination);
				else VARINT_STRING.encode(out, destination == 0 ? "minecraft:block" : "minecraft:entity");
				if (destination == 0) out.writeLong(0);
				else {
					VARINT.encode(out, 12);
					if (protocol >= 759) out.writeFloat(1.62F);
				}
				VARINT.encode(out, 15);
				header(out, 0, 0); out.writeByte(1); out.writeByte(255);
			});
		}
	}

	@TestFactory
	Stream<DynamicTest> readsPotionParticleListsBeforeTheBedField() {
		return Stream.of("766,18,13,14,20,35,99", "768,18,13,14,20,35,100", "773,17,14,15,21,38,103", "777,17,21,22,28,48,115").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			readsPotionParticleListsBeforeTheBedFieldCase(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[3]), Integer.parseInt(values[4]), Integer.parseInt(values[5]), Integer.parseInt(values[6]));
		}));
	}

	private void readsPotionParticleListsBeforeTheBedFieldCase(int protocol, int serializer, int dust, int transition, int color, int charge, int shriek) {
		MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
			VARINT.encode(out, 1); header(out, 10, serializer); VARINT.encode(out, 5);
			VARINT.encode(out, dust); out.writeZero(protocol < 768 ? 16 : 8);
			VARINT.encode(out, transition); out.writeZero(protocol < 768 ? 28 : 12);
			VARINT.encode(out, color); out.writeInt(0xAABBCC);
			VARINT.encode(out, charge); out.writeFloat(0.4F);
			VARINT.encode(out, shriek); VARINT.encode(out, 500);
			header(out, 14, 11); out.writeBoolean(false); out.writeByte(255);
		});
		assertEquals(Optional.empty(), packet.bedPosition());
		packet.setEntityFlags((byte) 1);
		assertEquals(Optional.empty(), reencode(packet).bedPosition());
	}

	@TestFactory
	Stream<DynamicTest> preservesInlinePaintingVariants() {
		return Stream.of("767,26", "768,26", "770,30", "773,29", "774,30", "775,34", "777,34").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			preservesInlinePaintingVariantsCase(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
		}));
	}

	private void preservesInlinePaintingVariantsCase(int protocol, int serializer) {
		roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
			VARINT.encode(out, 1); header(out, 30, serializer);
			VARINT.encode(out, 0); VARINT.encode(out, 2); VARINT.encode(out, 4);
			VARINT_STRING.encode(out, "minecraft:painting");
			if (protocol >= 768) { out.writeBoolean(false); out.writeBoolean(false); }
			header(out, 0, 0); out.writeByte(0x80); out.writeByte(255);
		});
	}

	@TestFactory
	Stream<DynamicTest> preservesBothResolvableProfileForms() {
		return Stream.of("773,36", "774,37", "775,41", "776,41", "777,41").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			preservesBothResolvableProfileFormsCase(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
		}));
	}

	private void preservesBothResolvableProfileFormsCase(int protocol, int serializer) {
		for (boolean complete : new boolean[]{false, true}) {
			roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
				VARINT.encode(out, 1); header(out, 30, serializer);
				out.writeBoolean(complete);
				if (complete) { out.writeLong(1); out.writeLong(2); VARINT_STRING.encode(out, "profile"); }
				else {
					out.writeBoolean(true); VARINT_STRING.encode(out, "profile");
					out.writeBoolean(true); out.writeLong(1); out.writeLong(2);
				}
				VARINT.encode(out, 1);
				VARINT_STRING.encode(out, "textures"); VARINT_STRING.encode(out, "value");
				out.writeBoolean(true); VARINT_STRING.encode(out, "signature");
				out.writeBoolean(true); VARINT_STRING.encode(out, "minecraft:skin");
				out.writeBoolean(false); out.writeBoolean(false); out.writeBoolean(true); out.writeBoolean(true);
				header(out, 0, 0); out.writeByte(1); out.writeByte(255);
			});
		}
	}

	@TestFactory
	Stream<DynamicTest> preservesItemAndComponentBytesDuringUnrelatedMutation() {
		return Stream.of("47", "335", "393", "764", "765", "766", "773", "777").map(row -> DynamicTest.dynamicTest(row, () -> {
			String[] values = row.split(",");
			preservesItemAndComponentBytesDuringUnrelatedMutationCase(Integer.parseInt(values[0]));
		}));
	}

	private void preservesItemAndComponentBytesDuringUnrelatedMutationCase(int protocol) {
		ByteBuf fixture = Unpooled.buffer();
		try {
			VARINT.encode(fixture, 1);
			if (protocol < 107) fixture.writeByte(5 << 5 | 10);
			else header(fixture, 30, protocol < 393 ? 5 : protocol < 761 ? 6 : 7);
			ItemStack.codecFor(protocol).encode(fixture, null);
			if (protocol >= 107) {
				header(fixture, 2, protocol < 393 ? 4 : protocol < 761 ? 5 : 6);
				if (protocol >= 393) fixture.writeBoolean(true);
				if (protocol < 765) VARINT_STRING.encode(fixture, "{ \"text\": \"name\" }");
				else { fixture.writeByte(8); fixture.writeShort(4); fixture.writeBytes(new byte[]{'n','a','m','e'}); }
			}
			fixture.writeByte(protocol < 107 ? 127 : 255);
			MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> out.writeBytes(fixture, 0, fixture.readableBytes()));
			packet.setEntityId(2);
			ByteBuf changed = Unpooled.buffer();
			try {
				encodePayload(packet, changed);
				assertEquals(2, VARINT.decode(changed));
				VARINT.decode(fixture);
				assertEquals(fixture, changed);
			} finally { changed.release(); }
		} finally { fixture.release(); }
	}

	@Test
	void readsAgeFlagsAndPreservesOtherArmorStandFlags() {
		for (int protocol : new int[]{47,107,210,477,573,755,777}) {
			for (String name : new String[]{"Cow", "Zombie", "ArmorStand"}) {
				var type = new EntityTypeData(name, null, 0, true, 0);
				MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol, out -> {
					VARINT.encode(out, 1); out.writeByte(protocol < 107 ? 127 : 255);
				});
				assertNull(packet.baby(type));
				packet.setBaby(type, true);
				assertEquals(true, reencode(packet).baby(type));
				packet.setBaby(type, false);
				assertEquals(false, reencode(packet).baby(type));
			}
		}
		var piglin = new EntityTypeData("Piglin", null, 0, true, 0);
		MutableEntityMetadataPacket piglinPacket = roundTrip(CLIENTBOUND, ENTITY_METADATA, 755, out -> {
			VARINT.encode(out, 1);
			header(out, 16, 7); out.writeBoolean(false); // Immunity is independent of age.
			header(out, 17, 7); out.writeBoolean(true);
			out.writeByte(255);
		});
		assertTrue(piglinPacket.baby(piglin));
		var stand = new EntityTypeData("ArmorStand", null, 0, true, 0);
		MutableEntityMetadataPacket standPacket = roundTrip(CLIENTBOUND, ENTITY_METADATA, 777, out -> {
			VARINT.encode(out, 1); header(out, 15, 0); out.writeByte(0x1D); out.writeByte(255);
		});
		standPacket.setBaby(stand, false);
		ByteBuf out = Unpooled.buffer();
		try {
			encodePayload(standPacket, out);
			assertEquals(1, VARINT.decode(out));
			assertEquals(15, out.readUnsignedByte());
			assertEquals(0, VARINT.decode(out));
			assertEquals(0x1C, out.readUnsignedByte());
		} finally { out.release(); }
	}

	@Test
	void rejectsTruncatedAndUnknownMetadata() {
		for (byte[] fields : new byte[][]{
			{0,0}, {0,127,0,(byte)255}, {14,11,1,(byte)255},
			{0,0,1,0,0}, {0,0,1,0,127,0,(byte)255}, {(byte)255,0}
		}) {
			ByteBuf in = Unpooled.buffer();
			try {
				VARINT.encode(in, 1); in.writeBytes(fields);
				assertThrows(RuntimeException.class, () -> decodePayload(EntityMetadataWirePacket.Protocol777.class, in));
			} finally { in.release(); }
		}
	}

	@TestFactory
	Stream<DynamicTest> repeatedIndexesRetainWireOrderAndExposeTheirFinalValues() {
		return Stream.of(47,107,477,573,755,766,776,777).map(protocol -> DynamicTest.dynamicTest(
			"protocol " + protocol, () -> {
				MutableEntityMetadataPacket packet = roundTrip(CLIENTBOUND, ENTITY_METADATA, protocol,
					out -> repeatedByteFields(out, protocol, 312, 0x80));
				assertEquals((byte) 0x80, packet.entityFlags());
				assertFalse(packet.couldHaveBeenMutated());
				if (protocol >= 755) {
					var armorStand = new EntityTypeData("ArmorStand", null, 0, true, 0);
					assertFalse(packet.baby(armorStand), "The second index 15 update clears the small flag");
				}

				// Re-encoding an unrelated mutation must retain both occurrences, including index 15.
				packet.setEntityId(42);
				assertRepeatedByteFields(packet, protocol, 42, 0x80);

				// A field mutation replaces only its final occurrence; earlier updates stay ordered.
				packet.setEntityFlags((byte) 0x40);
				assertRepeatedByteFields(packet, protocol, 42, 0x40);
				assertEquals((byte) 0x40, reencode(packet).entityFlags());
			}));
	}

	private static void repeatedByteFields(ByteBuf out, int protocol, int entityId, int finalFlags) {
		VARINT.encode(out, entityId);
		for (int[] field : new int[][]{{15, 0x1D}, {0, 1}, {15, 0x1C}, {0, finalFlags}}) {
			out.writeByte(field[0]);
			if (protocol >= 107) VARINT.encode(out, 0); // Byte serializer, packed into the legacy header.
			out.writeByte(field[1]);
		}
		out.writeByte(protocol < 107 ? 127 : 255);
	}

	private static void assertRepeatedByteFields(MutableEntityMetadataPacket packet, int protocol, int entityId, int finalFlags) {
		ByteBuf expected = Unpooled.buffer(), actual = Unpooled.buffer();
		try {
			repeatedByteFields(expected, protocol, entityId, finalFlags);
			encodePayload(packet, actual);
			assertEquals(expected, actual);
		} finally {
			expected.release();
			actual.release();
		}
	}

	private static void header(ByteBuf out, int index, int serializer) {
		out.writeByte(index); VARINT.encode(out, serializer);
	}

	private static MutableEntityMetadataPacket reencode(MutableEntityMetadataPacket packet) {
		ByteBuf out = Unpooled.buffer();
		try {
			encodePayload(packet, out);
			return (MutableEntityMetadataPacket) decodePayload(packet.getClass(), out);
		} finally { out.release(); }
	}
}

