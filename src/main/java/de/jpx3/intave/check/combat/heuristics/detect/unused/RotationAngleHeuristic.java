package de.jpx3.intave.check.combat.heuristics.detect.unused;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class RotationAngleHeuristic extends MetaCheckPart<Heuristics, RotationAngleHeuristic.RotationAngleMeta> {
  public RotationAngleHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationAngleMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK, POSITION
    }
  )
  public void rotationCheck(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle metadataBundle = user.meta();
    MovementMetadata movementMeta = metadataBundle.movement();
    AttackMetadata attackMeta = metadataBundle.attack();
    RotationAngleMeta heuristicsMeta = metaOf(user);
    float yawDirection = movementMeta.rotationYaw - movementMeta.lastRotationYaw;
    float pitchDirection = movementMeta.rotationPitch - movementMeta.lastRotationPitch;
    Vector2d rotationMovement = new Vector2d(
      yawDirection,
      pitchDirection * 2
    );
    Vector2d lastRotationMovement = heuristicsMeta.lastRotation;
    float angle = lastRotationMovement.length() * rotationMovement.length() == 0 ? 0 : rotationMovement.angleInDegrees(lastRotationMovement);
    ChatColor color;
    if (angle < 2) {
      color = ChatColor.GREEN;
    } else if (angle < 10) {
      color = ChatColor.YELLOW;
    } else if (angle < 100) {
      color = ChatColor.GOLD;
    } else {
      color = ChatColor.RED;
    }

    boolean look = true;

    Entity attackedEntity = attackMeta.lastAttackedEntity();

    if (attackedEntity != null && !attackedEntity.moving(0.05)) {
      look = false;
    }
    if (look && !attackMeta.recentlyAttacked(500) || attackMeta.recentlySwitchedEntity(1000)) {
      look = false;
    }

    float speed = Math.abs(yawDirection) + Math.abs(pitchDirection);
//    player.sendMessage(color + "" + angle + "deg " + speed + "k");

    if (look && angle > 300 && speed > 5 && heuristicsMeta.lastExcessiveRotation < 0) {
      heuristicsMeta.lastExcessiveRotation = 0;
    }

    if (heuristicsMeta.lastExcessiveRotation >= 0) {
      if (angle < 35 && speed < 10) {
//        player.sendMessage(heuristicsMeta.lastExcessiveRotation + " rotations to stop");
        heuristicsMeta.lastExcessiveRotation = -1;
      } else {
        heuristicsMeta.lastExcessiveRotation++;
      }
    }

    heuristicsMeta.lastRotation = rotationMovement;
  }

  static class Vector2d {
    double x;
    double y;

    public Vector2d(double x, double y) {
      this.x = x;
      this.y = y;
    }

    public float angleInDegrees(Vector2d other) {
      return (float) ((angle(other) / Math.PI) * 360f);
    }

    public float angle(Vector2d other) {
      double dot = dot(other) / (length() * other.length());

      return (float) Math.acos(dot);
    }

    public double dot(Vector2d other) {
      return x * other.x + y * other.y;
    }

    public double length() {
      return Math.sqrt(NumberConversions.square(x) + NumberConversions.square(y));
    }
  }

  public static class RotationAngleMeta extends CheckCustomMetadata {
    Vector2d lastRotation = new Vector2d(0, 0);

    int lastExcessiveRotation = -1;
  }
}
