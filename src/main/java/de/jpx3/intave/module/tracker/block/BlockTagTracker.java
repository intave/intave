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

import com.github.retrooper.packetevents.event.PacketSendEvent;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.UpdateTagReader;
import de.jpx3.intave.packet.view.PacketEventsUpdateTagView;
import de.jpx3.intave.packet.view.ProtocolLibUpdateTagView;
import de.jpx3.intave.packet.view.UpdateTagView;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.user.User;

import java.util.List;
import java.util.Map;

public final class BlockTagTracker extends Module {

	@PacketSubscription(
		packetsOut = PacketId.Server.UPDATE_TAGS
	)
	public void onTags(
		User user, UpdateTagReader reader
	) {
//		PacketType.Configuration.Server.UPDATE_TAGS

		handleTags(new ProtocolLibUpdateTagView(reader));
	}

	@PacketSubscription(
		engine = Engine.PACKETEVENTS,
		packetsOut = PacketId.Server.UPDATE_TAGS
	)
	public void onTags(PacketSendEvent event) {
		PacketEventsUpdateTagView view = PacketEventsUpdateTagView.of(event);
		if (view == null) {
			return;
		}
		handleTags(view);
	}

	/** Engine independent tag handling; see {@link UpdateTagView}. */
	private void handleTags(UpdateTagView view) {
		for (Map.Entry<MinecraftKey, List<MinecraftKey>> minecraftKeyListEntry : view.readTags().entrySet()) {
			System.out.println("Tag: " + minecraftKeyListEntry.getKey() + " -> " + minecraftKeyListEntry.getValue());
		}
	}
}
