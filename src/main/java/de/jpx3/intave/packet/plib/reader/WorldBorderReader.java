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

package de.jpx3.intave.packet.plib.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.world.border.WorldBorder;
import org.jetbrains.annotations.NotNull;

import static de.jpx3.intave.packet.plib.reader.WorldBorderReader.UpdateType.INITIALIZE;

public final class WorldBorderReader extends AbstractPacketReader {
	public @NotNull WorldBorder updated(@NotNull WorldBorder worldBorder) {
		if (type().updatesCenter()) {
			worldBorder = worldBorder.withCenterAt(center());
		}
		if (type().updatesRawSize()) {
			worldBorder = worldBorder.withSize(size());
		}
		if (type().updatesLerpSize()) {
			worldBorder = worldBorder.withLerpingSize(
				oldSize(), newSize(), lerpTime()
			);
		}
		if (type().updatesAbsoluteMaxSize()) {
			worldBorder = worldBorder.withAbsoluteMaxSize(absoluteMaxSize());
		}
		return worldBorder;
	}

	private Position center() {
		UpdateType type = type();
		if (type.updatesCenter()) {
			StructureModifier<Double> doubles = packet().getDoubles();
			double centerX = doubles.read(0);
			double centerZ = doubles.read(1);
			return new Position(centerX, 0, centerZ);
		}
		throw new IllegalStateException("Cannot get center for update type: " + type);
	}

	private double size() {
		UpdateType type = type();
		if (type.updatesRawSize()) {
			boolean legacyPacket = packet().getType() == PacketType.Play.Server.WORLD_BORDER;
			return (float) (double) packet().getDoubles().read(legacyPacket ? 2 : 0);
		}
		throw new IllegalStateException("Cannot get size for update type: " + type);
	}

	private double oldSize() {
		UpdateType type = type();
		if (type.updatesLerpSize()) {
			boolean legacyPacket = packet().getType() == PacketType.Play.Server.WORLD_BORDER;
			int initializeOffset = type() == INITIALIZE ? 2 : 0;
			return (float) (double) packet().getDoubles().read(legacyPacket ? 3 : initializeOffset);
		}
		throw new IllegalStateException("Cannot get old size for update type: " + type);
	}

	private double newSize() {
		UpdateType type = type();
		if (type.updatesLerpSize()) {
			boolean legacyPacket = packet().getType() == PacketType.Play.Server.WORLD_BORDER;
			int initializeOffset = type() == INITIALIZE ? 2 : 0;
			return (float) (double) packet().getDoubles().read(legacyPacket ? 2 : initializeOffset + 1);
		}
		throw new IllegalStateException("Cannot get new size for update type: " + type);
	}

	private int absoluteMaxSize() {
		UpdateType type = type();
		if (type.updatesAbsoluteMaxSize()) {
			return packet().getIntegers().read(0);
		}
		throw new IllegalStateException("Cannot get absolute max size for update type: " + type);
	}

	private long lerpTime() {
		UpdateType type = type();
		if (type.updatesLerpSize()) {
			return packet().getLongs().read(0);
		}
		throw new IllegalStateException("Cannot get lerp time for update type: " + type);
	}

	private UpdateType type() {
		PacketContainer packet = packet();
		PacketType type = packet.getType();
		if (type == PacketType.Play.Server.WORLD_BORDER) {
			return packet.getEnumModifier(UpdateType.class, 0).read(0);
		} else if (type == PacketType.Play.Server.SET_BORDER_LERP_SIZE) {
			return UpdateType.LERP_SIZE;
		} else if (type == PacketType.Play.Server.INITIALIZE_BORDER) {
			return INITIALIZE;
		} else if (type == PacketType.Play.Server.SET_BORDER_CENTER) {
			return UpdateType.SET_CENTER;
		} else if (type == PacketType.Play.Server.SET_BORDER_SIZE) {
			return UpdateType.SET_SIZE;
		} else if (type == PacketType.Play.Server.SET_BORDER_WARNING_DISTANCE) {
			return UpdateType.SET_WARNING_BLOCKS;
		} else if (type == PacketType.Play.Server.SET_BORDER_WARNING_DELAY) {
			return UpdateType.SET_WARNING_TIME;
		} else {
			throw new IllegalStateException("Unknown world border packet type: " + type);
		}
	}

	public enum UpdateType {
		SET_SIZE,
		LERP_SIZE,
		SET_CENTER,
		INITIALIZE,
		SET_WARNING_TIME,
		SET_WARNING_BLOCKS;

		public boolean updatesCenter() {
			return this == SET_CENTER || this == INITIALIZE;
		}

		public boolean updatesRawSize() {
			return this == SET_SIZE || this == INITIALIZE;
		}

		public boolean updatesLerpSize() {
			return this == LERP_SIZE || this == INITIALIZE;
		}

		public boolean updatesAbsoluteMaxSize() {
			return this == INITIALIZE;
		}
	}
}
