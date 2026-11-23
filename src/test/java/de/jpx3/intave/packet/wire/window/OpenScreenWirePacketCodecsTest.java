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

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.StringTag;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.contract.immutable.OpenScreenPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.OptionalInt;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.OPEN_WINDOW;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.*;

final class OpenScreenWirePacketCodecsTest {
	private static final String JSON_TITLE = "{ \"translate\": \"container.beacon\", \"color\": \"gold\" }";

	@Test
	void allRegisteredProtocolsClassifyTheMenuIndependentlyOfTheTitle() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			for (boolean beacon : new boolean[]{false, true}) {
				String title = beacon ? "\"Custom\"" : JSON_TITLE;
				int containerId = protocol < 477 ? 250 : 300;
				OpenScreenPacket packet = roundTrip(CLIENTBOUND, OPEN_WINDOW, protocol, buf -> {
					if (protocol < 477) {
						buf.writeByte(containerId);
						ByteBufStreamCodecs.VARINT_STRING.encode(buf, beacon ? "minecraft:beacon" : "minecraft:chest");
						ByteBufStreamCodecs.VARINT_STRING.encode(buf, title);
						buf.writeByte(54);
					} else {
						ByteBufStreamCodecs.VARINT.encode(buf, containerId);
						int beaconId = protocol < 765 ? 8 : 9;
						// Exercise both adjacent registry entries across the crafter boundary.
						buf.writeByte(beacon ? beaconId : (protocol < 765 ? 9 : 8));
						if (protocol < 765) {
							ByteBufStreamCodecs.VARINT_STRING.encode(buf, title);
						} else {
							buf.writeByte(8);
							writeNbtString(buf, beacon ? "Custom" : "container.beacon");
						}
					}
				});
				assertEquals(containerId, packet.containerId());
				assertEquals(beacon, packet.isBeaconMenu(), "protocol " + protocol);
			}
		}
	}

	@Test
	void legacyHorseWindowsCarryAnExtraEntityIdAfterTheSlotCount() {
		for (int protocol : new int[]{47, 404}) {
			OpenScreenWirePacket.Protocol47 packet = roundTrip(CLIENTBOUND, OPEN_WINDOW, protocol, buf -> {
				buf.writeByte(255);
				ByteBufStreamCodecs.VARINT_STRING.encode(buf, "EntityHorse");
				ByteBufStreamCodecs.VARINT_STRING.encode(buf, JSON_TITLE);
				buf.writeByte(200).writeInt(0x12345678);
			});
			assertEquals("EntityHorse", packet.menuType());
			assertEquals(JSON_TITLE, packet.titleJson());
			assertEquals(200, packet.slots());
			assertEquals(OptionalInt.of(0x12345678), packet.horseEntityId());
			assertFalse(packet.isBeaconMenu());
		}
	}

	@Test
	void nbtTitlesRetainListsAndNestedFormatting() {
		OpenScreenWirePacket.Protocol765 packet = roundTrip(CLIENTBOUND, OPEN_WINDOW, 765, buf -> {
			buf.writeByte(1).writeByte(9);
			buf.writeByte(9).writeByte(10).writeInt(1); // List of one compound component
			buf.writeByte(8);
			writeNbtString(buf, "translate");
			writeNbtString(buf, "container.beacon");
			buf.writeByte(9);
			writeNbtString(buf, "extra");
			buf.writeByte(10).writeInt(1).writeByte(8);
			writeNbtString(buf, "text");
			writeNbtString(buf, "!");
			buf.writeByte(1);
			writeNbtString(buf, "bold");
			buf.writeByte(1).writeByte(0).writeByte(0);
		});
		CompoundTag component = (CompoundTag) ((ListTag<?>) packet.title()).get(0);
		assertEquals("container.beacon", component.getString("translate"));
		assertEquals("!", ((CompoundTag) component.getListTag("extra").get(0)).getString("text"));

		OpenScreenWirePacket.Protocol765 compound = roundTrip(CLIENTBOUND, OPEN_WINDOW, 777, buf -> {
			buf.writeByte(1).writeByte(2).writeByte(10).writeByte(8);
			writeNbtString(buf, "text");
			writeNbtString(buf, "Custom");
			buf.writeByte(0);
		});
		((CompoundTag) compound.title()).put("text", new StringTag("Edited"));
		assertEquals("Custom", ((CompoundTag) compound.title()).getString("text"));
		assertFalse(compound.couldHaveBeenMutated());
	}

	@Test
	void absentAndTruncatedNbtTitlesAreRejected() {
		ByteBuf input = Unpooled.buffer();
		try {
			input.writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.TRUSTED_TAG.decode(input));
			assertEquals(0, input.readerIndex());
			input.clear().writeByte(8).writeShort(2).writeByte('a');
			assertThrows(IllegalArgumentException.class, () -> ByteBufStreamCodecs.TRUSTED_TAG.decode(input));
			assertEquals(0, input.readerIndex());
		} finally {
			input.release();
		}
	}

	@Test
	void longTitlesFollowTheLegacyJsonAndTrustedNbtLimits() {
		String longTitle = String.join("", Collections.nCopies(32768, "a"));
		java.util.function.Consumer<ByteBuf> legacy = buf -> {
			buf.writeByte(1);
			ByteBufStreamCodecs.VARINT_STRING.encode(buf, "minecraft:container");
			ByteBufStreamCodecs.stringCodec(262144).encode(buf, "\"" + longTitle + "\"");
			buf.writeByte(27);
		};
		assertThrows(IllegalArgumentException.class, () -> roundTrip(CLIENTBOUND, OPEN_WINDOW, 340, legacy));
		OpenScreenWirePacket.Protocol47 json = roundTrip(CLIENTBOUND, OPEN_WINDOW, 393, legacy);
		assertEquals(longTitle.length() + 2, json.titleJson().length());

		OpenScreenWirePacket.Protocol765 nbt = roundTrip(CLIENTBOUND, OPEN_WINDOW, 765, buf -> {
			buf.writeByte(1).writeByte(0).writeByte(9).writeByte(8).writeInt(65);
			for (int i = 0; i < 65; i++) writeNbtString(buf, longTitle);
		});
		assertEquals(65, ((ListTag<?>) nbt.title()).size());
	}

	private static void writeNbtString(ByteBuf buf, String ascii) {
		buf.writeShort(ascii.length());
		buf.writeCharSequence(ascii, StandardCharsets.US_ASCII);
	}
}
