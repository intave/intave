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

import de.jpx3.intave.item.ItemStack;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT_STRING;

/** Consumes a particle's parameters using the client's ParticleTypes registry. */
final class MetadataParticleCodec {
	private final int protocol;
	private final Payload[] payloads;

	MetadataParticleCodec(int protocol) {
		this.protocol = protocol;
		this.payloads = registryFor(protocol).payloads;
	}

	void read(ByteBuf in) {
		int id = VARINT.decode(in);
		if (id < 0 || id >= payloads.length) {
			throw new IllegalArgumentException("Unknown particle " + id + " in " + protocol);
		}
		switch (payloads[id]) {
			case SIMPLE: break;
			case BLOCK:
			case SHRIEK: VARINT.decode(in); break;
			case COLOR:
			case POWER:
			case GEYSER: in.skipBytes(4); break;
			case SPELL:
			case GEYSER_BASE: in.skipBytes(8); break;
			case DUST: in.skipBytes(protocol < 768 ? 16 : 8); break;
			case TRANSITION: in.skipBytes(protocol < 768 ? 28 : 12); break;
			case ITEM: ItemStack.codecFor(protocol).decode(in); break;
			case TRAIL: readTrail(in); break;
			case VIBRATION: readVibration(in); break;
		}
	}

	private void readTrail(ByteBuf in) {
		in.skipBytes(28); // Target (three doubles) and color (int).
		if (protocol >= 769) VARINT.decode(in); // Duration.
	}

	private void readVibration(ByteBuf in) {
		if (protocol < 759) in.skipBytes(8); // VibrationPath origin in 1.17–1.18.
		int source = readVibrationSource(in);
		switch (source) {
			case 0:
				in.skipBytes(8); // Block position.
				break;
			case 1:
				VARINT.decode(in); // Entity ID.
				if (protocol >= 759) in.skipBytes(4); // Eye-height offset.
				break;
			default:
				throw new IllegalArgumentException("Unknown vibration source " + source);
		}
		VARINT.decode(in); // Arrival ticks.
	}

	private int readVibrationSource(ByteBuf in) {
		if (protocol >= 765) return VARINT.decode(in);
		String key = VARINT_STRING.decode(in);
		switch (key) {
			case "minecraft:block":
			case "block": return 0;
			case "minecraft:entity":
			case "entity": return 1;
			default: throw new IllegalArgumentException("Unknown vibration source " + key);
		}
	}

