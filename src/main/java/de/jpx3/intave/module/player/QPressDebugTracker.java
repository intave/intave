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

package de.jpx3.intave.module.player;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.packet.ListenerPriority;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.BlockDigPacket;
import de.jpx3.intave.packet.contract.immutable.EntityVelocityPacket;
import de.jpx3.intave.packet.contract.mutable.MutableEntityVelocityPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.world.explosion.ExplosionOptionalMotionWirePacket;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.share.PlayerDigAction.DROP_ITEM;

public final class QPressDebugTracker extends Module {
	@OnReceive(priority = ListenerPriority.LATER)
	public void clientClickUpdate(User user, BlockDigPacket packet) {
		Player player = user.player();
		if (packet.hasAction() && packet.action() == DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR) {
			if (IntaveControl.TELEPORT_FAR_AWAY_ON_Q_PRESS) {
				Synchronizer.synchronize(user, () -> {
					Location from = player.getLocation().clone();
					Location randomLocation = player.getLocation().clone().add(Math.random() * 1000 - 500, 0, Math.random() * 1000 - 500);
					Block highestBlockAt = randomLocation.getWorld().getHighestBlockAt(randomLocation);
					randomLocation.setY(highestBlockAt.getY());
					PacketLogging logging = Modules.tracker().packetLogging();
					logging.logSystemMessage(user, () -> "TELEPORT ACTION source=Q_PRESS from=" + MathHelper.formatPosition(from) + " requested=" + MathHelper.formatPosition(randomLocation) + " destination_block=" + highestBlockAt.getType() + " destination_block_y=" + highestBlockAt.getY());
					boolean teleported = player.teleport(randomLocation);
					logging.logSystemMessage(user, () -> "TELEPORT ACTION RESULT source=Q_PRESS accepted=" + teleported + " server_position=" + MathHelper.formatPosition(player.getLocation()));

					if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
						user.sendMessage(IntavePlugin.prefix() + "Teleport to random " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " as " + ChatColor.RED + " it was command-requested");
					}
				});
			}

			if (IntaveControl.TELEPORT_UP_ON_Q_PRESS) {
				Synchronizer.synchronize(user, () -> user.teleportRelative(
					new Position(0, 4, 0)
				));
			}

			if (IntaveControl.GIVE_VELOCITY_ON_Q_PRESS) {
				Synchronizer.synchronize(user, () -> {
//          Vector randomVelocity = new Vector(Math.random() * 2 - 1, Math.random() * 2 - 1, Math.random() * 2 - 1);
//          player.setVelocity(new Vector(3, 0.4, 0.3));
					Vector randomVelocity = player.getLocation().getDirection().clone();
					randomVelocity.setY(0.4);
//          Vector randomVelocity = new Vector(0, 0.01, 0);
//					player.setVelocity(randomVelocity);
					sendVelocityPacket(user, randomVelocity);
					player.setFallDistance(0.0f);
					if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
						user.sendMessage(IntavePlugin.prefix() + "Set random velocity " + randomVelocity.getX() + " " + randomVelocity.getY() + " " + randomVelocity.getZ() + " as " + ChatColor.RED + " it was command-requested");
					}

//          Synchronizer.synchronizeDelayed(() -> {
//            // send explosion packet
//            sendExplosion(player, player.getLocation(), 4.0f, new Vector(0, -1, 0));
//          }, 2);
				});
			}

			if (IntaveControl.EXTREME_VELOCITY_ON_Q_PRESS) {
				Synchronizer.synchronize(user, () -> {
					Vector extremeVelocity = player.getLocation().getDirection().normalize().multiply(8.0);
					sendVelocityPacket(user, extremeVelocity);
					player.setFallDistance(0.0f);
					if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
						user.sendMessage(IntavePlugin.prefix() + "Requested extreme velocity " + extremeVelocity.getX() + " " + extremeVelocity.getY() + " " + extremeVelocity.getZ() + " as " + ChatColor.RED + " it was command-requested");
					}
				});
			}
		}
	}

	private static void sendVelocityPacket(User user, Vector velocity) {
		int protocol = PacketSender.bindingOf(user).protocolVersion();
		EntityVelocityPacket packet = velocityPacket(protocol, user.player().getEntityId(), velocity);
		PacketSender.send(user, packet.typedTo(PacketType.Server.ENTITY_VELOCITY));
	}

	static EntityVelocityPacket velocityPacket(int protocol, int entityId, Vector velocity) {
		MutableEntityVelocityPacket packet = WirePackets.createPlayPacket(PacketType.Server.ENTITY_VELOCITY, protocol);
		packet.setEntityId(entityId);
		packet.setMotion(new Motion(velocity.getX(), velocity.getY(), velocity.getZ()));
		return packet;
	}

	public static void sendExplosion(Player player, Location location, float radius, Vector knockback) {
		User user = UserRepository.userOf(player);
		int protocol = PacketSender.bindingOf(user).protocolVersion();
		if (protocol < 769) return;
		Motion motion = knockback == null ? null : new Motion(knockback.getX(), knockback.getY(), knockback.getZ());
		ExplosionOptionalMotionWirePacket packet = WirePackets.createPlayPacket(PacketType.Server.EXPLOSION, protocol);
		packet.setPosition(location.getX(), location.getY(), location.getZ());
		packet.setPower(radius);
		packet.setMotion(motion);
		packet.setDefaultEffects(protocol);
		PacketSender.send(user, packet.typedTo(PacketType.Server.EXPLOSION));
	}
}
