package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerActionReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

@Reserved
public final class SneakAndPlace extends MetaCheckPart<PlacementAnalysis, SneakAndPlace.SneakAndPlaceMeta> {
  public SneakAndPlace(PlacementAnalysis parentCheck) {
    super(parentCheck, SneakAndPlaceMeta.class);
  }

  @PacketSubscription(
      priority = ListenerPriority.HIGH,
      packetsIn = {
          FLYING, LOOK, POSITION, POSITION_LOOK
      }
  )
  public void clientTickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    SneakAndPlaceMeta meta = metaOf(player);
    if (meta.placedInThisTick || meta.sneakChangedInThisTick) {
//      player.sendMessage(meta.sneakInThisTick + "("+meta.startSneakInThisTick+","+meta.stopSneakInThisTick+")/" + meta.placedInThisTick);
      if (meta.placedInThisTick) {
        // difference to last sneak start
        long diff = meta.startSneakInThisTick ? 0 : meta.tickCount - meta.lastSneakStart;

        boolean suspiciousSneaking = diff <= 2 && meta.lastSneakDuration < 2;
        if (!suspiciousSneaking && meta.violationLevel > 0) {
          meta.violationLevel -= 0.1;
        } else if (suspiciousSneaking) {
          meta.violationLevel += diff > 1 ? 0.1 : 0.5;
//          player.sendMessage(ChatColor.YELLOW + "Sneak start -> Place: " + diff + " last duration: " + meta.lastSneakDuration);
        }
      }
      if (meta.sneakChangedInThisTick) {
        // difference to last place
        long diff = meta.placedInThisTick ? 0 : meta.tickCount - meta.lastPlace;
        boolean suspiciousSneaking = diff <= 2 && meta.lastSneakDuration < 2;
        if (!suspiciousSneaking && meta.violationLevel > 0) {
          meta.violationLevel -= 0.1;
        } else if (suspiciousSneaking) {
          meta.violationLevel += diff > 1 ? 0.1 : 0.75;
//          player.sendMessage(ChatColor.YELLOW +"Place -> Sneak start: " + diff + " last duration: " + meta.lastSneakDuration);
        }
      }
    }
    if (meta.startSneakInThisTick) {
      meta.lastSneakStart = meta.tickCount;
    }
    if (meta.placedInThisTick) {
      meta.lastPlace = meta.tickCount;
    }
    meta.startSneakInThisTick = false;
    meta.stopSneakInThisTick = false;
    meta.sneakChangedInThisTick = false;
    meta.placedInThisTick = false;
    meta.tickCount++;
    meta.sneakDuration++;
  }

  @PacketSubscription(
      priority = ListenerPriority.HIGH,
      packetsIn = {
          BLOCK_PLACE
      }
  )
  public void receivePlacementPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();
    SneakAndPlaceMeta meta = metaOf(player);

    Integer facing = packet.getIntegers().readSafely(0);
    if (facing == null) {
      facing = 0;
    }
    if (facing == 255) {
      return;
    }
    User user = userOf(player);
    Material material = user.meta().inventory().heldItemType();
    boolean hasPlaceable = material.isBlock() && material.isSolid();
    if (!hasPlaceable) {
      return;
    }
    meta.placedInThisTick = true;
  }

  @PacketSubscription(
      priority = ListenerPriority.HIGH,
      packetsIn = {
          ENTITY_ACTION_IN
      }
  )
  public void receiveEntityActionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    SneakAndPlaceMeta meta = metaOf(player);
    PacketContainer packet = event.getPacket();
    PlayerActionReader reader = PacketReaders.readerOf(packet);

    PlayerAction action = reader.playerAction();
    if (action.isStartSneak()) {
      meta.startSneakInThisTick = true;
      meta.sneakChangedInThisTick = true;
      meta.isSneaking = true;
      meta.sneakDuration = 0;
    } else if (action.isStopSneak()) {
      meta.stopSneakInThisTick = true;
      meta.sneakChangedInThisTick = true;
      meta.isSneaking = false;
      meta.lastSneakDuration = meta.sneakDuration;
      meta.sneakDuration = 0;
    }

    reader.release();
  }

  @BukkitEventSubscription
  public void on(BlockPlaceEvent place) {
    Player player = place.getPlayer();
    User user = userOf(player);
    SneakAndPlaceMeta meta = metaOf(user);

    if (place.getBlock().getY() < player.getLocation().getBlockY() && isOneLine(meta.lastBlocksPlaced) && blockAgainstWasPlaced(user, place.getBlockAgainst())) {
      if (meta.violationLevel > 5) {
        Violation violation = Violation.builderFor(PlacementAnalysis.class)
            .forPlayer(player).withDefaultThreshold()
            .withMessage(COMMON_FLAG_MESSAGE)
            .withDetails("sneaking seems to be automated")
            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
            .withDefaultThreshold().withVL(Math.min(meta.violationLevel / 1.5, 5)).build();
        Modules.violationProcessor().processViolation(violation);
      }
    } else {
      meta.violationLevel = 0;
    }
    if (place.isCancelled()) {
      return;
    }
    if (meta.lastBlocksPlaced.size() > 5) {
      meta.lastBlocksPlaced.remove(0);
    }
    meta.lastBlocksPlaced.add(place.getBlock().getLocation().toVector());
  }

  private boolean isOneLine(List<? extends Vector> blocks) {
    int lastBlockX = 0,
        lastBlockY = 0,
        lastBlockZ = 0;
    boolean lockedOnX = false,
        lockedOnZ = false;
    boolean first = true;
    int yTolerance = 2;
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

  public static class SneakAndPlaceMeta extends CheckCustomMetadata {
    private final List<Vector> lastBlocksPlaced = new CopyOnWriteArrayList<>();
    private boolean startSneakInThisTick;
    private boolean stopSneakInThisTick;
    private boolean sneakChangedInThisTick;
    private boolean placedInThisTick;
    private boolean isSneaking;
    private boolean suspicious;
    private long lastSneakStart;
    private long lastPlace;
    private long lastSneakDuration = 10;
    private long sneakDuration;
    private double violationLevel;
    private long tickCount;
  }
}
