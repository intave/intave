package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
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
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.BlockAccessor;
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
    priority = ListenerPriority.NORMAL,
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

    if(event.isCancelled()) {
      return;
    }

    World world = player.getWorld();
    Location blockAgainstLocation = blockPosition.toLocation(world).clone();
    Location blockPlacementLocation = blockAgainstLocation.clone().add(WrappedEnumDirection.getFront(enumDirection).getDirectionVec().convertToBukkitVec());

    // TODO: 01/10/21 replace with own resolve (getItemInHand is not synchronized !!!)
    Material itemTypeInHand = player.getItemInHand().getType();

    Block clickedBlock = BlockAccessor.blockAccess(blockAgainstLocation);
    Material clickedType = clickedBlock.getType();
    boolean clickable = clickableMaterials.contains(clickedType);
    boolean isPlacement = itemTypeInHand != Material.AIR && itemTypeInHand.isBlock() && !clickable;

    if(isPlacement) {
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

//      player.sendMessage("Block-placement confirmation for " + MathHelper.formatPosition(blockPlacementLocation));
      if(access) {
        // add to future bounding boxes
        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, blockX, blockY, blockZ, id, shape);
      }
    } else {
      // wooden doors and trapdoors
      if(clickedType == Material.WOODEN_DOOR) {
/*
        // TODO: 01/10/21 interact event

        Block blockOfInterest = clickedBlock;
        int data = blockOfInterest.getData();
        boolean upperPart = (data & 8) > 0;

        if(upperPart) {
          blockOfInterest = clickedBlock.getRelative(BlockFace.DOWN);
          data = blockOfInterest.getData();
        }

        boolean isOpen = (data & 4) > 0;

        player.sendMessage(String.valueOf(isOpen));

        int bitMask = 4;
        byte newData = (byte) (isOpen ? data & ~bitMask : data | bitMask);

        BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
        boundingBoxAccess.override(world, blockOfInterest.getX(), blockOfInterest.getY(), blockOfInterest.getZ(), blockOfInterest.getTypeId(), newData);
        if(upperPart) {
          boundingBoxAccess.override(world, blockOfInterest.getX(), blockOfInterest.getY() + 1, blockOfInterest.getZ(), blockOfInterest.getTypeId(), newData);
        }*/
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
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void receiveBreak(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if(blockPosition == null) {
      return;
    }
    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    if(!(playerDigType == STOP_DESTROY_BLOCK)) {
      return;
    }
    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    int enumDirection = direction == null ? 255 : direction.ordinal();
    if(enumDirection == 255) {
      return;
    }

    if(event.isCancelled()) {
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

//    player.sendMessage("Block-placement confirmation for " + MathHelper.formatPosition(blockBreakLocation));
    if(access) {
      int blockX = blockBreakLocation.getBlockX();
      int blockY = blockBreakLocation.getBlockY();
      int blockZ = blockBreakLocation.getBlockZ();

      // add to future bounding boxes


      BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
      boundingBoxAccess.override(world, blockX, blockY, blockZ, 0, (byte) 0);
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
}
