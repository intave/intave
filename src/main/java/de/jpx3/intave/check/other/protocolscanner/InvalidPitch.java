package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.view.PacketEventsRotationView;
import de.jpx3.intave.packet.view.ProtocolLibRotationView;
import de.jpx3.intave.packet.view.RotationView;
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
    handleRotation(new ProtocolLibRotationView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveRotation(PacketReceiveEvent event) {
    PacketEventsRotationView view = PacketEventsRotationView.of(event);
    if (view == null) {
      return;
    }
    handleRotation(view);
  }

  /** Engine independent pitch clamping; see {@link RotationView}. */
  private void handleRotation(RotationView view) {
    Player player = view.player();
    if (player == null) {
      return;
    }
    float rotationPitch = view.pitch();
    if (Math.abs(rotationPitch) > 90.000001f) {
      view.setPitch(0f);
      String message = "sent invalid rotation";
      String details = "pitch at " + MathHelper.formatDouble(rotationPitch, 4);
      Violation violation = Violation.builderFor(ProtocolScanner.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withVL(100)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
    view.release();
  }
}
