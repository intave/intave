package de.jpx3.intave.check.world.interaction;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.BucketAction;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.analytics.GlobalStatisticsRecorder;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.block.variant.BlockVariantReverseLookup;
import de.jpx3.intave.check.EventProcessor;
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
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
      case EMPTY_INTERACT:
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
        player, VolatileBlockAccess.fakeBlockAccess(user, blockBreakLocation));
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
      blockStateAccess.override(world, blockX, blockY, blockZ, Material.AIR, 0, "BREAK");
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

  private static final String STEP_PROPERTY_NAME = MinecraftVersions.VER1_13_0.atOrAbove() ? "type" : "half";
  private static final Set<Material> IGNORE_SET_IN_SELF = MaterialSearch.materialsThatContain("BUTTON", "PLATE");

  private EmulationResult emulatePlacement(Player player, Interaction interaction) {
    User user = userOf(player);
    ExtendedBlockStateCache blockStates = user.blockStates();
    World world = interaction.world();
    plugin.analytics().recorderOf(GlobalStatisticsRecorder.class).recordBlockPlaced();
    Location blockAgainstLocation = interaction.targetBlock().toLocation(world);
    Vector placementVector = Direction.getFront(interaction.targetDirectionIndex())
      .directionVector().convertToBukkitVec();
    Location defaultPlacementLocation = blockAgainstLocation.clone().add(placementVector);
    int originBlockX = blockAgainstLocation.getBlockX();
    int originBlockY = blockAgainstLocation.getBlockY();
    int originBlockZ = blockAgainstLocation.getBlockZ();
    boolean replace = BlockInteractionAccess.replacedOnPlacement(
      world, player, new BlockPosition(blockAgainstLocation.toVector())
    );

    Material itemTypeInHand = interaction.itemTypeInHand();

    // I don't want to hardcode this here, but where else should I put it?
    Material typeAtOB = blockStates.typeAt(originBlockX, originBlockY, originBlockZ);
//    System.out.println("typeAtOB: " + typeAtOB);
    Material typeAtDPL = blockStates.typeAt(
      defaultPlacementLocation.getBlockX(),
      defaultPlacementLocation.getBlockY(),
      defaultPlacementLocation.getBlockZ()
    );
    if (STEP_BLOCKS.contains(typeAtOB) && itemTypeInHand == typeAtOB) {
      BlockVariant variant = BlockVariantRegister.variantOf(typeAtOB, blockStates.variantIndexAt(originBlockX, originBlockY, originBlockZ));
      EnumHalf half = variant.enumProperty(EnumHalf.class, STEP_PROPERTY_NAME);
      Direction direction = interaction.targetDirection();
      if (direction == Direction.UP && half == EnumHalf.BOTTOM) {
        replace = true;
      } else if (direction == Direction.DOWN && half == EnumHalf.TOP) {
        replace = true;
      }
    }
    if (STEP_BLOCKS.contains(typeAtDPL) && itemTypeInHand == typeAtDPL) {
      replace = true;
    }

    Location blockPlacementLocation = replace ? blockAgainstLocation : defaultPlacementLocation;
    int blockX = blockPlacementLocation.getBlockX();
    int blockY = blockPlacementLocation.getBlockY();
    int blockZ = blockPlacementLocation.getBlockZ();
    Material placedBlockType = itemTypeInHand;
    int variant = 0;
    EstimationResult estimationResult = emulateBlockBehavior(
      user, itemTypeInHand, interaction.targetDirection(),
      blockStates.typeAt(blockX, blockY, blockZ),
      blockStates.variantIndexAt(blockX, blockY, blockZ),
      blockStates.typeAt(originBlockX, originBlockY, originBlockZ),
      blockStates.variantIndexAt(originBlockX, originBlockY, originBlockZ),
      blockX, blockY, blockZ,
      interaction.facingX(), interaction.facingY(), interaction.facingZ()
    );
    if (estimationResult != null) {
      placedBlockType = estimationResult.type();
      variant = estimationResult.variantIndex();
    }
    boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
      user, world,
      blockX, blockY, blockZ,
      placedBlockType, variant
    ) && !IGNORE_SET_IN_SELF.contains(placedBlockType);
    if (raytraceCollidesWithPosition) {
      if (IntaveControl.DEBUG_VARIANT_COMPILATION) {
        System.out.println("[variant/debug] Failed to place block due to raytrace collision (replacing: " + replace + ")");
      }
      return EmulationResult.FAILED;
    }
    EnumWrappers.Hand hand = interaction.hand();
    boolean access = WorldPermission.blockPlacePermission(
      player, world,
      hand == null || hand == EnumWrappers.Hand.MAIN_HAND,
      blockX, blockY, blockZ,
      interaction.targetDirectionIndex(),
      placedBlockType, variant
    );
    if (access) {
      /*
       This hardcode is required
      */
      if (placedBlockType == BlockTypeAccess.WEB) {
//        boolean playerInsideWeb = Collision.playerInImaginaryBlock(user, world, blockX, blockY, blockZ, Material.STONE, 0);
//        if (playerInsideWeb) {
          user.meta().movement().checkWebStateAgainNextTick = true;
//        }
      }
      user.meta().movement().pastBlockPlacement = 0;
      blockStates.override(world, blockX, blockY, blockZ, placedBlockType, variant, "PLACE");
      blockStates.invalidateCacheAt(blockX, blockY, blockZ);
      blockStates.lockOverride(blockX, blockY, blockZ);
      // enforce block reset later
      //      Synchronizer.synchronize(() -> {
      //        Synchronizer.synchronize(() -> blockStates.invalidateOverride(blockX, blockY,
      // blockZ));
      //      });
      return EmulationResult.SUCCEEDED;
    } else {
      return EmulationResult.FAILED;
    }
  }

  private static final Set<Material> STEP_BLOCKS = MaterialSearch.materialsThatContain("STEP", "SLAB");
  private static final boolean DOUBLE_IN_STEP_TYPE = MinecraftVersions.VER1_13_0.atOrAbove();

  private EstimationResult emulateBlockBehavior(
    User user, Material placementType, Direction targetDirection,
    Material presentType, int presentVariantIndex,
    Material originType, int originVariantIndex,
    int blockX, int blockY, int blockZ,
    float facingX, float facingY, float facingZ
  ) {
    float playerYaw = user.meta().movement().rotationYaw();
    if (placementType == Material.LADDER) {
      Direction playerDirection = Direction.getHorizontal(floor((double)(playerYaw * 4.0F / 360.0F) + 0.5) & 3).getOpposite();
      int uniqueId = playerDirection.hashCode();
      Set<Integer> possibleIds = BlockVariantReverseLookup.variantsOfConfiguration(
        placementType, uniqueId, propertyName -> Objects.equals(propertyName, "facing") ? playerDirection : null
      );
      return new EstimationResult(placementType, possibleIds.size() >= 1 ? possibleIds.iterator().next() : 0);
    }
    if (STEP_BLOCKS.contains(placementType)) {
      boolean isSlab = presentType == placementType;
      if (isSlab) {
        BlockVariant presentVariant = BlockVariantRegister.variantOf(presentType, presentVariantIndex);
        Comparable<?> variant = presentVariant.propertyOf("variant");
        if (DOUBLE_IN_STEP_TYPE) {
          int uniqueId = 64;
          Set<Integer> possibleIds = BlockVariantReverseLookup.variantsOfConfiguration(
            placementType, uniqueId, propertyName -> Objects.equals(propertyName, STEP_PROPERTY_NAME) ? "DOUBLE" : Objects.equals(propertyName, "variant") ? variant : null
          );
          return new EstimationResult(placementType, possibleIds.size() >= 1 ? possibleIds.iterator().next() : 0);
        } else {
          String enumName = placementType.name();
          boolean isSlab2 = enumName.contains("SLAB2");
          Material doubleSlabType;
          if (isSlab2) {
            doubleSlabType = MaterialSearch.materialThatIsNamed(enumName.substring(0, enumName.length() - 5) + "DOUBLE_SLAB2");
          } else {
            doubleSlabType = MaterialSearch.materialThatIsNamed(enumName.substring(0, enumName.length() - 4) + "DOUBLE_STEP");
          }
          // doesn't make a real difference, but hey - why not
          int uniqueId = variant.hashCode() ;
          Set<Integer> possibleIds = BlockVariantReverseLookup.variantsOfConfiguration(
            doubleSlabType, uniqueId, propertyName -> Objects.equals(propertyName, "variant") ? variant : null
          );
          return new EstimationResult(doubleSlabType, !possibleIds.isEmpty() ? possibleIds.iterator().next() : 0);
        }
      } else {boolean keep = targetDirection != Direction.DOWN && (targetDirection == Direction.UP || facingY <= 0.5);
        int uniqueId = Boolean.hashCode(keep);
        Set<Integer> possibleIds = BlockVariantReverseLookup.variantsOfConfiguration(
          placementType, uniqueId, propertyName -> Objects.equals(propertyName, STEP_PROPERTY_NAME) ? keep ? "BOTTOM" : "TOP" : null
        );
        return new EstimationResult(placementType, !possibleIds.isEmpty() ? possibleIds.iterator().next() : 0);
      }
    }
    return null;
  }

  public static class EstimationResult {
    private final Material type;
    private final int variantIndex;

    public EstimationResult(Material type, int variantIndex) {
      this.type = type;
      this.variantIndex = variantIndex;
    }

    public Material type() {
      return type;
    }

    public int variantIndex() {
      return variantIndex;
    }
  }

  @KeepEnumInternalNames
  public enum EnumHalf {
    TOP("top"),
    BOTTOM("bottom"),
    DOUBLE("double");

    private final String name;

    EnumHalf(String s) {
      this.name = s;
    }

    public String toString() {
      return this.name;
    }

    public String getName() {
      return this.name;
    }
  }

  public static int floor(double var0) {
    int var2 = (int)var0;
    return var0 < (double)var2 ? var2 - 1 : var2;
  }

  private EmulationResult emulateInteraction(Player player, Interaction interaction) {
    World world = interaction.world();
    BlockPosition blockPosition = interaction.targetBlock();
    Location clickedBlockLocation = blockPosition == null ? null : blockPosition.toLocation(world);
    Block clickedBlock = clickedBlockLocation == null ? null : VolatileBlockAccess.blockAccess(clickedBlockLocation);
    Material itemTypeInHand = interaction.itemTypeInHand();
    Location placementLocation = clickedBlock == null ? null :
      clickedBlockLocation.clone().add(Direction.getFront(interaction.targetDirectionIndex()).directionVecAsVector());
    emulateItemInteraction(player, itemTypeInHand);
    if (clickedBlock != null) {
      emulateInteractWithHandItem(player, clickedBlock, interaction.type(), placementLocation, itemTypeInHand);
      emulatePhysicalInteract(player, clickedBlock);
    }
    return EmulationResult.SUCCEEDED;
  }

  private void emulateItemInteraction(
    Player player, Material itemTypeInHand
  ) {
    User user = userOf(player);
    if (itemTypeInHand != Material.AIR) {
//      user.meta().movement().awaitClickMovementSkip = true;
//      player.sendMessage("Awaiting click movement for interact with " + itemTypeInHand);
    }
  }

  private final int fullWaterVariantIndex = BlockVariantReverseLookup.variantsOfConfiguration(
    Material.WATER, 512,
    propertyName -> Objects.equals(propertyName, "level") ? 7 : null
  ).iterator().next();

  private final int fullLavaVariantIndex = BlockVariantReverseLookup.variantsOfConfiguration(
    Material.LAVA, 732,
    propertyName -> Objects.equals(propertyName, "level") ? 7 : null
  ).iterator().next();

  private void emulateInteractWithHandItem(
    Player player, Block clickedBlock,
    InteractionType type,
    Location placementLocation, Material itemTypeInHand
  ) {
    User user = userOf(player);
    ExtendedBlockStateCache blockStateAccess = user.blockStates();
    World world = player.getWorld();
    switch (itemTypeInHand) {
      case BUCKET: {
        Material placementType = VolatileBlockAccess.typeAccess(user, placementLocation); // placementLocation.getBlock().getType();

        // remove liquid on location if exists
        if (MaterialMagic.isLavaOrWater(placementType) && type == InteractionType.INTERACT) {
          // emulate
          if (WorldPermission.bukkitActionPermission(
            player,
            BucketAction.FILL_BUCKET,
            clickedBlock,
            BlockFace.SELF,
            itemTypeInHand,
            null)
          ) {
            blockStateAccess.override(
              world,
              placementLocation.getBlockX(),
              placementLocation.getBlockY(),
              placementLocation.getBlockZ(),
              Material.AIR,
              0,
              "BUCKET"
            );
          }
        }
        break;
      }
      case WATER_BUCKET:
      case LAVA_BUCKET: {
        Material placementType = VolatileBlockAccess.typeAccess(user, placementLocation);
        boolean adventureMode = user.meta().abilities().inGameMode(AbilityTracker.GameMode.ADVENTURE);

        // emulate
        if (placementType == Material.AIR
          && !adventureMode
          && type == InteractionType.INTERACT
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
            itemTypeInHand == Material.WATER_BUCKET ? fullWaterVariantIndex : fullLavaVariantIndex,
            "BUCKET"
          );
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
      Material type = blockStateAccess.typeAt(block.getX(), block.getY(), block.getZ());
      int variant = blockStateAccess.variantIndexAt(block.getX(), block.getY(), block.getZ());
      BlockVariant properties = BlockVariantRegister.variantOf(type, variant);
      String propertyString = "{"+properties.propertyNames().stream().map(s -> s + ": " + properties.propertyOf(s)).collect(Collectors.joining(", ")) +"}";

//      Fluid fluid = Fluids.fluidAt(userOf(player), block.getX(), block.getY(), block.getZ());
      Fluid fluid = Fluids.fluidAt(userOf(player), block.getX(), block.getY(), block.getZ());
      player.sendMessage(type + "/" + variant + "."+propertyString+" f"+ fluid +" -> " + blockStateAccess.collisionShapeAt(block.getX(), block.getY(), block.getZ()));
    }

    switch (clickedType) {
      case ACACIA_DOOR:
      case DARK_OAK_DOOR:
      case BIRCH_DOOR:
      case JUNGLE_DOOR:
      case WOOD_DOOR:
      case WOODEN_DOOR: {
//        int upperData = BlockVariantNativeAccess.variantAccess(block);
//        int lowerData;
//
//        boolean isUpper = (upperData & 8) != 0;
//        if (isUpper) {
//          lowerData = BlockVariantNativeAccess.variantAccess(block = block.getRelative(BlockFace.DOWN));
//        } else {
//          lowerData = upperData;
//          upperData = BlockVariantNativeAccess.variantAccess(block.getRelative(BlockFace.UP));
//        }
//
//        // toggle close
//        lowerData = (lowerData & 4) != 0 ? lowerData ^ 4 : lowerData | 4;
//
//        blockStateAccess.override(world, block.getX(), block.getY(), block.getZ(), clickedType, lowerData);
//        blockStateAccess.override(world, block.getX(), block.getY() + 1, block.getZ(), clickedType, upperData);
//
//        Block finalBlock = block;
//        Synchronizer.synchronize(() -> {
//          blockStateAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY() - 1, finalBlock.getZ());
//          blockStateAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY(), finalBlock.getZ());
//          blockStateAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY() + 1, finalBlock.getZ());
//        });
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
//        int data = BlockVariantNativeAccess.variantAccess(block);
//        boolean newOpen = (data & 4) != 0;
//        int bitMask = 4;
//        byte newData = (byte) (!newOpen ? (data | bitMask) : (data & ~bitMask));
//        Material material = BlockTypeAccess.typeAccess(block, player);
//        blockStateAccess.override(world, block.getX(), block.getY(), block.getZ(), material, newData);
//        Block finalBlock1 = block;
//        Synchronizer.synchronize(() ->
//          blockStateAccess.invalidateOverride(finalBlock1.getX(), finalBlock1.getY(), finalBlock1.getZ())
//        );
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
