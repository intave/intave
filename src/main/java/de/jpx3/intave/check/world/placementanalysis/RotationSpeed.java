package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class RotationSpeed extends MetaCheckPart<PlacementAnalysis, RotationSpeed.RotationSpeedMeta> {
  private final IntavePlugin plugin;

  public RotationSpeed(PlacementAnalysis parentCheck) {
    super(parentCheck, RotationSpeedMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    RotationSpeedMeta meta = metaOf(user);
    float rotationMovement = Math.min(MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw), 360);
    if (System.currentTimeMillis() - meta.lastBlockPlacement > 2000 || movementData.lastTeleport <= 5) {
      return;
    }
    List<Float> rotationHistory = meta.rotationHistory;
    if (rotationHistory.size() > 5 * 20) {
      rotationHistory.remove(0);
    }
    rotationHistory.add(rotationMovement);
  }

  @BukkitEventSubscription
  public void on(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    RotationSpeedMeta meta = metaOf(user);
    meta.lastBlockPlacement = System.currentTimeMillis();

    if (System.currentTimeMillis() - meta.denyPlacementRequest < 1000) {
      place.setCancelled(true);
      return;
    }

//    Block belowPlaced = place.getBlockPlaced().getRelative(BlockFace.DOWN);
    if (place.getBlock().getY() < player.getLocation().getBlockY() /*&& belowPlaced.getType() == Material.AIR*/ && blockAgainstWasPlaced(user, place.getBlockAgainst())) {
      List<Float> rotationHistory = meta.rotationHistory;
      double rotationSum = 0.0;
      for (Float value : rotationHistory) {
        rotationSum += value;
      }

      float limit = 3000;
      if (!user.trustFactor().atLeast(TrustFactor.ORANGE)) {
        limit -= 750;
      }

      // check if past placements are in a straight line on one axis
      List<Vector> lastBlocksPlaced = meta.lastBlocksPlaced;
      if (isOneLine(lastBlocksPlaced)) {
        limit -= 750;
      }

      if (rotationSum > limit) {
        Violation violation = Violation.builderFor(PlacementAnalysis.class)
          .forPlayer(player).withDefaultThreshold()
          .withMessage(COMMON_FLAG_MESSAGE)
          .withDetails("high rotation activity while placing blocks") // + " (" + ((int) rotationSum) + " degrees)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDefaultThreshold().withVL(10).build();
        Modules.violationProcessor().processViolation(violation);
        meta.denyPlacementRequest = System.currentTimeMillis();
        place.setCancelled(true);
      }
    }
    if (place.isCancelled()) {
      return;
    }
    if (meta.lastBlocksPlaced.size() > 4) {
      meta.lastBlocksPlaced.remove(0);
    }
    meta.lastBlocksPlaced.add(place.getBlock().getLocation().toVector());
  }

  private boolean isOneLine(List<? extends Vector> blocks) {
    int lastBlockX = 0, lastBlockZ = 0;
    boolean lockedOnX = false, lockedOnZ = false;
    boolean first = true;
    for (Vector block : blocks) {
      if (!first) {
        if (lastBlockX == block.getX()) lockedOnX = true;
        else if (lockedOnX) return false;
        if (lastBlockZ == block.getZ()) lockedOnZ = true;
        else if (lockedOnZ) return false;
      }
      lastBlockX = block.getBlockX();
      lastBlockZ = block.getBlockZ();
      first = false;
    }
    return lockedOnX || lockedOnZ;
  }

  private boolean blockAgainstWasPlaced(User user, Block blockAgainst) {
    Vector vector = blockAgainst.getLocation().toVector();
    List<Vector> lastBlocksPlaced = metaOf(user).lastBlocksPlaced;
    for (Vector location : lastBlocksPlaced) {
      if (location.distance(vector) == 0) {
        return true;
      }
    }
    return false;
  }

  public static class RotationSpeedMeta extends CheckCustomMetadata {
    private final List<Float> rotationHistory = new CopyOnWriteArrayList<>();
    private final List<Vector> lastBlocksPlaced = new CopyOnWriteArrayList<>();
    private long lastBlockPlacement;
    private long denyPlacementRequest;
  }
}
