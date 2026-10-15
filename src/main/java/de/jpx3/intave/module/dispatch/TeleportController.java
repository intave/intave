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

package de.jpx3.intave.module.dispatch;

import ac.intave.samples.event.TeleportEvent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.share.MovementCorrection;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.nayoro.SampleTypes;
import de.jpx3.intave.packet.reader.EntityVelocityReader;
import de.jpx3.intave.packet.reader.ExplosionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.plib.PacketSender;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.packet.reader.PlayerTeleportReader;
import de.jpx3.intave.packet.reader.TeleportAcceptReader;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionAndRotation;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.share.Teleport;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.LONG_TELEPORT;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.feedback.FeedbackOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TELEPORT_ACCEPT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;

public final class TeleportController implements PacketEventSubscriber {
	private final static int TELEPORT_RESEND_TIMEOUT_TICKS = 40;
	private boolean teleportFeedbackSyncEnforcement = true;
	private final BiConsumer<User, Runnable> scheduler;
	private final BiConsumer<User, Teleport> transport;
	private final BiConsumer<User, TeleportEvent> eventEmitter;

	public TeleportController() {
		this.scheduler = Synchronizer::synchronize;
		this.transport = this::transmitTeleport;
		this.eventEmitter = (user, event) -> Modules.nayoro().emit(user, event);
	}

	TeleportController(BiConsumer<User, Runnable> scheduler, BiConsumer<User, Teleport> transport) {
		this(scheduler, transport, (user, event) -> {});
	}

	TeleportController(
		BiConsumer<User, Runnable> scheduler,
		BiConsumer<User, Teleport> transport,
		BiConsumer<User, TeleportEvent> eventEmitter
	) {
		this.scheduler = scheduler;
		this.transport = transport;
		this.eventEmitter = eventEmitter;
	}

	public void setup() {
		YamlConfiguration settings = IntavePlugin.singletonInstance().settings();
		String path = "compatibility.position-feedback-sync-enforcement";

		Modules.linker().packetEvents().linkSubscriptionsIn(this);

		boolean defaultSetting = true;

		if (Bukkit.getName().contains("Airplane") || Bukkit.getName().contains("Guard")) {
			IntavePlugin.singletonInstance().logger().info("Detected GuardSpigot server, disabling position feedback sync enforcement");
			teleportFeedbackSyncEnforcement = false;
		} else {
			teleportFeedbackSyncEnforcement = settings.getBoolean(path, defaultSetting);
		}
	}

	@PacketSubscription(
		priority = ListenerPriority.LOW,
		packetsOut = {
			POSITION
		}
	)
	public void onOutgoingTeleport(
		PacketEvent event,
		PlayerTeleportReader reader
	) {
		Player player = event.getPlayer();
		User user = UserRepository.userOf(player);
		MovementMetadata movementData = user.meta().movement();
		ProtocolMetadata protocol = user.meta().protocol();
		ReentrantLock teleportLock = movementData.teleportLock;
		try {
			teleportLock.lock();
			movementData.replaceRecoveryWithExternalTeleport();
			long teleportSequence = movementData.teleportSequence;
			movementData.teleportSequence = teleportSequence + 1;
			Teleport teleport = reader.readTeleport(teleportSequence);

			if (IntaveControl.DEBUG_TELEPORT_PACKET_STACKTRACE) {
				System.out.println("Teleporting " + player.getName() + " with " + teleport);
				Thread.dumpStack();
			}

			if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
				IntaveLogger.logger().info("[Intave] Sent teleportation request to " + player.getName());
			}

			if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
				user.sendMessage(IntavePlugin.prefix() + "You were instructed to teleport to " +
					teleport.change().position() + " as " + ChatColor.RED + " it was server-requested");
			}

			if (teleport.additiveMotionPacket() == null && protocol.legacyTeleportRelativeMotionBehavior()) {
				teleport.copyRelPosFlagsToRelDeltaFlags();
			}

