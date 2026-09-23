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

package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.player.ItemReleaseValidation;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

public final class InvalidRelease extends CheckPart<ProtocolScanner> {
	public InvalidRelease(ProtocolScanner parentCheck) {
		super(parentCheck);
	}

	@PacketSubscription(packetsIn = BLOCK_DIG)
	public void checkValidateRelease(PacketEvent event, BlockDigReader reader) {
		PacketContainer packet = event.getPacket();
		Player player = event.getPlayer();
		User user = userOf(player);
		EnumWrappers.PlayerDigType digType = reader.action();
		if (digType == null || user.protocolVersion() < 47) {
			return;
		}
		if (digType == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM) {
			EnumWrappers.Direction face = packet.getDirections().readSafely(0);
			// vanilla always sends down, anything else is a spoofed release
			// used to silently drop the server-side item state while the
			// item stays in use client-side, which removes the slowdown
			// Fix https://github.com/Raven-APlus/RavenAPlus/blob/master/src/main/java/keystrokesmod/module/impl/movement/noslow/IntaveNoSlow.java
			if (ItemReleaseValidation.isSpoofedRelease(face)) {
				// drop the packet before it reaches the hand tracker, so the
				// hand stays active and the movement simulation keeps
				// expecting the slowdown instead of trusting the desync
				event.setCancelled(true);
				Violation violation = Violation.builderFor(ProtocolScanner.class)
					.forPlayer(player).withMessage("sent invalid release").withDetails("face " + face.name().toLowerCase(Locale.ROOT))
					.withVL(3)
					.build();
				Modules.violationProcessor().processViolation(violation);
				InventoryMetadata inventory = user.meta().inventory();
				inventory.lastFoodConsumptionBlockRequest = System.currentTimeMillis();
				if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
					user.sendMessage(IntavePlugin.prefix() + "Blocked spoofed item release because of " + ChatColor.RED + "an invalid release packet");
				}
			}
		}
	}
}
