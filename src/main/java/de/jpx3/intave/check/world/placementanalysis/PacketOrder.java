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
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.plib.reader.BlockInteractionReader;
import de.jpx3.intave.user.User;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.math.MathHelper.averageOf;
import static de.jpx3.intave.packet.contract.PacketType.Client.*;

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
	public void checkPlacementPacketOrder(User user, BlockInteractionReader reader) {

		long now = System.currentTimeMillis();
		if (reader.enumDirection() == 255 || user.meta().protocol().combatUpdate()) {
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
							.forUser(user)
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

}
