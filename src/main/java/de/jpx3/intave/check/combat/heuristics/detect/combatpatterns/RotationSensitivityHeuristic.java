package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_2;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.SUGGEST_MINING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class RotationSensitivityHeuristic extends MetaCheckPart<Heuristics, RotationSensitivityHeuristic.RotationGCDMeta> {
  private final IntavePlugin plugin;

  public RotationSensitivityHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationGCDMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void rotationCheck(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    AttackMetadata attackData = user.meta().attack();
    MovementMetadata movementData = user.meta().movement();

    if (movementData.lastTeleport < 20) {
      return;
    }

    if (attackData.recentlyAttacked(200)) {
      ensureSensitivity(player, user);
    }
  }

  private void ensureSensitivity(Player player, User user) {
    RotationGCDMeta heuristicMeta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    float pitchDifference = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);

    float prevPitchGCD = heuristicMeta.prevPitchGCD;
    if (prevPitchGCD == 0) {
      prevPitchGCD = pitchDifference;
    }
    double pitchA = prevPitchGCD;
    double pitchB = pitchDifference;
    double pitchR;
    int pitchCountdown = 100;

    while ((pitchR = pitchA % pitchB) > Math.max(pitchA, pitchB) * 1e-3) {
      pitchA = pitchB;
      pitchB = pitchR;
      if (pitchCountdown-- < 0) {
        break;
      }
    }

    float pitchGCD = (float) pitchB;
    double gcdDifference = Math.abs(pitchGCD - prevPitchGCD);

    heuristicMeta.prevPitchGCD = pitchGCD;

    if (gcdDifference > 0.001) {
      if (pitchDifference > 1.0) {
        heuristicMeta.sensitivityVL += pitchDifference > 5 ? 10 : 5;
      }
      if ((int) Math.round(heuristicMeta.sensitivityVL / 2d) % 50 == 0 && heuristicMeta.sensitivityVL > 0) {
        parentCheck().saveAnomaly(
          player,
          Anomaly.anomalyOf(
            "pitch:sens",
            heuristicMeta.sensitivityVL >= 400 ? Confidence.PROBABLE : Confidence.NONE,
            Anomaly.Type.KILLAURA,
            "rotations are out of sync (gcd vl:" + heuristicMeta.sensitivityVL + ")",
            SUGGEST_MINING | LIMIT_2
          )
        );
        if (heuristicMeta.sensitivityVL > 300) {
          //dmc22
//          user.nerf(AttackNerfStrategy.DMG_LIGHT, "22");
          heuristicMeta.sensitivityVL = 300;
        }
      }
    } else if (heuristicMeta.sensitivityVL > 0) {
      heuristicMeta.sensitivityVL--;
    }
  }

  public static class RotationGCDMeta extends CheckCustomMetadata {
    private int sensitivityVL;
    private float prevPitchGCD;
  }
}
