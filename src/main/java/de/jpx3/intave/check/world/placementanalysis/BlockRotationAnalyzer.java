package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.shade.EnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ITEM;

public final class BlockRotationAnalyzer extends MetaCheckPart<PlacementAnalysis, BlockRotationAnalyzer.BlockRotationMeta> {
  private final IntavePlugin plugin;

  public BlockRotationAnalyzer(PlacementAnalysis parentCheck) {
    super(parentCheck, BlockRotationMeta.class);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      BLOCK_PLACE, USE_ITEM
    }
  )
  public void receivePlacementPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    MovementMetadata movement = user.meta().movement();
    AbilityMetadata abilities = user.meta().abilities();

    BlockRotationMeta meta = metaOf(user);

    BlockInteractionReader reader = PacketReaders.readerOf(packet);;
    com.comphenix.protocol.wrappers.BlockPosition blockPosition = reader.blockPosition();

    if (blockPosition == null || event.isCancelled() || movement.hasRidingEntity()) {
      reader.close();
      return;
    }

    int enumDirection = reader.enumDirection();
    if (enumDirection == 255) {
      reader.close();
      return;
    }

    Material clickedType = VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(player.getWorld()));
    boolean clickableInteraction = BlockInteractionAccess.isClickable(clickedType);
    Material heldItemType = user.meta().inventory().heldItemType();
    boolean interactionIsPlacement = heldItemType != Material.AIR && heldItemType.isBlock() && !clickableInteraction && !abilities.inGameMode(GameMode.ADVENTURE);

    if (!interactionIsPlacement || enumDirection < 2) {
      reader.close();
      return;
    }

    meta.placementSpeedHistory.add(Math.min(1000, System.currentTimeMillis() - meta.lastPlacement));
    meta.lastPlacement = System.currentTimeMillis();
    double average = 500;

    if (meta.placementSpeedHistory.size() >= 8) {
      average = meta.placementSpeedHistory.stream().mapToDouble(value -> value).average().orElse(500);
      meta.placementSpeedHistory.remove(0);
    }

    if (movement.rotationPitch > 85 && average < 400) {
      if (meta.vl++ > 3) {
        String details = "pitch of "+((int) movement.rotationPitch)+" placing blocks in " + MathHelper.formatDouble(average, 2) + " ms/block";
        Violation violation = Violation.builderFor(PlacementAnalysis.class)
          .forPlayer(player).withMessage(COMMON_FLAG_MESSAGE).withDetails(details)
          .withDefaultThreshold().withVL(0).build();
        Modules.violationProcessor().processViolation(violation);
      }
//      event.setCancelled(true);
//      Synchronizer.synchronizeDelayed(() -> refreshBlocksAround(player, blockPosition.toLocation(player.getWorld())), 20);
    } else if (meta.vl > 0){
      meta.vl /= 0.98;
      meta.vl -= 0.002;
    }
    reader.close();
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    Synchronizer.synchronize(() -> {
      player.updateInventory();
      refreshBlock(player, targetLocation);
      for (EnumDirection direction : EnumDirection.values()) {
        Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
        refreshBlock(player, placedBlock);
      }
    });
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) {
      return;
    }
    Block block = VolatileBlockAccess.unsafe__BlockAccess(location);
    Object handle = BlockVariantAccess.nativeVariantAccess(block);
    WrappedBlockData blockData = WrappedBlockData.fromHandle(handle);
    com.comphenix.protocol.wrappers.BlockPosition position = new com.comphenix.protocol.wrappers.BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockData().write(0, blockData);
    packet.getBlockPositionModifier().write(0, position);
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    } catch (InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }

  public static class BlockRotationMeta extends CheckCustomMetadata {
    private final List<Long> placementSpeedHistory = GarbageCollector.watch(new ArrayList<>());
    private long lastPlacement;
    private double vl;
  }
}
