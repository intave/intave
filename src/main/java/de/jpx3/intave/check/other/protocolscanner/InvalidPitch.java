package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
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
  public void receiveRotation(ProtocolPacketEvent event, WrapperPlayClientPlayerFlying packet) {
    Player player = event.getPlayer();
    float rotationPitch = packet.getLocation().getPitch();
    if (Math.abs(rotationPitch) > 90.000001f) {
      packet.getLocation().setPitch(0f);
      event.markForReEncode(true);
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
