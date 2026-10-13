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

package de.jpx3.intave.packet.reader;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.EntityAnimation;

public final class AnimationReader extends EntityReader {
	public EntityAnimation animation() {
		return decodeAnimation(packet().getIntegers().read(1), MinecraftVersions.VER26_3.atOrAbove());
	}

	static EntityAnimation decodeAnimation(int id, boolean modern) {
		// 26.3 moved swings to ClientboundSwingAnimationPacket and renumbered the remaining actions.
		if (modern) {
			switch (id) {
				case 0: return EntityAnimation.WAKEUP;
				case 1: return EntityAnimation.CRIT;
				case 2: return EntityAnimation.CRIT_MAGIC;
				default: return null;
			}
		}
		return id >= 0 && id < EntityAnimation.values().length ? EntityAnimation.values()[id] : null;
	}
}
