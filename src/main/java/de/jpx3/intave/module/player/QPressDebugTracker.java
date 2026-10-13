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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.WrappedParticle;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.packet.reader.BlockDigReader;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.share.PlayerDigAction;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

import static de.jpx3.intave.share.PlayerDigAction.DROP_ITEM;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;

public final class QPressDebugTracker extends Module {
	@PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = {BLOCK_DIG})
	public void clientClickUpdate(PacketEvent event, BlockDigReader reader) {
		Player player = event.getPlayer();
		User user = UserRepository.userOf(player);
		if (reader.action() == DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR) {
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
					player.setVelocity(randomVelocity);
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
					Vector transmittedVelocity = sendVelocityPacket(player, extremeVelocity);
					player.setFallDistance(0.0f);
					if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
						user.sendMessage(IntavePlugin.prefix() + "Sent extreme velocity " + transmittedVelocity.getX() + " " + transmittedVelocity.getY() + " " + transmittedVelocity.getZ() + " as " + ChatColor.RED + " it was command-requested");
					}
				});
			}
		}
	}

	private static Vector sendVelocityPacket(Player player, Vector velocity) {
		PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_VELOCITY);
		packet.getIntegers().write(0, player.getEntityId());
		Vector transmittedVelocity = velocity;
		if (packet.getVectors().size() > 0) {
			packet.getVectors().write(0, velocity);
		} else {
			int motionX = encodeLegacyVelocity(velocity.getX());
			int motionY = encodeLegacyVelocity(velocity.getY());
			int motionZ = encodeLegacyVelocity(velocity.getZ());
			packet.getIntegers().write(1, motionX);
			packet.getIntegers().write(2, motionY);
			packet.getIntegers().write(3, motionZ);
			transmittedVelocity = new Vector(motionX / 8000.0D, motionY / 8000.0D, motionZ / 8000.0D);
		}
		ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
		return transmittedVelocity;
	}

	private static int encodeLegacyVelocity(double velocity) {
		return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, velocity * 8000.0D));
	}

	public static void sendExplosion(Player player, Location location, float radius, Vector knockback) {
		if (!MinecraftVersions.VER1_21_4.atOrAbove()) {
			return;
		}
		PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.EXPLOSION);
		packet.getVectors().write(0, location.toVector());
		packet.getFloat().write(0, radius);
		packet.getIntegers().write(0, 0);
		packet.getOptionals(BukkitConverters.getVectorConverter()).write(0, Optional.ofNullable(knockback));
		packet.getNewParticles().write(0, WrappedParticle.create(Particle.CLOUD, null));
		packet.getSoundEffects().write(0, Sound.ENTITY_GENERIC_EXPLODE);
		packet.getModifier().write(6, createEmptyWeightedList(packet));
		ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
	}

	private static Object createEmptyWeightedList(PacketContainer packet) {
		try {
			// Field 6 is the WeightedList of block explosion particles
			Class<?> type = packet.getModifier().getField(6).getType();
			Method emptyFactory = Arrays.stream(type.getDeclaredMethods()).filter(method -> Modifier.isStatic(method.getModifiers())).filter(method -> method.getParameterCount() == 0).filter(method -> method.getReturnType() == type).findFirst().orElseThrow(() -> new IllegalStateException("Cannot find empty WeightedList factory"));
			emptyFactory.setAccessible(true);
			return emptyFactory.invoke(null);

		} catch (ReflectiveOperationException exception) {
			throw new RuntimeException("Could not create explosion block particle list", exception);
		}
	}
}
