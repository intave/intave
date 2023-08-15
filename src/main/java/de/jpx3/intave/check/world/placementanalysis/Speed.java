package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

@Reserved
public final class Speed extends MetaCheckPart<PlacementAnalysis, Speed.PlacementSpeedMeta> {
  private static final int CHECK_LENGTH = 8;
  private static final int DIRECTION_EVAL_LENGTH = 5;

  private final IntavePlugin plugin;

  public Speed(PlacementAnalysis parentCheck) {
    super(parentCheck, PlacementSpeedMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE, USE_ITEM
    }
  )
  public void receivePlacementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PlacementSpeedMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();

    if (event.getPacketType() == PacketType.Play.Client.BLOCK_PLACE) {
      Integer facing = packet.getIntegers().readSafely(0);
      if (facing == null) {
        facing = 0;
      }
      if (facing == 255) {
        meta.lastHardFaultClick = System.currentTimeMillis();
      }
    }
  }

  @BukkitEventSubscription
  public void blockPlacement(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    PlacementSpeedMeta meta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    EffectMetadata potionData = user.meta().potions();

    Block block = place.getBlockPlaced();
    Block blockAgainst = place.getBlockAgainst();

    if (blockUnderPlayer(block, player) && blockCollisions(block) < 2) {
      List<Long> placementSpeedHistory = meta.placementSpeedHistory;

      if (placementSpeedHistory.size() >= CHECK_LENGTH) {
        placementSpeedHistory.remove(0);
      }

      if (block.getY() == blockAgainst.getY()) {
        placementSpeedHistory.add(System.currentTimeMillis() - meta.lastPlacement);
        meta.lastPlacement = System.currentTimeMillis();
      } else {
        placementSpeedHistory.add(System.currentTimeMillis() - meta.lastPlacement + 1000);
      }

      if (placementSpeedHistory.size() >= CHECK_LENGTH) {
        double average = placementSpeedHistory.stream().mapToDouble(value -> value).average().orElse(500);
        boolean inOneLine = isOneLine(meta.placementHistory);

        boolean noHardFault = IntaveControl.GOMME_MODE && System.currentTimeMillis() - meta.lastHardFaultClick > 6000;
        boolean noSneaking = System.currentTimeMillis() - movementData.lastSneakingTimestamps > 8000;
        boolean recentJump = System.currentTimeMillis() - movementData.lastJump < 750;
        float yawToNextNinetyDeg = Math.abs(user.meta().movement().rotationYaw()) % 90;
        boolean ninetyDegreeAngle = yawToNextNinetyDeg < 10 || yawToNextNinetyDeg > 80;

        double minAverage;

        if (inOneLine) {
          if (recentJump) {
            minAverage = 450;
          } else if (ninetyDegreeAngle) {
            minAverage = noSneaking ? 500 : 350;
          } else {
            if (noHardFault) {
              minAverage = noSneaking ? 500 : 300;
            } else {
              minAverage = noSneaking ? 350 : 200;
            }
          }
        } else {
          minAverage = ninetyDegreeAngle || noSneaking ? 300 : 150;
        }

        int speedAmplifier = potionData.potionEffectSpeedAmplifier();
        minAverage /= 0.15 * speedAmplifier * speedAmplifier + 1;

//        player.sendMessage(average + "/" + minAverage + " " + noHardFault + "hf " + ninetyDegreeAngle + "90 " + noSneaking + "ns " + recentJump + "rj " + inOneLine + "il");
        if (average < minAverage) {
          Violation violation = Violation.builderFor(PlacementAnalysis.class)
            .forPlayer(player).withDefaultThreshold()
            .withMessage(COMMON_FLAG_MESSAGE)
            .withDetails(((int) average / 50) + " t/b, limit at " + ((int) minAverage / 50) + " t/b")
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .withDefaultThreshold().withVL(average > 400 ? 3 : average < 300 ? 5 : 4).build();

          ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
          if (violationContext.violationLevelAfter() > 20) {
            //dmc1
            parentCheck().applyPlacementAnalysisDamageCancel(user, "1");
          }
        }
      }
    }

    if (!place.isCancelled()) {
      List<Location> placementHistory = meta.placementHistory;
      if (placementHistory.size() >= DIRECTION_EVAL_LENGTH) {
        placementHistory.remove(0);
      }
      placementHistory.add(block.getLocation());
    }
  }

  private boolean blockUnderPlayer(Block block, Player player) {
    return block.getLocation().clone().add(0, 1, 0).distance(player.getLocation()) < 1.3;
  }

  private int blockCollisions(Block block) {
    int collisions = 0;

    if (!block.getRelative(BlockFace.SOUTH).getType().equals(Material.AIR)) collisions++;
    if (!block.getRelative(BlockFace.EAST).getType().equals(Material.AIR)) collisions++;
    if (!block.getRelative(BlockFace.NORTH).getType().equals(Material.AIR)) collisions++;
    if (!block.getRelative(BlockFace.WEST).getType().equals(Material.AIR)) collisions++;

    return collisions;
  }

  private boolean isOneLine(List<Location> blocks) {
    int lastBlockX = 0,
      lastBlockY = 0,
      lastBlockZ = 0;
    boolean lockedOnX = false,
      lockedOnZ = false;
    boolean first = true;

    int yTolerance = 2;
    for (Location block : blocks) {
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

  public static class PlacementSpeedMeta extends CheckCustomMetadata {
    private final List<Long> placementSpeedHistory = GarbageCollector.watch(new ArrayList<>());
    private final List<Location> placementHistory = GarbageCollector.watch(new ArrayList<>());
    private long lastPlacement;
    private long lastHardFaultClick;
  }
}
