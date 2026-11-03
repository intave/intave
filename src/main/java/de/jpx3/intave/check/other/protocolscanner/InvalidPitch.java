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

import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.plib.reader.PlayerMoveReader;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.packet.contract.PacketType.Client.LOOK;
import static de.jpx3.intave.packet.contract.PacketType.Client.POSITION_LOOK;

public final class InvalidPitch extends CheckPart<ProtocolScanner> {
  public InvalidPitch(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveRotation(User user, PlayerMoveReader reader) {
    float rotationPitch = reader.pitch();
    if (Math.abs(rotationPitch) > 90.000001f) {
      reader.setPitch(0f);
      String message = "sent invalid rotation";
      String details = "pitch at " + MathHelper.formatDouble(rotationPitch, 4);
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forUser(user).withMessage(message).withDetails(details)
        .withVL(100)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
  }
}