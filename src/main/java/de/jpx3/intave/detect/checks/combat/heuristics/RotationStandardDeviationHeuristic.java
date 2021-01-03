package de.jpx3.intave.detect.checks.combat.heuristics;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.RotationMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import java.util.List;

public final class RotationStandardDeviationHeuristic extends IntaveMetaCheckPart<Heuristics, RotationStandardDeviationHeuristic.RotationStandardDeviationMeta> {
  public RotationStandardDeviationHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationStandardDeviationMeta.class);
  }

  public static class RotationStandardDeviationMeta extends UserCustomCheckMeta {
    private final List<Float> distancesToPerfectYaw = Lists.newArrayList();
    private double rotationBalance;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaAttackData attackData = meta.attackData();
    RotationStandardDeviationMeta heuristicMeta = metaOf(player);
    WrappedEntity attackedEntity = attackData.lastAttackedEntity();

    if (attackedEntity != null && attackedEntity.moving(0.05) && attackData.recentlyAttacked(1000)) {
      float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
      float distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
      if (yawSpeed > 1.5) {
        heuristicMeta.distancesToPerfectYaw.add(distanceToPerfectYaw);
      }
      if (heuristicMeta.distancesToPerfectYaw.size() >= 7) {
        compareResult(user);
      }
    }
  }

  private void compareResult(User user) {
    Player player = user.player();
    RotationStandardDeviationMeta heuristicMeta = metaOf(user);
    double standardDeviation = RotationMathHelper.calculateStandardDeviationFloat(heuristicMeta.distancesToPerfectYaw);
    if (standardDeviation < 1.0) {
      if (heuristicMeta.rotationBalance++ >= 2) {
        Anomaly anomaly = new Anomaly("rx", Confidence.PROBABLE, Anomaly.AnomalyOption.LIMIT_1);
        parentCheck().saveAnomaly(player, anomaly);
        heuristicMeta.rotationBalance--;
      }
    } else {
      heuristicMeta.rotationBalance -= heuristicMeta.rotationBalance > 0 ? 0.2 : 0;
    }
    heuristicMeta.distancesToPerfectYaw.clear();
  }
}