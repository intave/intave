package de.jpx3.intave.detect.checks.world.placementanalysis;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheckPart;
import de.jpx3.intave.detect.checks.world.PlacementAnalysis;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.violation.Violation;
import de.jpx3.intave.event.violation.ViolationContext;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserMetaPotionData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.detect.checks.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;

public final class PlacementSpeedAnalyzer extends IntaveMetaCheckPart<PlacementAnalysis, PlacementSpeedAnalyzer.PlacementSpeedMeta> {
  private final static int CHECK_LENGTH = 8;
  private final static int DIRECTION_EVAL_LENGTH = 5;

  private final IntavePlugin plugin;

  public PlacementSpeedAnalyzer(PlacementAnalysis parentCheck) {
    super(parentCheck, PlacementSpeedMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
    }
  )
  public void receivePlacementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PlacementSpeedMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();

    if(event.getPacketType() == PacketType.Play.Client.BLOCK_PLACE) {
      Integer facing = packet.getIntegers().readSafely(0);
      if(facing == null) {
        facing = 0;
      }
      if(facing == 255) {
        meta.lastHardFaultClick = AccessHelper.now();
      }
    }
  }

  @BukkitEventSubscription
  public void blockPlacement(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    PlacementSpeedMeta meta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();
    UserMetaPotionData potionData = user.meta().potionData();

    Block block = place.getBlockPlaced();
    Block blockAgainst = place.getBlockAgainst();

    if(blockUnderPlayer(block, player) && blockCollisions(block) < 2) {
      List<Long> placementSpeedHistory = meta.placementSpeedHistory;

      if(placementSpeedHistory.size() >= CHECK_LENGTH) {
        placementSpeedHistory.remove(0);
      }

      if(block.getY() == blockAgainst.getY()) {
        placementSpeedHistory.add(AccessHelper.now() - meta.lastPlacement);
        meta.lastPlacement = AccessHelper.now();
      } else {
        placementSpeedHistory.add(AccessHelper.now() - meta.lastPlacement + 1000);
      }

      if(placementSpeedHistory.size() >= CHECK_LENGTH) {
        double average = placementSpeedHistory.stream().mapToDouble(value -> value).average().orElse(500);
        boolean inOneLine = isOneLine(meta.placementHistory);

        boolean noHardFault = AccessHelper.now() - meta.lastHardFaultClick > 8000;
        boolean noSneaking = AccessHelper.now() - movementData.lastSneakingTimestamps > 8000;
        boolean recentJump = AccessHelper.now() - movementData.lastJumpTimestamps < 750;
        double minAverage = (inOneLine ? ((recentJump ? 450 : noHardFault ? (noSneaking ? 500 : 350) : (noSneaking ? 375 : 200))) : 150);

        int speedAmplifier = potionData.potionEffectSpeedAmplifier();
        minAverage /= 0.15 * speedAmplifier * speedAmplifier + 1;

        if(average < minAverage) {
          Violation violation = Violation.builderFor(PlacementAnalysis.class)
            .forPlayer(player).withDefaultThreshold()
            .withMessage(COMMON_FLAG_MESSAGE)
            .withDetails(((int) average) + "ms/block, limit at " + ((int) minAverage) + "ms/block")
            .withDefaultThreshold().withVL(5).build();

          ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);
          if (violationContext.violationLevelAfter() > 20) {
            //dmc1
            parentCheck().applyPlacementAnalysisDamageCancel(user, "1");
          }
        }
      }
    }

    if(!place.isCancelled()) {
      List<Location> placementHistory = meta.placementHistory;
      if (placementHistory.size() >= DIRECTION_EVAL_LENGTH) {
        placementHistory.remove(0);
      }
      placementHistory.add(block.getLocation());
    }
  }

  private boolean blockUnderPlayer(Block block, Player player) {
    return block.getLocation().clone().add(0,1,0).distance(player.getLocation()) < 1.3;
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
    for (Location block : blocks) {
      if(!first) {
        if(lastBlockY != block.getY()) {
          return false;
        }
        if(lastBlockX == block.getX()) {
          lockedOnX = true;
        } else if(lockedOnX) {
          return false;
        }
        if(lastBlockZ == block.getZ()) {
          lockedOnZ = true;
        } else if(lockedOnZ) {
          return false;
        }
      }
      lastBlockX = block.getBlockX();
      lastBlockY = block.getBlockY();
      lastBlockZ = block.getBlockZ();
      first = false;
    }
    return lockedOnX || lockedOnZ;
  }

  public static class PlacementSpeedMeta extends UserCustomCheckMeta {
    private final List<Long> placementSpeedHistory = new ArrayList<>();
    private final List<Location> placementHistory = new ArrayList<>();
    private long lastPlacement;
    private long lastHardFaultClick;
  }
}
