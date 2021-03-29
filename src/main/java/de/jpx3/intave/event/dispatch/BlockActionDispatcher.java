package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.reflect.ReflectiveMaterialAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.collision.BoundingBoxAccess;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.permission.WorldPermission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
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

//  @PacketSubscription(
//    priority = ListenerPriority.LOW,
//    packets = {
//      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
//      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
//    }
//  )
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

    World world = player.getWorld();
    Location blockAgainstLocation = blockPosition.toLocation(world).clone();

    User user = UserRepository.userOf(player);


    // context resolve
    Block clickedBlock = BukkitBlockAccess.blockAccess(blockAgainstLocation);
    Material clickedType = clickedBlock.getType();
    boolean targetBlockClickable = BlockDataAccess.isClickable(clickedType);
    Location defaultPlacementLocation = blockAgainstLocation.clone().add(WrappedEnumDirection.getFront(enumDirection).getDirectionVec().convertToBukkitVec());
    boolean replace = BlockDataAccess.replacementPlace(world, new BlockPosition(blockAgainstLocation.toVector()));
    Location blockPlacementLocation = replace ? blockAgainstLocation : defaultPlacementLocation;

    // precache
    Material itemTypeInHand = user.meta().inventoryData().heldItemType();
    boolean placeableBlockInHand = itemTypeInHand != Material.AIR && (itemTypeInHand.isBlock());
    EnumWrappers.Hand hand = packet.getHands().readSafely(0);
    int replacementId = itemTypeInHand.getId();
    byte shape = 0;

    boolean isPlacement = placeableBlockInHand && !targetBlockClickable;

    if(isPlacement) {
      int blockX = blockPlacementLocation.getBlockX();
      int blockY = blockPlacementLocation.getBlockY();
      int blockZ = blockPlacementLocation.getBlockZ();

      // we need to check if a player is on 1.16 and ViaVersion emulates a placement packet invalid
      int dat = 0;
      boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
        user, world, blockX, blockY, blockZ,
        itemTypeInHand.getId(),
        dat
      );

      if(raytraceCollidesWithPosition) {
        event.setCancelled(true);
        refreshBlocksAround(player, blockPlacementLocation);
        return;
      }

      boolean access = WorldPermission.blockPlacePermission(
          player,
          world,
          hand == null || hand == EnumWrappers.Hand.MAIN_HAND,
          blockX, blockY, blockZ,
          enumDirection,
          replacementId,
          (byte) 0
        );

      if(access) {
        if (IntaveControl.DEBUG_BLOCK_CACHING) {
          player.sendMessage("Internal place emulation at " + MathHelper.formatPosition(blockPlacementLocation) + " with " + ReflectiveMaterialAccess.materialById(replacementId));
        }

        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, blockX, blockY, blockZ, replacementId, shape);

        // enforce block reset later
        Synchronizer.packetSynchronize(() -> {
          Synchronizer.synchronize(() -> boundingBoxAccess.invalidateOverride(world, blockX, blockY, blockZ));
        });
      } else {
        if (IntaveControl.DEBUG_BLOCK_CACHING) {
          Synchronizer.synchronize(() -> {
            player.sendMessage("Internal place emulation denied at " + MathHelper.formatPosition(blockPlacementLocation) + " with " + ReflectiveMaterialAccess.materialById(replacementId));
          });
        }

        event.setCancelled(true);
        refreshBlocksAround(player, blockPlacementLocation);
      }
    } else {
      // ->

      if(clickedType == Material.WOODEN_DOOR) {

      } else if(clickedType == Material.TRAP_DOOR) {
        int data = clickedBlock.getData();
        boolean newOpen = (data & 4) != 0;
        int bitMask = 4;
        byte newData = (byte) (!newOpen ? (data | bitMask) : (data & ~bitMask));

        int id = clickedBlock.getType().getId();
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ(), id, newData);

        Synchronizer.packetSynchronize(() ->
          boundingBoxAccess.invalidateOverride(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
      }
    }
  }

//  @BukkitEventSubscription(ignoreCancelled = true)
//  public void onPre(BlockPlaceEvent place) {
//    if(/*place.isCancelled() && */place.getClass().equals(BlockPlaceEvent.class)) {
//      Block block = place.getBlock();
//      if(IntaveControl.DEBUG_BLOCK_CACHING) {
//        place.getPlayer().sendMessage("PlaceEvent " + place.getBlock());
//      }
//      BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(place.getPlayer()).boundingBoxAccess();
//      boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
//      boundingBoxAccess.invalidateOverride(block.getWorld(), block.getX(), block.getY(), block.getZ());
//    }
//  }

//  @PacketSubscription(
//    priority = ListenerPriority.LOWEST,
//    packets = {
//      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
//    }
//  )
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

    boolean access = WorldPermission.blockBreakPermission(
      player,
      BukkitBlockAccess.blockAccess(blockBreakLocation)
    );

    if (user.boundingBoxAccess().currentlyInOverride(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ())) {
      if (IntaveControl.DEBUG_BLOCK_CACHING) {
        player.sendMessage("Block break deny on client-side block");
      }
      return;
    }

    if(access) {
      if (IntaveControl.DEBUG_BLOCK_CACHING) {
        player.sendMessage("Internal break emulation at " + MathHelper.formatPosition(blockBreakLocation));
      }

      int blockX = blockBreakLocation.getBlockX();
      int blockY = blockBreakLocation.getBlockY();
      int blockZ = blockBreakLocation.getBlockZ();

      // add to future bounding boxes
      BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
      boundingBoxAccess.override(world, blockX, blockY, blockZ, 0, (byte) 0);
    } else {
      refreshBlocksAround(player, blockBreakLocation);
      event.setCancelled(true);
    }
  }

