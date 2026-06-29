package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.math.MathHelper.averageOf;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketOrder extends PlayerCheckPart<PlacementAnalysis> {
	private double packetOrderBalance;
	private long lastIncrement;
	private final List<Long> placementDifferences = new ArrayList<>();
	private long lastMovePacket;

	public PacketOrder(User user, PlacementAnalysis parentCheck) {
		super(user, parentCheck);
	}

	@PacketSubscription(
		packetsIn = {
			FLYING, LOOK, POSITION, POSITION_LOOK
		}
	)
	public void receiveMovement(PacketEvent event) {
		lastMovePacket = System.currentTimeMillis();
	}

	@PacketSubscription(
		packetsIn = {
			BLOCK_PLACE
		}
	)
	public void checkPlacementPacketOrder(PacketEvent event) {
		Player player = event.getPlayer();
		User user = userOf(player);
		PacketContainer packet = event.getPacket();

		long now = System.currentTimeMillis();
		if (blockingPlacementPacket(packet) || user.meta().protocol().combatUpdate()) {
			return;
		}

		long timeDiff = now - lastMovePacket;
		placementDifferences.add(timeDiff);

		if (placementDifferences.size() == 4) {
			double average = averageOf(placementDifferences);

			if (average < 20) {
				long permutePacketIncrementDiff = now - lastIncrement;

				if (permutePacketIncrementDiff > 20) {
					if (packetOrderBalance++ >= 2) {
						Violation violation = Violation.builderFor(PlacementAnalysis.class)
							.forPlayer(player)
							.withMessage(COMMON_FLAG_MESSAGE)
							.withDetails("invalid packet order")
							.withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "cloud-thresholds.on-premise")
							.withVL(2)
							.build();
						ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
						if (violationContext.violationLevelAfter() > 5) {
							//dmc2
							parentCheck().applyPlacementAnalysisDamageCancel(user, "2");
						}
					}
					lastIncrement = now;
				}

			} else if (packetOrderBalance >= 0) {
				packetOrderBalance--;
			}

			placementDifferences.clear();
		}
	}

	private boolean blockingPlacementPacket(PacketContainer packet) {
		Integer integer = packet.getIntegers().readSafely(0);
		return integer != null && integer == 255;
	}

}
