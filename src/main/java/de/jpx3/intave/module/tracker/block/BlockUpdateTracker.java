/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.tracker.block;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.PendingCountingFeedbackObserver;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.*;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.share.PlayerDigAction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;

import static de.jpx3.intave.share.PlayerDigAction.*;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.NEARBY_COLLISION_INACCURACY;
import static de.jpx3.intave.module.feedback.FeedbackOptions.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class BlockUpdateTracker extends Module {
  @PacketSubscription(
//    engine = Engine.INTERNAL,
    packetsOut = {
      MAP_CHUNK, MAP_CHUNK_BULK
    },
    ignoreCancelled = false
  )
  public void chunkUpdate(
    User user, Player player, ChunkCoordinateReader coordinates
  ) {
    int[] xCoordinates = coordinates.xCoordinates();
    int[] zCoordinates = coordinates.zCoordinates();
    if (xCoordinates.length != zCoordinates.length) {
      throw new IllegalStateException();
    }
    if (xCoordinates.length == 0) {
      return;
    }
    ConnectionMetadata connection = user.meta().connection();
    Runnable[] confirmations = new Runnable[xCoordinates.length];
    for (int k = 0; k < xCoordinates.length; k++) {
      confirmations[k] = connection.pendingClientChunkLoad(xCoordinates[k], zCoordinates[k]);
    }
    if (xCoordinates.length > 1) {
      user.tickFeedback(
        () -> {
          for (int k = 0; k < xCoordinates.length; k++) {
            confirmations[k].run();
            BlockUpdateTracker.this.chunkInvalidate(player, xCoordinates[k], zCoordinates[k]);
          }
        },
        APPEND_ON_OVERFLOW | SELF_SYNCHRONIZATION
      );
    } else {
      int chunkX = xCoordinates[0], chunkZ = zCoordinates[0];
      Position position = user.meta().movement().position();
      int playerChunkX = position.chunkX(), playerChunkZ = position.chunkZ();
      double distance = Math.sqrt(
        NumberConversions.square(playerChunkX - chunkX) +
          NumberConversions.square(playerChunkZ - chunkZ)
      );
      boolean relevant = distance <= 4 || user.blockCache().hasOverridesInBounds(chunkX << 4, (chunkX + 1) << 4, chunkZ << 4, (chunkZ + 1) << 4);
      user.tickFeedback(
        () -> {
          confirmations[0].run();
          BlockUpdateTracker.this.chunkInvalidate(player, chunkX, chunkZ);
        },
        (relevant ? APPEND_ON_OVERFLOW : APPEND) | SELF_SYNCHRONIZATION
      );
    }
  }

  @PacketSubscription(
    engine = Engine.INTERNAL,
    packetsOut = {
      UNLOAD_CHUNK
    },
    ignoreCancelled = false
  )
  public void chunkUnload(User user, PacketContainer packet) {
    ChunkCoordIntPair coordinates = packet.getChunkCoordIntPairs().readSafely(0);
    if (coordinates != null) {
      user.meta().connection().unloadClientChunk(coordinates.getChunkX(), coordinates.getChunkZ());
      return;
    }
    Integer chunkX = packet.getIntegers().readSafely(0);
    Integer chunkZ = packet.getIntegers().readSafely(1);
    if (chunkX != null && chunkZ != null) {
      user.meta().connection().unloadClientChunk(chunkX, chunkZ);
    }
  }

  @BukkitEventSubscription
  public void worldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    if (UserRepository.hasUser(player)) {
      UserRepository.userOf(player).meta().connection().clearClientChunks();
    }
  }

  private void chunkInvalidate(Player player, int chunkX, int chunkZ) {
    int chunkXMinPos = chunkX << 4, chunkXMaxPos = chunkXMinPos + 16;
    int chunkZMinPos = chunkZ << 4, chunkZMaxPos = chunkZMinPos + 16;
    BlockCache blockStateAccess = UserRepository.userOf(player).blockCache();
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
      PlayerDigAction playerDigType = ((BlockDigReader) reader).action();
      if (playerDigType == null) return;
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
      SimulationEnvironment movementData = user.meta().movement();
      BlockPosition blockPosition = reader.blockPosition();
      if (blockPosition == null) {
        return;
      }
      Vector targetBlock = blockPosition.toVector();
      Vector playerLocation = new Vector(movementData.lastPositionX(), movementData.lastPositionY(), movementData.lastPositionZ());
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
    boolean speculativeBlocks = user.meta().protocol().clientSpeculativeBlocks();
    PendingCountingFeedbackObserver pendingBlockUpdates = user.meta().connection().pendingBlockUpdates;

    PacketContainer packet = event.getPacket();

    BlockChanges changes = PacketReaders.readerOf(packet);
    List<BlockPosition> blockPositions = changes.blockPositions();
    List<WrappedBlockData> blockDataList = changes.blockDataList();
    changes.release();

    World world = player.getWorld();
    EmptyFeedbackCallback process = () -> {
      BlockCache blockCache = user.blockCache();
      Position verifiedPosition = user.meta().movement().verifiedPosition();
      for (int i = 0; i < blockPositions.size(); i++) {
        BlockPosition blockPosition = blockPositions.get(i);
        WrappedBlockData blockData = blockDataList.get(i);
        if (distance(verifiedPosition, blockPosition) < 2) {
          user.meta().movement().activeTick(NEARBY_COLLISION_INACCURACY);
        }
        Material material = blockData.getType();
        int variant = BlockVariantNativeAccess.variantAccess(blockData);
        int positionX = blockPosition.getX();
        int positionY = blockPosition.getY();
        int positionZ = blockPosition.getZ();
        if (!speculativeBlocks || !blockCache.updateClientSpeculationValue(world, positionX, positionY, positionZ, material, variant)) {
          blockCache.unlockOverride(positionX, positionY, positionZ);
          blockCache.override(world, positionX, positionY, positionZ, material, variant, "UPDATE");
          blockCache.invalidateCacheAround(positionX, positionY, positionZ);
        }
      }
    };

    Location location = player.getLocation();
    boolean transactionSynchronize = inDistance(blockPositions, location, 8);
    if (transactionSynchronize) {
      user.tracedPacketTickFeedback(event, process, pendingBlockUpdates);
    } else {
      process.success();
    }
  }

  @PacketSubscription(
    packetsOut = {
      BLOCK_CHANGED_ACK
    }
  )
  public void blockChangedAck(User user, PacketEvent event, BlockChangedAckReader reader) {
    int sequenceNumber = reader.sequenceNumber();
    debugBlockAck(user, "BLOCK_ACK_SENT", sequenceNumber);
    user.packetTickFeedback(event, () -> {
      debugBlockAck(user, "BLOCK_ACK_CONFIRMED", sequenceNumber);
      user.blockCache().moveClientSpeculationsToOverride(user.player().getWorld(), sequenceNumber);
    });
  }

  private static void debugBlockAck(User user, String phase, int sequenceNumber) {
    if (IntaveControl.BLOCK_CACHE_DEBUG || user.receives(MessageChannel.DEBUG_BLOCK_CACHE)) {
      user.sendMessage(phase + " sequence=" + sequenceNumber);
    }
  }

  private static boolean inDistance(Collection<? extends BlockPosition> blockPositions, Location playerLocation, int requiredDistance) {
    Position playerPosition = Position.of(playerLocation);
    for (BlockPosition blockPosition : blockPositions) {
      if (distance(playerPosition, blockPosition) < requiredDistance) {
        return true;
      }
    }
    return false;
  }

  private static double distance(Position playerLocation, BlockPosition blockPosition) {
    return Math.sqrt(
      NumberConversions.square(playerLocation.getBlockX() - blockPosition.getX()) +
        NumberConversions.square(playerLocation.getBlockY() - blockPosition.getY()) +
        NumberConversions.square(playerLocation.getBlockZ() - blockPosition.getZ())
    );
  }
}
