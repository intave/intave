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
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.reflect.Reflection;
import de.jpx3.intave.reflect.ReflectionFailureException;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedEnumDirection;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.block.BlockDataAccess;
import de.jpx3.intave.world.collision.BoundingBoxAccess;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;

public final class BlockActionDispatcher implements EventProcessor {
  private final IntavePlugin plugin;

  public final List<Material> clickableMaterials = new ArrayList<>();
  public final List<Material> replaceableMaterials = new ArrayList<>();

  // TODO: 01/09/21 prevent invalid chunk access

  public BlockActionDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
    this.loadClickableMaterials();
  }

  public void loadClickableMaterials() {
    Class<?> blockClass = Reflection.lookupServerClass("Block");
    Method getByIdMethod;
    try {
      getByIdMethod = blockClass.getMethod("getById", Integer.TYPE);
    } catch (NoSuchMethodException exception) {
      throw new ReflectionFailureException(exception);
    }

    Class<?> world = Reflection.lookupServerClass("World");
    Class<?> blockPosition = Reflection.lookupServerClass("BlockPosition");
    Class<?> iBlockData = Reflection.lookupServerClass("IBlockData");
    Class<?> entityHuman = Reflection.lookupServerClass("EntityHuman");
    Class<?> enumDirection = Reflection.lookupServerClass("EnumDirection");
    Class<Float> floatClass = Float.TYPE;

    // TODO: 01/10/21 check version availability


/*
    for (IBlockData x : net.minecraft.server.v1_8_R3.Block.d) {
      int integer = 0;
      for (int i = 0; i < 4096; i++) {
        if(net.minecraft.server.v1_8_R3.Block.d.a(i) == x) {
          integer = i;
        }
      }

      net.minecraft.server.v1_8_R3.Block block = x.getBlock();
      if(block instanceof BlockTrapdoor) {
        System.out.println(net.minecraft.server.v1_8_R3.Block.getId(block) + " " + x + " " + Integer.toBinaryString(integer));

      }
    }
*/

    try {
      for (int i = 0; i < 64000; i++) {
        Material material = Material.getMaterial(i);
        if(material == null) {
          continue;
        }
        Object block = getByIdMethod.invoke(null, i);
        if(block == null) {
          continue;
        }
        if(block.getClass().getMethod("interact", world, blockPosition, iBlockData, entityHuman, enumDirection, floatClass, floatClass, floatClass).getDeclaringClass() != blockClass) {
          clickableMaterials.add(material);
        }
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
    }
  )
  public void receiveInteraction(PacketEvent event) {
    Player player = event.getPlayer();
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

    if(BlockAccessor.blockAccess(blockAgainstLocation).getType() == Material.AIR) {
      event.setCancelled(true);
      refreshBlocksAround(player, blockAgainstLocation);
      return;
    }

    boolean replace = BlockDataAccess.replacementPlace(world, new BlockPosition(blockAgainstLocation.toVector()));
    Location blockPlacementLocation = replace ? blockAgainstLocation : blockAgainstLocation.clone().add(WrappedEnumDirection.getFront(enumDirection).getDirectionVec().convertToBukkitVec());

    User user = UserRepository.userOf(player);

    Material itemTypeInHand = user.meta().inventoryData().heldItemType();

    Block clickedBlock = BlockAccessor.blockAccess(blockAgainstLocation);
    Material clickedType = clickedBlock.getType();
    boolean clickable = clickableMaterials.contains(clickedType);
    boolean isPlacement = itemTypeInHand != Material.AIR && itemTypeInHand.isBlock() && !clickable;

    if(isPlacement) {
//      Bukkit.broadcastMessage(blockAgainstLocation + " " + WrappedEnumDirection.getFront(enumDirection));

      int blockX = blockPlacementLocation.getBlockX();
      int blockY = blockPlacementLocation.getBlockY();
      int blockZ = blockPlacementLocation.getBlockZ();

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
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, blockX, blockY, blockZ, id, shape);

        Synchronizer.synchronizeDelayed(() -> {
          boundingBoxAccess.invalidateOverride(world, blockX, blockY, blockZ);
        }, 1);
      } else {
        refreshBlocksAround(player, blockPlacementLocation);
        event.setCancelled(true);
      }
    } else {
      if(clickedType == Material.WOODEN_DOOR) {


      } else if(clickedType == Material.TRAP_DOOR) {
        int data = clickedBlock.getData();
        boolean isOpen = (data & 4) > 0;
        int bitMask = 4;
        byte newData = (byte) (!isOpen ? (data | bitMask) : (data & ~bitMask));

        int id = clickedBlock.getType().getId();
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ(), id, newData);

        Synchronizer.synchronize(() -> {
          boundingBoxAccess.invalidateOverride(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());
        });
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

//      player.sendMessage("Block-placement confirmation for " + MathHelper.formatPosition(blockBreakLocation));
//      Synchronizer.synchronize(() -> {
//        Raytracer.ignoreBlock(player, blockBreakLocation);
//      });

      // add to future bounding boxes
      BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
      boundingBoxAccess.override(world, blockX, blockY, blockZ, 0, (byte) 0);
    } else {
      refreshBlocksAround(player, blockBreakLocation);
      event.setCancelled(true);
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
    player.updateInventory();
    refreshBlock(player, targetLocation);
    for (WrappedEnumDirection direction : WrappedEnumDirection.values()) {
      Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
      refreshBlock(player, placedBlock);
    }
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
