package de.jpx3.intave.check.world.interaction;

import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.BucketAction;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.block.variant.BlockVariantReverseLookup;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.world.permission.WorldPermission;
import org.bukkit.Bukkit;
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
import static de.jpx3.intave.IntaveControl.REMOVE_PLACED_BLOCKS_WITH_DELAY;

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
      BlockCache blockStateAccess = userOf(place.getPlayer()).blockCache();
      blockStateAccess.invalidateCacheAround(block.getX(), block.getY(), block.getZ());
      //      blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
    }
    if (REMOVE_PLACED_BLOCKS_WITH_DELAY) {
      Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
        place.getBlock().setType(Material.AIR);
      }, 20 * 5);
    }
  }

  @BukkitEventSubscription
  public void on(PlayerBucketFillEvent fill) {
    Player player = fill.getPlayer();
    Block block = fill.getBlockClicked().getRelative(fill.getBlockFace());
    BlockCache blockStateAccess = userOf(player).blockCache();
    blockStateAccess.invalidateCacheAround(block.getX(), block.getY(), block.getZ());
    blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  @BukkitEventSubscription
  public void on(PlayerBucketEmptyEvent empty) {
    Player player = empty.getPlayer();
    Block block = empty.getBlockClicked().getRelative(empty.getBlockFace());
    BlockCache blockStateAccess = userOf(player).blockCache();
    blockStateAccess.invalidateCacheAround(block.getX(), block.getY(), block.getZ());
    blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  @BukkitEventSubscription(ignoreCancelled = true)
  public void onPre(BlockBreakEvent breeak) {
    if (breeak.getClass().equals(BlockBreakEvent.class)) {
      Block block = breeak.getBlock();
      BlockCache blockStateAccess = userOf(breeak.getPlayer()).blockCache();
      blockStateAccess.invalidateCacheAround(block.getX(), block.getY(), block.getZ());
      //      blockStateAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
    }
  }

  public EmulationResult emulate(Interaction interaction) {
    if (interaction.hasBeenEmulated()) {
      return EmulationResult.FAILED_NON_CRITICAL;
    }
    interaction.setEmulated();
    Player player = interaction.player();
    InteractionType interactionType = interaction.type();
    switch (interactionType) {
      case PLACE:
        return emulatePlacement(player, interaction);
      case START_BREAK:
      case INTERACT:
        return emulateInteraction(player, interaction);
      case EMPTY_INTERACT:
        return emulateEmptyInteraction(player, interaction);
      case BREAK:
        return emulateBreak(player, interaction);
      default:
        return EmulationResult.FAILED_NON_CRITICAL;
    }
  }

  public void undo(Interaction interaction) {
    if (!interaction.hasBeenEmulated()) {
      return;
    }
    Player player = interaction.player();
    InteractionType interactionType = interaction.type();
    switch (interactionType) {
      case PLACE:
        undoPlacement(player, interaction);
        break;
      case START_BREAK:
      case INTERACT:
        break;
      case EMPTY_INTERACT:
        break;
      case BREAK:
        break;
    }
  }

  private EmulationResult emulateBreak(Player player, Interaction interaction) {
    User user = userOf(player);
    World world = interaction.world();
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
      BlockCache blockStateAccess = userOf(player).blockCache();

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
      blockStateAccess.invalidateCacheAround(blockX, blockY, blockZ);
    }
    return access ? EmulationResult.SUCCEEDED : EmulationResult.FAILED_CRITICAL;
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
    BlockCache blockStates = user.blockCache();
    World world = interaction.world();
    Location blockAgainstLocation = interaction.targetBlock().toLocation(world);

    if (System.currentTimeMillis() - user.meta().violationLevel().lastBlockPlaceDenyRequest < 1250) {
      return EmulationResult.FAILED_CRITICAL;
    }

    Material itemTypeInHand = interaction.itemTypeInHand();

    int originBlockX = blockAgainstLocation.getBlockX();
    int originBlockY = blockAgainstLocation.getBlockY();
    int originBlockZ = blockAgainstLocation.getBlockZ();
    boolean replace = BlockInteractionAccess.replacedOnPlacement(
      world, player, new BlockPosition(blockAgainstLocation)
    );

    Location blockPlacementLocation = placementLocation(
      world, player, blockStates, itemTypeInHand, interaction.targetDirection(),
      blockAgainstLocation
    );
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
      // only failed, not critical failed, this should not be possible to abuse
      return EmulationResult.FAILED_NON_CRITICAL;
    }
    InteractionHand hand = interaction.hand();
    boolean access = WorldPermission.blockPlacePermission(
      player, world,
      hand == null || hand == InteractionHand.MAIN_HAND,
      blockX, blockY, blockZ,
      interaction.targetDirectionIndex(),
      placedBlockType, variant
    );
    if (access) {
      /*
       This hardcode is required
      */
      MovementMetadata movement = user.meta().movement();
      if (placedBlockType == BlockTypeAccess.WEB) {
//        boolean playerInsideWeb = Collision.playerInImaginaryBlock(user, world, blockX, blockY, blockZ, Material.STONE, 0);
//        if (playerInsideWeb) {
          movement.checkWebStateAgainNextTick = true;
//        }
      }

      if (!movement.placementTrustChain.tryAction(
        new de.jpx3.intave.share.BlockPosition(blockPlacementLocation),
        new de.jpx3.intave.share.BlockPosition(blockAgainstLocation))) {
        return EmulationResult.FAILED_CRITICAL;
      }

      Material presentType = VolatileBlockAccess.typeAccess(user, blockX, blockY, blockZ);
      int presentVariant = VolatileBlockAccess.variantIndexAccess(user, world, blockX, blockY, blockZ);
      movement.pastBlockPlacement = 0;
      blockStates.override(world, blockX, blockY, blockZ, placedBlockType, variant, "PLACE");
      blockStates.invalidateCacheAround(blockX, blockY, blockZ);
      blockStates.lockOverride(blockX, blockY, blockZ);
      ProtocolMetadata protocol = user.meta().protocol();
      int sequenceNumber = interaction.sequenceNumber();
      if (protocol.clientSpeculativeBlocks()) {
        blockStates.setClientSpeculationValue(
          world, blockX, blockY, blockZ, presentType, presentVariant, sequenceNumber
        );
      }
      if (protocol.selfAcknowledgePlacements()) {
        Synchronizer.synchronize(() -> {
          user.tickFeedback(() ->
            user.blockCache().moveClientSpeculationsToOverride(player.getWorld(), sequenceNumber)
          );
        });
      }

      if (presentType != placedBlockType) {
        interaction.markPlacementEmulated();
        interaction.setEmulationPosition(
          new BlockPosition(blockX, blockY, blockZ)
        );
      }
      // enforce block reset later
      //      Synchronizer.synchronize(() -> {
      //        Synchronizer.synchronize(() -> blockStates.invalidateOverride(blockX, blockY,
      // blockZ));
      //      });

      Nayoro nayoro = Modules.nayoro();
      Position eyePosition = Position.of(
        movement.positionX,
        movement.positionY + movement.eyeHeight(),
        movement.positionZ
      );
      Position endOfRaytrace = Position.empty();
      if (interaction.hasRaytraceResult()) {
        MovingObjectPosition movingObjectPosition = interaction.raytraceResult();
        endOfRaytrace = movingObjectPosition.hitVec.toPosition();
      }
      de.jpx3.intave.module.nayoro.event.BlockPlaceEvent placeEvent = de.jpx3.intave.module.nayoro.event.BlockPlaceEvent.create(
        Position.of(blockX, blockY, blockZ),
        replace ? null : Position.of(originBlockX, originBlockY, originBlockZ),
        interaction.targetDirection(),
        movement.rotation(),
        eyePosition, endOfRaytrace,
        interaction.hand(),
        interaction.itemTypeInHand().name(),
        interaction.itemInHand().getAmount(),
        interaction.facingX(), interaction.facingY(), interaction.facingZ()
      );
      nayoro.sinkCallback().accept(user, placeEvent::accept);
      return EmulationResult.SUCCEEDED;
    } else {
      return EmulationResult.FAILED_CRITICAL;
    }
  }

  public void undoPlacement(Player player, Interaction interaction) {
    User user = userOf(player);
    World world = player.getWorld();
    BlockCache blockStates = user.blockCache();
    if (interaction.wasPlacementEmulated()) {
      BlockPosition blockPosition = interaction.emulationBlockPosition();
      int blockX = blockPosition.getX();
      int blockY = blockPosition.getY();
      int blockZ = blockPosition.getZ();
      if (user.meta().protocol().clientSpeculativeBlocks()) {
        blockStates.undoClientSpeculation(world, blockX, blockY, blockZ);
      }
//      blockStates.unlockOverride(blockX, blockY, blockZ);
      blockStates.invalidateCacheAround(blockX, blockY, blockZ);
      blockStates.override(world, blockX, blockY, blockZ, Material.AIR, 0, "UNDO_PLACE");
//      player.sendMessage("Undo placement at " + blockX + ", " + blockY + ", " + blockZ);
    }
//    player.sendMessage(blockStates.typeAt(blockX, blockY, blockZ) + "");
  }

  public Location placementLocation(
    World world, Player player,
    BlockCache blockStates,
    Material itemTypeInHand,
    Direction direction,
    Location blockAgainstLocation
  ) {
    int originBlockX = blockAgainstLocation.getBlockX();
    int originBlockY = blockAgainstLocation.getBlockY();
    int originBlockZ = blockAgainstLocation.getBlockZ();
    boolean replace = BlockInteractionAccess.replacedOnPlacement(
      world, player, new BlockPosition(blockAgainstLocation)
    );

    // I don't want to hardcode this here, but where else should I put it?
    Material typeAtOB = blockStates.typeAt(originBlockX, originBlockY, originBlockZ);

    Vector placementVector = direction.directionVector().convertToBukkitVec();
    Location defaultPlacementLocation = blockAgainstLocation.clone().add(placementVector);

    Material typeAtDPL = blockStates.typeAt(
      defaultPlacementLocation.getBlockX(),
      defaultPlacementLocation.getBlockY(),
      defaultPlacementLocation.getBlockZ()
    );
    if (STEP_BLOCKS.contains(typeAtOB) && itemTypeInHand == typeAtOB) {
      BlockVariant variant = BlockVariantRegister.variantOf(typeAtOB, blockStates.variantIndexAt(originBlockX, originBlockY, originBlockZ));
      EnumHalf half = variant.enumProperty(EnumHalf.class, STEP_PROPERTY_NAME);
      if (direction == Direction.UP && half == EnumHalf.BOTTOM) {
        replace = true;
      } else if (direction == Direction.DOWN && half == EnumHalf.TOP) {
        replace = true;
      }
    }
    if (STEP_BLOCKS.contains(typeAtDPL) && itemTypeInHand == typeAtDPL) {
      replace = true;
    }

    return replace ? blockAgainstLocation : defaultPlacementLocation;
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
      return new EstimationResult(placementType, !possibleIds.isEmpty() ? possibleIds.iterator().next() : 0);
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
          return new EstimationResult(placementType, !possibleIds.isEmpty() ? possibleIds.iterator().next() : 0);
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
      } else {
        boolean keep = targetDirection != Direction.DOWN && (targetDirection == Direction.UP || facingY <= 0.5);
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

  private EmulationResult emulateEmptyInteraction(Player player, Interaction interaction) {
    emulatePhysicalInteract(player, VolatileBlockAccess.blockAccess(interaction.targetBlock().toLocation(interaction.world())));
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
    BlockCache blockStateAccess = user.blockCache();
    World world = player.getWorld();
    switch (itemTypeInHand) {
      case BUCKET: {
        Material placementType = VolatileBlockAccess.typeAccess(user, placementLocation); // placementLocation.getBlock().getType();

        // remove liquid on location if exists
        if (MaterialMagic.isLavaOrWater(placementType) && type == InteractionType.INTERACT) {
          // emulate
//          Synchronizer.synchronize(() -> {
//            player.sendMessage(ChatColor.DARK_PURPLE + "Emulating bucket empty");
//          });
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
//        Synchronizer.synchronize(() -> {
//          player.sendMessage(ChatColor.DARK_PURPLE + "Emulating bucket place");
//        });
        // emulate
        if (
          !MaterialMagic.blockSolid(placementType)
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
//          Synchronizer.synchronize(() -> {
//            player.sendMessage(ChatColor.DARK_PURPLE + "OVERRIDE " + fullWaterVariantIndex);
//          });
        }
        break;
      }
      case FLINT_AND_STEEL:
        Material underlyingType = VolatileBlockAccess.typeAccess(user, clickedBlock.getLocation());
        if (underlyingType == Material.TNT) {
          blockStateAccess.override(
            world,
            clickedBlock.getX(),
            clickedBlock.getY(),
            clickedBlock.getZ(),
            Material.AIR,
            0,
            "TNT_REPLACE"
          );
        }
        break;
    }
  }

  private void emulatePhysicalInteract(Player player, Block block) {
//    World world = player.getWorld();
    BlockCache blockStateAccess = userOf(player).blockCache();
    Material clickedType = BlockTypeAccess.typeAccess(block, player);

    if (DUMP_BLOCK_HITBOX_ON_RIGHT_CLICK) {
      Material type = blockStateAccess.typeAt(block.getX(), block.getY(), block.getZ());
      int variant = blockStateAccess.variantIndexAt(block.getX(), block.getY(), block.getZ());
      BlockVariant properties = BlockVariantRegister.variantOf(type, variant);
      String propertyString = "{"+properties.propertyNames().stream().map(s -> s + ": " + properties.propertyOf(s)).collect(Collectors.joining(", ")) +"}";
      Fluid fluid = Fluids.fluidAt(userOf(player), block.getX(), block.getY(), block.getZ());
      player.sendMessage(type + "/" + variant + "."+propertyString+" f"+ fluid +" -> " + blockStateAccess.collisionShapeAt(block.getX(), block.getY(), block.getZ()) +"/"+blockStateAccess.outlineShapeAt(block.getX(), block.getY(), block.getZ()));
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
    FAILED_NON_CRITICAL,
    FAILED_CRITICAL

    ;

    public boolean isSuccessful() {
      return this == SUCCEEDED;
    }

    public boolean denyForward() {
      return this == FAILED_CRITICAL;
    }
  }
}
