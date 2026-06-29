package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
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

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class SharpRotation extends PlayerCheckPart<PlacementAnalysis> {
	private long sharpRotations = 0;
	private long sharpRotationReset = System.currentTimeMillis();
	private long lastBlockPlacement = 0;
	private final List<Vector> lastBlocksPlaced = new ArrayList<>();

	public SharpRotation(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(
		priority = ListenerPriority.HIGH,
		packetsIn = {
			POSITION_LOOK, LOOK
		}
	)
	public void on(PacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);
		MovementMetadata movementData = user.meta().movement();
		float rotationMovement = Math.min(MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw), 360);

		boolean recentBlockPlacement = System.currentTimeMillis() - lastBlockPlacement < 2000;
		boolean hit = Math.abs(rotationMovement - 180) < 10;
		if (hit && recentBlockPlacement) {
			sharpRotations++;
		}
	}

	@BukkitEventSubscription
	public void on(BlockPlaceEvent place) {
		Player player = place.getPlayer();
		User user = userOf(player);

		if (place.getBlock().getY() < player.getLocation().getBlockY() &&
			place.getBlock().getY() == place.getBlockAgainst().getY()) {
			if (System.currentTimeMillis() - sharpRotationReset > 10000) {
				sharpRotations -= 1;
				sharpRotations /= 2;
				sharpRotationReset = System.currentTimeMillis();
			}
			lastBlockPlacement = System.currentTimeMillis();
			if (sharpRotations > 4 && blockAgainstWasPlaced(user, place.getBlockAgainst())) {
				String details = "maintains sharp 180deg rotations";
				Violation violation = Violation.builderFor(PlacementAnalysis.class)
					.forPlayer(player).withMessage(COMMON_FLAG_MESSAGE).withDetails(details)
					.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
					.withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
					.withVL(sharpRotations > 10 ? 10 : 0).build();
				Modules.violationProcessor().processViolation(violation);
				place.setCancelled(true);
			}
		}
		if (place.isCancelled()) {
			return;
		}
		if (lastBlocksPlaced.size() > 10) {
			lastBlocksPlaced.remove(0);
		}
		lastBlocksPlaced.add(place.getBlock().getLocation().toVector());
	}

	private boolean blockAgainstWasPlaced(User user, Block blockAgainst) {
		Vector vector = blockAgainst.getLocation().toVector();
		for (Vector location : lastBlocksPlaced) {
			if (location.distance(vector) == 0) {
				return true;
			}
		}
		return false;
	}
}
