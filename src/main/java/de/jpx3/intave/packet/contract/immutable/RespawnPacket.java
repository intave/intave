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

package de.jpx3.intave.packet.contract.immutable;

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.MinecraftKey;

public interface RespawnPacket extends PacketContract {
	boolean isLegacyDimensionIdAvailable();

	int legacyDimensionId();

	boolean isDimensionTypeIdAvailable();

	int dimensionTypeId();

	boolean isDimensionTypeKeyAvailable();

	MinecraftKey dimensionTypeKey();

	boolean isDimensionTypeDataAvailable();

	/** Returns a detached copy of the dimension type NBT. */
	CompoundTag dimensionTypeData();

	boolean isWorldKeyAvailable();

	MinecraftKey worldKey();

	boolean isLevelTypeAvailable();

	String levelType();

	boolean isDifficultyIdAvailable();

	int difficultyId();

	boolean isHashedSeedAvailable();

	long hashedSeed();

	int gameModeId();

	boolean isPreviousGameModeAvailable();

	default boolean hasPreviousGameMode() {
		return previousGameModeId() != -1;
	}

	/** Returns -1 when the previous game mode is absent. */
	int previousGameModeId();

	boolean isDebugWorldAvailable();

	boolean debugWorld();

	boolean isFlatWorldAvailable();

	boolean flatWorld();

	boolean isKeepAttributesAvailable();

	/** The standalone keep-attributes flag used before the keep mask. */
	boolean keepAttributes();

	boolean isKeepMaskAvailable();

	byte keepMask();

	boolean isLastDeathLocationAvailable();

	default boolean hasLastDeathLocation() {
		return lastDeathPosition() != null;
	}

	/** Returns null when the last death location is absent. */
	@Nullable MinecraftKey lastDeathDimension();

	/** Returns null when the last death location is absent. */
	@Nullable BlockPosition lastDeathPosition();

	boolean isPortalCooldownAvailable();

	int portalCooldown();

	boolean isSeaLevelAvailable();

	int seaLevel();

	@Override
	default boolean appliesTo(ConnectionState state, PacketType type) {
		return state == ConnectionState.PLAY && type == PacketType.Server.RESPAWN;
	}
}
