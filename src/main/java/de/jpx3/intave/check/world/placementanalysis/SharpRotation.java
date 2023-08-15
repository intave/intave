package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Reserved;
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

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

@Reserved
public final class SharpRotation extends MetaCheckPart<PlacementAnalysis, SharpRotation.SharpRotationMeta> {
  private final IntavePlugin plugin;

  public SharpRotation(PlacementAnalysis parentCheck) {
    super(parentCheck, SharpRotationMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void on(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    SharpRotationMeta meta = metaOf(user);
    float rotationMovement = Math.min(MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw), 360);

    boolean recentBlockPlacement = System.currentTimeMillis() - meta.lastBlockPlacement < 2000;
    boolean hit = Math.abs(rotationMovement - 180) < 10;
    if (hit && recentBlockPlacement) {
      meta.sharpRotations++;
    }
  }

  @BukkitEventSubscription
  public void on(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    SharpRotationMeta meta = metaOf(user);

    if (place.getBlock().getY() < player.getLocation().getBlockY()) {
      if (System.currentTimeMillis() - meta.sharpRotationReset > 10000) {
        meta.sharpRotations -= 1;
        meta.sharpRotations /= 2;
        meta.sharpRotationReset = System.currentTimeMillis();
      }
      meta.lastBlockPlacement = System.currentTimeMillis();
      if (meta.sharpRotations > 4 && blockAgainstWasPlaced(user, place.getBlockAgainst())) {
        String details = "maintains sharp 180deg rotations";
        Violation violation = Violation.builderFor(PlacementAnalysis.class)
          .forPlayer(player).withMessage(COMMON_FLAG_MESSAGE).withDetails(details)
          .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
          .withDefaultThreshold().withVL(meta.sharpRotations > 10 ? 10 : 0).build();
        Modules.violationProcessor().processViolation(violation);
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

  public static class SharpRotationMeta extends CheckCustomMetadata {
    private long sharpRotations = 0;
    private long sharpRotationReset = System.currentTimeMillis();
    private long lastBlockPlacement = 0;

    private final List<Vector> lastBlocksPlaced = new ArrayList<>();
  }
}
