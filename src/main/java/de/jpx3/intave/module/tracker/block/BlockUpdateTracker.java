package de.jpx3.intave.module.tracker.block;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.PendingCountingFeedbackObserver;
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
import org.bukkit.event.Cancellable;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.*;
import static de.jpx3.intave.module.feedback.FeedbackOptions.APPEND_ON_OVERFLOW;
import static de.jpx3.intave.module.feedback.FeedbackOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class BlockUpdateTracker extends Module {
  @PacketSubscription(
    engine = Engine.ASYNC_INTERNAL,
    packetsOut = {
      MAP_CHUNK, MAP_CHUNK_BULK
    }
  )
  public void chunkUpdate(
    User user, Player player, ChunkCoordinateReader coordinates
  ) {
    int[] xCoordinates = coordinates.xCoordinates();
    int[] zCoordinates = coordinates.zCoordinates();
    if (xCoordinates.length != zCoordinates.length) {
      throw new IllegalStateException();
    }
    user.tickFeedback(
      () -> {
        for (int k = 0; k < xCoordinates.length; k++) {
          BlockUpdateTracker.this.chunkInvalidate(player, xCoordinates[k], zCoordinates[k]);
        }
      },
      APPEND_ON_OVERFLOW | SELF_SYNCHRONIZATION
    );
  }

  private void chunkInvalidate(Player player, int chunkX, int chunkZ) {
    int chunkXMinPos = chunkX << 4, chunkXMaxPos = chunkXMinPos + 16;
    int chunkZMinPos = chunkZ << 4, chunkZMaxPos = chunkZMinPos + 16;
    ExtendedBlockStateCache blockStateAccess = UserRepository.userOf(player).blockStates();
    blockStateAccess.invalidateOverridesInBounds(chunkXMinPos, chunkXMaxPos, chunkZMinPos, chunkZMaxPos);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      BLOCK_DIG, BLOCK_PLACE, USE_ITEM
    }
  )
  public void checkInteractionTarget(
    User user, PacketContainer packet,
    BlockPositionReader reader, Cancellable cancellable
  ) {
    PacketType packetType = packet.getType();
    boolean check = true;

    if (packetType == PacketType.Play.Client.BLOCK_DIG) {
      EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().read(0);
      check = playerDigType == START_DESTROY_BLOCK || playerDigType == STOP_DESTROY_BLOCK || playerDigType == ABORT_DESTROY_BLOCK;
    } else if (packetType == PacketType.Play.Client.BLOCK_PLACE) {
      BlockPosition blockPosition = reader.blockPosition();
      if (blockPosition == null) {
        return;
      }
      BlockInteractionReader placeInterpreter = (BlockInteractionReader) reader;
      if (placeInterpreter.enumDirection() == 255 || cancellable.isCancelled()) {
        check = false;
      }
    }

    if (check) {
      MovementMetadata movementData = user.meta().movement();
      BlockPosition blockPosition = reader.blockPosition();
      if (blockPosition == null) {
        return;
      }
      Vector targetBlock = blockPosition.toVector();
      Vector playerLocation = new Vector(movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      if (playerLocation.distance(targetBlock) > 16) {
        cancellable.setCancelled(true);
      }
    }
  }

  @PacketSubscription(
    packetsOut = {
      BLOCK_BREAK, BLOCK_CHANGE, MULTI_BLOCK_CHANGE
    }
  )
  public void sentBlockUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PendingCountingFeedbackObserver pendingBlockUpdates = user.meta().connection().pendingBlockUpdates;

    PacketContainer packet = event.getPacket();

    BlockChanges changes = PacketReaders.readerOf(packet);
    List<BlockPosition> blockPositions = changes.blockPositions();
    List<WrappedBlockData> blockDataList = changes.blockDataList();
    changes.release();

    World world = player.getWorld();
    EmptyFeedbackCallback process = () -> {
      ExtendedBlockStateCache blockStateAccess = user.blockStates();
      Location verifiedLocation = user.meta().movement().verifiedLocation();
      for (int i = 0; i < blockPositions.size(); i++) {
        BlockPosition blockPosition = blockPositions.get(i);
        WrappedBlockData blockData = blockDataList.get(i);
        if (distance(verifiedLocation, blockPosition) < 2) {
          user.meta().movement().pastNearbyCollisionInaccuracy = 0;
        }
//        player.sendMessage("");
        Material material = blockData.getType();
        int variant = BlockVariantNativeAccess.variantAccess(blockData);
        int positionX = blockPosition.getX();
        int positionY = blockPosition.getY();
        int positionZ = blockPosition.getZ();

        blockStateAccess.unlockOverride(positionX, positionY, positionZ);
        blockStateAccess.override(world, positionX, positionY, positionZ, material, variant, "UPDATE");
        blockStateAccess.invalidateCacheAround(positionX, positionY, positionZ);
      }
    };

    Location location = player.getLocation();
    boolean transactionSynchronize = inDistance(blockPositions, location, 8);
    if (transactionSynchronize) {
      user.tracedTickFeedback(process, pendingBlockUpdates);
    } else {
      process.success();
    }
  }

  private static boolean inDistance(Collection<? extends BlockPosition> blockPositions, Location playerLocation, int requiredDistance) {
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