//  @BukkitEventSubscription(ignoreCancelled = true)
//  public void onPre(BlockBreakEvent breeak) {
//    if(/*breeak.isCancelled() && */breeak.getClass().equals(BlockBreakEvent.class)) {
//      Block block = breeak.getBlock();
//      BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(breeak.getPlayer()).boundingBoxAccess();
//      boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
//      boundingBoxAccess.invalidateOverride(block.getWorld(), block.getX(), block.getY(), block.getZ());
////      Synchronizer.synchronizeDelayed(() -> {
////        if (IntaveControl.DEBUG_BLOCK_CACHING) {
////          breeak.getPlayer().sendMessage("Reset break");
////        }
////      }, 2);
//    }
//  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "MAP_CHUNK"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "MAP_CHUNK_BULK")
    }
  )
  public void chunkUpdate(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    PacketType type = packet.getType();
    Player player = event.getPlayer();
    if(type == PacketType.Play.Server.MAP_CHUNK_BULK) {
      StructureModifier<int[]> integerArrays = packet.getIntegerArrays();
      int[] xArr = integerArrays.read(0).clone();
      int[] zArr = integerArrays.read(1).clone();
      plugin.eventService().transactionFeedbackService().requestPong(player, null, (player1, target) -> {
        for (int i = 0; i < xArr.length; i++) {
          chunkInvalidate(player, xArr[i], zArr[i]);
        }
      });
    } else {
      int x = packet.getIntegers().read(0);
      int z = packet.getIntegers().read(1);
      plugin.eventService().transactionFeedbackService().requestPong(player, null, (player1, target) -> {
        chunkInvalidate(player, x, z);
      });
    }
  }

  private void chunkInvalidate(Player player, int chunkX, int chunkZ) {
    int chunkXMinPos = chunkX << 4,
        chunkXMaxPos = chunkXMinPos + 16,
        chunkZMinPos = chunkZ << 4,
        chunkZMaxPos = chunkZMinPos + 16;
    BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
    boundingBoxAccess.invalidateOverridesInBounds(chunkXMinPos, chunkXMaxPos, chunkZMinPos, chunkZMaxPos);
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "BLOCK_BREAK"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "BLOCK_CHANGE"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "MULTI_BLOCK_CHANGE")
    }
  )
  public void sentBlockUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    PacketType packetType = event.getPacketType();

    BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();

    List<BlockPosition> blockPositions;
    List<WrappedBlockData> blockDataList;

    if(packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
      MultiBlockChangeInfo[] multiBlockChangeInfos = packet.getMultiBlockChangeInfoArrays().readSafely(0);
      blockPositions = new ArrayList<>();
      blockDataList = new ArrayList<>();
      for (MultiBlockChangeInfo multiBlockChangeInfo : multiBlockChangeInfos) {
        blockPositions.add(new BlockPosition(multiBlockChangeInfo.getAbsoluteX(), multiBlockChangeInfo.getY(), multiBlockChangeInfo.getAbsoluteZ()));
        blockDataList.add(multiBlockChangeInfo.getData());
      }
    } else {
      BlockPosition position = packet.getBlockPositionModifier().readSafely(0);
      blockPositions = Collections.singletonList(position);
      blockDataList = Collections.singletonList(packet.getBlockData().read(0));
    }

    boolean transactionSynchronize = false;
    Location location = player.getLocation();
    for (BlockPosition blockPosition : blockPositions) {
      if(distance(location, blockPosition) < 8) {
        transactionSynchronize = true;
        break;
      }
    }

    World world = player.getWorld();
    if(transactionSynchronize) {
//      player.sendMessage("Updated " + blockDataList.size() + " blocks: " + blockDataList);
      plugin.eventService().transactionFeedbackService().requestPong(player, null, (player1, target) -> {
        for (int i = 0; i < blockPositions.size(); i++) {
          BlockPosition blockPosition = blockPositions.get(i);
          WrappedBlockData blockData = blockDataList.get(i);
          int id = blockData.getType().getId();
          boundingBoxAccess.override(world, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), id, blockData.getData());
          boundingBoxAccess.invalidate(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
        }
      });
    } else {
      for (int i = 0; i < blockPositions.size(); i++) {
        BlockPosition blockPosition = blockPositions.get(i);
        WrappedBlockData blockData = blockDataList.get(i);
        boundingBoxAccess.override(world, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), blockData.getType().getId(), blockData.getData());
        boundingBoxAccess.invalidate(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
      }
    }
  }

  private double distance(Location playerLocation, BlockPosition blockPosition) {
    return Math.sqrt(NumberConversions.square(playerLocation.getBlockX() - blockPosition.getX()) + NumberConversions.square(playerLocation.getBlockY() - blockPosition.getY()) + NumberConversions.square(playerLocation.getBlockZ() - blockPosition.getZ()));
  }

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
    Block block = BukkitBlockAccess.blockAccess(location);
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

  public static abstract class ChunkDataMapper {
    public final static int NO_MODIFICATION = -1;

    public abstract int mapBlockData(int legacyId, int legacyData, int blockX, int blockY, int blockZ);

    protected final int buildResponse(int legacyBlockId, int blockData) {
      return (legacyBlockId << 4) | blockData;
    }
  }
}
