package de.jpx3.intave.check.world.placementanalysis;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.EffectMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
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
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class Sneak extends MetaCheckPart<PlacementAnalysis, Sneak.SneakMeta> {
  private static final int CHECK_LENGTH = 24;

  private final IntavePlugin plugin;

  public Sneak(PlacementAnalysis parentCheck) {
    super(parentCheck, SneakMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @BukkitEventSubscription
  public void blockPlacement(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    SneakMeta meta = metaOf(user);
    MetadataBundle metadata = user.meta();
    MovementMetadata movementData = metadata.movement();
    EffectMetadata potionData = metadata.potions();
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
        boolean noSneaking = System.currentTimeMillis() - movementData.lastSneakingTimestamps > 6000;
        double limit = 500;

        int speedAmplifier = potionData.potionEffectSpeedAmplifier();
        limit /= 0.15 * speedAmplifier * speedAmplifier + 1;

        if (average < limit && inOneLine && noSneaking) {
          int ticksPerBlock = (int) (average / 50d);
          Violation violation = Violation.builderFor(PlacementAnalysis.class)
            .forPlayer(player).withDefaultThreshold()
            .withMessage(COMMON_FLAG_MESSAGE)
            .withDetails(ticksPerBlock + " t/b in a straight line without sneaking")
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .withDefaultThreshold().withVL(3).build();
          ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
          if (violationContext.violationLevelAfter() > 20) {
            //dmc79
            parentCheck().applyPlacementAnalysisDamageCancel(user, "79");
          }
        }
      }
    } else {
      meta.placementHistory.clear();
      meta.placementSpeedHistory.clear();
    }
    if (!place.isCancelled()) {
      List<Location> placementHistory = meta.placementHistory;
      if (placementHistory.size() >= CHECK_LENGTH) {
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

  private boolean isOneLine(List<? extends Location> blocks) {
    int lastBlockX = 0, lastBlockY = 0, lastBlockZ = 0;
    boolean lockedOnX = false, lockedOnZ = false;
    boolean first = true;
    for (Location block : blocks) {
      if (!first) {
        if (lastBlockY != block.getY()) return false;
        if (lastBlockX == block.getX()) lockedOnX = true;
        else if (lockedOnX) return false;
        if (lastBlockZ == block.getZ()) lockedOnZ = true;
        else if (lockedOnZ) return false;
      }
      lastBlockX = block.getBlockX();
      lastBlockY = block.getBlockY();
      lastBlockZ = block.getBlockZ();
      first = false;
    }
    return lockedOnX || lockedOnZ;
  }

  public static class SneakMeta extends CheckCustomMetadata {
    private final List<Long> placementSpeedHistory = GarbageCollector.watch(new ArrayList<>());
    private final List<Location> placementHistory = GarbageCollector.watch(new ArrayList<>());
    private long lastPlacement;
  }
}
