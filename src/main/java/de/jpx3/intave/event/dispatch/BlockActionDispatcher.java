package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.block.BlockDataAccess;
import de.jpx3.intave.world.collision.BoundingBoxAccess;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;

public final class BlockActionDispatcher implements EventProcessor {
  private final IntavePlugin plugin;

  public final List<Material> replaceableMaterials = new ArrayList<>();

  // TODO: 01/09/21 prevent invalid chunk access

  public BlockActionDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
    }
  )
  public void receiveInteraction(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
      event.setCancelled(true);
      return;
    }
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if(blockPosition == null) {
      return;
    }
    Integer enumDirection = packet.getIntegers().readSafely(0);
    if(enumDirection == null) {
      enumDirection = packet.getDirections().readSafely(0).ordinal();
    }
    if(enumDirection == 255) {
      return;
    }

/*    if(event.isCancelled()) {
      return;
    }*/

    // distance check

    World world = player.getWorld();
    Location blockAgainstLocation = blockPosition.toLocation(world).clone();

    // air check

//    if(BlockAccessor.blockAccess(blockAgainstLocation).getType() == Material.AIR) {
//      event.setCancelled(true);
//      refreshBlocksAround(player, blockAgainstLocation);
//      return;
//    }

    boolean replace = BlockDataAccess.replacementPlace(world, new BlockPosition(blockAgainstLocation.toVector()));
    Location finalBlockLocation = blockAgainstLocation.clone().add(WrappedEnumDirection.getFront(enumDirection).getDirectionVec().convertToBukkitVec());
    Location blockPlacementLocation = replace ? blockAgainstLocation : finalBlockLocation;

    User user = UserRepository.userOf(player);
    Material itemTypeInHand = user.meta().inventoryData().heldItemType();

    Block clickedBlock = BlockAccessor.blockAccess(blockAgainstLocation);
    Material clickedType = clickedBlock.getType();

    boolean targetBlockClickable = BlockDataAccess.isClickable(clickedType);
    boolean placeableBlockInHand = itemTypeInHand != Material.AIR && (itemTypeInHand.isBlock());
    boolean isPlacement = placeableBlockInHand && !targetBlockClickable;

    if(isPlacement) {
      int blockX = blockPlacementLocation.getBlockX();
      int blockY = blockPlacementLocation.getBlockY();
      int blockZ = blockPlacementLocation.getBlockZ();

      // we need to check if a player is on 1.16 and ViaVersion emulates a placement packet invalid
      Material material = user.meta().inventoryData().heldItemType();
      int dat = 0;
      boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
        user, world, blockX, blockY, blockZ,
        material.getId(),
        dat
      );

      if(raytraceCollidesWithPosition) {
        event.setCancelled(true);
        refreshBlocksAround(player, blockPlacementLocation);
        return;
      }

      EnumWrappers.Hand hand = packet.getHands().readSafely(0);

      int id = itemTypeInHand.getId();
      byte shape = 0;

      boolean access = plugin.interactionPermissionService()
        .blockPlacePermissionCheck()
        .hasPermission(
          player,
          world,
          hand == null || hand == EnumWrappers.Hand.MAIN_HAND,
          blockX, blockY, blockZ,
          id,
          (byte) 0
        );

      if(access) {
        player.sendMessage("Internal place emulation at " + MathHelper.formatPosition(blockPlacementLocation) + " with " + Material.getMaterial(id));

        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, blockX, blockY, blockZ, id, shape);
