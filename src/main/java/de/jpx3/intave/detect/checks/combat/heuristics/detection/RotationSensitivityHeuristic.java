package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackCancelType;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import static de.jpx3.intave.detect.checks.combat.heuristics.Anomaly.AnomalyOption.*;

public final class RotationSensitivityHeuristic extends IntaveMetaCheckPart<Heuristics, RotationSensitivityHeuristic.RotationGCDMeta> {
  private final IntavePlugin plugin;

  public RotationSensitivityHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationGCDMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
    }
  )
  public void rotationCheck(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RotationGCDMeta heuristicMeta = metaOf(user);

    UserMetaAttackData attackData = user.meta().attackData();
    UserMetaMovementData movementData = user.meta().movementData();

    float rotationYaw = movementData.rotationYaw;
    float rotationPitch = movementData.rotationPitch;

    float yawDifference = Math.abs(rotationYaw - movementData.lastRotationYaw);
    float pitchDifference = Math.abs(rotationPitch - movementData.lastRotationPitch);

    if (movementData.lastTeleport < 20) {
      return;
    }

    // old liquidbounce gcd patch
    // detects a few clients
    if (pitchDifference > 0 && yawDifference > 0 && user.meta().attackData().recentlyAttacked(16000)) {
      int yawDecimal = decimalPlacesOf(rotationYaw);
      int pitchDecimal = decimalPlacesOf(rotationPitch);

      if (yawDecimal <= 3 && pitchDecimal <= 2) {
        heuristicMeta.decimalVL += 16;
        // vl+
        if (heuristicMeta.decimalVL > 70) {
          heuristicMeta.decimalVL = 0;
          parentCheck().saveAnomaly(
            player,
            Anomaly.anomalyOf(
              "111",
              Confidence.PROBABLE,
              Anomaly.Type.KILLAURA,
              "rotations have too few decimals",
              LIMIT_1 | LIMIT_2 | DELAY_16s | SUGGEST_MINING
            )
          );
          plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.DCRM);
        }
      } else if (heuristicMeta.decimalVL > 0) {
        heuristicMeta.decimalVL--;
      }
    }

    if (attackData.recentlyAttacked(200)) {
      ensureSensitivity(player, user);
    }
  }

  private void ensureSensitivity(Player player, User user) {
    RotationGCDMeta heuristicMeta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();
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
      if (heuristicMeta.sensitivityVL % 100 == 0) {
        parentCheck().saveAnomaly(
          player,
          Anomaly.anomalyOf(
            "112",
            Confidence.NONE,
            Anomaly.Type.KILLAURA,
            "gcd vl:" + heuristicMeta.sensitivityVL,
            SUGGEST_MINING
          )
        );
        if (heuristicMeta.sensitivityVL > 300) {
          plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.DCRM);
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

  public static class RotationGCDMeta extends UserCustomCheckMeta {
    private int decimalVL;
    private int sensitivityVL;
    private float prevPitchGCD;
  }
}
