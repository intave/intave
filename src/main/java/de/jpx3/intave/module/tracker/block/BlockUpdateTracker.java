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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
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
import de.jpx3.intave.packet.view.BlockPositionView;
import de.jpx3.intave.packet.view.ChunkUnloadView;
import de.jpx3.intave.packet.view.FeedbackHandle;
import de.jpx3.intave.packet.view.PacketEventsBlockPositionView;
import de.jpx3.intave.packet.view.PacketEventsChunkUnloadView;
import de.jpx3.intave.packet.view.PacketEventsFeedbackHandle;
import de.jpx3.intave.packet.view.ProtocolLibBlockPositionView;
import de.jpx3.intave.packet.view.ProtocolLibChunkUnloadView;
import de.jpx3.intave.packet.view.ProtocolLibFeedbackHandle;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
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
    User user, Player player, PacketContainer packet, ChunkCoordinateReader coordinates
  ) {
    handleChunkUpdate(user, player, new ProtocolLibBlockPositionView(player, packet, coordinates));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      MAP_CHUNK, MAP_CHUNK_BULK
    },
    ignoreCancelled = false
  )
  public void chunkUpdate(PacketSendEvent event) {
    PacketEventsBlockPositionView view = PacketEventsBlockPositionView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleChunkUpdate(UserRepository.userOf(player), player, view);
  }

  /** Engine independent chunk load tracking; see {@link BlockPositionView}. */
  private void handleChunkUpdate(User user, Player player, BlockPositionView view) {
    int[] xCoordinates = view.chunkXCoordinates();
    int[] zCoordinates = view.chunkZCoordinates();
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
    ProtocolLibChunkUnloadView view = ProtocolLibChunkUnloadView.of(packet);
    if (view == null) {
      return;
    }
    handleChunkUnload(user, view);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      UNLOAD_CHUNK
    },
    ignoreCancelled = false
  )
  public void chunkUnload(PacketSendEvent event) {
    PacketEventsChunkUnloadView view = PacketEventsChunkUnloadView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleChunkUnload(UserRepository.userOf(player), view);
  }

  /** Engine independent chunk unload tracking; see {@link ChunkUnloadView}. */
  private void handleChunkUnload(User user, ChunkUnloadView view) {
    user.meta().connection().unloadClientChunk(view.chunkX(), view.chunkZ());
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
    handleInteractionTarget(user, new ProtocolLibBlockPositionView(user.player(), packet, reader, cancellable));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      BLOCK_DIG, BLOCK_PLACE, USE_ITEM
    }
  )
  public void checkInteractionTarget(PacketReceiveEvent event) {
    PacketEventsBlockPositionView view = PacketEventsBlockPositionView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleInteractionTarget(UserRepository.userOf(player), view);
  }

  /** Engine independent interaction target check; see {@link BlockPositionView}. */
  private void handleInteractionTarget(User user, BlockPositionView view) {
    BlockPositionView.Kind kind = view.kind();
    boolean check = true;

    if (kind == BlockPositionView.Kind.BLOCK_DIG) {
      BlockPositionView.DigAction digAction = view.digAction();
      check = digAction == BlockPositionView.DigAction.START_DESTROY_BLOCK
        || digAction == BlockPositionView.DigAction.STOP_DESTROY_BLOCK
        || digAction == BlockPositionView.DigAction.ABORT_DESTROY_BLOCK;
    } else if (kind == BlockPositionView.Kind.BLOCK_PLACE) {
      de.jpx3.intave.share.BlockPosition blockPosition = view.blockPosition();
      if (blockPosition == null) {
        return;
      }
      if (view.enumDirection() == 255 || view.cancelled()) {
        check = false;
      }
    }

    if (check) {
      SimulationEnvironment movementData = user.meta().movement();
      de.jpx3.intave.share.BlockPosition blockPosition = view.blockPosition();
      if (blockPosition == null) {
        return;
      }
      Vector targetBlock = blockPosition.convertToBukkitVec();
      Vector playerLocation = new Vector(movementData.lastPositionX(), movementData.lastPositionY(), movementData.lastPositionZ());
      if (playerLocation.distance(targetBlock) > 16) {
        view.setCancelled(true);
      }
    }
  }

  /**
   * ProtocolLib only, no PacketEvents twin. Re-checked against PacketEvents 2.13.0.
   * <p>
   * The feedback attachment itself would port fine (see {@link #blockChangedAck(PacketSendEvent)}),
   * but the body's payload does not: every changed block is turned into a
   * {@code (Material, variantIndex)} pair, and the variant index comes from
   * {@link BlockVariantNativeAccess#variantAccess(com.comphenix.protocol.wrappers.WrappedBlockData)},
   * which looks the state up by the <em>native</em> {@code IBlockData} object
   * {@code WrappedBlockData.getHandle()} hands out. {@code BlockVariantRegister}'s index is keyed on
   * exactly those server-side state instances by identity (see {@code ModernIndexer}, which stores
   * the elements of {@code block.getStates().a()} as map keys), so nothing but a real
   * {@code IBlockData} resolves through it. PacketEvents describes the same block as a
   * {@code WrappedBlockState}, a protocol level global palette id, and 2.13.0 still offers no route
   * from one to the other.
   * <p>
   * <b>What 2.13.0 actually provides.</b> {@code SpigotConversionUtil} converts block states only as
   * far as {@code org.bukkit.block.data.BlockData} - {@code toBukkitBlockData} is one line,
   * {@code Bukkit.createBlockData(state.toString())}, which does not exist below 1.13 and therefore
   * throws {@code NoSuchMethodError} on the 1.8 to 1.12 servers this plugin still supports. There is
   * no inverse: the only native block bridge PacketEvents exposes is
   * {@code SpigotReflectionUtil.GET_CRAFT_BLOCK_DATA_FROM_IBLOCKDATA}, which runs native to Bukkit,
   * the direction that is already covered.
   * <p>
   * <b>Why that gap cannot be papered over.</b> Every block state lookup in 2.13.0 answers a miss
   * with air rather than with a failure. {@code WrappedBlockState.getByGlobalId} and
   * {@code getByString} both end in {@code Map.getOrDefault(key, AIR)}, and
   * {@code SpigotConversionUtil.fromBukkitBlockData} is {@code getByString} over
   * {@code BlockData.getAsString}. So neither direction carries a miss signal: a state PacketEvents'
   * generated tables do not hold comes back as {@code minecraft:air}, a real and plausible block
   * that would be written into the cache as if it had been read off the wire. That also rules out
   * building a reverse table server-side and keying it on the global id, because the table itself
   * would be built through the same silently-defaulting lookup.
   * <p>
   * <b>The property-map detour is not a fallback either.</b> The two sides do not share a
   * vocabulary. {@code IndexedBlockVariant} stores enum values as the <em>NMS constant name</em>
   * ({@code ((Enum) value).name()}), while Bukkit's {@code getAsString} and PacketEvents both emit
   * the <em>serialized</em> name. {@code minecraft:piston_head} is a counterexample present on every
   * supported version: {@code BlockPropertyPistonType.DEFAULT} serializes as {@code "normal"} and
   * PacketEvents spells the same value {@code Type.NORMAL}, so the whole-property-set comparison in
   * {@code BlockVariantReverseLookup#variantIdOfProperties} cannot match and it returns -1. Its only
   * existing caller ({@code BlockInteractionPatch}) feeds it properties that came out of the
   * register itself, which is why the exact match is safe there and not here.
   * <p>
   * <b>And an unknown block is not representable at this call site.</b> Both sinks below,
   * {@code BlockCache#override} and {@code BlockCache#setClientSpeculationValue}, take a mandatory
   * {@code (Material, int)} pair. Writing -1 makes {@code BlockVariantRegister#rawVariantOf} return
   * null, and the shape drills answer a null state with {@code BlockShapes.emptyShape()} - a solid
   * block that has no collision box in the client cache, which is precisely the false positive this
   * port must not create. Skipping the entry instead is no better: it leaves the stale override in
   * place and skips the {@code unlockOverride} and {@code invalidateCacheAround} that belong with it.
   * <p>
   * Below 1.13 {@code variantAccess} returns the legacy data nibble, which
   * {@code SpigotConversionUtil.toBukkitMaterialData} does reach, but the modern half is the one
   * that blocks and half a twin is worse than none. This becomes portable when a verified
   * server-side state to variant mapping with a real miss signal exists, not before.
   */
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
      Location verifiedLocation = user.meta().movement().verifiedLocation();
      for (int i = 0; i < blockPositions.size(); i++) {
        BlockPosition blockPosition = blockPositions.get(i);
        WrappedBlockData blockData = blockDataList.get(i);
        if (distance(verifiedLocation, blockPosition) < 2) {
          user.meta().movement().activeTick(NEARBY_COLLISION_INACCURACY);
        }
        Material material = blockData.getType();
        int variant = BlockVariantNativeAccess.variantAccess(blockData);
        int positionX = blockPosition.getX();
        int positionY = blockPosition.getY();
        int positionZ = blockPosition.getZ();
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
  public void blockChangedAck(PacketEvent event) {
    Player player = event.getPlayer();
    handleBlockChangedAck(
      UserRepository.userOf(player),
      player,
      event.getPacket().getIntegers().read(0),
      ProtocolLibFeedbackHandle.of(event)
    );
  }

  /**
   * PacketEvents twin of the subscription above.
   * <p>
   * The packet carries exactly one field, the block change sequence number the client has to
   * acknowledge, which {@code getIntegers().read(0)} reads on the ProtocolLib side and
   * {@link WrapperPlayServerAcknowledgeBlockChanges#getSequence()} reads here; no engine neutral
   * view is warranted for a single scalar. The feedback request is routed through a
   * {@link PacketEventsFeedbackHandle}, which reports no ProtocolLib bundling target and therefore
   * makes the feedback module take the same unbundled branch it already takes on every server
   * below 1.19.4 - the transaction is sent on its own and this packet stays untouched.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      BLOCK_CHANGED_ACK
    }
  )
  public void blockChangedAck(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.ACKNOWLEDGE_BLOCK_CHANGES) {
      return;
    }
    Object rawPlayer = event.getPlayer();
    if (!(rawPlayer instanceof Player)) {
      return;
    }
    Player player = (Player) rawPlayer;
    handleBlockChangedAck(
      UserRepository.userOf(player),
      player,
      new WrapperPlayServerAcknowledgeBlockChanges(event).getSequence(),
      PacketEventsFeedbackHandle.of(event)
    );
  }

  /**
   * Engine independent block change acknowledgement handling shared by both entry points above.
   * The only packet field involved is the sequence number, which the callers read on their own
   * engine and pass in.
   */
  private void handleBlockChangedAck(
    User user, Player player, int sequenceNumber, FeedbackHandle handle
  ) {
    user.packetTickFeedback(handle, () ->
      user.blockCache().moveClientSpeculationsToOverride(player.getWorld(), sequenceNumber)
    );
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
