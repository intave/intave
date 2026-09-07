package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.view.PacketEventsBlockInteractionView;
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
		handleMovement();
	}

	@PacketSubscription(
		engine = Engine.PACKETEVENTS,
		packetsIn = {
			FLYING, LOOK, POSITION, POSITION_LOOK
		}
	)
	public void receiveMovement(Player player) {
		handleMovement();
	}

	/** Engine independent body: a movement packet only stamps the arrival time here. */
	private void handleMovement() {
		lastMovePacket = System.currentTimeMillis();
	}

	@PacketSubscription(
		packetsIn = {
			BLOCK_PLACE
		}
	)
	public void checkPlacementPacketOrder(PacketEvent event) {
		handlePlacement(event.getPlayer(), blockingPlacementPacket(event.getPacket()));
	}

	/**
	 * PacketEvents entry point. The 255 face index the view reports is the same "blocking
	 * placement" marker the ProtocolLib body reads out of the packet's first integer.
	 */
	@PacketSubscription(
		engine = Engine.PACKETEVENTS,
		packetsIn = {
			BLOCK_PLACE
		}
	)
	public void checkPlacementPacketOrder(PacketReceiveEvent event) {
		PacketEventsBlockInteractionView view = PacketEventsBlockInteractionView.of(event);
		if (view == null || view.player() == null) {
			return;
		}
		handlePlacement(view.player(), view.enumDirection() == 255);
	}

	/** Engine independent body: measures how closely a placement follows the last movement packet. */
	private void handlePlacement(Player player, boolean blockingPlacement) {
		User user = userOf(player);

		long now = System.currentTimeMillis();
		if (blockingPlacement || user.meta().protocol().combatUpdate()) {
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
							.withDefaultThreshold()
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
