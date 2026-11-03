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

package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.event.Cancellable;

import static de.jpx3.intave.packet.contract.PacketType.Client.*;

public final class MoveTickLimit extends PlayerCheckPart<ProtocolScanner> {
  private final int violationLevel;
  private int movementPacketsSinceTickEnd;

  public MoveTickLimit(User user, ProtocolScanner parentCheck) {
    super(user, parentCheck);
    this.violationLevel = parentCheck.configuration().settings().intBy("packet-order-vl", 5);
  }

  @PacketSubscription(
    // MovementDispatcher identifies teleport and item-use movement at LOW priority.
    priority = ListenerPriority.NORMAL,
    packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK}
  )
  public void receiveMovement(Cancellable cancellable) {
    User user = owningUser();
    if (!user.meta().protocol().sendsClientTickEnd() || shouldIgnoreMovement(user.meta().movement())) {
      return;
    }

    int packetCount = ++movementPacketsSinceTickEnd;
    if (packetCount == 1 || violationLevel == 0) {
      return;
    }

    cancellable.setCancelled(true);
    Violation violation = Violation.builderFor(ProtocolScanner.class)
      .forPlayer(user.player())
      .withMessage("sent multiple movement packets")
      .withDetails(packetCount + " packets without client tick end")
      .withVL(violationLevel)
      .build();
    Modules.violationProcessor().processViolation(violation);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = CLIENT_TICK_END
  )
  public void receiveClientTickEnd() {
    movementPacketsSinceTickEnd = 0;
  }

  static boolean shouldIgnoreMovement(MovementMetadata movement) {
    return movement.isTeleportConfirmationPacket
      || (movement.awaitClickMovementSkip && movement.dropPostTickMotionProcessing);
  }
}
