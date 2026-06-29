package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;
import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Snap extends PlayerCheckPart<PlacementAnalysis> {
	private static final int HISTORY_LENGTH = 6;

	private float snapYaw;
	private float snapPitch;
	private int snapAge;
	private int degree;
	private long detectionTime;
	private final float[] yawHistory = new float[HISTORY_LENGTH];
	private final float[] pitchHistory = new float[HISTORY_LENGTH];

	public Snap(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(
		priority = ListenerPriority.HIGH,
		packetsIn = {
			FLYING, LOOK, POSITION, POSITION_LOOK
		}
	)
	public void receiveMovementPacket(PacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);
		MovementMetadata movementData = user.meta().movement();
		if (movementData.ticksPast(TELEPORT) == 0) {
			return;
		}

		double currentYawMotion = Math.abs(movementData.lastRotationYaw - movementData.rotationYaw);
		double currentPitchMotion = Math.abs(movementData.lastRotationPitch - movementData.rotationPitch);
		if (IntaveControl.SCAFFOLD_ACTION_DEBUG) {
			player.sendMessage(formatDouble(currentYawMotion, 4) + ", " + formatDouble(currentPitchMotion, 4));
		}
		boolean alphaCondition = pitchAt(1) > 70;
		int pitchLimit = alphaCondition ? 20 : 40;

    /*
      Are all of these values arbitrary? Yes.
      Are they based on just brute force testing? Absolutely.
      Will I ever change them? Probably not.
     */

		// 1st degree snap
		if (currentYawMotion < 15 && currentPitchMotion < 15 &&
			yawMotion(1) > 70 && pitchMotion(1) > pitchLimit &&
			yawMotion(2) < 10 && pitchMotion(2) < 10
		) {
			snapYaw = yawAt(1);
			snapPitch = pitchAt(1);
			snapAge = 0;
		}

		// 2nd degree snap
		if (currentYawMotion < 8 && currentPitchMotion < 8 &&
			(yawMotion(1) > 10 || pitchMotion(1) > 10) &&
			(yawMotion(2) > 10 || pitchMotion(2) > 10) &&
			absYawDiff(yawAt(1), yawAt(3)) > 70 && absPitchDiff(pitchAt(1), pitchAt(3)) > pitchLimit &&
			yawMotion(3) < 8 && pitchMotion(3) < 8
		) {
			snapYaw = yawAt(1);
			snapPitch = pitchAt(1);
			snapAge = 0;
			degree = 2;
		}

		// 3rd degree snap
//    if (yawMotion < 2 && pitchMotion < 2 &&
//      (yawMotion(1) > 10 || pitchMotion(1) > 10) &&
//      (yawMotion(2) > 10 || pitchMotion(2) > 10) &&
//      (yawMotion(3) > 10 || pitchMotion(3) > 10) &&
//      absYawDiff(yawAt(1), yawAt(4)) > 110 && absPitchDiff(pitchAt(1), pitchAt(4)) > pitchLimit &&
//      yawMotion(4) < 2 && pitchMotion(4) < 2
//    ) {
//      snapYaw = yawAt(1);
//      snapPitch = pitchAt(1);
//      snapAge = 0;
//      degree = 3;
////      player.sendMessage(ChatColor.RED + "3rd degree snap");
//    }

		if (yawMotion(1) > 30 && pitchMotion(1) > 30) {
			if (Math.abs(yawMotion(1) - yawMotion(2)) < 5 && Math.abs(pitchMotion(1) - pitchMotion(2)) < 5) {
				if (absYawDiff(yawAt(1), yawAt(3)) < 3 && absPitchDiff(pitchAt(1), pitchAt(3)) < 3) {
					Violation violation = Violation.builderFor(PlacementAnalysis.class)
						.forPlayer(player)
						.withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
						.withMessage(COMMON_FLAG_MESSAGE).withDetails("back snap")
						.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
						.withVL(0).build();
					Modules.violationProcessor().processViolation(violation);
				}
			}
		}

		float yaw = movementData.rotationYaw;
		float pitch = movementData.rotationPitch;

		for (int i = 0; i < yawHistory.length - 1; i++) {
			yawHistory[i] = yawHistory[i + 1];
			pitchHistory[i] = pitchHistory[i + 1];
		}

		yawHistory[yawHistory.length - 1] = yaw;
		pitchHistory[pitchHistory.length - 1] = pitch;
		snapAge++;
	}

	@BukkitEventSubscription
	public void on(BlockPlaceEvent place) {
		Player player = place.getPlayer();
		Location location = player.getLocation();
		float yaw = location.getYaw();
		float pitch = location.getPitch();
		if (IntaveControl.SCAFFOLD_ACTION_DEBUG) {
			player.sendMessage(ChatColor.DARK_PURPLE + " PLACE: " + yaw + " " + pitch);
		}

		if (System.currentTimeMillis() - detectionTime < 2_500) {
			Violation violation = Violation.builderFor(PlacementAnalysis.class)
				.forPlayer(player)
				.withDefaultThreshold()
				.withMessage(COMMON_FLAG_MESSAGE)
				.withDetails(asWord(degree) + "-tick block-aligned snap")
				.withVL(degree == 1 ? 20 : 2.5)
				.build();
			return;
		}

		if (snapAge > 20) {
			return;
		}

		float yawDiff = absYawDiff(snapYaw, yaw);
		float pitchDiff = absPitchDiff(snapPitch, pitch);
		boolean inCircle = yawDiff > (360f / 10f) || pitchDiff > (180f / 10f);

		if (inCircle) {
			return;
		}

		detectionTime = System.currentTimeMillis();
	}

	private static String asWord(int degree) {
		switch (degree) {
			case 0:
				return "none";
			case 1:
				return "one";
			case 2:
				return "two";
			case 3:
				return "three";
			default:
				return "unknown";
		}
	}

	private float yawAt(int age) {
		int index = HISTORY_LENGTH - age;
		if (index < 0) {
			return 0;
		}
		return yawHistory[index];
	}

	private float pitchAt(int age) {
		int index = HISTORY_LENGTH - age;
		if (index < 0) {
			return 0;
		}
		return pitchHistory[index];
	}

	private float yawMotion(int age) {
		int index = HISTORY_LENGTH - age;
		if (index < 0) {
			return 0;
		}
		return absYawDiff(yawHistory[index], yawHistory[index - 1]);
	}

	private float pitchMotion(int age) {
		int index = HISTORY_LENGTH - age;
		if (index < 0) {
			return 0;
		}
		return absPitchDiff(pitchHistory[index], pitchHistory[index - 1]);
	}

	private static float absYawDiff(float a, float b) {
		return Math.abs(yawDiff(a, b));
	}

	private static float absPitchDiff(float a, float b) {
		return Math.abs(pitchDiff(a, b));
	}

	private static float yawDiff(float alpha, float beta) {
		float phi = Math.abs(beta - alpha) % 360;
		return phi > 180 ? 360 - phi : phi;
	}

	private static float pitchDiff(float a, float b) {
		return Math.abs(a - b);
	}
}
