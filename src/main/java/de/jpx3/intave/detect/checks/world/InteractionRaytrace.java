package de.jpx3.intave.detect.checks.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.BucketAction;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.dispatch.PlayerAbilityEvaluator;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.violation.Violation;
import de.jpx3.intave.event.violation.ViolationContext;
import de.jpx3.intave.tools.annotate.DispatchCrossCall;
import de.jpx3.intave.tools.client.MaterialLogic;
import de.jpx3.intave.tools.items.InventoryUseItemHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaInventoryData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.permission.WorldPermission;
import de.jpx3.intave.world.raytrace.Raytracer;
import org.bukkit.GameMode;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.*;

public final class InteractionRaytrace extends IntaveMetaCheck<InteractionRaytrace.InteractionMeta> {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;

  private boolean enabled;

  public InteractionRaytrace(IntavePlugin plugin) {
    super("InteractionRaytrace", "interactionraytrace", InteractionMeta.class);
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, 1);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_PLACE"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ITEM")
    }
  )
  public void receiveInteraction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    InteractionMeta interactionMeta = metaOf(user);
    UserMetaMovementData movementData = user.meta().movementData();
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = readBlockPositionFrom(packet);//packet.getBlockPositionModifier().readSafely(0);
    if (blockPosition == null || movementData.inVehicle()) {
      return;
    }
    int enumDirection = readEnumDirectionFrom(packet);
    if (enumDirection == 255 || event.isCancelled()) {
      return;
    }
    Vector facing = null;
    StructureModifier<Float> floatStructureModifier = packet.getFloat();
    if (floatStructureModifier.size() == 3) {
      facing = new Vector();
      facing.setX(floatStructureModifier.read(0));
      facing.setY(floatStructureModifier.read(1));
      facing.setZ(floatStructureModifier.read(2));
    }
    Material clickedType = BukkitBlockAccess.blockAccess(blockPosition.toLocation(player.getWorld())).getType();
    boolean clickable = BlockDataAccess.isClickable(clickedType);
    Material itemTypeInHand = user.meta().inventoryData().heldItemType();
    boolean isPlacement = itemTypeInHand != Material.AIR && itemTypeInHand.isBlock() && !clickable;
    EnumWrappers.Hand hand = packet.getHands().readSafely(0);
    Interaction interaction = new Interaction(
      player.getWorld(), player, blockPosition, enumDirection, packet.deepClone(),
      isPlacement ? InteractionType.PLACE : InteractionType.INTERACT, facing,
      itemTypeInHand,
      hand == null ? EnumWrappers.Hand.MAIN_HAND : hand,
      false,
      false
    );
    Location playerLocation = new Location(player.getWorld(), movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);
    Location playerLocationmdf = playerLocation.clone();
    playerLocationmdf.setYaw(movementData.lastRotationYaw);

    boolean mustPostValidate = interactionMeta.remainingBlockStart > 0 || interactionMeta.isBreakingBlock || movementData.awaitTeleport;
    if(!mustPostValidate && prevalidateInteraction(interaction, playerLocation, playerLocationmdf)) {
      emulate(interaction);
    } else {
      interactionMeta.interactionList.add(interaction);
      event.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "BLOCK_DIG")
    }
  )
  public void receiveBreak(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    BlockPosition blockPosition = readBlockPositionFrom(packet);//packet.getBlockPositionModifier().readSafely(0);
    User user = userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    if (blockPosition == null || event.isCancelled()) {
      return;
    }
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    ItemStack heldItemStack = inventoryData.heldItem();
    if (InventoryUseItemHelper.isSwordItem(player, heldItemStack) && player.getGameMode() == GameMode.CREATIVE) {
      event.setCancelled(true);
      return;
    }
    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    float blockDamage = BlockDataAccess.blockDamage(player, user.meta().inventoryData().heldItem(), blockPosition);
    boolean instantBreak = blockDamage == Float.POSITIVE_INFINITY || blockDamage >= 1.0f || user.meta().abilityData().inGameMode(PlayerAbilityEvaluator.GameMode.CREATIVE);
    boolean breakBlock = instantBreak || playerDigType == STOP_DESTROY_BLOCK;
    int enumDirection = readEnumDirectionFrom(packet);
    boolean blocking = blockPosition.getX() == 0 && blockPosition.getY() == 0 &&  blockPosition.getZ() == 0 && enumDirection == 0;
    if (enumDirection == 255 || blocking) {
      return;
    }

    InteractionMeta interactionMeta = metaOf(user);
    Interaction interaction = new Interaction(
      player.getWorld(), player, blockPosition, enumDirection, packet.deepClone(), breakBlock ? InteractionType.BREAK : InteractionType.INTERACT,
      null,
      user.meta().inventoryData().heldItemType(),
      EnumWrappers.Hand.MAIN_HAND,
      playerDigType == ABORT_DESTROY_BLOCK,
      playerDigType == START_DESTROY_BLOCK
    );
    Location playerLocation = new Location(player.getWorld(), movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);
    Location playerLocationmdf = playerLocation.clone();
    playerLocationmdf.setYaw(movementData.lastRotationYaw);

    boolean mustPostValidate = interactionMeta.remainingBlockStart > 0 || interactionMeta.isBreakingBlock || movementData.awaitTeleport;
    if(!mustPostValidate && prevalidateInteraction(interaction, playerLocation, playerLocationmdf)) {
      emulate(interaction);
    } else {
      interactionMeta.interactionList.add(interaction);
      event.setCancelled(true);
      if(playerDigType == START_DESTROY_BLOCK) {
        interactionMeta.remainingBlockStart++;
      }
    }
    if(breakBlock || playerDigType == ABORT_DESTROY_BLOCK) {
      interactionMeta.isBreakingBlock = false;
    } else if(playerDigType == START_DESTROY_BLOCK) {
      interactionMeta.isBreakingBlock = true;
    }
  }

  @DispatchCrossCall
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    World world = player.getWorld();
    User user = userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    InteractionMeta interactionMeta = metaOf(user);
    List<Interaction> interactionList = interactionMeta.interactionList;
    if (interactionList.isEmpty()) {
      return;
    }

    Location playerLocation = new Location(world, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);

