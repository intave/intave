package de.jpx3.intave.module.tracker.block;

import com.github.retrooper.packetevents.event.CancellableEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgePlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkDataBulk;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.feedback.EmptyFeedbackCallback;
import de.jpx3.intave.module.feedback.PendingCountingFeedbackObserver;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.util.PacketEventsConversions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static de.jpx3.intave.module.feedback.FeedbackOptions.*;
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
    User user, Player player, ProtocolPacketEvent event
  ) {
    int[][] coordinates = chunkCoordinates((PacketSendEvent) event);
    int[] xCoordinates = coordinates[0];
    int[] zCoordinates = coordinates[1];
    if (xCoordinates.length != zCoordinates.length) {
      throw new IllegalStateException();
    }
    if (xCoordinates.length > 1) {
      user.tickFeedback(
        () -> {
          for (int k = 0; k < xCoordinates.length; k++) {
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
        () -> BlockUpdateTracker.this.chunkInvalidate(player, chunkX, chunkZ),
        (relevant ? APPEND_ON_OVERFLOW : APPEND) | SELF_SYNCHRONIZATION
      );
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
    User user, ProtocolPacketEvent event, CancellableEvent cancellableEvent
  ) {
    PacketTypeCommon packetType = event.getPacketType();
    boolean check = true;
    BlockPosition blockPosition = null;

    if (packetType == PacketType.Play.Client.PLAYER_DIGGING) {
      WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging((com.github.retrooper.packetevents.event.PacketReceiveEvent) event);
      DiggingAction playerDigType = packet.getAction();
      check = playerDigType == DiggingAction.START_DIGGING || playerDigType == DiggingAction.FINISHED_DIGGING || playerDigType == DiggingAction.CANCELLED_DIGGING;
      blockPosition = PacketEventsConversions.toBlockPosition(packet.getBlockPosition());
    } else if (packetType == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
      WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement((com.github.retrooper.packetevents.event.PacketReceiveEvent) event);
      blockPosition = PacketEventsConversions.toBlockPosition(packet.getBlockPosition());
      if (blockPosition == null) {
        return;
      }
      if (packet.getFace() == BlockFace.OTHER || cancellableEvent.isCancelled()) {
        check = false;
      }
    }

    if (check) {
      MovementMetadata movementData = user.meta().movement();
      if (blockPosition == null) {
        return;
      }
      Vector targetBlock = new Vector(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
      Vector playerLocation = new Vector(movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      if (playerLocation.distance(targetBlock) > 16) {
        cancellableEvent.setCancelled(true);
      }
    }
  }

  @PacketSubscription(
    packetsOut = {
      BLOCK_BREAK, BLOCK_CHANGE, MULTI_BLOCK_CHANGE
    }
  )
  public void sentBlockUpdate(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    boolean speculativeBlocks = user.meta().protocol().clientSpeculativeBlocks();
    PendingCountingFeedbackObserver pendingBlockUpdates = user.meta().connection().pendingBlockUpdates;

    List<BlockChangeData> blockChanges = blockChanges((PacketSendEvent) event, player);
    if (blockChanges.isEmpty()) {
      return;
    }

    World world = player.getWorld();
    EmptyFeedbackCallback process = () -> {
      BlockCache blockCache = user.blockCache();
      Location verifiedLocation = user.meta().movement().verifiedLocation();
      for (BlockChangeData blockChange : blockChanges) {
        if (distance(verifiedLocation, blockChange.position) < 2) {
          user.meta().movement().pastNearbyCollisionInaccuracy = 0;
        }
        Material material = blockChange.material;
        int variant = blockChange.variant;
        int positionX = blockChange.position.getX();
        int positionY = blockChange.position.getY();
        int positionZ = blockChange.position.getZ();
        if (speculativeBlocks && blockCache.isClientSpeculatingAt(positionX, positionY, positionZ)) {
          blockCache.setClientSpeculationValue(world, positionX, positionY, positionZ, material, variant, user.meta().inventory().lastBlockSequenceNumber);
        } else {
          blockCache.unlockOverride(positionX, positionY, positionZ);
          blockCache.override(world, positionX, positionY, positionZ, material, variant, "UPDATE");
          blockCache.invalidateCacheAround(positionX, positionY, positionZ);
        }
      }
    };

    Location location = player.getLocation();
    boolean transactionSynchronize = inDistance(blockChanges, location, 8);
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
  public void blockChangedAck(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    int sequenceNumber = new WrapperPlayServerAcknowledgeBlockChanges((PacketSendEvent) event).getSequence();
    user.packetTickFeedback(event, () ->
      user.blockCache().moveClientSpeculationsToOverride(player.getWorld(), sequenceNumber)
    );
  }

  private int[][] chunkCoordinates(PacketSendEvent event) {
    if (event.getPacketType() == PacketType.Play.Server.MAP_CHUNK_BULK) {
      WrapperPlayServerChunkDataBulk packet = new WrapperPlayServerChunkDataBulk(event);
      return new int[][]{packet.getX(), packet.getZ()};
    }
    WrapperPlayServerChunkData packet = new WrapperPlayServerChunkData(event);
    return new int[][]{new int[]{packet.getColumn().getX()}, new int[]{packet.getColumn().getZ()}};
  }

  private List<BlockChangeData> blockChanges(PacketSendEvent event, Player player) {
    List<BlockChangeData> changes = new ArrayList<>();
    PacketTypeCommon packetType = event.getPacketType();
    if (packetType == PacketType.Play.Server.BLOCK_CHANGE) {
      WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
      changes.add(blockChange(player, PacketEventsConversions.toBlockPosition(packet.getBlockPosition()), packet.getBlockState()));
    } else if (packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
      WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
      for (WrapperPlayServerMultiBlockChange.EncodedBlock block : packet.getBlocks()) {
        BlockPosition position = new BlockPosition(block.getX(), block.getY(), block.getZ());
        changes.add(blockChange(player, position, block.getBlockState(playerVersion(player))));
      }
    } else if (packetType == PacketType.Play.Server.ACKNOWLEDGE_PLAYER_DIGGING) {
      WrapperPlayServerAcknowledgePlayerDigging packet = new WrapperPlayServerAcknowledgePlayerDigging(event);
      changes.add(blockChange(player, PacketEventsConversions.toBlockPosition(packet.getBlockPosition()), WrappedBlockState.getByGlobalId(packet.getClientVersion(), packet.getBlockId())));
    }
    return changes;
  }

  private com.github.retrooper.packetevents.protocol.player.ClientVersion playerVersion(Player player) {
    return com.github.retrooper.packetevents.PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
  }

  private BlockChangeData blockChange(Player player, BlockPosition position, WrappedBlockState state) {
    MaterialData materialData = SpigotConversionUtil.toBukkitMaterialData(state);
    return new BlockChangeData(position, materialData.getItemType(), BlockVariantNativeAccess.variantAccess(state));
  }

  private static boolean inDistance(Collection<BlockChangeData> blockChanges, Location playerLocation, int requiredDistance) {
    for (BlockChangeData blockChange : blockChanges) {
      if (distance(playerLocation, blockChange.position) < requiredDistance) {
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

  private static final class BlockChangeData {
    private final BlockPosition position;
    private final Material material;
    private final int variant;

    private BlockChangeData(BlockPosition position, Material material, int variant) {
      this.position = position;
      this.material = material;
      this.variant = variant;
    }
  }
}
