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
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
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
	public void checkValidateRelease(PacketEvent event) {
		PacketContainer packet = event.getPacket();
		Player player = event.getPlayer();
		User user = userOf(player);
		EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().readSafely(0);
		if (digType == null || user.protocolVersion() < 47) {
			return;
		}
		if (digType == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM) {
			EnumWrappers.Direction face = packet.getDirections().readSafely(0);
			// Vanilla always sends DOWN
			// Fix https://github.com/Raven-APlus/RavenAPlus/blob/master/src/main/java/keystrokesmod/module/impl/movement/noslow/IntaveNoSlow.java
			if (face != EnumWrappers.Direction.DOWN) {
				handleInvalidRelease(player, user, face.name().toLowerCase(Locale.ROOT));
			}
		}
	}

	/**
	 * PacketEvents entry point. No engine neutral view exists for the digging packet, so the two
	 * fields this check reads - the dig action and the block face - are pulled straight from the
	 * PacketEvents wrapper here. Both enums carry the same constant names as ProtocolLib's
	 * {@code EnumWrappers}, so the violation details string is byte for byte the same.
	 */
	@PacketSubscription(engine = Engine.PACKETEVENTS, packetsIn = BLOCK_DIG)
	public void checkValidateRelease(PacketReceiveEvent event) {
		if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
			return;
		}
		Object rawPlayer = event.getPlayer();
		if (!(rawPlayer instanceof Player)) {
			return;
		}
		Player player = (Player) rawPlayer;
		User user = userOf(player);
		// Decoded once: every PacketEvents getter re-reads the packet buffer.
		WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
		DiggingAction digType = wrapper.getAction();
		if (digType == null || user.protocolVersion() < 47) {
			return;
		}
		if (digType == DiggingAction.RELEASE_USE_ITEM) {
			BlockFace face = wrapper.getBlockFace();
			// Vanilla always sends DOWN
			if (face != BlockFace.DOWN) {
				handleInvalidRelease(player, user, face == null ? "null" : face.name().toLowerCase(Locale.ROOT));
			}
		}
	}

	/** Engine independent body: everything past "the client sent a release with a bad face". */
	private void handleInvalidRelease(Player player, User user, String faceName) {
		Violation violation = Violation.builderFor(ProtocolScanner.class)
			.forPlayer(player).withMessage("sent invalid release").withDetails("face " + faceName)
			.withVL(3)
			.build();
		Modules.violationProcessor().processViolation(violation);
		InventoryMetadata inventory = user.meta().inventory();
		inventory.lastFoodConsumptionBlockRequest = System.currentTimeMillis();
		inventory.releaseItemNextTick();
		if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
			user.player().sendMessage(IntavePlugin.prefix() + "Requesting item usage reset because of " + ChatColor.RED + "an invalid release packet");
		}
	}
}
