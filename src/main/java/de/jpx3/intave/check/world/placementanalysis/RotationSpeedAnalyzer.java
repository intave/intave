package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.violation.Violation;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class RotationSpeedAnalyzer extends MetaCheckPart<PlacementAnalysis, RotationSpeedAnalyzer.RotationSpeedMeta> {
  private final IntavePlugin plugin;

  public RotationSpeedAnalyzer(PlacementAnalysis parentCheck) {
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
    if (place.getBlock().getY() < player.getLocation().getBlockY() && blockAgainstWasPlaced(user, place.getBlockAgainst())) {
      List<Float> rotationHistory = meta.rotationHistory;
      double rotationSum = rotationHistory.stream().mapToDouble(value -> value).sum();
      if (rotationSum > 3000) {
        Violation violation = Violation.builderFor(PlacementAnalysis.class)
          .forPlayer(player).withDefaultThreshold()
          .withMessage(COMMON_FLAG_MESSAGE)
          .withDetails("high rotation activity while placing blocks") // + " (" + ((int) rotationSum) + " degrees)
          .withDefaultThreshold().withVL(0).build();
        plugin.violationProcessor().processViolation(violation);
        place.setCancelled(true);
      }
    }
    if (place.isCancelled()) {
      return;
    }
    if (meta.lastBlocksPlaced.size() > 10) {
      meta.lastBlocksPlaced.remove(0);
    }
    meta.lastBlocksPlaced.add(place.getBlock().getLocation().toVector());
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
    private final List<Float> rotationHistory = new ArrayList<>();
    private final List<Vector> lastBlocksPlaced = new ArrayList<>();
    private long lastBlockPlacement;
  }
}