			if (teleportFeedbackSyncEnforcement) {
				user.doubleTickFeedback(
					event,
					() -> beforeTeleportTransactionReceive(user, teleport),
					() -> afterTeleportTransactionReceive(user, teleport),
					SELF_SYNCHRONIZATION
				);
			} else {
				teleport.allow();
			}
			movementData.teleportResendCountdown = TELEPORT_RESEND_TIMEOUT_TICKS;
			movementData.pendingTeleports.getAndUpdate(queue -> {
				queue.add(teleport);
				return queue;
			});
		} finally {
			teleportLock.unlock();
			reader.release();
		}
	}

	@PacketSubscription(
		priority = ListenerPriority.NORMAL,
		packetsIn = {TELEPORT_ACCEPT}
	)
	public void receiveTeleportAccept(
		User user, TeleportAcceptReader reader
	) {
		receiveTeleportAccept(user, reader.teleportId(), reader.positionAndRotation());
	}

	void receiveTeleportAccept(User user, int teleportId, PositionAndRotation acceptedState) {
		// Combined acknowledgements bypass the movement packet's finite-value check.
		if (acceptedState != null && !acceptedState.isFinite()) {
			return;
		}
		MovementMetadata movementData = user.meta().movement();
		movementData.lastTeleportAcceptId = teleportId;
		movementData.sentTeleportIdBefore = true;
		if (acceptedState != null && user.meta().protocol().teleportAcceptIncludesPositionAndRotation()) {
			confirmTeleport(user, acceptedState.position(), acceptedState.rotation());
		}
	}

	public boolean setbackTeleportsAllowed(User user) {
		MovementMetadata movementData = user.meta().movement();
		ReentrantLock lock = movementData.teleportLock;
		try {
			lock.lock();
			return !movementData.inRecovery && movementData.pendingTeleports.get().isEmpty();
		} finally {
			lock.unlock();
		}
	}

	public void movementCorrection(User user, PositionMoveRotation change) {
		movementCorrection(user, change, null);
	}

	public void movementCorrection(User user, MovementCorrection correction) {
		movementCorrection(user, correction.change(), correction.onGround());
	}

	private void movementCorrection(User user, PositionMoveRotation change, Boolean onGround) {
		MovementMetadata movement = user.meta().movement();
		movement.teleportLock.lock();
		try {
			movement.invalidMovement = true;
			if (!movement.pendingTeleports.get().isEmpty()) {
				movement.recoverPendingTeleport();
				return;
			}
			long recovery = movement.beginRecovery();
			if (recovery == -1) {
				return;
			}
			PositionMoveRotation snapshot = PositionMoveRotation.withoutRotation(
				Position.mutableCopy(change.position()), change.motion().copy()
			);
			scheduler.accept(user, () -> {
				movement.teleportLock.lock();
				try {
					if (!movement.isRecovering(recovery)) return;
					movement.finishRecoveryOnNextTeleport(recovery);
					teleport(user, snapshot, Relative.RELATIVE_ROTATION, onGround, true);
				} finally {
					movement.teleportLock.unlock();
				}
			});
		} finally {
			movement.teleportLock.unlock();
		}
	}

	public boolean processMovementPackets(User user) {
		MovementMetadata movementData = user.meta().movement();
		ReentrantLock lock = movementData.teleportLock;
		try {
			lock.lock();
			return !movementData.inRecovery;
		} finally {
			lock.unlock();
		}
	}

	public boolean forwardMovementPackets(User user) {
		if (!processMovementPackets(user)) {
			return false;
		}
		MovementMetadata movementData = user.meta().movement();
		ReentrantLock lock = movementData.teleportLock;
		try {
			lock.lock();
			return movementData.pendingTeleports.get().isEmpty();
		} finally {
			lock.unlock();
		}
	}

	// return true if the movement packet was a teleport
	@DispatchTarget
	boolean receiveMove(PacketEvent event, PlayerMoveReader reader) {
		Player player = event.getPlayer();
		User user = UserRepository.userOf(player);
		return confirmTeleport(user, reader.position(), reader.rotation());
	}

	boolean confirmTeleport(User user, Position sentPosition, Rotation sentRotation) {
		MovementMetadata movementData = user.meta().movement();
		ProtocolMetadata protocol = user.meta().protocol();

		if (protocol.supportsTeleportAccepts() && !movementData.sentTeleportIdBefore) {
			return false;
		}

		ReentrantLock teleportLock = movementData.teleportLock;
		TeleportEvent event = null;
		try {
			teleportLock.lock();
			Deque<Teleport> teleports = movementData.pendingTeleports.get();
			Teleport first = teleports.peekFirst();
			if (first == null) {
				return false;
			}

			if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
//			user.sendMessage(IntavePlugin.prefix() + "Received movement packet " + reader.position() + " " + reader.rotation());
				String overallStatus = "";
				overallStatus += "P:"+teleports.size()+" ";
				overallStatus += "A:"+first.isAllowed()+" ";
				overallStatus += "V:"+first.wasAccepted()+" ";
				overallStatus += "I:"+first.id()+" ";
				overallStatus += "L:"+movementData.lastTeleportAcceptId+" ";

				ActionBar.sendActionBar(user.player(), overallStatus);
			}

			int teleportId = movementData.lastTeleportAcceptId;
			Position lastPosition = movementData.verifiedLastPosition();
			Rotation currentRotation = movementData.rotation();

			PositionMoveRotation expected = first.expectedPositionMoveRotation(
				lastPosition, currentRotation, movementData.mutableBaseMotionCopy()
			);

			double positionOffset = expected.position().distanceTo(sentPosition);
			float rotationOffset = expected.rotation().distanceTo(sentRotation);

			if (first.matchesId(teleportId) && first.matches(
				lastPosition, currentRotation,
				sentPosition, sentRotation,
				0.001, Float.NaN
			)) {
				teleports.pollFirst();
				first.accept();
				expected.applyTo(movementData);
				movementData.discardVelocityBefore(first.uniqueId());
				// Rotation is not validated, so trust the client's view: relative rotations are
				// resolved against its current view, which we may not know exactly. The teleport
				// also starts a new rotation baseline, as it is not a turn made by the player.
				movementData.setRotation(sentRotation);
				movementData.setLastRotation(sentRotation);
				movementData.activeTick(TELEPORT);
				if (MathHelper.resolveHorizontalDistance(
					lastPosition.getX(), lastPosition.getZ(), expected.position().getX(), expected.position().getZ()
				) > 20) {
					movementData.activeTick(LONG_TELEPORT);
				}
				if (first.simulatedOnGround() != null) {
					movementData.onGround = first.simulatedOnGround();
					movementData.setLastOnGround(first.simulatedOnGround());
				}
				movementData.completeRecovery(first);
				if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
					user.sendMessage(IntavePlugin.prefix() + "Movement matched the teleport request to " + expected);
				}
				Integer acceptedTeleportId = first.id().isPresent() ? first.id().getAsInt() : null;
				event = new TeleportEvent(
					SampleTypes.position(expected.position()),
					SampleTypes.rotation(expected.rotation()),
					acceptedTeleportId
				);
			} else {
				if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
					user.sendMessage(IntavePlugin.prefix() + "Movement did not match the teleport request (delta-position: " + positionOffset + ", delta-rotation: " + rotationOffset + ") to " + expected);
				}
			}
		} finally {
			teleportLock.unlock();
		}
		if (event == null) {
			return false;
		}
		eventEmitter.accept(user, event);
		return true;
	}

	public void onResendTimeout(User user) {
		if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
			user.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Teleport resend timeout reached, clearing pending teleports.");
		}
		MovementMetadata movementData = user.meta().movement();
		ReentrantLock teleportLock = movementData.teleportLock;
		try {
			teleportLock.lock();
			Deque<Teleport> teleports = movementData.pendingTeleports.get();
			Teleport latest = teleports.peekLast();
			if (latest == null) {
				return;
			}
			// Resolve the outstanding chain in send order. Replaying an older relative
			// request can apply its offset twice or undo a newer server destination.
			PositionMoveRotation target = new PositionMoveRotation(
				movementData.verifiedLastPosition(), movementData.mutableBaseMotionCopy(), movementData.rotation());
			Boolean onGround = null;
			for (Teleport pending : teleports) {
				target = target.merge(pending.change(), pending.relativeSet());
				if (pending.simulatedOnGround() != null) onGround = pending.simulatedOnGround();
			}
			// Keep the logical sequence and wire ID: native server teleports still
			// require their original acknowledgement. Callback identity belongs to
			// this transmission instance, not to the logical request or packet ID.
			target = new PositionMoveRotation(target.position(),
				movementData.preserveVelocity(target.motion(), latest.uniqueId()), target.rotation());
			Teleport retry = Teleport.of(latest.uniqueId(), latest.id(), target,
				EnumSet.noneOf(Relative.class), onGround, MinecraftVersions.VER1_21_3.atOrAbove());
			teleports.clear();
			sendTeleport(user, retry);
		} finally {
			teleportLock.unlock();
		}
	}

	public void teleport(User user, PositionMoveRotation change, Set<Relative> relativeSet) {
		teleport(user, change, relativeSet, null, false);
	}

	private void teleport(User user, PositionMoveRotation change, Set<Relative> relativeSet, Boolean onGround, boolean preserveServerMotion) {
		MovementMetadata movement = user.meta().movement();
		movement.teleportLock.lock();
		try {
			if (!movement.pendingTeleports.get().isEmpty()) return;
			movement.replaceRecoveryWithExternalTeleport();
			if (preserveServerMotion) {
				change = new PositionMoveRotation(change.position(), movement.preserveVelocity(change.motion(), -1), change.rotation());
			}
			OptionalInt id = user.meta().protocol().supportsTeleportAccepts()
				? OptionalInt.of(-ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE))
				: OptionalInt.empty();
			long teleportSequence = movement.teleportSequence;
			movement.teleportSequence = teleportSequence + 1;
			Teleport teleport = Teleport.of(
				teleportSequence, id, change,
				relativeSet, onGround,
				MinecraftVersions.VER1_21_3.atOrAbove()
			);
			if (teleport.additiveMotionPacket() == null
				&& user.meta().protocol().legacyTeleportRelativeMotionBehavior()
			) {
				teleport.copyRelPosFlagsToRelDeltaFlags();
			}
			sendTeleport(user, teleport);
		} finally {
			movement.teleportLock.unlock();
		}
	}

	private void sendTeleport(User user, Teleport teleport) {
		MovementMetadata movement = user.meta().movement();
		movement.pendingTeleports.get().add(teleport);
		movement.teleportResendCountdown = TELEPORT_RESEND_TIMEOUT_TICKS;
		transport.accept(user, teleport);
	}

	private void transmitTeleport(User user, Teleport teleport) {
		PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.POSITION);
		try (PlayerTeleportReader reader = PacketReaders.readerOf(packet)) {
			reader.writeTeleport(teleport);
		}
		PacketContainer companion = motionCompanionPacket(user.player(), teleport);
		user.tickFeedback(() -> beforeTeleportTransactionReceive(user, teleport));
		PacketSender.sendServerPacketWithoutEvent(user.player(), packet);
		if (companion != null) {
			PacketSender.sendServerPacketWithoutEvent(user.player(), companion);
		}
		user.tickFeedback(() -> afterTeleportTransactionReceive(user, teleport));
	}

	private PacketContainer motionCompanionPacket(Player player, Teleport teleport) {
		Boolean additive = teleport.additiveMotionPacket();
		if (MinecraftVersions.VER1_21_3.atOrAbove() || additive == null) {
			return null;
		}
		PacketContainer companion = ProtocolLibrary.getProtocolManager().createPacket(additive ?
			PacketType.Play.Server.EXPLOSION : PacketType.Play.Server.ENTITY_VELOCITY
		);
		if (additive) {
			try (ExplosionReader reader = PacketReaders.readerOf(companion)) {
				reader.setSilentDefaults();
				reader.setMotion(teleport.change().motion());
			}
		} else {
			try (EntityVelocityReader reader = PacketReaders.readerOf(companion)) {
				reader.setEntityId(player.getEntityId());
				reader.setMotion(teleport.change().motion());
			}
		}
		return companion;
	}

	void beforeTeleportTransactionReceive(User user, Teleport teleport) {
		MovementMetadata movement = user.meta().movement();
		movement.teleportLock.lock();
		try {
			if (movement.pendingTeleports.get().stream().noneMatch(pending -> pending == teleport)) return;
			teleport.allow();
		} finally {
			movement.teleportLock.unlock();
		}
	}

	void afterTeleportTransactionReceive(User user, Teleport teleport) {
		MovementMetadata movement = user.meta().movement();
		movement.teleportLock.lock();
		try {
			// A confirmation or retry may have retired this request already.
			if (movement.pendingTeleports.get().stream().noneMatch(pending -> pending == teleport)) return;
			if (!teleport.isAllowed()) {
				user.kick("Severe error occurred in Intave. Please contact the server administrator.");
				return;
			}
			teleport.disallow();
			if (!teleport.wasAccepted() && movement.pendingTeleports.get().peekFirst() == teleport) {
				onResendTimeout(user);
			}
		} finally {
			movement.teleportLock.unlock();
		}
	}
}
