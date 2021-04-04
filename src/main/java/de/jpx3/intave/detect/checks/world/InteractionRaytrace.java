package de.jpx3.intave.detect.checks.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.event.BucketAction;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.dispatch.PlayerAbilityEvaluator;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
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
import de.jpx3.intave.world.collision.BoundingBoxAccess;
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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK;
import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;

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
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    if (blockPosition == null || movementData.inVehicle()) {
      return;
    }
    Integer enumDirection = packet.getIntegers().readSafely(0);
    if (enumDirection == null) {
      enumDirection = packet.getDirections().readSafely(0).ordinal();
    }
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
    Location playerLocation = movementData.verifiedLocation().clone();
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);
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
      false
    );
    interactionMeta.interactionList.add(interaction);
    event.setCancelled(true);
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
    BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    User user = userOf(player);
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
    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    int enumDirection = direction == null ? 255 : direction.ordinal();
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
      playerDigType == ABORT_DESTROY_BLOCK
    );
    interactionMeta.interactionList.add(interaction);
    event.setCancelled(true);
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
    WrappedMovingObjectPosition raycastResult = Raytracer.blockRayTrace(player, playerLocation);
    WrappedMovingObjectPosition raycastResultmdf = Raytracer.blockRayTrace(player, playerLocationmdf);
    boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;

    // first raytrace check
    WrappedMovingObjectPosition firstRaytraceResult = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
    boolean hitMiss = (firstRaytraceResult == null || firstRaytraceResult.hitVec == WrappedVector.ZERO);
    WrappedBlockPosition raycastVector = hitMiss ? WrappedBlockPosition.ORIGIN : firstRaytraceResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    Location targetLocation = interaction.targetBlock.toLocation(world);
    boolean invalidFacing = interaction.facingVector() != null &&
      firstRaytraceResult != null &&
      validateFacingVector(interaction.targetBlock, firstRaytraceResult.hitVec, interaction.facingVector(), interaction.type());
    boolean invalid = hitMiss ||
      raycastLocation.distance(targetLocation) > 0 ||
      interaction.targetDirection != firstRaytraceResult.sideHit.getIndex() ||
      invalidFacing;

    // if first raytrace failed..
    if (invalid) {
      // ..try again with mouse delay fix toggled differently
      WrappedMovingObjectPosition secondRaytraceResult = estimateMouseDelayFix ? raycastResult : raycastResultmdf;
      boolean hitMiss2 = secondRaytraceResult == null || secondRaytraceResult.hitVec == WrappedVector.ZERO;
      WrappedBlockPosition raycastVector2 = hitMiss2 ? WrappedBlockPosition.ORIGIN : secondRaytraceResult.getBlockPos();
      Location raycastLocation2 = raycastVector2.toLocation(world);
      invalidFacing = interaction.facingVector() != null &&
        secondRaytraceResult != null &&
        validateFacingVector(interaction.targetBlock, secondRaytraceResult.hitVec, interaction.facingVector(), interaction.type());
      invalid = hitMiss2 ||
        raycastLocation2.distance(targetLocation) > 0 ||
        interaction.targetDirection != secondRaytraceResult.sideHit.getIndex() ||
        invalidFacing;
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
      WrappedVector origin = Raytracer.resolvePositionEyes(location, location, 1.0f, user.meta().movementData().eyeHeight());
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
    boolean emulationFailed = false;
    /* emulate placement */
    if(interaction.type == InteractionType.PLACE && !invalid) {
      emulationFailed = !emulatePlacement(player, interaction);
    } else if(interaction.type == InteractionType.INTERACT && !invalid) {
      emulationFailed = !emulateInteraction(player, interaction);
    } else if(interaction.type == InteractionType.BREAK && !invalid) {
      emulationFailed = !emulateBreak(player, interaction);
    }
    if(emulationFailed) {
      emulatePacket(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, true, true);
      return;
    }
    boolean flag = invalid && !interaction.ignoreFlags && performFlag(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, atLeastLookingAtBlock);
    emulatePacket(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, flag, invalidFacing);
  }

  private boolean emulatePlacement(Player player, Interaction interaction) {
    User user = userOf(player);
    World world = interaction.world();
    Location blockAgainstLocation = interaction.targetBlock.toLocation(world);
    Location defaultPlacementLocation = blockAgainstLocation.clone().add(WrappedEnumDirection.getFront(interaction.targetDirection).getDirectionVec().convertToBukkitVec());
    boolean replace = BlockDataAccess.replacementPlace(world, new BlockPosition(blockAgainstLocation.toVector()));
    Location blockPlacementLocation = replace ? blockAgainstLocation : defaultPlacementLocation;
    Material itemTypeInHand = interaction.itemTypeInHand;
    int blockX = blockPlacementLocation.getBlockX();
    int blockY = blockPlacementLocation.getBlockY();
    int blockZ = blockPlacementLocation.getBlockZ();
    int dat = 0;
    boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
      user, world, blockX, blockY, blockZ,
      itemTypeInHand,
      dat
    );
    if(raytraceCollidesWithPosition) {
      return false;
    }
    Material replacementType = interaction.itemTypeInHand;
    byte shape = 0;
    boolean access = WorldPermission.blockPlacePermission(
      player,
      world,
      interaction.hand == null || interaction.hand == EnumWrappers.Hand.MAIN_HAND,
      blockX, blockY, blockZ,
      interaction.targetDirection,
      replacementType,
      (byte) 0
    );
    if(access) {
      BoundingBoxAccess boundingBoxAccess = userOf(player).boundingBoxAccess();
      boundingBoxAccess.override(world, blockX, blockY, blockZ, replacementType, shape);
      // enforce block reset later
      Synchronizer.packetSynchronize(() -> {
        Synchronizer.synchronize(() -> boundingBoxAccess.invalidateOverride(blockX, blockY, blockZ));
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
      BoundingBoxAccess boundingBoxAccess = userOf(place.getPlayer()).boundingBoxAccess();
      boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
      boundingBoxAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
    }
  }

  private boolean emulateInteraction(Player player, Interaction interaction) {
    World world = interaction.world();
    Location clickedBlockLocation = interaction.targetBlock.toLocation(world);
    Block clickedBlock = BukkitBlockAccess.blockAccess(clickedBlockLocation);
    Material clickedType = clickedBlock.getType();
    Material itemTypeInHand = interaction.itemTypeInHand;
    BoundingBoxAccess boundingBoxAccess = userOf(player).boundingBoxAccess();
    Location placementLocation = clickedBlockLocation.clone().add(WrappedEnumDirection.getFront(interaction.targetDirection).getDirectionVec().convertToBukkitVec());
    Material placementType = placementLocation.getBlock().getType();
    if(itemTypeInHand == Material.BUCKET) {
      // remove liquid on location if exists
      if(MaterialLogic.isLiquid(placementType)) {
        // emulate
        if (WorldPermission.bukkitActionPermission(player, BucketAction.FILL_BUCKET, clickedBlock, BlockFace.SELF, itemTypeInHand, null)) {
          boundingBoxAccess.override(world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(), Material.AIR, 0);
        }
      }
    } else if(itemTypeInHand == Material.WATER_BUCKET || itemTypeInHand == Material.LAVA_BUCKET) {
      // emulate
      if (WorldPermission.bukkitActionPermission(player, BucketAction.EMPTY_BUCKET, clickedBlock, BlockFace.SELF, itemTypeInHand, null)) {
        boundingBoxAccess.override(world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(), itemTypeInHand == Material.WATER_BUCKET ? Material.WATER : Material.LAVA, 15);
      }
    }
    if(clickedType == Material.WOODEN_DOOR) {

    } else if(clickedType == Material.TRAP_DOOR) {
      int data = clickedBlock.getData();
      boolean newOpen = (data & 4) != 0;
      int bitMask = 4;
      byte newData = (byte) (!newOpen ? (data | bitMask) : (data & ~bitMask));
      Material material = clickedBlock.getType();
      boundingBoxAccess.override(world, clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ(), material, newData);
      Synchronizer.packetSynchronize(() ->
        boundingBoxAccess.invalidateOverride(clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ()));
    }
    return true;
  }

  @BukkitEventSubscription
  public void on(PlayerBucketFillEvent fill) {
    Player player = fill.getPlayer();
    Block block = fill.getBlockClicked().getRelative(fill.getBlockFace());
    BoundingBoxAccess boundingBoxAccess = userOf(player).boundingBoxAccess();
    boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
    boundingBoxAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  @BukkitEventSubscription
  public void on(PlayerBucketEmptyEvent empty) {
    Player player = empty.getPlayer();
    Block block = empty.getBlockClicked().getRelative(empty.getBlockFace());
    BoundingBoxAccess boundingBoxAccess = userOf(player).boundingBoxAccess();
    boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
    boundingBoxAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
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
      BoundingBoxAccess boundingBoxAccess = userOf(player).boundingBoxAccess();
      boundingBoxAccess.override(world, blockX, blockY, blockZ, Material.AIR, (byte) 0);
    }
    return access;
  }

  @BukkitEventSubscription(ignoreCancelled = true)
  public void onPre(BlockBreakEvent breeak) {
    Block block = breeak.getBlock();
    BoundingBoxAccess boundingBoxAccess = userOf(breeak.getPlayer()).boundingBoxAccess();
    boundingBoxAccess.invalidate(block.getX(), block.getY(), block.getZ());
    boundingBoxAccess.invalidateOverride(block.getX(), block.getY(), block.getZ());
  }

  private boolean validateFacingVector(BlockPosition blockPosition, WrappedVector hitVec, Vector sent, InteractionType interactionType) {
    return false;
  }

  private void emulatePacket(
    Interaction interaction,
    WrappedMovingObjectPosition raycastResult,
    Location targetLocation,
    Location raycastLocation,
    boolean hitMiss,
    boolean punishment,
    boolean invalidFacing
  ) {

    Player player = interaction.player();
    User user = userOf(player);
    ResponseType response = interaction.type().response();
    BoundingBoxAccess boundingBoxAccess = user.boundingBoxAccess();
    if (invalidFacing) {
      response = ResponseType.CANCEL;
    }
    boolean canRefreshBlocks = interaction.type != InteractionType.INTERACT;
    if (response == ResponseType.RAYTRACE_CAST) {
      if (hitMiss || raycastResult == null) {
        if (canRefreshBlocks) {
          refreshBlocksAround(player, targetLocation);
          boundingBoxAccess.invalidateOverride(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
        }
      } else {
        // check if player collides with placement location
        if (canRefreshBlocks) {
          World world = player.getWorld();
          Material material = user.meta().inventoryData().heldItemType();
          int dat = 0;
          boolean replace = BlockDataAccess.replacementPlace(world, new BlockPosition(raycastLocation.toVector()));
          Location placementLocation = replace ? raycastLocation : raycastLocation.clone().add(raycastResult.sideHit.getDirectionVec().convertToBukkitVec());
          boolean raytraceCollidesWithPosition = Collision.playerInImaginaryBlock(
            user, world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(),
            material, dat
          );
          if (raytraceCollidesWithPosition) {
            refreshBlocksAround(player, targetLocation);
            boundingBoxAccess.invalidateOverride(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
            return;
          }
        }
        PacketContainer packet = interaction.thePacket();
        if (punishment) {
          if (packet.getDirections().size() > 0) {
            packet.getDirections().write(0, raycastResult.sideHit.toDirection());
          } else {
            packet.getIntegers().write(0, raycastResult.sideHit.getIndex());
          }
          packet.getBlockPositionModifier().write(
            0,
            new BlockPosition(raycastLocation.getBlockX(), raycastLocation.getBlockY(), raycastLocation.getBlockZ())
          );
        }
        receiveExcludedPacket(player, packet);
        if (canRefreshBlocks) {
          Synchronizer.synchronize(() -> refreshBlocksAround(player, targetLocation));
        }
      }
    } else {
      if (punishment) {
        if (canRefreshBlocks) {
          refreshBlocksAround(player, targetLocation);
        }
      } else {
        receiveExcludedPacket(player, interaction.thePacket);
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
        append = "looking at " + blockName + " block";
        vl = longBreakDuration ? 20 : 5;
      } else if (interaction.targetDirection != raycastResult.sideHit.getIndex()) {
        append = "invalid block face";
        vl = longBreakDuration ? 20 : 15;
      }
      message = "performed invalid break";// +" -" + append;
      details = typeName + " block, " + append;
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
        vl *= 0.5;
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
    return plugin.violationProcessor().processViolation(player, vl, name(), message, details) || mustFlag;
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    Block block = BukkitBlockAccess.blockAccess(location);
    WrappedBlockData blockData = WrappedBlockData.createData(block.getType(), block.getData());
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

//  private Vector resolveLocationWithoutKeyPress(User user) {
//    ComplexColliderSimulationResult result = simulationProcessor.simulateMovementWithLastKeys(user);
//    return resolvePositionMotion(user, result.context());
//  }
//
//  private Vector resolvePositionWithLastKeys(User user) {
//    ComplexColliderSimulationResult result = simulationProcessor.simulateMovementWithLastKeys(user);
//    return resolvePositionMotion(user, result.context());
//  }

  private Vector resolvePositionMotion(User user, MotionVector vector) {
    UserMetaMovementData movementData = user.meta().movementData();
    return new Vector(
      movementData.positionX + vector.motionX,
      movementData.positionY + vector.motionY,
      movementData.positionZ + vector.motionZ
    );
  }

  public static class InteractionMeta extends UserCustomCheckMeta {
    final List<Interaction> interactionList = new CopyOnWriteArrayList<>();
    public long lastPlacement;
    public boolean estimateMouseDelayFix = false;
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
    private final boolean ignoreFlags;
    private boolean entered = false;
    private boolean changeLocked = true;

    public Interaction(
      World world, Player player,
      BlockPosition targetBlock,
      int targetDirection,
      PacketContainer thePacket,
      InteractionType type,
      Vector facingVector, Material itemTypeInHand, EnumWrappers.Hand hand,
      boolean ignoreFlags
    ) {
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
}