//    Location playerVerifiedLocation = new Location(world, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);

    Location playerLocationmdf = playerLocation.clone();
    playerLocationmdf.setYaw(movementData.lastRotationYaw);

    for (Interaction interaction : interactionList) {
      processTraceReport(event, interaction, playerLocation, playerLocationmdf);
    }
    interactionList.clear();
  }

  private void processTraceReport(
    PacketEvent event,
    Interaction interaction,
    Location playerLocation,
    Location playerLocationmdf
  ) {
    if (interaction.entered()) {
      return;
    }
    interaction.enter();

    World world = interaction.world();
    Player player = interaction.player();
    User user = userOf(player);
    InteractionMeta interactionMeta = metaOf(player);

    if(interaction.isStartBlockBreak()) {
      interactionMeta.remainingBlockStart--;
    }

    WrappedMovingObjectPosition raycastResult;
    WrappedMovingObjectPosition raycastResultmdf;
    try {
      raycastResult = Raytracer.blockRayTrace(player, playerLocation);
      raycastResultmdf = Raytracer.blockRayTrace(player, playerLocationmdf);
    } catch (Exception exception) {
      exception.printStackTrace();
      if(interaction.targetBlock.toLocation(world).distance(player.getLocation()) < 6) {
        emulatePacket(interaction, null, interaction.targetBlock.toLocation(world), interaction.targetBlock.toLocation(world), false, false, false);
      }
      return;
    }
    boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;

    // first raytrace check
    WrappedMovingObjectPosition firstRaytraceResult = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
    boolean hitMiss = (firstRaytraceResult == null || firstRaytraceResult.hitVec == WrappedVector.ZERO);
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : firstRaytraceResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    Location targetLocation = interaction.targetBlock.toLocation(world);
    boolean invalid = hitMiss ||
      raycastLocation.distance(targetLocation) > 0 ||
      interaction.targetDirection != firstRaytraceResult.sideHit.getIndex();

    // if first raytrace failed..
    if (invalid) {
      // ..try again with mouse delay fix toggled differently
      WrappedMovingObjectPosition secondRaytraceResult = estimateMouseDelayFix ? raycastResult : raycastResultmdf;
      boolean hitMiss2 = secondRaytraceResult == null || secondRaytraceResult.hitVec == WrappedVector.ZERO;
      WrappedBlockPosition raycastVector2 = hitMiss2 ? WrappedBlockPosition.ORIGIN : secondRaytraceResult.getBlockPos();
      Location raycastLocation2 = raycastVector2.toLocation(world);
      invalid = hitMiss2 ||
        raycastLocation2.distance(targetLocation) > 0 ||
        interaction.targetDirection != secondRaytraceResult.sideHit.getIndex();
      interactionMeta.estimateMouseDelayFix = invalid == interactionMeta.estimateMouseDelayFix;
    }
    if (!invalid) {
      decrementer.decrement(user, 0.25);
    }
    boolean atLeastLookingAtBlock = false;
    WrappedMovingObjectPosition movingObjectPosition = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
    if(movingObjectPosition != null && invalid) {
      WrappedVector hitVec = movingObjectPosition.hitVec;
      WrappedAxisAlignedBB targetBlockBox = new WrappedAxisAlignedBB(
        targetLocation.getBlockX(),
        targetLocation.getBlockY(),
        targetLocation.getBlockZ(),
        targetLocation.getBlockX() + 1,
        targetLocation.getBlockY() + 1,
        targetLocation.getBlockZ() + 1
      ).grow(0.1);
      Location location = estimateMouseDelayFix ? playerLocationmdf : playerLocation;
      WrappedVector origin = Raytracer.resolvePositionEyes(location, location, user.meta().movementData().eyeHeight(), 1f);
      WrappedVector directionVector = hitVec.subtract(origin).normalize().scale(0.2);
      WrappedVector itrVector = origin.scale(1);
      if(targetBlockBox.isVecInside(hitVec)) {
        atLeastLookingAtBlock = true;
      } else {
        int i = 0;
        while (origin.distanceTo(itrVector) < 4 && i < 50) {
          itrVector = itrVector.add(directionVector);
          if(targetBlockBox.isVecInside(itrVector)) {
            atLeastLookingAtBlock = true;
            break;
          }
          i++;
        }
      }
    }

    if(!invalid) {
      boolean emulationFailed = emulate(interaction);
      if(emulationFailed) {
        emulatePacket(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, true, true);
        return;
      }
    }

    boolean flag = enabled && invalid && !interaction.ignoreFlags && performFlag(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, atLeastLookingAtBlock);
    emulatePacket(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, flag, false);
  }

  private boolean emulate(Interaction interaction) {
    Player player = interaction.player();
    boolean emulationFailed = false;
    if (interaction.type == InteractionType.PLACE) {
      emulationFailed = !emulatePlacement(player, interaction);
    } else if (interaction.type == InteractionType.INTERACT) {
      emulationFailed = !emulateInteraction(player, interaction);
    } else if (interaction.type == InteractionType.BREAK) {
      emulationFailed = !emulateBreak(player, interaction);
    }
    return emulationFailed;
  }

  private boolean prevalidateInteraction(Interaction interaction, Location playerLocation, Location playerLocationmdf) {
    World world = interaction.world();
    Player player = interaction.player();
    InteractionMeta interactionMeta = metaOf(player);
    WrappedMovingObjectPosition raycastResult;
    WrappedMovingObjectPosition raycastResultmdf;
    try {
      raycastResult = Raytracer.blockRayTrace(player, playerLocation);
      raycastResultmdf = Raytracer.blockRayTrace(player, playerLocationmdf);
    } catch (Exception exception) {
      exception.printStackTrace();
      return interaction.targetBlock.toLocation(world).distance(player.getLocation()) < 6;
    }
    boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;

    // first raytrace check
    WrappedMovingObjectPosition firstRaytraceResult = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
    boolean hitMiss = (firstRaytraceResult == null || firstRaytraceResult.hitVec == WrappedVector.ZERO);
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : firstRaytraceResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    Location targetLocation = interaction.targetBlock.toLocation(world);
    boolean invalid = hitMiss ||
      raycastLocation.distance(targetLocation) > 0 ||
      interaction.targetDirection != firstRaytraceResult.sideHit.getIndex();

    // if first raytrace failed..
    if (invalid) {
      // ..try again with mouse delay fix toggled differently
      WrappedMovingObjectPosition secondRaytraceResult = estimateMouseDelayFix ? raycastResult : raycastResultmdf;
      boolean hitMiss2 = secondRaytraceResult == null || secondRaytraceResult.hitVec == WrappedVector.ZERO;
      WrappedBlockPosition raycastVector2 = hitMiss2 ? WrappedBlockPosition.ORIGIN : secondRaytraceResult.getBlockPos();
      Location raycastLocation2 = raycastVector2.toLocation(world);
      invalid = hitMiss2 ||
        raycastLocation2.distance(targetLocation) > 0 ||
        interaction.targetDirection != secondRaytraceResult.sideHit.getIndex();
    }
    return !invalid;
  }

  private boolean emulatePlacement(Player player, Interaction interaction) {
    User user = userOf(player);
    World world = interaction.world();
    Location blockAgainstLocation = interaction.targetBlock.toLocation(world);
    Location defaultPlacementLocation = blockAgainstLocation.clone().add(WrappedEnumDirection.getFront(interaction.targetDirection).getDirectionVec().convertToBukkitVec());
    boolean replace = BlockDataAccess.replacementPlace(world, player, new BlockPosition(blockAgainstLocation.toVector()));
    Location blockPlacementLocation = replace ? blockAgainstLocation : defaultPlacementLocation;
    Material itemTypeInHand = interaction.itemTypeInHand;
    int blockX = blockPlacementLocation.getBlockX();
    int blockY = blockPlacementLocation.getBlockY();
    int blockZ = blockPlacementLocation.getBlockZ();
    int dat = 0;
    boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
      user, world, blockX, blockY, blockZ, itemTypeInHand, dat
    );
    if(raytraceCollidesWithPosition) {
      return false;
    }
    Material replacementType = interaction.itemTypeInHand;
    byte shape = 0;
    boolean access = WorldPermission.blockPlacePermission(
      player, world,
      interaction.hand == null || interaction.hand == EnumWrappers.Hand.MAIN_HAND,
      blockX, blockY, blockZ, interaction.targetDirection, replacementType,
      shape
    );
    if(access) {
      OCBlockShapeAccess blockShapeAccess = userOf(player).blockShapeAccess();
      blockShapeAccess.override(world, blockX, blockY, blockZ, replacementType, shape);
      // enforce block reset later
      Synchronizer.packetSynchronize(() -> {
        Synchronizer.synchronize(() -> blockShapeAccess.invalidateOverride(blockX, blockY, blockZ));
      });
    } else {
      return false;
    }
    return true;
  }

  @BukkitEventSubscription(ignoreCancelled = true)
  public void onPre(BlockPlaceEvent place) {
    if(place.getClass().equals(BlockPlaceEvent.class)) {
      Block block = place.getBlock();
      OCBlockShapeAccess blockShapeAccess = userOf(place.getPlayer()).blockShapeAccess();
      blockShapeAccess.invalidate(block.getX(), block.getY(), block.getZ());
      blockShapeAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
    }
  }

  private boolean emulateInteraction(Player player, Interaction interaction) {
    World world = interaction.world();
    Location clickedBlockLocation = interaction.targetBlock.toLocation(world);
    Block clickedBlock = BukkitBlockAccess.blockAccess(clickedBlockLocation);
    Material itemTypeInHand = interaction.itemTypeInHand;
    Location placementLocation = clickedBlockLocation.clone().add(WrappedEnumDirection.getFront(interaction.targetDirection).getDirectionVec().convertToBukkitVec());
    emulateInteractWithHandItem(player, clickedBlock, placementLocation, itemTypeInHand);
    emulatePhysicalInteract(player, clickedBlock);
    return true;
  }

  private void emulateInteractWithHandItem(
    Player player,
    Block clickedBlock,
    Location placementLocation,
    Material itemTypeInHand
  ) {
    OCBlockShapeAccess blockShapeAccess = userOf(player).blockShapeAccess();
    World world = player.getWorld();
    Material placementType = placementLocation.getBlock().getType();
    switch (itemTypeInHand) {
      case BUCKET: {
        // remove liquid on location if exists
        if(MaterialLogic.isLiquid(placementType)) {
          // emulate
          if (WorldPermission.bukkitActionPermission(player, BucketAction.FILL_BUCKET, clickedBlock, BlockFace.SELF, itemTypeInHand, null)) {
            blockShapeAccess.override(world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(), Material.AIR, 0);
          }
        }
        break;
      }
      case WATER_BUCKET:
      case LAVA_BUCKET: {
        // emulate
        if (WorldPermission.bukkitActionPermission(player, BucketAction.EMPTY_BUCKET, clickedBlock, BlockFace.SELF, itemTypeInHand, null)) {
          blockShapeAccess.override(world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(), itemTypeInHand == Material.WATER_BUCKET ? Material.WATER : Material.LAVA, 15);
        }
        break;
      }
    }
  }

  private void emulatePhysicalInteract(Player player, Block block) {
    World world = player.getWorld();
    OCBlockShapeAccess blockShapeAccess = userOf(player).blockShapeAccess();
    Material clickedType = block.getType();
    switch (clickedType) {
      case ACACIA_DOOR:
      case DARK_OAK_DOOR:
      case BIRCH_DOOR:
      case JUNGLE_DOOR:
      case WOOD_DOOR:
      case WOODEN_DOOR: {
        int upperData = BlockDataAccess.dataIndexOf(block);
        int lowerData;

        boolean isUpper = (upperData & 8) != 0;
        if(isUpper) {
          lowerData = BlockDataAccess.dataIndexOf(block = block.getRelative(BlockFace.DOWN));
        } else {
          lowerData = upperData;
          upperData = BlockDataAccess.dataIndexOf(block.getRelative(BlockFace.UP));
        }

        // toggle close
        lowerData = (lowerData & 4) != 0 ? lowerData ^ 4 : lowerData | 4;

        blockShapeAccess.override(world, block.getX(), block.getY(), block.getZ(), clickedType, lowerData);
        blockShapeAccess.override(world, block.getX(), block.getY() + 1, block.getZ(), clickedType, upperData);

        Block finalBlock = block;
        Synchronizer.packetSynchronize(() -> {
          blockShapeAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY(), finalBlock.getZ());
          blockShapeAccess.invalidateOverride(finalBlock.getX(), finalBlock.getY() + 1, finalBlock.getZ());
        });
        break;
      }
      case ACACIA_FENCE_GATE:
      case BIRCH_FENCE_GATE:
      case DARK_OAK_FENCE_GATE:
      case FENCE_GATE:
      case JUNGLE_FENCE_GATE:
      case SPRUCE_FENCE_GATE: {
        //TODO
        break;
      }
      case TRAP_DOOR: {
        int data = BlockDataAccess.dataIndexOf(block);
        boolean newOpen = (data & 4) != 0;
        int bitMask = 4;
        byte newData = (byte) (!newOpen ? (data | bitMask) : (data & ~bitMask));
        Material material = block.getType();
        blockShapeAccess.override(world, block.getX(), block.getY(), block.getZ(), material, newData);
        Block finalBlock1 = block;
        Synchronizer.packetSynchronize(() -> blockShapeAccess.invalidateOverride(finalBlock1.getX(), finalBlock1.getY(), finalBlock1.getZ()));
        break;
      }
    }
  }

  @BukkitEventSubscription
  public void on(PlayerBucketFillEvent fill) {
    Player player = fill.getPlayer();
    Block block = fill.getBlockClicked().getRelative(fill.getBlockFace());
    OCBlockShapeAccess blockShapeAccess = userOf(player).blockShapeAccess();
    blockShapeAccess.invalidate(block.getX(), block.getY(), block.getZ());
    blockShapeAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  @BukkitEventSubscription
  public void on(PlayerBucketEmptyEvent empty) {
    Player player = empty.getPlayer();
    Block block = empty.getBlockClicked().getRelative(empty.getBlockFace());
    OCBlockShapeAccess blockShapeAccess = userOf(player).blockShapeAccess();
    blockShapeAccess.invalidate(block.getX(), block.getY(), block.getZ());
    blockShapeAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  private boolean emulateBreak(Player player, Interaction interaction) {
    World world = interaction.world();
    BlockPosition blockPosition = interaction.targetBlock;
    Location blockBreakLocation = blockPosition.toLocation(world);
    boolean access = WorldPermission.blockBreakPermission(
      player, BukkitBlockAccess.blockAccess(blockBreakLocation)
    );
    if(access) {
      int blockX = blockBreakLocation.getBlockX();
      int blockY = blockBreakLocation.getBlockY();
      int blockZ = blockBreakLocation.getBlockZ();
      // add to future bounding boxes
      OCBlockShapeAccess blockShapeAccess = userOf(player).blockShapeAccess();
      blockShapeAccess.override(world, blockX, blockY, blockZ, Material.AIR, (byte) 0);

//      player.sendMessage("Cleared " + blockX + " " + blockY + " " + blockZ + " with AIR");
    }
    return access;
  }

  @BukkitEventSubscription(ignoreCancelled = true)
  public void onPre(BlockBreakEvent breeak) {
    Block block = breeak.getBlock();
    OCBlockShapeAccess blockShapeAccess = userOf(breeak.getPlayer()).blockShapeAccess();
    blockShapeAccess.invalidate(block.getX(), block.getY(), block.getZ());
    blockShapeAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  private void emulatePacket(
    Interaction interaction,
    WrappedMovingObjectPosition raycastResult,
    Location targetLocation,
    Location raycastLocation,
    boolean hitMiss,
    boolean punishment,
    boolean enforceCancel
  ) {
    Player player = interaction.player();
    User user = userOf(player);
    ResponseType response = interaction.type().response();
    OCBlockShapeAccess blockShapeAccess = user.blockShapeAccess();
    if (enforceCancel) {
      response = ResponseType.CANCEL;
    }
    if(user.meta().movementData().awaitTeleport) {
      punishment = true;
    }
    boolean canRefreshBlocks = interaction.type != InteractionType.INTERACT;
//    interaction.player().sendMessage((punishment ? ChatColor.RED : ChatColor.GREEN) + "" + interaction.type + ": " + ChatColor.GRAY + response + "/" + canRefreshBlocks);
    if (response == ResponseType.RAYTRACE_CAST) {
      if (hitMiss || raycastResult == null) {
        if (canRefreshBlocks) {
          refreshBlocksAround(player, targetLocation);
          blockShapeAccess.invalidateOverride(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
        }
      } else {
        PacketContainer packet = interaction.thePacket();
        if (punishment) {
          // check if player collides with placement location
          {
            World world = player.getWorld();
            Material material = user.meta().inventoryData().heldItemType();
            int dat = 0;
            boolean replace = BlockDataAccess.replacementPlace(world, player, new BlockPosition(raycastLocation.toVector()));
            Location placementLocation = replace ? raycastLocation : raycastLocation.clone().add(raycastResult.sideHit.getDirectionVec().convertToBukkitVec());
            boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
              user, world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(),
              material, dat
            );
            if (raytraceCollidesWithPosition) {
              refreshBlocksAround(player, targetLocation);
              blockShapeAccess.invalidateOverride(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
              return;
            }
          }
          writeEnumDirection(packet, raycastResult.sideHit);
          BlockPosition value = new BlockPosition(raycastLocation.getBlockX(), raycastLocation.getBlockY(), raycastLocation.getBlockZ());
          writeBlockPosition(packet, value);
        }
        receiveExcludedPacket(player, packet);
        if (canRefreshBlocks && punishment) {
          Synchronizer.synchronize(() -> refreshBlocksAround(player, targetLocation));
        }
      }
    } else {
      if (punishment) {
        if (canRefreshBlocks) {
          refreshBlocksAround(player, targetLocation);
        }
      } else {
        receiveExcludedPacket(player, interaction.thePacket());
      }
    }
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    Synchronizer.synchronize(() -> {
      player.updateInventory();
      refreshBlock(player, targetLocation);
      for (WrappedEnumDirection direction : WrappedEnumDirection.values()) {
        Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
        refreshBlock(player, placedBlock);
      }
    });
  }

  private boolean performFlag(
    Interaction interaction,
    WrappedMovingObjectPosition raycastResult,
    Location targetLocation,
    Location raycastLocation,
    boolean hitMiss,
    boolean lookingAtBlock
  ) {
    Player player = interaction.player();
    User user = userOf(player);
    InteractionType type = interaction.type();
    Block targetLocationBlock = BukkitBlockAccess.blockAccess(targetLocation);
    Block raycastLocationBlock = BukkitBlockAccess.blockAccess(raycastLocation);
    if (targetLocationBlock.getType() == Material.AIR || raycastLocationBlock.getType() == Material.AIR) {
      return true;
    }
    double vl = 0;
    boolean mustFlag = false;
    String message, details;
    if (type == InteractionType.BREAK) {
      boolean longBreakDuration = BlockDataAccess.blockDamage(player, user.meta().inventoryData().heldItem(), interaction.targetBlock) < 0.8;
      String typeName = shortenTypeName(targetLocationBlock.getType());
      String append = "";
      if (hitMiss || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
        append = "looking in air";
        vl = longBreakDuration ? 20 : 5;
      } else if (raycastLocation.distance(targetLocation) > 0 && raycastLocationBlock.getType() != Material.AIR) {
        String blockName = shortenTypeName(raycastLocationBlock.getType());
        if (raycastLocationBlock.getType() == targetLocationBlock.getType()) {
          blockName = "a different " + blockName;
        }
        append = "but looking at " + blockName + " block";
        vl = longBreakDuration ? 20 : 5;
      } else if (interaction.targetDirection != raycastResult.sideHit.getIndex()) {
        append = "invalid block face";
        vl = longBreakDuration ? 20 : 15;
      }
      float blockDamage = BlockDataAccess.blockDamage(player, user.meta().inventoryData().heldItem(), interaction.targetBlock);
      boolean instantBreak = blockDamage == Float.POSITIVE_INFINITY || blockDamage >= 1.0f || user.meta().abilityData().inGameMode(PlayerAbilityEvaluator.GameMode.CREATIVE);
      if(instantBreak) {
        vl = 0;
      }
      message = "performed invalid break";// +" -" + append;
      details = typeName + " block, " + append;
      mustFlag = true;
    } else if (type == InteractionType.PLACE) {
      String typeAgainstName = shortenTypeName(targetLocationBlock.getType());
      String typeName = shortenTypeName(user.meta().inventoryData().heldItemType());

      String append = "";
      if (hitMiss || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
        append = "looking in air";
        vl = 5;
      } else if (raycastLocation.distance(targetLocation) > 0 && raycastLocationBlock.getType() != Material.AIR) {
        String blockName = shortenTypeName(raycastLocationBlock.getType());
        if (raycastLocationBlock.getType() == targetLocationBlock.getType()) {
          blockName = "a different " + blockName;
        }
        append = "looking at " + blockName + " block";
        vl = 2.5;
      } else if (interaction.targetDirection != raycastResult.sideHit.getIndex()) {
        append = "invalid block face";
        vl = 2.5;
      }
      if(lookingAtBlock) {
        double multiplier = trustFactorSetting("k-multiplier", player) / 100d;
        vl *= multiplier;
      }
      message = "performed invalid placement";
      details = typeName + " block on " + typeAgainstName + " block, " + append;
    } else {
      String typeAgainstName = shortenTypeName(targetLocationBlock.getType());
      message = "invalid interaction";
      details = typeAgainstName + " block";
      mustFlag = true;
      vl = 0;
    }
    if(user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      mustFlag = false;
    }
    if(user.meta().movementData().awaitTeleport) {
      mustFlag = true;
    }
    Violation violation = Violation.builderFor(InteractionRaytrace.class)
      .forPlayer(player).withMessage(message).withDetails(details).withVL(vl)
      .build();
    ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);
    return violationContext.shouldCounterThreat() || mustFlag;
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    Block block = BukkitBlockAccess.blockAccess(location);
    WrappedBlockData blockData = WrappedBlockData.createData(block.getType(), BlockDataAccess.dataIndexOf(block));
    BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockData().write(0, blockData);
    packet.getBlockPositionModifier().write(0, position);
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    } catch (InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    try {
      userOf(player).ignoreNextPacket();
      ProtocolLibrary.getProtocolManager().recieveClientPacket(player, packet);
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  @Override
  public boolean enabled() {
    this.enabled = super.enabled();
    return true;
  }

  private final static boolean BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION = MinecraftVersions.VER1_13_0.atOrAbove();

  private BlockPosition readBlockPositionFrom(PacketContainer packet) {
    if(BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION) {
      MovingObjectPositionBlock movingObjectPositionBlock = packet.getMovingBlockPositions().readSafely(0);
      return movingObjectPositionBlock == null ? null : movingObjectPositionBlock.getBlockPosition();
    } else {
      return packet.getBlockPositionModifier().readSafely(0);
    }
  }

  private void writeBlockPosition(PacketContainer packet, BlockPosition blockPosition) {
    if(BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION) {
      MovingObjectPositionBlock movingObjectPositionBlock = packet.getMovingBlockPositions().readSafely(0);
      movingObjectPositionBlock.setBlockPosition(blockPosition);
    } else {
      packet.getBlockPositionModifier().write(0, blockPosition);
    }
  }

  private int readEnumDirectionFrom(PacketContainer packet) {
    if(BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION) {
      MovingObjectPositionBlock movingObjectPositionBlock = packet.getMovingBlockPositions().readSafely(0);
      return movingObjectPositionBlock == null ? 255 : movingObjectPositionBlock.getDirection().ordinal();
    } else {
      Integer enumDirection = packet.getIntegers().readSafely(0);
      return enumDirection == null ? packet.getDirections().readSafely(0).ordinal() : enumDirection;
    }
  }

  private void writeEnumDirection(PacketContainer packet, WrappedEnumDirection enumDirection) {
    if(BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION) {
      MovingObjectPositionBlock movingObjectPositionBlock = packet.getMovingBlockPositions().readSafely(0);
      movingObjectPositionBlock.setDirection(enumDirection.toDirection());
    } else {
      if (packet.getDirections().size() > 0) {
        packet.getDirections().write(0, enumDirection.toDirection());
      } else {
        packet.getIntegers().write(0, enumDirection.getIndex());
      }
    }
  }

  private Vector resolvePositionMotion(User user, MotionVector vector) {
    UserMetaMovementData movementData = user.meta().movementData();
    return new Vector(
      movementData.positionX + vector.motionX,
      movementData.positionY + vector.motionY,
      movementData.positionZ + vector.motionZ
    );
  }

  public enum InteractionType {
    BREAK(ResponseType.CANCEL, false),
    INTERACT(ResponseType.RAYTRACE_CAST, false),
    PLACE(ResponseType.RAYTRACE_CAST, false);

    final ResponseType response;
    final boolean bufferAvailable;

    InteractionType(ResponseType response, boolean bufferAvailable) {
      this.response = response;
      this.bufferAvailable = bufferAvailable;
    }

    public ResponseType response() {
      return response;
    }
  }

  public enum ResponseType {
    RAYTRACE_CAST,
    CANCEL
  }

  public static class Interaction {
    private final World world;
    private final Player player;
    private final BlockPosition targetBlock;
    private final int targetDirection;
    private final PacketContainer thePacket;
    private final InteractionType type;
    private final Vector facingVector;
    private final Material itemTypeInHand;
    private final EnumWrappers.Hand hand;
    private boolean ignoreFlags;
    private boolean isStartBlockBreak;
    private boolean entered = false;
    private boolean changeLocked = true;

    public Interaction(
      World world, Player player,
      BlockPosition targetBlock,
      int targetDirection,
      PacketContainer thePacket,
      InteractionType type,
      Vector facingVector, Material itemTypeInHand, EnumWrappers.Hand hand,
      boolean ignoreFlags,
      boolean isStartBlockBreak) {
      this.world = world;
      this.player = player;
      this.targetBlock = targetBlock;
      this.targetDirection = targetDirection;
      this.thePacket = thePacket;
      this.type = type;
      this.facingVector = facingVector;
      this.itemTypeInHand = itemTypeInHand;
      this.hand = hand;
      this.ignoreFlags = ignoreFlags;
      this.isStartBlockBreak = isStartBlockBreak;
    }

    public PacketContainer thePacket() {
      return thePacket;
    }

    public InteractionType type() {
      return type;
    }

    public Vector facingVector() {
      return facingVector;
    }

    public World world() {
      return world;
    }

    public Player player() {
      return player;
    }

    public Material itemTypeInHand() {
      return itemTypeInHand;
    }

    public EnumWrappers.Hand hand() {
      return hand;
    }

    public void unlock() {
      changeLocked = false;
    }

    public boolean isChangeLocked() {
      return changeLocked;
    }

    public boolean isIgnoreFlags() {
      return ignoreFlags;
    }

    public void enter() {
      entered = true;
    }

    public boolean entered() {
      return entered;
    }

    public boolean isStartBlockBreak() {
      return isStartBlockBreak;
    }

    @Override
    public String toString() {
      return "Interaction{" +
        "targetBlock=" + targetBlock +
        ", targetDirection=" + targetDirection +
        ", type=" + type +
        ", facingVector=" + facingVector +
        ", itemTypeInHand=" + itemTypeInHand +
        ", hand=" + hand +
        ", entered=" + entered +
        '}';
    }
  }

  public static class InteractionMeta extends UserCustomCheckMeta {
    final List<Interaction> interactionList = new CopyOnWriteArrayList<>();
    public long lastPlacement;
    public boolean estimateMouseDelayFix = false;
    public boolean isBreakingBlock = false;
    public long remainingBlockStart = 0;
  }
}
