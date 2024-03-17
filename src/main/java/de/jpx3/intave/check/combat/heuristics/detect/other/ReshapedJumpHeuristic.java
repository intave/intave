package de.jpx3.intave.check.combat.heuristics.detect.other;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

@Reserved
public final class ReshapedJumpHeuristic extends MetaCheckPart<Heuristics, ReshapedJumpHeuristic.ReshapedJumpHeuristicMeta> {
  private final IntavePlugin plugin;

  public ReshapedJumpHeuristic(Heuristics parentCheck) {
    super(parentCheck, ReshapedJumpHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK
    }
  )
  public void checkInvalidJump(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ReshapedJumpHeuristicMeta heuristicMeta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    AttackMetadata attackData = user.meta().attack();
    InventoryMetadata inventoryData = user.meta().inventory();

    boolean recentlyAttacked = attackData.recentlyAttacked(1000);
    boolean jump = Math.abs(movementData.jumpMotion() - movementData.motionY()) < 1e-5;
    if (jump && movementData.sprinting && movementData.lastTeleport > 5 && movementData.suspiciousMovement && !movementData.collidedHorizontally) {
      float rotationYaw = movementData.rotationYaw;
      float yawSine = SinusCache.sin(rotationYaw * (float) Math.PI / 180.0F, false);
      float yawCosine = SinusCache.cos(rotationYaw * (float) Math.PI / 180.0F, false);

      double physicsMotionX = movementData.baseMotionX;
      double physicsMotionZ = movementData.baseMotionZ;
      ItemStack itemStack = inventoryData.heldItem();
      boolean knockbackEnchantment = itemStack != null && itemStack.containsEnchantment(Enchantment.KNOCKBACK);

      if (!knockbackEnchantment && movementData.pastPlayerReduceAttackPhysics == 0) {
        physicsMotionX *= 0.6;
        physicsMotionZ *= 0.6;
      }

      Vector motion = new Vector(physicsMotionX, 0.0, physicsMotionZ);
      float friction = movementData.friction();
      float moveForward = movementData.keyForward * 0.98f;
      float moveStrafe = movementData.keyStrafe * 0.98f;

      physicsApplyJumpTo(yawSine, yawCosine, motion);
      physicsCalculateRelativeMovement(motion, friction, yawSine, yawCosine, moveForward, moveStrafe);
      double preDistance = Hypot.fast(motion.getX() - movementData.motionX(), motion.getZ() - movementData.motionZ());

      double leniency = 0.001;
      if (preDistance > leniency) {
        motion = new Vector(physicsMotionX, 0.0, physicsMotionZ);
        physicsCalculateRelativeMovement(motion, friction, yawSine, yawCosine, moveForward, moveStrafe);
        double alternativeDistance = Hypot.fast(motion.getX() - movementData.motionX(), motion.getZ() - movementData.motionZ());
        if (Math.abs(alternativeDistance - 0.2) < leniency * 2) {
          heuristicMeta.balance++;
          String description = "horizontal motion not correlated with jump vl:" + MathHelper.formatDouble(heuristicMeta.balance, 1);
          if (recentlyAttacked) {
            description += " | attacked";
          }
          description += " | pre-dist:" + preDistance + ", alt-dist:" + alternativeDistance;
          description += " | " + user.meta().protocol().versionString();
          int options = Anomaly.AnomalyOption.LIMIT_8 | Anomaly.AnomalyOption.SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("61", Confidence.NONE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          //dmc15
//            user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "15");
        }
      } else {
        heuristicMeta.balance -= heuristicMeta.balance > 0 ? 0.2 : 0;
      }
    }
  }

  private void physicsApplyJumpTo(float yawSine, float yawCosine, Vector motion) {
    motion.setX(motion.getX() - yawSine * 0.2F);
    motion.setZ(motion.getZ() + yawCosine * 0.2F);
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

  public static final class ReshapedJumpHeuristicMeta extends CheckCustomMetadata {
    private double balance;
  }
}