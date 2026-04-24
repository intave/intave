package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.util.PacketEventsConversions;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class Constraint extends MetaCheckPart<PlacementAnalysis, Constraint.ConstraintMeta> {
  public Constraint(PlacementAnalysis parentCheck) {
    super(parentCheck, ConstraintMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movement = user.meta().movement();
    ConstraintMeta meta = metaOf(user);

    if (movement.lastTeleport == 0) {
      return;
    }

    int forward = movement.keyForward;
    int strafe = movement.keyStrafe;
//    player.sendMessage(resolveKeysFromInput(forward, strafe) + " ("+meta.backwardsStreak+") " + movement.rotationYaw + " " + movement.rotationPitch);

    if (forward == -1 && strafe == 0) {
      meta.backwardsStreak++;
    } else {
      if (forward == 0 && strafe == 0) {
        meta.backwardsStreak -= 4;
        meta.backwardsStreak = Math.max(0, meta.backwardsStreak);
      } else {
        meta.backwardsStreak = 0;
      }
    }

    meta.tickCount++;

    if (meta.tickCount > 20) {
      meta.tickCount = 0;
      meta.lastBlockClicks = meta.blockClicks;
      meta.blockClicks = 0;
    }

    boolean bad = meta.lastBlockClicks < 10 && meta.blockClicks < 10 && meta.backwardsStreak > 30;
    if (bad) {

    }
//    player.sendMessage((bad ? ChatColor.RED : ChatColor.GRAY) + "bs" + meta.backwardsStreak + " lbc" + meta.lastBlockClicks + " bc" + meta.blockClicks);
  }

  @PacketSubscription(
    packetsIn = {USE_ITEM, BLOCK_PLACE},
    priority = ListenerPriority.LOW
  )
  public void rightClick(
    User user, ProtocolPacketEvent event
  ) {
    Player player = user.player();
    String name = event.getPacketType().getName();

    ConstraintMeta meta = metaOf(user);
    Direction direction = null;
    Vector facingVector = null;
    if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
      WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement((PacketReceiveEvent) event);
      direction = PacketEventsConversions.toDirection(packet.getFace());
      facingVector = PacketEventsConversions.toBukkitVector(packet.getCursorPosition());
    }
    String k = MathHelper.formatMotion(facingVector);
    if (direction == null) {
      meta.blockClicks++;
      return;
    }
//    Synchronizer.synchronize(() -> {
//      player.sendMessage(name + " " + direction + " " + k);
//    });

//    if ()
  }

  private static String resolveKeysFromInput(int forward, int strafe) {
    String key = "";
    if (forward == 1) {
      key += "W";
    } else if (forward == -1) {
      key += "S";
    }
    if (strafe == 1) {
      key += "A";
    } else if (strafe == -1) {
      key += "D";
    }
    return key;
  }

  public static class ConstraintMeta extends CheckCustomMetadata {
    private int backwardsStreak;
    private int lastBlockClicks;
    private int blockClicks;
    private int tickCount;
  }
}