//        boundingBoxAccess.invalidate(blockX, blockY, blockZ);

      } else {
        event.setCancelled(true);
        refreshBlocksAround(player, blockPlacementLocation);
      }
    } else {
      if(clickedType == Material.WOODEN_DOOR) {

      } else if(clickedType == Material.TRAP_DOOR) {
        int data = clickedBlock.getData();
        boolean newOpen = (data & 4) != 0;
        int bitMask = 4;
        byte newData = (byte) (!newOpen ? (data | bitMask) : (data & ~bitMask));

//        player.sendMessage("Currently open: " + newOpen + " " + Integer.toBinaryString(data) + " -> " + Integer.toBinaryString(newData));

        int id = clickedBlock.getType().getId();
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ(), id, newData);

        Synchronizer.packetSynchronize(() ->
          boundingBoxAccess.invalidateOverride(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
      } else {
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();

        Synchronizer.synchronizeDelayed(() ->
          boundingBoxAccess.invalidate(finalBlockLocation.getBlockX(), finalBlockLocation.getBlockY(), finalBlockLocation.getBlockZ()),
        2);
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void receiveBreak(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
      event.setCancelled(true);
      return;
    }
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if(blockPosition == null) {
      return;
    }
    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);

    float blockDamage = BlockDataAccess.blockDamage(player, user.meta().inventoryData().heldItem(), blockPosition);
    boolean instantBreak = blockDamage == Float.POSITIVE_INFINITY || blockDamage >= 1.0f;
    boolean breakBlock = instantBreak || playerDigType == STOP_DESTROY_BLOCK;

    if(!breakBlock) {
      return;
    }

    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    int enumDirection = direction == null ? 255 : direction.ordinal();
    if(enumDirection == 255) {
      return;
    }

    World world = player.getWorld();
    Location blockBreakLocation = blockPosition.toLocation(world);

    boolean access = plugin.interactionPermissionService()
      .blockBreakPermissionCheck()
      .hasPermission(
        player,
        BlockAccessor.blockAccess(blockBreakLocation)
      );

    if(access) {
      int blockX = blockBreakLocation.getBlockX();
      int blockY = blockBreakLocation.getBlockY();
      int blockZ = blockBreakLocation.getBlockZ();

//      player.sendMessage("Internal break emulation at " + MathHelper.formatPosition(blockBreakLocation));
//      Synchronizer.synchronize(() -> {
//        Raytracer.ignoreBlock(player, blockBreakLocation);
//      });

      // add to future bounding boxes
      BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
      boundingBoxAccess.override(world, blockX, blockY, blockZ, 0, (byte) 0);
      boundingBoxAccess.invalidate(blockX, blockY, blockZ);

//      Synchronizer.synchronizeDelayed(() -> {
////        boundingBoxAccess.invalidateOverride(world, blockX, blockY, blockZ);
//        boundingBoxAccess.invalidate(blockX, blockY, blockZ);
//      }, 2);
    } else {
      refreshBlocksAround(player, blockBreakLocation);
      event.setCancelled(true);
    }
  }

  @BukkitEventSubscription(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onPre(BlockPlaceEvent place) {
    if(/*place.isCancelled() && */place.getClass().equals(BlockPlaceEvent.class)) {
      Block block = place.getBlock();
      Synchronizer.synchronizeDelayed(() -> {
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(place.getPlayer()).boundingBoxAccess();
        boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
        boundingBoxAccess.invalidateOverride(block.getWorld(), block.getX(), block.getY(), block.getZ());
        place.getPlayer().sendMessage("Reset place");
      }, 2);
    }
  }

  @BukkitEventSubscription(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onPre(BlockBreakEvent breeak) {
    if(/*breeak.isCancelled() && */breeak.getClass().equals(BlockBreakEvent.class)) {
      Block block = breeak.getBlock();
      Synchronizer.synchronizeDelayed(() -> {
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(breeak.getPlayer()).boundingBoxAccess();
        boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
        boundingBoxAccess.invalidateOverride(block.getWorld(), block.getX(), block.getY(), block.getZ());
        breeak.getPlayer().sendMessage("Reset break");
      }, 2);
    }
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "BLOCK_BREAK"),
//      @PacketDescriptor(sender = Sender.SERVER, packetName = "BLOCK_ACTION"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "BLOCK_CHANGE"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "MULTI_BLOCK_CHANGE")
    }
  )
  public void sentBlockUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    PacketType packetType = event.getPacketType();

    BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
    World world = player.getWorld();
    if(packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
      MultiBlockChangeInfo[] multiBlockChangeInfos = packet.getMultiBlockChangeInfoArrays().readSafely(0);
      for (MultiBlockChangeInfo multiBlockChangeInfo : multiBlockChangeInfos) {
        boundingBoxAccess.invalidate(multiBlockChangeInfo.getAbsoluteX(), multiBlockChangeInfo.getY(), multiBlockChangeInfo.getAbsoluteZ());
//        boundingBoxAccess.invalidateOverride(world, multiBlockChangeInfo.getAbsoluteX(), multiBlockChangeInfo.getY(), multiBlockChangeInfo.getAbsoluteZ());
      }
    } else {
      BlockPosition position = packet.getBlockPositionModifier().readSafely(0);
      boundingBoxAccess.invalidate(position.getX(), position.getY(), position.getZ());
//      boundingBoxAccess.invalidateOverride(world, position.getX(), position.getY(), position.getZ());
    }
  }

//  @BukkitEventSubscription(ignoreCancelled = true)
//  public void on(BlockBreakEvent event) {
//    Raytracer.clearIgnoreBlock(event.getPlayer(), event.getBlock().getLocation());
//  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    Location targetLocationClone = targetLocation.clone();
    Synchronizer.synchronize(() -> {
      player.updateInventory();
      refreshBlock(player, targetLocationClone);
      for (WrappedEnumDirection direction : WrappedEnumDirection.values()) {
        Location placedBlock = targetLocationClone.clone().add(direction.getDirectionVec().convertToBukkitVec());
        refreshBlock(player, placedBlock);
      }
    });
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    Block block = BlockAccessor.blockAccess(location);
    WrappedBlockData blockData = WrappedBlockData.createData(block.getType(), block.getData());
    BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockData().write(0, blockData);
    packet.getBlockPositionModifier().write(0, position);
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    } catch (InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }
}
