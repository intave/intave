package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_128s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.SUGGEST_MINING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class RotationAccuracyPitchHeuristic extends MetaCheckPart<Heuristics, RotationAccuracyPitchHeuristic.RotationAccuracyHeuristicMeta> {
  public RotationAccuracyPitchHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationAccuracyPitchHeuristic.RotationAccuracyHeuristicMeta.class);
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
    Entity attackedEntity = attackData.lastAttackedEntity();
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(user);

    if (movementData.lastTeleport < 20) {
      return;
    }

    if (attackedEntity != null && attackedEntity.moving(0.05) && attackData.recentlyAttacked(1000)) {
      float pitchSpeed = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);
      float distanceToPerfectPitch = Math.abs(movementData.rotationPitch - attackData.perfectPitch());

      int timeAddOnDetection = 40 * 20;

      if (pitchSpeed > 1.0) {
        // Check perfect yaw
        if (distanceToPerfectPitch == 0) {
          heuristicMeta.threshold += timeAddOnDetection;
          int vl = heuristicMeta.threshold / timeAddOnDetection;
          Confidence confidence = vl <= 2 ? Confidence.PROBABLE : Confidence.LIKELY;
          String description = "rotated pitch too precisely (0.0) vl:" + vl + ", conf:" + confidence.level();
          int options = DELAY_128s | SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("pitch:acc", confidence, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
        } else if (heuristicMeta.threshold > 0) {
          heuristicMeta.threshold--;
        }
      }
    }
  }

  public static final class RotationAccuracyHeuristicMeta extends CheckCustomMetadata {
    public int threshold;
  }
}