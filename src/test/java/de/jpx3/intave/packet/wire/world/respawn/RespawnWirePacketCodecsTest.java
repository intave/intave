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

package de.jpx3.intave.packet.wire.world.respawn;

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.packet.contract.immutable.RespawnPacket;
import de.jpx3.intave.packet.contract.mutable.MutableRespawnPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.RESPAWN;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class RespawnWirePacketCodecsTest {
	@Test
	void roundTripsTheRespawnLayoutForEverySupportedProtocol() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			for (boolean hasPrevious : new boolean[]{false, true}) {
				MutableRespawnPacket packet = roundTrip(CLIENTBOUND, RESPAWN, protocol, buf -> writeRespawn(buf, protocol, hasPrevious));
				assertAvailability(packet, protocol);
				assertFixtureValues(packet, hasPrevious);
				assertFalse(packet.couldHaveBeenMutated(), "reading respawn in " + protocol);
			}
		}
	}

	private static void assertAvailability(RespawnPacket packet, int protocol) {
		assertEquals(protocol < 735, packet.isLegacyDimensionIdAvailable(), "LegacyDimensionId in " + protocol);
		assertEquals(protocol >= 766, packet.isDimensionTypeIdAvailable(), "DimensionTypeId in " + protocol);
		assertEquals((protocol >= 735 && protocol < 751) || (protocol >= 759 && protocol < 766), packet.isDimensionTypeKeyAvailable(), "DimensionTypeKey in " + protocol);
		assertEquals(protocol >= 751 && protocol < 759, packet.isDimensionTypeDataAvailable(), "DimensionTypeData in " + protocol);
		assertEquals(protocol >= 735, packet.isWorldKeyAvailable(), "WorldKey in " + protocol);
		assertEquals(protocol < 735, packet.isLevelTypeAvailable(), "LevelType in " + protocol);
		assertEquals(protocol < 477, packet.isDifficultyIdAvailable(), "DifficultyId in " + protocol);
		assertEquals(protocol >= 573, packet.isHashedSeedAvailable(), "HashedSeed in " + protocol);
		assertEquals(protocol >= 735, packet.isPreviousGameModeAvailable(), "PreviousGameMode in " + protocol);
		assertEquals(protocol >= 735, packet.isDebugWorldAvailable(), "DebugWorld in " + protocol);
		assertEquals(protocol >= 735, packet.isFlatWorldAvailable(), "FlatWorld in " + protocol);
		assertEquals(protocol >= 735 && protocol < 761, packet.isKeepAttributesAvailable(), "KeepAttributes in " + protocol);
		assertEquals(protocol >= 761, packet.isKeepMaskAvailable(), "KeepMask in " + protocol);
		assertEquals(protocol >= 759, packet.isLastDeathLocationAvailable(), "LastDeathLocation in " + protocol);
		assertEquals(protocol >= 763, packet.isPortalCooldownAvailable(), "PortalCooldown in " + protocol);
		assertEquals(protocol >= 768, packet.isSeaLevelAvailable(), "SeaLevel in " + protocol);
	}

	private static void assertFixtureValues(RespawnPacket packet, boolean hasPrevious) {
		assertEquals(2, packet.gameModeId());
		boolean previous = packet.isPreviousGameModeAvailable() && hasPrevious;
		assertEquals(previous, packet.hasPreviousGameMode());
		assertEquals(previous ? 3 : -1, packet.previousGameModeId());
		if (packet.isLegacyDimensionIdAvailable()) assertEquals(-1, packet.legacyDimensionId());
		if (packet.isDimensionTypeIdAvailable()) assertEquals(300, packet.dimensionTypeId());
		if (packet.isDimensionTypeKeyAvailable()) assertEquals(MinecraftKey.withDefaultNamespace("the_nether"), packet.dimensionTypeKey());
		if (packet.isDimensionTypeDataAvailable()) assertEquals(0.25F, packet.dimensionTypeData().getFloat("ambient_light"));
		if (packet.isWorldKeyAvailable()) assertEquals(new MinecraftKey("example", "test_world"), packet.worldKey());
		if (packet.isLevelTypeAvailable()) assertEquals("flat", packet.levelType());
		if (packet.isDifficultyIdAvailable()) assertEquals(3, packet.difficultyId());
		if (packet.isHashedSeedAvailable()) assertEquals(0x1122334455667788L, packet.hashedSeed());
		if (packet.isDebugWorldAvailable()) assertEquals(false, packet.debugWorld());
		if (packet.isFlatWorldAvailable()) assertEquals(true, packet.flatWorld());
		if (packet.isKeepAttributesAvailable()) assertEquals(hasPrevious, packet.keepAttributes());
		if (packet.isKeepMaskAvailable()) assertEquals(3, packet.keepMask());
		if (packet.isPortalCooldownAvailable()) assertEquals(280, packet.portalCooldown());
		if (packet.isSeaLevelAvailable()) assertEquals(-32, packet.seaLevel());
		boolean death = packet.isLastDeathLocationAvailable() && hasPrevious;
		assertEquals(death, packet.hasLastDeathLocation());
		assertEquals(death ? MinecraftKey.withDefaultNamespace("the_end") : null, packet.lastDeathDimension());
		assertEquals(death ? new BlockPosition(-6144, 1092, 112) : null, packet.lastDeathPosition());
	}

	@Test
	void changesSurviveTheWireFormatForEverySupportedProtocol() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			MutableRespawnPacket packet = roundTrip(CLIENTBOUND, RESPAWN, protocol, buf -> writeRespawn(buf, protocol, true));
			MinecraftKey type = new MinecraftKey("test", "custom_type");
			MinecraftKey world = new MinecraftKey("test", "other_world");
			BlockPosition death = new BlockPosition(123, -64, -321);
			CompoundTag data = new CompoundTag();
			data.putInt("height", 384);
			int gameMode = protocol >= 777 ? 301 : 1;
			int previous = protocol >= 777 ? 300 : 0;
			packet.setGameModeId(gameMode);
			if (packet.isLegacyDimensionIdAvailable()) packet.setLegacyDimensionId(1);
			if (packet.isDimensionTypeIdAvailable()) packet.setDimensionTypeId(512);
			if (packet.isDimensionTypeKeyAvailable()) packet.setDimensionTypeKey(type);
			if (packet.isDimensionTypeDataAvailable()) packet.setDimensionTypeData(data);
			if (packet.isWorldKeyAvailable()) packet.setWorldKey(world);
			if (packet.isLevelTypeAvailable()) packet.setLevelType("amplified");
			if (packet.isDifficultyIdAvailable()) packet.setDifficultyId(1);
			if (packet.isHashedSeedAvailable()) packet.setHashedSeed(-42L);
			if (packet.isPreviousGameModeAvailable()) packet.setPreviousGameModeId(previous);
			if (packet.isDebugWorldAvailable()) packet.setDebugWorld(true);
			if (packet.isFlatWorldAvailable()) packet.setFlatWorld(false);
			if (packet.isKeepAttributesAvailable()) packet.setKeepAttributes(false);
			if (packet.isKeepMaskAvailable()) packet.setKeepMask((byte) 0x81);
			if (packet.isLastDeathLocationAvailable()) packet.setLastDeathLocation(world, death);
			if (packet.isPortalCooldownAvailable()) packet.setPortalCooldown(800);
			if (packet.isSeaLevelAvailable()) packet.setSeaLevel(-63);
			assertTrue(packet.couldHaveBeenMutated());
			RespawnPacket edited = roundTrip(CLIENTBOUND, RESPAWN, protocol, buf -> encodePayload(packet, buf));
			assertEquals(gameMode, edited.gameModeId());
			assertEquals(edited.isPreviousGameModeAvailable() ? previous : -1, edited.previousGameModeId());
			if (edited.isLegacyDimensionIdAvailable()) assertEquals(1, edited.legacyDimensionId());
			if (edited.isDimensionTypeIdAvailable()) assertEquals(512, edited.dimensionTypeId());
			if (edited.isDimensionTypeKeyAvailable()) assertEquals(type, edited.dimensionTypeKey());
			if (edited.isDimensionTypeDataAvailable()) assertEquals(data, edited.dimensionTypeData());
			if (edited.isWorldKeyAvailable()) assertEquals(world, edited.worldKey());
			if (edited.isLevelTypeAvailable()) assertEquals("amplified", edited.levelType());
			if (edited.isDifficultyIdAvailable()) assertEquals(1, edited.difficultyId());
			if (edited.isHashedSeedAvailable()) assertEquals(-42L, edited.hashedSeed());
			if (edited.isDebugWorldAvailable()) assertEquals(true, edited.debugWorld());
			if (edited.isFlatWorldAvailable()) assertEquals(false, edited.flatWorld());
			if (edited.isKeepAttributesAvailable()) assertEquals(false, edited.keepAttributes());
			if (edited.isKeepMaskAvailable()) assertEquals((byte) 0x81, edited.keepMask());
			if (edited.isPortalCooldownAvailable()) assertEquals(800, edited.portalCooldown());
			if (edited.isSeaLevelAvailable()) assertEquals(-63, edited.seaLevel());
			if (edited.isLastDeathLocationAvailable()) {
				assertEquals(world, edited.lastDeathDimension());
				assertEquals(death, edited.lastDeathPosition());
				packet.clearLastDeathLocation();
			}
			if (edited.isPreviousGameModeAvailable()) packet.setPreviousGameModeId(-1);
			RespawnPacket cleared = roundTrip(CLIENTBOUND, RESPAWN, protocol, buf -> encodePayload(packet, buf));
			assertFalse(cleared.hasPreviousGameMode());
			assertFalse(cleared.hasLastDeathLocation());
		}
	}

	@Test
	void dimensionNbtIsDetachedOnReadAndWrite() {
		MutableRespawnPacket packet = roundTrip(CLIENTBOUND, RESPAWN, 751, buf -> writeRespawn(buf, 751, true));
		CompoundTag data = packet.dimensionTypeData();
		data.putInt("height", 384);
		assertFalse(packet.dimensionTypeData().contains("height"));
		assertFalse(packet.couldHaveBeenMutated());
		packet.setDimensionTypeData(data);
		data.putInt("height", 128);
		assertEquals(384, packet.dimensionTypeData().getInt("height"));
	}

	@Test
	void rejectsUnavailableFieldsAndValuesThatWouldBeTruncated() {
		MutableRespawnPacket legacy = WirePackets.createPlayPacket(RESPAWN, 47);
		assertThrows(UnsupportedOperationException.class, legacy::worldKey);
		assertThrows(UnsupportedOperationException.class, () -> legacy.setWorldKey(MinecraftKey.withDefaultNamespace("overworld")));
		assertThrows(UnsupportedOperationException.class, () -> legacy.setPreviousGameModeId(0));
		assertThrows(UnsupportedOperationException.class, legacy::clearLastDeathLocation);
		assertThrows(IllegalArgumentException.class, () -> legacy.setGameModeId(256));
		assertThrows(IllegalArgumentException.class, () -> legacy.setDifficultyId(-1));
		MutableRespawnPacket modern = WirePackets.createPlayPacket(RESPAWN, 768);
		assertThrows(UnsupportedOperationException.class, modern::dimensionTypeData);
		assertThrows(UnsupportedOperationException.class, () -> modern.setKeepAttributes(true));
		assertThrows(IllegalArgumentException.class, () -> modern.setPreviousGameModeId(128));
		assertEquals(-1, modern.previousGameModeId());
	}

	private static void writeRespawn(ByteBuf buf, int protocol, boolean hasPrevious) {
		long seed = 0x1122334455667788L;
		if (protocol < 735) {
			buf.writeInt(-1);
			if (protocol < 477) buf.writeByte(3);
			if (protocol >= 573) buf.writeLong(seed);
			buf.writeByte(2);
			VARINT_STRING.encode(buf, "flat");
			return;
		}

		if (protocol >= 766) {
			VARINT.encode(buf, 300);
		} else if (protocol >= 751 && protocol < 759) {
			// A named compound root with one float tag.
			buf.writeByte(10).writeShort(0).writeByte(5).writeShort(13);
			buf.writeCharSequence("ambient_light", StandardCharsets.US_ASCII);
			buf.writeFloat(0.25F).writeByte(0);
		} else {
			VARINT_STRING.encode(buf, "the_nether");
		}
		VARINT_STRING.encode(buf, "example:test_world");
		buf.writeLong(seed);
		if (protocol >= 777) {
			VARINT.encode(buf, 2);
			VARINT.encode(buf, hasPrevious ? 4 : 0);
		} else {
			buf.writeByte(2).writeByte(hasPrevious ? 3 : -1);
		}
		buf.writeBoolean(false).writeBoolean(true);
		if (protocol < 761) buf.writeBoolean(hasPrevious);
		if (protocol >= 761 && protocol < 764) buf.writeByte(3);

		if (protocol >= 759) {
			buf.writeBoolean(hasPrevious);
			if (hasPrevious) {
				VARINT_STRING.encode(buf, "the_end");
				buf.writeLong(0xFFFA000000070444L);
			}
		}
		if (protocol >= 763) VARINT.encode(buf, 280);
		if (protocol >= 768) VARINT.encode(buf, -32);
		if (protocol >= 764) buf.writeByte(3);
	}
}
