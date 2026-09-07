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

package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class RotationSpeed extends PlayerCheckPart<PlacementAnalysis> {
	private static final int QUEUE_SIZE = 12;
	private static final int MIN_ACTIVATION_DATA = 4;
	// Rotation activity represents one recent bridging sequence. Bounding it by
	// both time and count prevents old sequences from contaminating a new one.
	private static final int MAX_ROTATION_SAMPLES = 5 * 20;
	private static final long ROTATION_WINDOW_MS = 5000;
	// Report suspicious activity first; only deny placements after the shared
	// violation processor confirms that the player crossed a sustained threshold.
	private static final double PREVENTION_VL = 20;
	private final int rotationLimit;
	private final List<RotationSample> rotationHistory = new CopyOnWriteArrayList<>();
	private final List<PlacedBlock> placementHistory = new CopyOnWriteArrayList<>();
	private long lastBlockPlacement;
	private long denyPlacementRequest;

	public RotationSpeed(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
		rotationLimit = (int) parentCheck.configuration().settings().doubleBy("rotation-limit", 3000);
	}

	@PacketSubscription(
		priority = ListenerPriority.LOW,
		packetsIn = {
			POSITION_LOOK, LOOK
		}
	)
	public void on(PacketEvent event) {
		handleRotation(event.getPlayer());
	}

	@PacketSubscription(
		engine = Engine.PACKETEVENTS,
		priority = ListenerPriority.LOW,
		packetsIn = {
			POSITION_LOOK, LOOK
		}
	)
	public void on(Player player) {
		if (player == null) {
			return;
		}
		handleRotation(player);
	}

	/**
	 * Engine independent body: only the player's rotation metadata is read, so no packet field has
	 * to cross the engine boundary.
	 */
	private void handleRotation(Player player) {
		User user = userOf(player);
		MovementMetadata movementData = user.meta().movement();
		float rotationMovement = Math.min(MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw), 360);
		long now = System.currentTimeMillis();
		if (now - lastBlockPlacement > 1000 || movementData.ticksPast(TELEPORT) <= 5) {
			// Expire samples while collection is inactive so the next bridge cannot
			// inherit rotation activity from an older sequence.
			discardExpiredRotations(rotationHistory, now);
			return;
		}
		recordRotation(rotationHistory, rotationMovement, now);
	}

	@BukkitEventSubscription
	public void on(BlockPlaceEvent place) {
		Player player = place.getPlayer();
		User user = userOf(player);
		long now = System.currentTimeMillis();
		lastBlockPlacement = now;

		Block blockAgainst = place.getBlockAgainst();
		if (blockAgainst == null) {
			return;
		}

		if (now - denyPlacementRequest < 3000) {
			place.setCancelled(true);
			return;
		}

		boolean placedBelow = place.getBlock().getY() < player.getLocation().getBlockY();
		boolean enoughBlockSamples = placementHistory.size() >= MIN_ACTIVATION_DATA;

		if (placedBelow && enoughBlockSamples && isBridgeCreation(placementHistory)) {
			// Sum only the current window. A count-only history could combine
			// multiple legitimate bridging sequences into one violation.
			double rotationSum = rotationSum(rotationHistory, now);

			float limit = rotationLimit;
			if (!user.trustFactor().atLeast(TrustFactor.ORANGE)) {
				limit -= 500;
			}
			limit -= 750;

			if (rotationSum > limit) {
				Violation violation = Violation.builderFor(PlacementAnalysis.class)
					.forPlayer(player).withDefaultThreshold()
					.withMessage(COMMON_FLAG_MESSAGE)
					.withDetails("high rotation activity while placing blocks")
					.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
					.withVL(10).build();
				ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
				// Consume this evidence after reporting it. Reusing the same rotation
				// burst on following placements would repeatedly add violation level.
				rotationHistory.clear();
				// One unusual sequence may alert, but cannot immediately cancel building.
				// Prevention starts only after the shared VL has accumulated.
				if (violationContext.violationLevelAfter() > PREVENTION_VL) {
					denyPlacementRequest = now;
					user.meta().violationLevel().lastBlockPlaceDenyRequest = now;
					place.setCancelled(true);
				}
			}
		}
		if (place.isCancelled()) {
			return;
		}
		if (placementHistory.size() >= QUEUE_SIZE) {
			placementHistory.remove(0);
		}
		Vector blockPosition = place.getBlock().getLocation().toVector();
		Vector blockAgainstPosition = blockAgainst.getLocation().toVector();
		placementHistory.add(new PlacedBlock(blockPosition, blockAgainstPosition));
	}

	static void recordRotation(List<RotationSample> history, float movement, long now) {
		// Time expiry defines the activity window; the count cap is a defensive
		// memory bound for clients sending unusually many look packets.
		discardExpiredRotations(history, now);
		if (history.size() >= MAX_ROTATION_SAMPLES) {
			history.remove(0);
		}
		history.add(new RotationSample(movement, now));
	}

	static double rotationSum(List<RotationSample> history, long now) {
		// Prune at read time because a placement may occur without another look
		// packet that would otherwise trigger recordRotation().
		discardExpiredRotations(history, now);
		double sum = 0.0;
		for (RotationSample sample : history) {
			sum += sample.movement;
		}
		return sum;
	}

	private static void discardExpiredRotations(List<RotationSample> history, long now) {
		long cutoff = now - ROTATION_WINDOW_MS;
		history.removeIf(sample -> sample.timestamp < cutoff);
	}

	private boolean isBridgeCreation(List<PlacedBlock> blocks) {
		// we check for two things:
		// 1) the player placed these blocks against each other (with a few exceptions)
		// 2) the blocks are placed (roughly) horizontally

		int placedAgainstHorizontallyCount = 0;
		int connections = blocks.size() - 1;

		for (int i = connections; i > 0; i--) {
			PlacedBlock current = blocks.get(i);
			PlacedBlock next = blocks.get(i - 1);

			boolean placedAgainst = current.placedAgainstPosition.distance(next.position) == 0;
			boolean placedHorizontally = current.position.getBlockY() == next.position.getBlockY();

			if (placedAgainst && placedHorizontally) {
				placedAgainstHorizontallyCount++;
			}
		}
		return (double) placedAgainstHorizontallyCount / (double) connections > 0.3;
	}

	public static class PlacedBlock {
		// position measured as ints
		private final Vector position;
		private final Vector placedAgainstPosition;

		public PlacedBlock(Vector position, Vector placedAgainstPosition) {
			this.placedAgainstPosition = placedAgainstPosition;
			this.position = position;
		}
	}

	static final class RotationSample {
		private final float movement;
		private final long timestamp;

		RotationSample(float movement, long timestamp) {
			this.movement = movement;
			this.timestamp = timestamp;
		}
	}

}
