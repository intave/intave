package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.PlayerCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.Histogram;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.util.PacketEventsConversions;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.github.retrooper.packetevents.event.CancellableEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public class RotationFlick extends PlayerCheckPart<PlacementAnalysis> {
  private final Histogram rotationHistogram = new Histogram(70, 90, 0.2, 100);
  private final List<Float> rotationHistory = new CopyOnWriteArrayList<>();
  private final List<Long> placementSpeedHistory = new ArrayList<>();
  private final List<Vector> lastBlocksPlaced = new CopyOnWriteArrayList<>();
  private long lastPlacement;
  private double vl;
  private float lastPitch;

  public RotationFlick(User user, PlacementAnalysis parentCheck) {
    super(user, parentCheck);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      BLOCK_PLACE, USE_ITEM
    }
  )
  public void receivePlacementPacket(
    Player player, ProtocolPacketEvent event, CancellableEvent CancellableEvent
  ) {
    if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
      return;
    }
    User user = userOf(player);
    MovementMetadata movement = user.meta().movement();
    AbilityMetadata abilities = user.meta().abilities();
    WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement((PacketReceiveEvent) event);
    BlockPosition blockPosition = PacketEventsConversions.toBlockPosition(packet.getBlockPosition());

    if (blockPosition == null || CancellableEvent.isCancelled() || movement.isInVehicle()) {
      return;
    }

    int enumDirection = packet.getFaceId();
    if (enumDirection == 255) {
      return;
    }

    Material clickedType = VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(player.getWorld()));
    boolean clickableInteraction = BlockInteractionAccess.isClickable(clickedType);
    Material heldItemType = user.meta().inventory().heldItemType();
    boolean interactionIsPlacement = heldItemType != Material.AIR && heldItemType.isBlock() && !clickableInteraction && !abilities.inGameMode(GameMode.ADVENTURE);

    if (!interactionIsPlacement || enumDirection < 2) {
      return;
    }

    long lastPlacementDiff = Math.min(1000, System.currentTimeMillis() - lastPlacement);

    while (lastBlocksPlaced.size() > 4 || (!lastBlocksPlaced.isEmpty() && System.currentTimeMillis() - lastPlacement > 5000)) {
      lastBlocksPlaced.remove(0);
    }
    lastBlocksPlaced.add(blockPosition.convertToBukkitVec());

    placementSpeedHistory.add(lastPlacementDiff);
    lastPlacement = System.currentTimeMillis();
    double average = 500;

    if (placementSpeedHistory.size() >= 8) {
      average = placementSpeedHistory.stream().mapToDouble(value -> value).average().orElse(500);
      placementSpeedHistory.remove(0);
    }

    float eyeHeight = user.meta().movement().eyeHeight();
    Position eyePosition = user.meta().movement().position().add(0, eyeHeight, 0);
    Direction direction = Direction.getFront(enumDirection);

    Position blockMidpoint = new Position(blockPosition.getX() + 0.5, blockPosition.getY() + 0.5, blockPosition.getZ() + 0.5);
    blockMidpoint = blockMidpoint.add(direction.directionVecAsVector().clone().multiply(0.5));

    Position[] edgeMidpoints = new Position[4];

    // [0] and [1] are trivial
    edgeMidpoints[0] = blockMidpoint.add(0, 0.5, 0);
    edgeMidpoints[1] = blockMidpoint.add(0, -0.5, 0);

    boolean northSouth = direction == Direction.NORTH || direction == Direction.SOUTH;
    edgeMidpoints[2] = blockMidpoint.add(northSouth ? 0.5 : 0, 0, northSouth ? 0 : 0.5);
    edgeMidpoints[3] = blockMidpoint.add(northSouth ? -0.5 : 0, 0, northSouth ? 0 : -0.5);

    Rotation[] rotations = new Rotation[4];
    for (int i = 0; i < 4; i++) {
      rotations[i] = eyePosition.rotationTo(edgeMidpoints[i]);
    }

    float horizontalLineLength = Math.abs(rotations[0].yaw() - rotations[1].yaw());
    float verticalLineLength = Math.abs(rotations[0].pitch() - rotations[1].pitch());

    float prevPitch = lastPitch;
    float pitchDiff = Math.abs(movement.rotationPitch - prevPitch);
    lastPitch = movement.rotationPitch;

    if (pitchDiff > 3 && pitchDiff < 20 && movement.rotationPitch > 70 && verticalLineLength < 5) {
      if ((average < 400 || isOneLine(lastBlocksPlaced)) && lastPlacementDiff < 800) {
        vl += Math.min(20, pitchDiff / (verticalLineLength / 10));
        if (movement.rotationPitch > 89.5) {
          vl += 5;
        }
        if (vl > 100) {
          Violation violation = Violation.builderFor(PlacementAnalysis.class)
            .forPlayer(player).withDefaultThreshold()
            .withMessage(COMMON_FLAG_MESSAGE)
            .withDetails("exhibits micro pitch adjustments")
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .withCustomThreshold(PlacementAnalysis.legacyConfigurationLayout() ? "thresholds" : "analysis-thresholds.on-premise")
            .withVL(10).build();
          Modules.violationProcessor().processViolation(violation);
//          user.meta().violationLevel().lastBlockPlaceDenyRequest = System.currentTimeMillis();
          vl -= 10;
          user.nerfPermanently(AttackNerfStrategy.RECEIVE_MORE_KNOCKBACK, "mpa");
          user.nerfPermanently(AttackNerfStrategy.APPLY_LESS_KNOCKBACK, "mpa");
        }
      }
//      player.sendMessage(ChatColor.RED + "Rotation flick " + movement.rotationYaw + " " + movement.rotationPitch + " +-" + pitchDiff + " " + verticalLineLength + " " + average + " " + rotationSum + " -> " + vl);
    } else if (vl > 0) {
      vl *= 0.99;
      vl -= 0.01;
    }
  }

  private boolean isOneLine(List<? extends Vector> blocks) {
    int lastBlockX = 0,
      lastBlockY = 0,
      lastBlockZ = 0;
    boolean lockedOnX = false,
      lockedOnZ = false;
    boolean first = true;
    int yTolerance = 1;
    for (Vector block : blocks) {
      if (!first) {
        if (lastBlockY != block.getY()) {
          if (yTolerance-- <= 0) {
            return false;
          }
        } else {
          if (lastBlockX == block.getX()) {
            lockedOnX = true;
          } else if (lockedOnX) {
            return false;
          }
          if (lastBlockZ == block.getZ()) {
            lockedOnZ = true;
          } else if (lockedOnZ) {
            return false;
          }
        }
      }
      lastBlockX = block.getBlockX();
      lastBlockY = block.getBlockY();
      lastBlockZ = block.getBlockZ();
      first = false;
    }
    return lockedOnX || lockedOnZ;
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      POSITION_LOOK, LOOK, POSITION, FLYING
    }
  )
  public void on(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    float rotationMovement = Math.min(MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw), 360);
    rotationHistogram.add(movementData.rotationPitch);

    if (System.currentTimeMillis() - lastPlacement > 2000 || movementData.lastTeleport <= 5) {
      if (rotationHistogram.size() > 10) {
//        for (String s : rotationHistogram.plot()) {
//          player.sendMessage(s);
//        }
      }
      rotationHistogram.clear();
      return;
    }
//    player.sendMessage(ChatColor.GRAY + "" + movementData.rotationYaw + " " + (movementData.rotationYaw % 45));
    if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION || event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING) {
      return;
    }
//    player.sendMessage(ChatColor.GRAY + "Rotation to " + movementData.rotationPitch + " " + MathHelper.formatDouble(rotationHistogram.mean(), 2) +  " " + MathHelper.formatDouble(rotationHistogram.variance(), 2));
    while (rotationHistory.size() > 3 * 20) {
      rotationHistory.remove(0);
    }
    rotationHistory.add(rotationMovement);
  }
}
