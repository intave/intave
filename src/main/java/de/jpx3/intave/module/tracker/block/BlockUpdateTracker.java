package de.jpx3.intave.module.tracker.block;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.state.BlockStateAccess;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackCallback;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.*;
import static de.jpx3.intave.module.feedback.TransactionOptions.APPEND_ON_OVERFLOW;
import static de.jpx3.intave.module.feedback.TransactionOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class BlockUpdateTracker extends Module {
  @PacketSubscription(
    engine = Engine.ASYNC_INTERNAL,
    packetsOut = {
      MAP_CHUNK, MAP_CHUNK_BULK
    }
  )
  public void chunkUpdate(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();

    ChunkCoordinateReader coordinates = PacketReaders.readerOf(packet);
    int[] xCoordinates = coordinates.xCoordinates();
    int[] zCoordinates = coordinates.zCoordinates();
    coordinates.close();

    if (xCoordinates.length != zCoordinates.length) {
      throw new IllegalStateException();
    }

    Modules.feedback().synchronize(
      player, (player1, target) -> {
        for (int k = 0; k < xCoordinates.length; k++) {
          chunkInvalidate(player, xCoordinates[k], zCoordinates[k]);
        }
      },
      APPEND_ON_OVERFLOW | SELF_SYNCHRONIZATION
    );
  }

  private void chunkInvalidate(Player player, int chunkX, int chunkZ) {
    int chunkXMinPos = chunkX << 4, chunkXMaxPos = chunkXMinPos + 16;
    int chunkZMinPos = chunkZ << 4, chunkZMaxPos = chunkZMinPos + 16;
    BlockStateAccess blockStateAccess = UserRepository.userOf(player).blockStates();
    blockStateAccess.invalidateOverridesInBounds(chunkXMinPos, chunkXMaxPos, chunkZMinPos, chunkZMaxPos);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      BLOCK_DIG, BLOCK_PLACE, USE_ITEM
    }
  )
  public void checkInteractionTarget(PacketEvent event) {
    Player player = event.getPlayer();
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();
    BlockPositionReader reader = PacketReaders.readerOf(packet);

    boolean check = true;
    if (packetType == PacketType.Play.Client.BLOCK_DIG) {
      EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().read(0);
      check = playerDigType == START_DESTROY_BLOCK || playerDigType == STOP_DESTROY_BLOCK || playerDigType == ABORT_DESTROY_BLOCK;
    } else if (packetType == PacketType.Play.Client.BLOCK_PLACE) {
      BlockPosition blockPosition = reader.blockPosition();
      if (blockPosition == null) {
        reader.close();
        return;
      }
      BlockInteractionReader placeInterpreter = (BlockInteractionReader) reader;
      if (placeInterpreter.enumDirection() == 255 || event.isCancelled()) {
        check = false;
      }
    }

    if (check) {
      BlockPosition blockPosition = reader.blockPosition();
      if (blockPosition == null) {
        reader.close();
        return;
      }
      Vector targetBlock = blockPosition.toVector();
      User user = UserRepository.userOf(player);
      MovementMetadata movementData = user.meta().movement();
      Vector playerLocation = new Vector(movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      if (playerLocation.distance(targetBlock) > 16) {
        event.setCancelled(true);
      }
    }
    reader.close();
  }

  @PacketSubscription(
    packetsOut = {
      BLOCK_BREAK, BLOCK_CHANGE, MULTI_BLOCK_CHANGE
    }
  )
  public void sentBlockUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    BlockChanges changes = PacketReaders.readerOf(packet);
    List<BlockPosition> blockPositions = changes.blockPositions();
    List<WrappedBlockData> blockDataList = changes.blockDataList();
    changes.close();

    World world = player.getWorld();
    FeedbackCallback<Object> process = (player1, target) -> {
      BlockStateAccess blockStateAccess = UserRepository.userOf(player1).blockStates();
      for (int i = 0; i < blockPositions.size(); i++) {
        BlockPosition blockPosition = blockPositions.get(i);
        WrappedBlockData blockData = blockDataList.get(i);
        Material material = blockData.getType();
        int variant = BlockVariantAccess.variantAccess(blockData);
        blockStateAccess.override(world, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ(), material, variant);
        blockStateAccess.invalidateCacheAt(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
      }
    };

    Location location = player.getLocation();
    boolean transactionSynchronize = inDistance(blockPositions, location, 8);
    if (transactionSynchronize) {
      Modules.feedback().synchronize(player, null, process, APPEND_ON_OVERFLOW);
    } else {
      process.success(player, null);
    }
  }

  private static boolean inDistance(Collection<BlockPosition> blockPositions, Location playerLocation, int requiredDistance) {
    for (BlockPosition blockPosition : blockPositions) {
      if (distance(playerLocation, blockPosition) < requiredDistance) {
        return true;
      }
    }
    return false;
  }

  private static double distance(Location playerLocation, BlockPosition blockPosition) {
    return Math.sqrt(
      NumberConversions.square(playerLocation.getBlockX() - blockPosition.getX()) +
      NumberConversions.square(playerLocation.getBlockY() - blockPosition.getY()) +
      NumberConversions.square(playerLocation.getBlockZ() - blockPosition.getZ())
    );
  }
}
