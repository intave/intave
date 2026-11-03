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

import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.PositionAndRotation;

public final class TeleportAcceptReader extends AbstractPacketReader {
	public int teleportId() {
		return packet().getIntegers().read(0);
	}

	/**
	 * Minecraft 26.3 replaced the separate acknowledgement and movement response
	 * with one packet carrying the position and rotation after applying the teleport.
	 */
	public @Nullable PositionAndRotation positionAndRotation() {
		if (MinecraftVersions.VER26_3.below()) {
			return null;
		}
		StructureModifier<Double> position = packet().getDoubles();
		StructureModifier<Float> rotation = packet().getFloat();
		if (position.size() < 3 || rotation.size() < 2) {
			return null;
		}
		return new PositionAndRotation(
			position.read(0), position.read(1), position.read(2),
			rotation.read(0), rotation.read(1)
		);
	}
}
