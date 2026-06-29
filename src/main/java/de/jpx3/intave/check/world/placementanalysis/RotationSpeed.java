package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class RotationSpeed extends PlayerCheckPart<PlacementAnalysis> {
	private static final int QUEUE_SIZE = 12;
	private static final int MIN_ACTIVATION_DATA = 4;
	private final int rotationLimit;
	private final List<Float> rotationHistory = new CopyOnWriteArrayList<>();
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
		Player player = event.getPlayer();
		User user = userOf(player);
		MovementMetadata movementData = user.meta().movement();
		float rotationMovement = Math.min(MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw), 360);
		if (System.currentTimeMillis() - lastBlockPlacement > 1000 || movementData.ticksPast(TELEPORT) <= 5) {
			return;
		}
		List<Float> rotationHistory = this.rotationHistory;
		if (rotationHistory.size() > 5 * 20) {
			rotationHistory.remove(0);
		}
		rotationHistory.add(rotationMovement);
	}

	@BukkitEventSubscription
	public void on(BlockPlaceEvent place) {
		Player player = place.getPlayer();
		User user = userOf(player);
		lastBlockPlacement = System.currentTimeMillis();

		Block blockAgainst = place.getBlockAgainst();
		if (blockAgainst == null) {
			return;
		}

		if (System.currentTimeMillis() - denyPlacementRequest < 3000) {
			place.setCancelled(true);
			return;
		}

		boolean placedBelow = place.getBlock().getY() < player.getLocation().getBlockY();
		boolean enoughBlockSamples = placementHistory.size() >= MIN_ACTIVATION_DATA;

		if (placedBelow && enoughBlockSamples && isBridgeCreation(placementHistory)) {
			List<Float> rotationHistory = this.rotationHistory;
			double rotationSum = 0.0;
			for (Float value : rotationHistory) {
				rotationSum += value;
			}

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
					.withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
					.withVL(10).build();
				Modules.violationProcessor().processViolation(violation);
				denyPlacementRequest = System.currentTimeMillis();
				user.meta().violationLevel().lastBlockPlaceDenyRequest = System.currentTimeMillis();
				place.setCancelled(true);
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

}
