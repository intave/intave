package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackCancelType;
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
  private final IntavePlugin plugin;

  public RotationStandardDeviationHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationStandardDeviationMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
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

    if (attackedEntity != null && attackData.recentlyAttacked(500) && attackedEntity.moving(0.05)) {
      /*
      Yaw deviation
       */
      float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
      float distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
      if (yawSpeed > 2.5) {
        heuristicMeta.distancesToPerfectYaw.add(distanceToPerfectYaw);
      }
      if (heuristicMeta.distancesToPerfectYaw.size() >= 7) {
        evaluateYawPatterns(user);
        heuristicMeta.distancesToPerfectYaw.clear();
      }

      /*
      Pitch deviation
       */
      float pitchSpeed = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);
      float distanceToPerfectPitch = Math.abs(movementData.rotationPitch - attackData.perfectPitch());
      if (pitchSpeed > 0.5 && yawSpeed > 3) {
        heuristicMeta.distancesToPerfectPitch.add(distanceToPerfectPitch);
      }
      if (heuristicMeta.distancesToPerfectPitch.size() >= 10) {
        evaluatePitchPatterns(user);
        heuristicMeta.distancesToPerfectPitch.clear();
      }
    }
  }

  private void evaluateYawPatterns(User user) {
    Player player = user.player();
    RotationStandardDeviationMeta heuristicMeta = metaOf(user);
    double standardDeviation = RotationMathHelper.calculateStandardDeviation(heuristicMeta.distancesToPerfectYaw);

    if (standardDeviation < 1.0) {
      if (heuristicMeta.rotationBalanceYaw++ >= 2) {
        String description = "standard deviation (yaw) (" + standardDeviation + ")";
        Anomaly anomaly = Anomaly.anomalyOf("121", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, Anomaly.AnomalyOption.LIMIT_2);
        parentCheck().saveAnomaly(player, anomaly);
        heuristicMeta.rotationBalanceYaw--;
        plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.DCRM);
      }
    } else {
      heuristicMeta.rotationBalanceYaw -= heuristicMeta.rotationBalanceYaw > 0 ? 0.2 : 0;
    }
  }

  private void evaluatePitchPatterns(User user) {
    Player player = user.player();
    RotationStandardDeviationMeta heuristicMeta = metaOf(user);
    double standardDeviation = RotationMathHelper.calculateStandardDeviation(heuristicMeta.distancesToPerfectPitch);

    if (standardDeviation < 3.0) {
      if (heuristicMeta.rotationBalancePitch++ >= 4) {
        String description = "standard deviation (pitch) (" + standardDeviation + ")";
        Anomaly anomaly = Anomaly.anomalyOf("122", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, Anomaly.AnomalyOption.LIMIT_2);
        parentCheck().saveAnomaly(player, anomaly);
        heuristicMeta.rotationBalancePitch--;
//        plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.DCRM);
      }
    } else {
      heuristicMeta.rotationBalancePitch -= heuristicMeta.rotationBalancePitch > 0 ? 0.2 : 0;
    }
  }

  public static class RotationStandardDeviationMeta extends UserCustomCheckMeta {
    private final List<Float> distancesToPerfectYaw = Lists.newArrayList();
    private final List<Float> distancesToPerfectPitch = Lists.newArrayList();
    private double rotationBalanceYaw;
    private double rotationBalancePitch;
  }
}