package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import com.comphenix.protocol.events.PacketEvent;
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

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public class RotationExactHeuristic extends ClassicHeuristic<RotationExactHeuristic.RotationExactHeuristicMeta> {
  public RotationExactHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ROTATION_EXACT, RotationExactHeuristicMeta.class);
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

    if (movementData.lastTeleport < 20) {
      return;
    }

    if (attackedEntity == null || !attackedEntity.moving(0.05) || !attackData.recentlyAttacked(1000)) {
      return;
    }

    float rotationYaw = movementData.rotationYaw;
    float yawSpeed = MathHelper.distanceInDegrees(rotationYaw, movementData.lastRotationYaw);
    if (yawSpeed > 1.0) {
      float perfectYaw = attackData.perfectYaw();
      float closestPerfectYaw = attackData.perfectClosestYaw();
      float distanceToPerfectYaw = MathHelper.distanceInDegrees(perfectYaw, rotationYaw);
      float distanceToClosestPerfectYaw = MathHelper.distanceInDegrees(closestPerfectYaw, rotationYaw);

      if (distanceToPerfectYaw == 0 || distanceToClosestPerfectYaw == 0) {
        flag(user.player(), "sent exact yaw rotation");
        user.nerf(AttackNerfStrategy.CRITICALS, nerfId);
      }
    }

    float pitchSpeed = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);
    float distanceToPerfectPitch = Math.abs(movementData.rotationPitch - attackData.perfectPitch());
    if (pitchSpeed > 1.0 && distanceToPerfectPitch == 0) {
      flag(player, "sent exact pitch rotation");
      user.nerf(AttackNerfStrategy.CRITICALS, nerfId);
    }
  }

  public static class RotationExactHeuristicMeta extends CheckCustomMetadata {
  }
}
