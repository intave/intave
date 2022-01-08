package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import org.bukkit.entity.Player;

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
  public void receiveRotation(PacketEvent event) {
    Player player = event.getPlayer();
    float rotationPitch = event.getPacket().getFloat().read(1);
    if (Math.abs(rotationPitch) > 90.05f) {
      event.getPacket().getFloat().writeSafely(1, 0f);
      String message = "sent invalid rotation";
      String details = "pitch at " + MathHelper.formatDouble(rotationPitch, 4);
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withVL(100)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
  }

}