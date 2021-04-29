package de.jpx3.intave.detect.checks.combat.heuristics.detection;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.punishment.AttackNerfStrategy;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class ReshapedJumpHeuristic extends IntaveMetaCheckPart<Heuristics, ReshapedJumpHeuristic.ReshapedJumpHeuristicMeta> {
  private final IntavePlugin plugin;

  public ReshapedJumpHeuristic(Heuristics parentCheck) {
    super(parentCheck, ReshapedJumpHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK")
    }
  )
  public void checkInvalidJump(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ReshapedJumpHeuristicMeta heuristicMeta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaAttackData attackData = user.meta().attackData();

    if (!attackData.recentlyAttacked(1000)) {
      return;
    }

    boolean jump = Math.abs(movementData.jumpUpwardsMotion() - movementData.motionY()) < 1e-5;
    if (jump && movementData.sprinting && movementData.suspiciousMovement && movementData.lastTeleport > 5) {
      float rotationYaw = movementData.rotationYaw;
      float yawSine = SinusCache.sin(rotationYaw * (float) Math.PI / 180.0F, false);
      float yawCosine = SinusCache.cos(rotationYaw * (float) Math.PI / 180.0F, false);

      Vector motion = new Vector(movementData.physicsMotionX, 0.0, movementData.physicsMotionZ);
      float friction = 0.13f;
      float moveForward = movementData.keyForward * 0.98f;
      float moveStrafe = movementData.keyStrafe * 0.98f;

      physicsCalculateRelativeMovement(motion, friction, yawSine, yawCosine, moveForward, moveStrafe);
      double distance = Math.hypot(motion.getX() - movementData.motionX(), motion.getZ() - movementData.motionZ());
      double abs = Math.abs(distance - 0.2);

      if (abs < 1e-5) {
        if (heuristicMeta.balance++ >= 1) {
          String description = "player performed rotation hop";
          int options = Anomaly.AnomalyOption.LIMIT_2 | Anomaly.AnomalyOption.DELAY_128s | Anomaly.AnomalyOption.SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("61", Confidence.LIKELY, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM);
        }
      } else {
        heuristicMeta.balance -= heuristicMeta.balance > 0 ? 0.2 : 0;
      }
    }
  }

  private void physicsCalculateRelativeMovement(
    Vector motion, float friction,
    float yawSine, float yawCosine,
    float moveForward, float moveStrafe
  ) {
    float f = moveStrafe * moveStrafe + moveForward * moveForward;
    if (f >= 1.0E-4F) {
      f = (float) Math.sqrt(f);
      f = friction / Math.max(1.0f, f);
      moveStrafe *= f;
      moveForward *= f;
      motion.setX(motion.getX() + (moveStrafe * yawCosine - moveForward * yawSine));
      motion.setZ(motion.getZ() + (moveForward * yawCosine + moveStrafe * yawSine));
    }
  }

  public static final class ReshapedJumpHeuristicMeta extends UserCustomCheckMeta {
    private double balance;
  }
}