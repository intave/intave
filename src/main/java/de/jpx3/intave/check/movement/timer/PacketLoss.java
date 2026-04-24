package de.jpx3.intave.check.movement.timer;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

@Deprecated
public final class PacketLoss extends MetaCheckPart<Timer, PacketLoss.PacketLossMeta> {
  private static final double AVERAGE = 50;
  private final BalanceButActuallyGood balanceButActuallyGood;

  public PacketLoss(
    Timer parentCheck,
    BalanceButActuallyGood balanceButActuallyGood
  ) {
    super(parentCheck, PacketLossMeta.class);
    this.balanceButActuallyGood = balanceButActuallyGood;
  }

  @PacketSubscription(
    packetsIn = {
      POSITION_LOOK, POSITION, FLYING, LOOK
    }
  )
  public void clientTickUpdate(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    long now = System.currentTimeMillis();

    PacketLossMeta packetLossMeta = metaOf(user);

    long lastFlying = packetLossMeta.lastFlying;
    packetLossMeta.lastFlying = now;

    if (lastFlying == -1) {
      return;
    }

    long diff = now - lastFlying;

    double max = AVERAGE * 1.25;
    if (diff > max) {
      if (++packetLossMeta.lossWave > 2) {

        if (packetLossMeta.vl++ > 2) {
          Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
            .withMessage("irregular packet delay")
            .withVL(0.5)
            .build();
          ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
          if (violationContext.shouldCounterThreat()) {
            MovementMetadata movementData = user.meta().movement();
            movementData.invalidMovement = true;
            Vector setback = new Vector(movementData.baseMotionX, movementData.baseMotionY, movementData.baseMotionZ);
            Modules.mitigate().movement().emulationSetBack(player, setback, 12, false);

            balanceButActuallyGood.reset(user);
          }

        }

      }
    } else {
      packetLossMeta.lossWave = 0;
      packetLossMeta.vl = Math.max(packetLossMeta.vl - 0.1, 0);
    }

  }

  public static final class PacketLossMeta extends CheckCustomMetadata {
    public long lastFlying = -1;
    public int lossWave;
    public double vl;

  }
}