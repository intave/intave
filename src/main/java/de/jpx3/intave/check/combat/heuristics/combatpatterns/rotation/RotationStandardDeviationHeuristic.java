package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class RotationStandardDeviationHeuristic extends ClassicHeuristic<RotationStandardDeviationHeuristic.RotationStandardDeviationMeta> {
  private final IntavePlugin plugin;

  public RotationStandardDeviationHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_ACCURACY, RotationStandardDeviationMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AttackMetadata attackData = meta.attack();
    RotationStandardDeviationMeta heuristicMeta = metaOf(player);
    Entity entity = attackData.lastAttackedEntity();

    if (entity != null && attackData.recentlyAttacked(500) && entity.moving(0.05)) {
      /*
      Yaw deviation
       */
      float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
      float distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
      if (yawSpeed > 2.6) {
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
    double standardDeviation = standardDeviation(heuristicMeta.distancesToPerfectYaw);

    if (standardDeviation < 1.0) {
      if (heuristicMeta.rotationBalanceYaw++ >= 2) {
        String description = "standard deviation (yaw) (" + MathHelper.formatDouble(standardDeviation, 4) + ")";
        flag(player, description);
        heuristicMeta.rotationBalanceYaw--;
        user.nerf(AttackNerfStrategy.DMG_LIGHT, nerfId);
      }
    } else {
      heuristicMeta.rotationBalanceYaw -= heuristicMeta.rotationBalanceYaw > 0 ? 0.2 : 0;
    }
  }

  private void evaluatePitchPatterns(User user) {
    Player player = user.player();
    RotationStandardDeviationMeta heuristicMeta = metaOf(user);
    double standardDeviation = standardDeviation(heuristicMeta.distancesToPerfectPitch);

    if (standardDeviation < 3.0) {
      if (heuristicMeta.rotationBalancePitch++ >= 4) {
        String description = "standard deviation (pitch) (" + standardDeviation + ")";
        flag(player, description);
        heuristicMeta.rotationBalancePitch -= 2;
        user.nerf(AttackNerfStrategy.HT_LIGHT, nerfId);
      }
    } else {
      heuristicMeta.rotationBalancePitch -= heuristicMeta.rotationBalancePitch > 0 ? 0.2 : 0;
    }
  }

  private double standardDeviation(List<? extends Number> sd) {
    double sum = 0, newSum = 0;
    for (Number v : sd) {
      sum = sum + v.doubleValue();
    }
    double mean = sum / sd.size();
    for (Number v : sd) {
      newSum = newSum + (v.doubleValue() - mean) * (v.doubleValue() - mean);
    }
    return Math.sqrt(newSum / sd.size());
  }

  public static class RotationStandardDeviationMeta extends CheckCustomMetadata {
    private final List<Float> distancesToPerfectYaw = Lists.newArrayList();
    private final List<Float> distancesToPerfectPitch = Lists.newArrayList();
    private double rotationBalanceYaw;
    private double rotationBalancePitch;
  }
}