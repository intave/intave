package de.jpx3.intave.check.world.interaction;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.BucketAction;
import de.jpx3.intave.analytics.GlobalStatisticsRecorder;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantAccess;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.permission.WorldPermission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.util.NumberConversions;

import static de.jpx3.intave.IntaveControl.DUMP_BLOCK_HITBOX_ON_RIGHT_CLICK;

@Relocate
public final class InteractionEmulator implements EventProcessor {
  private final IntavePlugin plugin;

  public InteractionEmulator(IntavePlugin plugin) {
    this.plugin = plugin;
    this.setup();
  }

  private void setup() {
    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription(ignoreCancelled = true)
  public void onPre(BlockPlaceEvent place) {
    if (place.getClass().equals(BlockPlaceEvent.class)) {
      Block block = place.getBlock();
      ExtendedBlockStateCache blockStateAccess = userOf(place.getPlayer()).blockStates();
      blockStateAccess.invalidateCacheAt(block.getX(), block.getY(), block.getZ());
      //      blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
    }
  }

  @BukkitEventSubscription
  public void on(PlayerBucketFillEvent fill) {
    Player player = fill.getPlayer();
    Block block = fill.getBlockClicked().getRelative(fill.getBlockFace());
    ExtendedBlockStateCache blockStateAccess = userOf(player).blockStates();
    blockStateAccess.invalidateCacheAt(block.getX(), block.getY(), block.getZ());
    blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  @BukkitEventSubscription
  public void on(PlayerBucketEmptyEvent empty) {
    Player player = empty.getPlayer();
    Block block = empty.getBlockClicked().getRelative(empty.getBlockFace());
    ExtendedBlockStateCache blockStateAccess = userOf(player).blockStates();
    blockStateAccess.invalidateCacheAt(block.getX(), block.getY(), block.getZ());
    blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  @BukkitEventSubscription(ignoreCancelled = true)
  public void onPre(BlockBreakEvent breeak) {
    if (breeak.getClass().equals(BlockBreakEvent.class)) {
      Block block = breeak.getBlock();
      ExtendedBlockStateCache blockStateAccess = userOf(breeak.getPlayer()).blockStates();
      blockStateAccess.invalidateCacheAt(block.getX(), block.getY(), block.getZ());
      //      blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
    }
  }

  public EmulationResult emulate(Interaction interaction) {
    Player player = interaction.player();
    EmulationResult emulationResult;
    InteractionType interactionType = interaction.type();
    switch (interactionType) {
      case PLACE:
        emulationResult = emulatePlacement(player, interaction);
        break;
      case START_BREAK:
      case INTERACT:
        emulationResult = emulateInteraction(player, interaction);
        break;
      case BREAK:
        emulationResult = emulateBreak(player, interaction);
        break;
      default:
        emulationResult = EmulationResult.FAILED;
        break;
    }
    return emulationResult;
  }

  private EmulationResult emulateBreak(Player player, Interaction interaction) {
    User user = userOf(player);
    World world = interaction.world();
    plugin.analytics().recorderOf(GlobalStatisticsRecorder.class).recordBlockDestroyed();
    BlockPosition blockPosition = interaction.targetBlock();
    Location blockBreakLocation = blockPosition.toLocation(world);
    boolean access =
      WorldPermission.blockBreakPermission(
        player, VolatileBlockAccess.blockAccess(blockBreakLocation));
    if (access) {
      int blockX = blockBreakLocation.getBlockX();
      int blockY = blockBreakLocation.getBlockY();
      int blockZ = blockBreakLocation.getBlockZ();
      // add to future bounding boxes
      ExtendedBlockStateCache blockStateAccess = userOf(player).blockStates();

      Location verifiedLocation = user.meta().movement().verifiedLocation();
      if (distance(verifiedLocation, blockPosition) < 2
        && blockPosition.getY() < verifiedLocation.getBlockY()) {
        user.meta().movement().pastNearbyCollisionInaccuracy = 0;
      }

      Material material = blockStateAccess.typeAt(blockX, blockY, blockZ);
      if (material == BlockTypeAccess.WEB) {
        boolean playerInsideWeb =
          Collision.playerInImaginaryBlock(
            user, world, blockX, blockY, blockZ, Material.STONE, 0);
        if (playerInsideWeb) {
          user.meta().movement().checkWebStateAgainNextTick = true;
        }
      }
      blockStateAccess.override(world, blockX, blockY, blockZ, Material.AIR, 0);
      blockStateAccess.invalidateCacheAt(blockX, blockY, blockZ);
    }
    return access ? EmulationResult.SUCCEEDED : EmulationResult.FAILED;
  }

  private static double distance(Location playerLocation, BlockPosition blockPosition) {
    return Math.sqrt(
      NumberConversions.square(playerLocation.getBlockX() - blockPosition.getX())
        + NumberConversions.square(playerLocation.getBlockY() - blockPosition.getY())
        + NumberConversions.square(playerLocation.getBlockZ() - blockPosition.getZ()));
  }

  private EmulationResult emulatePlacement(Player player, Interaction interaction) {
    User user = userOf(player);
    World world = interaction.world();
    plugin.analytics().recorderOf(GlobalStatisticsRecorder.class).recordBlockPlaced();
    Location blockAgainstLocation = interaction.targetBlock().toLocation(world);
    Location defaultPlacementLocation =
      blockAgainstLocation
        .clone()
        .add(
          Direction.getFront(interaction.targetDirection())
            .getDirectionVec()
            .convertToBukkitVec());
    boolean replace =
      BlockInteractionAccess.replacedOnPlacement(
        world, player, new BlockPosition(blockAgainstLocation.toVector()));
    Location blockPlacementLocation = replace ? blockAgainstLocation : defaultPlacementLocation;
    Material itemTypeInHand = interaction.itemTypeInHand();
    int blockX = blockPlacementLocation.getBlockX();
    int blockY = blockPlacementLocation.getBlockY();
    int blockZ = blockPlacementLocation.getBlockZ();
    int dat = 0;
    boolean raytraceCollidesWithPosition =
      Collision.playerInImaginaryBlock(user, world, blockX, blockY, blockZ, itemTypeInHand, dat);
    if (raytraceCollidesWithPosition) {
      return EmulationResult.FAILED;
    }
    Material replacementType = interaction.itemTypeInHand();
    int variant = 0;
    EnumWrappers.Hand hand = interaction.hand();
    boolean access =
      WorldPermission.blockPlacePermission(
        player,
        world,
        hand == null || hand == EnumWrappers.Hand.MAIN_HAND,
        blockX,
        blockY,
        blockZ,
        interaction.targetDirection(),
        replacementType,
        variant);
    if (access) {
      /*
       This hardcode is required
      */
      if (replacementType == BlockTypeAccess.WEB) {
        boolean playerInsideWeb =
          Collision.playerInImaginaryBlock(
            user, world, blockX, blockY, blockZ, Material.STONE, 0);
        if (playerInsideWeb) {
          user.meta().movement().checkWebStateAgainNextTick = true;
        }
      }
      user.meta().movement().pastBlockPlacement = 0;
      ExtendedBlockStateCache blockStates = user.blockStates();
      blockStates.override(world, blockX, blockY, blockZ, replacementType, variant);
      blockStates.invalidateCacheAt(blockX, blockY, blockZ);
      // enforce block reset later
      //      Synchronizer.synchronize(() -> {
      //        Synchronizer.synchronize(() -> blockStates.invalidateOverride(blockX, blockY,
      // blockZ));
      //      });
    } else {
      return EmulationResult.FAILED;
    }
    return EmulationResult.SUCCEEDED;
  }

  private EmulationResult emulateInteraction(Player player, Interaction interaction) {
    World world = interaction.world();
    Location clickedBlockLocation = interaction.targetBlock().toLocation(world);
    Block clickedBlock = VolatileBlockAccess.blockAccess(clickedBlockLocation);
    Material itemTypeInHand = interaction.itemTypeInHand();
    Location placementLocation =
      clickedBlockLocation
        .clone()
        .add(
          Direction.getFront(interaction.targetDirection())
            .getDirectionVec()
            .convertToBukkitVec());
    emulateInteractWithHandItem(player, clickedBlock, placementLocation, itemTypeInHand);
    emulatePhysicalInteract(player, clickedBlock);
    return EmulationResult.SUCCEEDED;
  }

  private void emulateInteractWithHandItem(
    Player player, Block clickedBlock, Location placementLocation, Material itemTypeInHand) {
    User user = userOf(player);
    ExtendedBlockStateCache blockStateAccess = user.blockStates();
    World world = player.getWorld();
    switch (itemTypeInHand) {
      case BUCKET: {
        Material placementType =
          VolatileBlockAccess.typeAccess(
            user, placementLocation); // placementLocation.getBlock().getType();
        // remove liquid on location if exists
        if (MaterialMagic.isLiquid(placementType)) {
          // emulate
          if (WorldPermission.bukkitActionPermission(
            player,
            BucketAction.FILL_BUCKET,
            clickedBlock,
            BlockFace.SELF,
            itemTypeInHand,
            null)) {
            blockStateAccess.override(
              world,
              placementLocation.getBlockX(),
              placementLocation.getBlockY(),
              placementLocation.getBlockZ(),
              Material.AIR,
              0);
          }
        }
        break;
      }
      case WATER_BUCKET:
      case LAVA_BUCKET: {
        Material placementType = VolatileBlockAccess.typeAccess(user, placementLocation);
        boolean adventureMode =
          user.meta().abilities().inGameMode(AbilityTracker.GameMode.ADVENTURE);

        // emulate
        if (placementType == Material.AIR
          && !adventureMode
          && WorldPermission.bukkitActionPermission(
          player,
          BucketAction.EMPTY_BUCKET,
          clickedBlock,
          BlockFace.SELF,
          itemTypeInHand,
          null)) {
          blockStateAccess.override(
            world,
            placementLocation.getBlockX(),
            placementLocation.getBlockY(),
            placementLocation.getBlockZ(),
            itemTypeInHand == Material.WATER_BUCKET ? Material.WATER : Material.LAVA,
            15);
        }
        break;
      }
    }
  }

  private void emulatePhysicalInteract(Player player, Block block) {
    World world = player.getWorld();
    ExtendedBlockStateCache blockStateAccess = userOf(player).blockStates();
    Material clickedType = BlockTypeAccess.typeAccess(block, player);

    if (DUMP_BLOCK_HITBOX_ON_RIGHT_CLICK) {
      player.sendMessage(String.valueOf(blockStateAccess.collisionShapeAt(block.getX(), block.getY(), block.getZ())));
    }

    switch (clickedType) {
      case ACACIA_DOOR:
      case DARK_OAK_DOOR:
      case BIRCH_DOOR:
      case JUNGLE_DOOR:
      case WOOD_DOOR:
      case WOODEN_DOOR: {
        int upperData = BlockVariantAccess.variantAccess(block);
        int lowerData;

        boolean isUpper = (upperData & 8) != 0;
        if (isUpper) {
          lowerData = BlockVariantAccess.variantAccess(block = block.getRelative(BlockFace.DOWN));
        } else {
          lowerData = upperData;
          upperData = BlockVariantAccess.variantAccess(block.getRelative(BlockFace.UP));
        }

        // toggle close
        lowerData = (lowerData & 4) != 0 ? lowerData ^ 4 : lowerData | 4;

        blockStateAccess.override(world, block.getX(), block.getY(), block.getZ(), clickedType, lowerData);
        blockStateAccess.override(world, block.getX(), block.getY() + 1, block.getZ(), clickedType, upperData);

        Block finalBlock = block;
        Synchronizer.synchronize(() -> {
          blockStateAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY() - 1, finalBlock.getZ());
          blockStateAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY(), finalBlock.getZ());
          blockStateAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY() + 1, finalBlock.getZ());
        });
        break;
      }
      case ACACIA_FENCE_GATE:
      case BIRCH_FENCE_GATE:
      case DARK_OAK_FENCE_GATE:
      case FENCE_GATE:
      case JUNGLE_FENCE_GATE:
      case SPRUCE_FENCE_GATE: {
        // TODO
        break;
      }
      case TRAP_DOOR: {
        // flawed
        int data = BlockVariantAccess.variantAccess(block);
        boolean newOpen = (data & 4) != 0;
        int bitMask = 4;
        byte newData = (byte) (!newOpen ? (data | bitMask) : (data & ~bitMask));
        Material material = BlockTypeAccess.typeAccess(block, player);
        blockStateAccess.override(world, block.getX(), block.getY(), block.getZ(), material, newData);
        Block finalBlock1 = block;
        Synchronizer.synchronize(() ->
          blockStateAccess.invalidateOverride(finalBlock1.getX(), finalBlock1.getY(), finalBlock1.getZ())
        );
        break;
      }
    }
  }

  private User userOf(Player player) {
    return UserRepository.userOf(player);
  }

  public enum EmulationResult {
    SUCCEEDED,
    FAILED
  }
}
