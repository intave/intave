package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
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
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.BoundingBoxAccess;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.*;

public final class BlockActionDispatcher implements EventProcessor {
  private final IntavePlugin plugin;

  public BlockActionDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
  }

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
      plugin.eventService().transactionFeedbackService().requestPong(
        player, null,
        (player1, target) -> {
          for (int i = 0; i < xArr.length; i++) {
            chunkInvalidate(player, xArr[i], zArr[i]);
          }
        }
      );
    } else {
      int x = packet.getIntegers().read(0);
      int z = packet.getIntegers().read(1);
      plugin.eventService().transactionFeedbackService().requestPong(
        player, null,
        (player1, target) -> chunkInvalidate(player, x, z)
      );
    }
  }

  private void chunkInvalidate(Player player, int chunkX, int chunkZ) {
    int chunkXMinPos = chunkX << 4, chunkXMaxPos = chunkXMinPos + 16;
    int chunkZMinPos = chunkZ << 4, chunkZMaxPos = chunkZMinPos + 16;
    BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
    boundingBoxAccess.invalidateOverridesInBounds(chunkXMinPos, chunkXMaxPos, chunkZMinPos, chunkZMaxPos);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
    }
  )
  public void checkInteractionTarget(PacketEvent event) {
    Player player = event.getPlayer();
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();

    boolean check = true;

    if(packetType == PacketType.Play.Client.BLOCK_DIG) {
      EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().read(0);
      check = playerDigType == START_DESTROY_BLOCK || playerDigType == STOP_DESTROY_BLOCK || playerDigType == ABORT_DESTROY_BLOCK;
    } else if(packetType == PacketType.Play.Client.BLOCK_PLACE) {
      Integer enumDirection = packet.getIntegers().readSafely(0);
      if (enumDirection == null) {
        enumDirection = packet.getDirections().readSafely(0).ordinal();
      }
      if (enumDirection == 255 || event.isCancelled()) {
        check = false;
      }
    }

    if(check) {
      BlockPosition blockPosition = packet.getBlockPositionModifier().read(0);
      // distance check

      if(blockPosition == null) {
        return;
      }

      Vector targetBlock = blockPosition.toVector();

      User user = UserRepository.userOf(player);
      UserMetaMovementData movementData = user.meta().movementData();
      Vector playerLocation = new Vector(movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);

      if(playerLocation.distance(targetBlock) > 16) {
        event.setCancelled(true);
      }
    }
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
      plugin.eventService().transactionFeedbackService().requestPong(player, null, (player1, target) -> {
        for (int i = 0; i < blockPositions.size(); i++) {
          BlockPosition blockPosition = blockPositions.get(i);
          WrappedBlockData blockData = blockDataList.get(i);
          Material material = blockData.getType();
          boundingBoxAccess.override(world, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), material, blockData.getData());
          boundingBoxAccess.invalidate(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
        }
      });
    } else {
      for (int i = 0; i < blockPositions.size(); i++) {
        BlockPosition blockPosition = blockPositions.get(i);
        WrappedBlockData blockData = blockDataList.get(i);
        boundingBoxAccess.override(world, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), blockData.getType(), blockData.getData());
        boundingBoxAccess.invalidate(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
      }
    }
  }

  private double distance(Location playerLocation, BlockPosition blockPosition) {
    return Math.sqrt(NumberConversions.square(playerLocation.getBlockX() - blockPosition.getX()) + NumberConversions.square(playerLocation.getBlockY() - blockPosition.getY()) + NumberConversions.square(playerLocation.getBlockZ() - blockPosition.getZ()));
  }
}
