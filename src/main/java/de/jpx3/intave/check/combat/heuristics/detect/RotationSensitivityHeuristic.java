package de.jpx3.intave.check.combat.heuristics.detect;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
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
    RotationGCDMeta heuristicMeta = metaOf(user);

    AttackMetadata attackData = user.meta().attack();
    MovementMetadata movementData = user.meta().movement();

    float rotationYaw = movementData.rotationYaw;
    float rotationPitch = movementData.rotationPitch;

    float yawDifference = Math.abs(rotationYaw - movementData.lastRotationYaw);
    float pitchDifference = Math.abs(rotationPitch - movementData.lastRotationPitch);

    if (movementData.lastTeleport < 20) {
      return;
    }

    // old liquidbounce gcd patch
    // detects a few clients
    if (pitchDifference > 0 && yawDifference > 0 && user.meta().attack().recentlyAttacked(16000)) {
      int yawDecimal = decimalPlacesOf(rotationYaw);
      int pitchDecimal = decimalPlacesOf(rotationPitch);

      if (yawDecimal <= 3 && pitchDecimal <= 2) {
        heuristicMeta.decimalVL += 4;
        // vl+
        if (heuristicMeta.decimalVL > 80) {
          heuristicMeta.decimalVL = 0;
          parentCheck().saveAnomaly(
            player,
            Anomaly.anomalyOf(
              "111",
              Confidence.MAYBE,
              Anomaly.Type.KILLAURA,
              "rotations have too few decimals",
              LIMIT_2 | DELAY_16s | SUGGEST_MINING
            )
          );
          //dmc21
          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "21");
        }
      } else if (heuristicMeta.decimalVL > 0) {
        heuristicMeta.decimalVL--;
      }

      // Another check
      yawDecimal = decimalPlacesOf(yawDifference);
      pitchDecimal = decimalPlacesOf(pitchDifference);

      if (yawDecimal < 3 && pitchDecimal < 3) {
        heuristicMeta.decimalSpeedVL += 50;
        heuristicMeta.decimalSpeedVL = Math.min(heuristicMeta.decimalSpeedVL, 1000);
        if (heuristicMeta.decimalSpeedVL++ > 200) {
          double violationLevel = heuristicMeta.decimalSpeedVL / 200.0;
          Anomaly anomaly = Anomaly.anomalyOf(
            "113",
            Confidence.NONE,
            Anomaly.Type.KILLAURA,
            "rotations have too few decimals, vl:" + violationLevel,
            LIMIT_2 | DELAY_16s | SUGGEST_MINING
          );
          parentCheck().saveAnomaly(player, anomaly);
        }
      } else if (heuristicMeta.decimalSpeedVL > 0){
        heuristicMeta.decimalSpeedVL--;
      }
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
            "112",
            heuristicMeta.sensitivityVL >= 400 ? Confidence.PROBABLE : Confidence.NONE,
            Anomaly.Type.KILLAURA,
            "rotations are out of sync (gcd vl:" + heuristicMeta.sensitivityVL + ")",
            SUGGEST_MINING | LIMIT_2
          )
        );
        if (heuristicMeta.sensitivityVL > 300) {
          //dmc22
          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "22");
          heuristicMeta.sensitivityVL = 300;
        }
      }
    } else if (heuristicMeta.sensitivityVL > 0) {
      heuristicMeta.sensitivityVL--;
    }
  }

  private static int decimalPlacesOf(float value) {
    String s = Float.toString(value);
    s = s.substring(s.indexOf(".") + 1);
    return s.length();
  }

  public static class RotationGCDMeta extends CheckCustomMetadata {
    private int decimalVL;
    private int decimalSpeedVL;
    private int sensitivityVL;
    private float prevPitchGCD;
  }
}
