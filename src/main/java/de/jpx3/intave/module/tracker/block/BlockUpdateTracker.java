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

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.*;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.util.NumberConversions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.NEARBY_COLLISION_INACCURACY;
import static de.jpx3.intave.module.feedback.FeedbackOptions.*;
import static de.jpx3.intave.packet.ListenerPriority.FIRST;
import static de.jpx3.intave.packet.ListenerPriority.LATER;
import static de.jpx3.intave.share.PlayerDigAction.*;

public final class BlockUpdateTracker extends Module {
  @OnSend(priority = LATER)
  public void chunkUpdate(User user, ChunkLoadPacket packet, PacketSendEvent event) {
    if (event.isCancelled() || !packet.hasChunks()) {
      return;
    }
    int[] xCoordinates = new int[packet.chunkCount()];
    int[] zCoordinates = new int[packet.chunkCount()];
    Runnable[] confirmations = new Runnable[packet.chunkCount()];
    ConnectionMetadata connection = user.meta().connection();
    for (int k = 0; k < confirmations.length; k++) {
      xCoordinates[k] = packet.chunkX(k);
      zCoordinates[k] = packet.chunkZ(k);
      confirmations[k] = connection.pendingClientChunkLoad(xCoordinates[k], zCoordinates[k]);
    }
    int options = APPEND_ON_OVERFLOW | SELF_SYNCHRONIZATION;
    if (confirmations.length == 1) {
      int chunkX = xCoordinates[0], chunkZ = zCoordinates[0];
      Position position = user.meta().movement().position();
      double distance = Math.sqrt(
        NumberConversions.square(position.chunkX() - chunkX)
          + NumberConversions.square(position.chunkZ() - chunkZ)
      );
      boolean relevant = distance <= 4 || user.blockCache().hasOverridesInBounds(
        chunkX << 4, (chunkX + 1) << 4, chunkZ << 4, (chunkZ + 1) << 4
      );
      options = (relevant ? APPEND_ON_OVERFLOW : APPEND) | SELF_SYNCHRONIZATION;
    }
    Modules.feedback().synchronize(user, event, () -> {
      for (int k = 0; k < confirmations.length; k++) {
        confirmations[k].run();
        chunkInvalidate(user, xCoordinates[k], zCoordinates[k]);
      }
    }, null, options);
  }

  @OnSend(priority = LATER)
  public void chunkUnload(User user, UnloadChunkPacket packet, Cancellable cancellable) {
    if (!cancellable.isCancelled()) {
      user.meta().connection().unloadClientChunk(packet.chunkX(0), packet.chunkZ(0));
    }
  }

  @BukkitEventSubscription
  public void worldChange(PlayerChangedWorldEvent event) {
    Player player = event.getPlayer();
    if (UserRepository.hasUser(player)) {
      UserRepository.userOf(player).meta().connection().clearClientChunks();
    }
  }

  private void chunkInvalidate(User user, int chunkX, int chunkZ) {
    user.blockCache().invalidateOverridesInBounds(
      chunkX << 4, (chunkX + 1) << 4, chunkZ << 4, (chunkZ + 1) << 4
    );
  }

  @OnReceive(priority = FIRST)
  public void checkDigTarget(User user, BlockDigPacket packet, Cancellable cancellable) {
    PlayerDigAction action = packet.action();
    if (action == START_DESTROY_BLOCK || action == STOP_DESTROY_BLOCK || action == ABORT_DESTROY_BLOCK) {
      checkInteractionTarget(user, packet.blockPosition(), cancellable);
    }
  }

  @OnReceive(priority = FIRST)
  public void checkPlaceTarget(User user, BlockInteractionPacket packet, Cancellable cancellable) {
    if (!packet.isDirectItemUseRequest() && !cancellable.isCancelled()) {
      checkInteractionTarget(user, packet.blockPosition(), cancellable);
    }
  }

  private void checkInteractionTarget(User user, @Nullable BlockPosition position, Cancellable cancellable) {
    if (position == null) {
      return;
    }
    SimulationEnvironment movement = user.meta().movement();
    double distanceSquared = NumberConversions.square(movement.lastPositionX() - position.getX())
      + NumberConversions.square(movement.lastPositionY() - position.getY())
      + NumberConversions.square(movement.lastPositionZ() - position.getZ());
    if (distanceSquared > 16 * 16) {
      cancellable.setCancelled(true);
    }
  }

  @OnSend(priority = LATER)
  public void sentBlockUpdate(User user, BlockUpdates packet, PacketSendEvent event) {
    if (event.isCancelled() || packet.blockUpdates().isEmpty()) {
      return;
    }
    // The callback must retain the values sent, even if the packet is subsequently reused.
    List<BlockUpdate> updates = new ArrayList<>(packet.blockUpdates());
    int protocolVersion = user.protocolVersion();
    boolean speculativeBlocks = user.meta().protocol().clientSpeculativeBlocks();
    World world = user.player().getWorld();
    EmptyFeedbackCallback process = () -> {
      BlockCache blockCache = user.blockCache();
      Position verifiedPosition = user.meta().movement().verifiedPosition();
      for (BlockUpdate update : updates) {
        Optional<MaterialAndVariant> resolved = update.resolveFor(protocolVersion);
        if (!resolved.isPresent()) {
          continue;
        }
        BlockPosition position = update.position();
        if (distance(verifiedPosition, position) < 2) {
          user.meta().movement().activeTick(NEARBY_COLLISION_INACCURACY);
        }
        Material material = resolved.get().material();
        int variant = resolved.get().variantId();
        int x = position.getX(), y = position.getY(), z = position.getZ();
        if (!speculativeBlocks || !blockCache.updateClientSpeculationValue(world, x, y, z, material, variant)) {
          blockCache.unlockOverride(x, y, z);
          blockCache.override(world, x, y, z, material, variant, "UPDATE");
          blockCache.invalidateCacheAround(x, y, z);
        }
      }
    };
    Position playerPosition = Position.of(user.player().getLocation());
    boolean nearby = updates.stream().anyMatch(update -> distance(playerPosition, update.position()) < 8);
    if (nearby) {
      Modules.feedback().synchronize(user, event, process, user.meta().connection().pendingBlockUpdates, 0);
    } else {
      process.success();
    }
  }

  @OnSend(priority = LATER)
  public void blockChangedAck(User user, BlockChangedAckPacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    int sequenceNumber = packet.sequenceNumber().getAsInt();
    debugBlockAck(user, "BLOCK_ACK_SENT", sequenceNumber);
    Modules.feedback().synchronize(user, event, () -> {
      debugBlockAck(user, "BLOCK_ACK_CONFIRMED", sequenceNumber);
      user.blockCache().moveClientSpeculationsToOverride(user.player().getWorld(), sequenceNumber);
    });
  }

  private static void debugBlockAck(User user, String phase, int sequenceNumber) {
    if (IntaveControl.BLOCK_CACHE_DEBUG || user.receives(MessageChannel.DEBUG_BLOCK_CACHE)) {
      user.sendMessage(phase + " sequence=" + sequenceNumber);
    }
  }

  private static double distance(Position playerLocation, BlockPosition blockPosition) {
    return Math.sqrt(
      NumberConversions.square(playerLocation.getBlockX() - blockPosition.getX())
        + NumberConversions.square(playerLocation.getBlockY() - blockPosition.getY())
        + NumberConversions.square(playerLocation.getBlockZ() - blockPosition.getZ())
    );
  }
}
