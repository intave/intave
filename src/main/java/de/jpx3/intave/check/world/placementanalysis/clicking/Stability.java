package de.jpx3.intave.check.world.placementanalysis.clicking;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Stability extends PlayerCheckPart<PlacementAnalysis> {
	private static final int BUFFER_TIMEOUT = 4000;
	private static final int BUFFER_LENGTH = 50;
	private final Queue<Long> places = new ArrayDeque<>();
	private final Queue<Long> deviations = new ArrayDeque<>();
	private double vl = 0;
	private long lastSwing = 0;
	private long started = System.currentTimeMillis();

	public Stability(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(
		packetsIn = BLOCK_PLACE
	)
	public void receiveSwing(PacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);

		// Calculating when the last swing was
		long lastSwing = this.lastSwing;
		long swingDifference = System.currentTimeMillis() - lastSwing;
		this.lastSwing = System.currentTimeMillis();

		// When the check is disabled, there is no need to check
		if (checkDeactivated(user, swingDifference)) {
			places.clear();
			return;
		}

		if (places.isEmpty()) {
			started = System.currentTimeMillis();
		}
		places.add(swingDifference);

		if (places.size() >= BUFFER_LENGTH) {
			long length = System.currentTimeMillis() - started;

			double standardDeviation = standardDeviation(places);
			// Necessary for the statistically low variance check
			deviations.add((long) standardDeviation);
			places.clear();
		}

		// After we got 5 deviation samples, we are going to check the deviation of these samples, if it's too low, the player is performing a long-term consistency
		if (deviations.size() >= 5) {
			double std = standardDeviation(deviations);

			long length = System.currentTimeMillis() - started;

			if (std < 10 && length < 4000) {
				int vlAdd = 1;
				vl += vlAdd;
				if (vl > 2) {
					Violation violation = Violation.builderFor(PlacementAnalysis.class)
						.forPlayer(player).withDefaultThreshold()
						.withMessage(COMMON_FLAG_MESSAGE)
						.withDetails("clicking stability")
						.appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
						.withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
						.withVL(2.5).build();
					Modules.violationProcessor().processViolation(violation);

					if (vl > 6) {
						//dmc45
						user.nerfPermanently(AttackNerfStrategy.GARBAGE_HITS, "45");
						user.nerf(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "45");
						user.nerf(AttackNerfStrategy.CRITICALS, "45");
						vl -= 0.2;
						vl *= 0.98;
					}
				}
			} else if (vl > 0) {
				vl -= 0.2;
				vl *= 0.98;
			}
			deviations.clear();
		}
	}

	private boolean checkDeactivated(
		User user,
		long swingDifference
	) {
		AttackMetadata attack = user.meta().attack();
		ItemStack heldItem = user.meta().inventory().heldItem();
		return swingDifference > BUFFER_TIMEOUT ||
			attack.inBreakProcess ||
			System.currentTimeMillis() - attack.lastBreak < 3000 ||
			(heldItem != null && heldItem.getType() == Material.FISHING_ROD);
	}

	private double standardDeviation(Collection<? extends Number> sd) {
		double sum = 0, newSum = 0;
		for (Number v : sd) {
			sum = sum + v.doubleValue();
		}
		double mean = sum / sd.size();
		for (Number v : sd) {
			newSum = newSum + (v.doubleValue() - mean) * (v.doubleValue() - mean);
		}
		return Math.sqrt(newSum / sd.size());
	}

}
