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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.codec.transform.PentaFunction;
import io.netty.buffer.ByteBuf;

import java.util.Objects;
import java.util.OptionalInt;

public class LegacyOpenScreenWirePacket extends OpenScreenWirePacket {
	private static final StreamCodec<ByteBuf, ByteBuf, String> MENU_TYPE = ByteBufStreamCodecs.stringCodec(32);
	public static final StreamCodec<ByteBuf, ByteBuf, LegacyOpenScreenWirePacket> STREAM_CODEC =
		codec(ByteBufStreamCodecs.VARINT_STRING, LegacyOpenScreenWirePacket::new);

	private final String menuType;
	private final String titleJson;
	private final int slots;
	private final OptionalInt horseEntityId;

	public LegacyOpenScreenWirePacket() {
		this(0, "minecraft:container", "\"\"", 0, OptionalInt.empty());
	}

	public LegacyOpenScreenWirePacket(int containerId, String menuType, String titleJson, int slots, OptionalInt horseEntityId) {
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

	static <T extends LegacyOpenScreenWirePacket> StreamCodec<ByteBuf, ByteBuf, T> codec(
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
