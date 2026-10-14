package de.jpx3.intave.check.other.protocolscanner;

import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

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