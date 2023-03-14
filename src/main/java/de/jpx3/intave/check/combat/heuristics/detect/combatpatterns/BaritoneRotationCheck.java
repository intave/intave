package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.share.ClientMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

@Reserved
public final class BaritoneRotationCheck extends MetaCheckPart<Heuristics, BaritoneRotationCheck.BaritoneRotationMeta> {
  public BaritoneRotationCheck(Heuristics parentCheck) {
    super(parentCheck, BaritoneRotationMeta.class);
//    System.out.println("What's up?");
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();
    BaritoneRotationMeta heuristicMeta = metaOf(user);

    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    float pitchSpeed = MathHelper.distanceInDegrees(movementData.rotationPitch, movementData.lastRotationPitch);

    float rotationYaw = ClientMathHelper.wrapAngleTo180_float(movementData.rotationYaw);
    float rotationPitch = ClientMathHelper.wrapAngleTo180_float(movementData.rotationPitch);

//    player.sendMessage("Yaw: " + formatDouble(rotationYaw, 4) + "/" + formatDouble(yawSpeed, 4) + " Pitch: " + formatDouble(rotationPitch, 4) + "/" + formatDouble(pitchSpeed, 4));
  }

  public static class BaritoneRotationMeta extends CheckCustomMetadata {

  }
}
