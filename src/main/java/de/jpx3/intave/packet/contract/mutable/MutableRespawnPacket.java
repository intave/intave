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

package de.jpx3.intave.packet.contract.mutable;

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.packet.contract.immutable.RespawnPacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.MinecraftKey;

public interface MutableRespawnPacket extends RespawnPacket, Mutable {
	void setLegacyDimensionId(int legacyDimensionId);

	void setDimensionTypeId(int dimensionTypeId);

	void setDimensionTypeKey(MinecraftKey dimensionTypeKey);

	void setDimensionTypeData(CompoundTag dimensionTypeData);

	void setWorldKey(MinecraftKey worldKey);

	void setLevelType(String levelType);

	void setDifficultyId(int difficultyId);

	void setHashedSeed(long hashedSeed);

	void setGameModeId(int gameModeId);

	void setPreviousGameModeId(int previousGameModeId);

	default void clearPreviousGameModeId() {
		setPreviousGameModeId(-1);
	}

	void setDebugWorld(boolean debugWorld);

	void setFlatWorld(boolean flatWorld);

	void setKeepAttributes(boolean keepAttributes);

	void setKeepMask(byte keepMask);

	void setLastDeathLocation(MinecraftKey dimension, BlockPosition position);

	void clearLastDeathLocation();

	void setPortalCooldown(int portalCooldown);

	void setSeaLevel(int seaLevel);
}