	private static Registry registryFor(int protocol) {
		// Before 1.13.
		if (protocol < 393) {
			return new Registry(0);
		}
		// 1.13.
		if (protocol < 477) {
			return new Registry(50)
				.with(Payload.BLOCK, 3, 20)
				.with(Payload.DUST, 11)
				.with(Payload.ITEM, 27);
		}
		// 1.14.
		if (protocol < 573) {
			return new Registry(58)
				.with(Payload.BLOCK, 3, 23)
				.with(Payload.DUST, 14)
				.with(Payload.ITEM, 32);
		}
		// 1.15.
		if (protocol < 735) {
			return new Registry(62)
				.with(Payload.BLOCK, 3, 23)
				.with(Payload.DUST, 14)
				.with(Payload.ITEM, 32);
		}
		// 1.16.
		if (protocol < 755) {
			return new Registry(72)
				.with(Payload.BLOCK, 3, 23)
				.with(Payload.DUST, 14)
				.with(Payload.ITEM, 34);
		}
		// 1.17.
		if (protocol < 757) {
			return new Registry(89)
				.with(Payload.BLOCK, 4, 25)
				.with(Payload.DUST, 15)
				.with(Payload.TRANSITION, 16)
				.with(Payload.ITEM, 36)
				.with(Payload.VIBRATION, 37);
		}
		// 1.18.
		if (protocol < 759) {
			return new Registry(88)
				.with(Payload.BLOCK, 2, 3, 24)
				.with(Payload.DUST, 14)
				.with(Payload.TRANSITION, 15)
				.with(Payload.ITEM, 35)
				.with(Payload.VIBRATION, 36);
		}
		// 1.19 and 1.19.3.
		if (protocol < 762) {
			return new Registry(93)
				.with(Payload.BLOCK, 2, 3, 25)
				.with(Payload.DUST, 14)
				.with(Payload.TRANSITION, 15)
				.with(Payload.POWER, 30)
				.with(Payload.ITEM, 39)
				.with(Payload.VIBRATION, 40)
				.with(Payload.SHRIEK, 92);
		}
		// 1.19.4.
		if (protocol < 763) {
			return new Registry(96)
				.with(Payload.BLOCK, 2, 3, 25)
				.with(Payload.DUST, 14)
				.with(Payload.TRANSITION, 15)
				.with(Payload.POWER, 33)
				.with(Payload.ITEM, 42)
				.with(Payload.VIBRATION, 43)
				.with(Payload.SHRIEK, 95);
		}
		// 1.20 and 1.20.2.
		if (protocol < 765) {
			return new Registry(95)
				.with(Payload.BLOCK, 2, 3, 25)
				.with(Payload.DUST, 14)
				.with(Payload.TRANSITION, 15)
				.with(Payload.POWER, 31)
				.with(Payload.ITEM, 40)
				.with(Payload.VIBRATION, 41)
				.with(Payload.SHRIEK, 93);
		}
		// 1.20.3.
		if (protocol < 766) {
			return new Registry(101)
				.with(Payload.BLOCK, 2, 3, 27)
				.with(Payload.DUST, 14)
				.with(Payload.TRANSITION, 15)
				.with(Payload.POWER, 33)
				.with(Payload.ITEM, 42)
				.with(Payload.VIBRATION, 43)
				.with(Payload.SHRIEK, 96);
		}
		// 1.20.5 and 1.21.
		if (protocol < 768) {
			return new Registry(109)
				.with(Payload.BLOCK, 1, 2, 28, 105)
				.with(Payload.DUST, 13)
				.with(Payload.TRANSITION, 14)
				.with(Payload.COLOR, 20)
				.with(Payload.POWER, 35)
				.with(Payload.ITEM, 44)
				.with(Payload.VIBRATION, 45)
				.with(Payload.SHRIEK, 99);
		}
		// 1.21.2.
		if (protocol < 769) {
			return new Registry(111)
				.with(Payload.BLOCK, 1, 2, 28, 106, 110)
				.with(Payload.DUST, 13)
				.with(Payload.TRANSITION, 14)
				.with(Payload.COLOR, 20)
				.with(Payload.POWER, 35)
				.with(Payload.ITEM, 44)
				.with(Payload.VIBRATION, 45)
				.with(Payload.TRAIL, 46)
				.with(Payload.SHRIEK, 100);
		}
		// 1.21.4.
		if (protocol < 770) {
			return new Registry(112)
				.with(Payload.BLOCK, 1, 2, 28, 107, 111)
				.with(Payload.DUST, 13)
				.with(Payload.TRANSITION, 14)
				.with(Payload.COLOR, 20)
				.with(Payload.POWER, 36)
				.with(Payload.ITEM, 45)
				.with(Payload.VIBRATION, 46)
				.with(Payload.TRAIL, 47)
				.with(Payload.SHRIEK, 101);
		}
		// 1.21.5–1.21.7.
		if (protocol < 773) {
			return new Registry(114)
				.with(Payload.BLOCK, 1, 2, 28, 108, 112)
				.with(Payload.DUST, 13)
				.with(Payload.TRANSITION, 14)
				.with(Payload.COLOR, 20, 35)
				.with(Payload.POWER, 37)
				.with(Payload.ITEM, 46)
				.with(Payload.VIBRATION, 47)
				.with(Payload.TRAIL, 48)
				.with(Payload.SHRIEK, 102);
		}
		// 1.21.9 and 1.21.11.
		if (protocol < 775) {
			return new Registry(115)
				.with(Payload.BLOCK, 1, 2, 29, 109, 113)
				.with(Payload.DUST, 14)
				.with(Payload.TRANSITION, 15)
				.with(Payload.COLOR, 21, 36, 42)
				.with(Payload.POWER, 8, 38)
				.with(Payload.SPELL, 16, 46)
				.with(Payload.ITEM, 47)
				.with(Payload.VIBRATION, 48)
				.with(Payload.TRAIL, 49)
				.with(Payload.SHRIEK, 103);
		}
		// 26.1.
		if (protocol < 776) {
			return new Registry(117)
				.with(Payload.BLOCK, 1, 2, 29, 111, 115)
				.with(Payload.DUST, 14)
				.with(Payload.TRANSITION, 15)
				.with(Payload.COLOR, 21, 36, 42)
				.with(Payload.POWER, 8, 38)
				.with(Payload.SPELL, 16, 46)
				.with(Payload.ITEM, 47)
				.with(Payload.VIBRATION, 48)
				.with(Payload.TRAIL, 49)
				.with(Payload.SHRIEK, 105);
		}
		// 26.2.
		if (protocol < 777) {
			return new Registry(125)
				.with(Payload.BLOCK, 1, 2, 36, 118, 122)
				.with(Payload.GEYSER, 7, 10)
				.with(Payload.GEYSER_BASE, 8, 9)
				.with(Payload.DUST, 21)
				.with(Payload.TRANSITION, 22)
				.with(Payload.COLOR, 28, 43, 49)
				.with(Payload.POWER, 15, 45)
				.with(Payload.SPELL, 23, 53)
				.with(Payload.ITEM, 54)
				.with(Payload.VIBRATION, 55)
				.with(Payload.TRAIL, 56)
				.with(Payload.SHRIEK, 112);
		}
		// 26.3.
		return new Registry(128)
			.with(Payload.BLOCK, 1, 2, 36, 121, 125)
			.with(Payload.GEYSER, 7, 10)
			.with(Payload.GEYSER_BASE, 8, 9)
			.with(Payload.DUST, 21)
			.with(Payload.TRANSITION, 22)
			.with(Payload.COLOR, 28, 46, 52)
			.with(Payload.POWER, 15, 48)
			.with(Payload.SPELL, 23, 56)
			.with(Payload.ITEM, 57)
			.with(Payload.VIBRATION, 58)
			.with(Payload.TRAIL, 59)
			.with(Payload.SHRIEK, 115);
	}

	private enum Payload {
		SIMPLE, BLOCK, SHRIEK, COLOR, POWER, SPELL, GEYSER, GEYSER_BASE,
		DUST, TRANSITION, ITEM, TRAIL, VIBRATION
	}

	private static final class Registry {
		private final Payload[] payloads;

		private Registry(int size) {
			payloads = new Payload[size];
			Arrays.fill(payloads, Payload.SIMPLE);
		}

		private Registry with(Payload payload, int... ids) {
			for (int id : ids) payloads[id] = payload;
			return this;
		}
	}
}

