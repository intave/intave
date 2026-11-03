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

package de.jpx3.intave.module.tracker.block;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.plib.reader.UpdateTagReader;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.user.User;

import java.util.List;
import java.util.Map;

public final class BlockTagTracker extends Module {

	@PacketSubscription(
		packetsOut = PacketType.Server.UPDATE_TAGS
	)
	public void onTags(
		User user, UpdateTagReader reader
	) {
//		PacketType.Configuration.Server.UPDATE_TAGS

		for (Map.Entry<MinecraftKey, List<MinecraftKey>> minecraftKeyListEntry : reader.readTags().entrySet()) {
			System.out.println("Tag: " + minecraftKeyListEntry.getKey() + " -> " + minecraftKeyListEntry.getValue());
		}
	}
}
