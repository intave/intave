package de.jpx3.intave.check.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.world.interaction.Interaction;
import de.jpx3.intave.check.world.interaction.InteractionEmulator;
import de.jpx3.intave.check.world.interaction.InteractionType;
import de.jpx3.intave.executor.RateLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.converter.BlockPositionConverter;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.EntityReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.*;
import static de.jpx3.intave.check.world.interaction.InteractionEmulator.EmulationResult.FAILED;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.BLOCK_BREAK_ANIMATION;
import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.CREATIVE;

@Relocate
public final class InteractionRaytrace extends MetaCheck<InteractionRaytrace.InteractionMeta> {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;
  private final InteractionEmulator interactionEmulator;

  public InteractionRaytrace(IntavePlugin plugin) {
    super("InteractionRaytrace", "interactionraytrace", InteractionMeta.class);
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, 1);
    this.interactionEmulator = new InteractionEmulator(plugin);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      BLOCK_PLACE, USE_ITEM
    }
  )
  public void receiveInteractionAndPlace(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    InteractionMeta interactionMeta = metaOf(user);
    MovementMetadata movementData = user.meta().movement();
    AbilityMetadata abilityMetadata = user.meta().abilities();
    PacketContainer packet = event.getPacket();
    BlockInteractionReader reader = PacketReaders.readerOf(packet);
    try {
      com.comphenix.protocol.wrappers.BlockPosition blockPosition = reader.blockPosition();
      if (blockPosition == null || event.isCancelled() || movementData.isInVehicle()) {
        return;
      }
      int enumDirection = reader.enumDirection();
      if (enumDirection == 255) {
        return;
      }

      float facingX = -1;
      float facingY = -1;
      float facingZ = -1;
      StructureModifier<Float> floatsInPacket = packet.getFloat();
      if (floatsInPacket.size() >= 3 && user.meta().protocol().sendsFacings()) {
        facingX = floatsInPacket.read(0);
        facingY = floatsInPacket.read(1);
        facingZ = floatsInPacket.read(2);

        if (Float.isNaN(facingX) || Float.isNaN(facingY) || Float.isNaN(facingZ)) {
          if (MinecraftVersions.VER1_19.atOrAbove()) {
            int sequenceNumber = packet.getIntegers().read(0);
            acknowledgeBlockChange(player, sequenceNumber);
          }
          event.setCancelled(true);
          return;
        }
      }

      Material clickedType = blockPosition == null ? Material.AIR : VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(player.getWorld()));
      boolean clickedIsInteractable = BlockInteractionAccess.isClickable(clickedType);

      EnumWrappers.Hand handSlot = packet.getHands().readSafely(0);
      handSlot = handSlot == null ? EnumWrappers.Hand.MAIN_HAND : handSlot;

      ItemStack heldItem = user.meta().inventory().heldItem();
      Material heldItemType = heldItem == null ? Material.AIR : heldItem.getType();
      Material offHandItemType = user.meta().inventory().offhandItemType();
      Material typeUsedInHand = handSlot == EnumWrappers.Hand.MAIN_HAND ? heldItemType : offHandItemType;
      if (typeUsedInHand == null) {
        typeUsedInHand = Material.AIR;
      }

      boolean interactionIsPlacement = typeUsedInHand != Material.AIR
        && typeUsedInHand.isBlock()
        && !clickedIsInteractable
        && !abilityMetadata.inGameMode(GameMode.ADVENTURE);

      InteractionType type = interactionIsPlacement ? InteractionType.PLACE : InteractionType.INTERACT;

      if (IntaveControl.DEBUG_INTERACTION) {
        player.sendMessage(type + " " + typeUsedInHand + " " + enumDirection);
      }

      Interaction interaction =
        new Interaction(
          packet.shallowClone(),
          player.getWorld(), player,
          blockPosition, enumDirection, type,
          typeUsedInHand,
          handSlot == EnumWrappers.Hand.MAIN_HAND
            ? heldItem
            : user.meta().inventory().offhandItem(),
          handSlot, null, facingX, facingY, facingZ
        );

      boolean mustPostValidate = interactionMeta.remainingBlockStart > 0;
      if (!mustPostValidate && preprocessInteraction(interaction)) {
        InteractionEmulator.EmulationResult emulate = interactionEmulator.emulate(interaction);
        if (emulate == FAILED) {
          if (MinecraftVersions.VER1_19.atOrAbove()) {
            int sequenceNumber = packet.getIntegers().read(0);
            acknowledgeBlockChange(player, sequenceNumber);
          }
          if (blockPosition != null) {
            refreshBlocksAround(player, blockPosition.toLocation(player.getWorld()));
          }
          event.setCancelled(true);
          if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(player.getName())) {
            System.out.println("[Intave/DID] PLACE/INITIAL/PREPRO/EMU_FAILED " + type + " " + typeUsedInHand + " " + enumDirection);
          }
        } else {
          if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(player.getName())) {
            System.out.println("[Intave/DID] PLACE/INITIAL/PREPRO/EMU_SUCCESS " + type + " " + typeUsedInHand + " " + enumDirection);
          }
        }
      } else {
        interactionMeta.interactionList.add(interaction);
        // For items which require longer consume time, the cancellation should be done after double-checking
        boolean usable = ItemProperties.canItemBeUsed(player, heldItem)
          && !ItemProperties.isPotion(interaction.itemTypeInHand());
        if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(player.getName())) {
          System.out.println("[Intave/DID] PLACE/INITIAL/POSTPONE " + type + " " + typeUsedInHand + " " + enumDirection + " " + usable);
        }
        if (!usable) {
          if (MinecraftVersions.VER1_19.atOrAbove()) {
            int sequenceNumber = packet.getIntegers().read(0);
            acknowledgeBlockChange(player, sequenceNumber);
          }
          event.setCancelled(true);
        }
      }
    } finally {
      reader.release();
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveBreak(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    InteractionMeta interactionMeta = metaOf(user);
    MetadataBundle meta = user.meta();
    AttackMetadata attack = meta.attack();
    AbilityMetadata abilityData = meta.abilities();
    InventoryMetadata inventoryData = meta.inventory();
    ProtocolMetadata protocol = meta.protocol();

    PacketContainer packet = event.getPacket();

//    com.comphenix.protocol.wrappers.BlockPosition blockPosition = packet.getBlockPositionModifier().readSafely(0);
    com.comphenix.protocol.wrappers.BlockPosition blockPosition = event.getPacket().getModifier()
      .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
      .read(0);

    if (blockPosition == null || event.isCancelled()) {
      if (attack.inBreakProcess) {
        attack.lastBreak = System.currentTimeMillis();
      }
      interactionMeta.isBreakingBlock = attack.inBreakProcess = false;
      return;
    }

    ItemStack heldItemStack = inventoryData.heldItem();
    Material heldItemType = inventoryData.heldItemType();
    if (ItemProperties.isSwordItem(heldItemStack) && user.meta().abilities().inGameMode(GameMode.CREATIVE)) {
      Violation violation = Violation.builderFor(InteractionRaytrace.class)
        .forPlayer(player)
        .withVL(0)
        .withMessage("performed invalid block break")
        .withDetails("sword in creative mode")
        .build();
      Modules.violationProcessor().processViolation(violation);
      event.setCancelled(true);
      return;
    }

    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    float blockDamage = BlockInteractionAccess.blockDamage(player, inventoryData.heldItem(), blockPosition);
    boolean instantBreak = blockDamage >= 1.0f || abilityData.inGameMode(CREATIVE);
    boolean breakBlock = instantBreak || playerDigType == STOP_DESTROY_BLOCK;

    EnumWrappers.Direction direction = packet.getDirections().readSafely(0);
    int enumDirection = direction == null ? 0 : direction.ordinal();
    boolean nullBlock = blockPosition.getX() == 0 && blockPosition.getY() == 0 && blockPosition.getZ() == 0;

    if (nullBlock && enumDirection == 0) {
      return;
    }

    if (protocol.isPreMinecraft8() &&
      nullBlock &&
      direction == EnumWrappers.Direction.SOUTH &&
      playerDigType == RELEASE_USE_ITEM
    ) {
      return;
    }

    InteractionType type = breakBlock ? InteractionType.BREAK : InteractionType.START_BREAK;
    if (IntaveControl.DEBUG_INTERACTION) {
      player.sendMessage(type + " " + heldItemType + " " + playerDigType);
    }

    Interaction interaction = new Interaction(
      packet.shallowClone(), player.getWorld(), player, blockPosition, enumDirection,
      type,
      heldItemType, heldItemStack, EnumWrappers.Hand.MAIN_HAND, playerDigType,
      Float.NaN, Float.NaN, Float.NaN
    );

    boolean mustPostValidate = interactionMeta.remainingBlockStart > 0;
    if (!mustPostValidate && preprocessInteraction(interaction)) {
      if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(player.getName())) {
        System.out.println("[Intave/DID] BREAK/INITIAL/PREPRO " + type + " " + heldItemType + " " + playerDigType);
      }
      interactionEmulator.emulate(interaction);
    } else {
      interactionMeta.interactionList.add(interaction);
      event.setCancelled(true);
      if (MinecraftVersions.VER1_19.atOrAbove()) {
        int sequenceNumber = packet.getIntegers().read(0);
        acknowledgeBlockChange(player, sequenceNumber);
      }
      if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(player.getName())) {
        System.out.println("[Intave/DID] BREAK/INITIAL/POSTPONE " + type + " " + heldItemType + " " + playerDigType);
      }
      if (playerDigType == START_DESTROY_BLOCK) {
        interactionMeta.remainingBlockStart++;
      }
    }

    if (breakBlock || playerDigType == ABORT_DESTROY_BLOCK) {
      interactionMeta.isBreakingBlock = attack.inBreakProcess = false;
      attack.lastBreak = System.currentTimeMillis();
    } else if (playerDigType == START_DESTROY_BLOCK) {
      interactionMeta.isBreakingBlock = attack.inBreakProcess = true;
    }
  }

  @DispatchTarget
  public boolean receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    World world = player.getWorld();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    InteractionMeta interactionMeta = metaOf(user);
    List<Interaction> interactionList = interactionMeta.interactionList;
    if (interactionList.isEmpty()) {
      return false;
    }
    Location playerLocation = new Location(world, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
    playerLocation.setYaw(movementData.rotationYaw);
    playerLocation.setPitch(movementData.rotationPitch);
    Location playerLocationmdf = playerLocation.clone();
    playerLocationmdf.setYaw(movementData.lastRotationYaw);
    for (Interaction interaction : interactionList) {
      processInteraction(interaction, playerLocation, playerLocationmdf);
    }
    interactionList.clear();
    return true;
  }

  private boolean preprocessInteraction(Interaction interaction) {
    World world = interaction.world();
    Player player = interaction.player();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    InteractionMeta interactionMeta = metaOf(user);

    if (interaction.hasTargetBlock()) {
      Location playerLocation = new Location(player.getWorld(), movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      playerLocation.setYaw(movementData.rotationYaw);
      playerLocation.setPitch(movementData.rotationPitch);
      Location playerLocationmdf = playerLocation.clone();
      playerLocationmdf.setYaw(movementData.lastRotationYaw);

      MovingObjectPosition raycastResult;
      MovingObjectPosition raycastResultmdf;
      try {
        raycastResult = Raytracing.blockRayTrace(player, playerLocation);
        raycastResultmdf = Raytracing.blockRayTrace(player, playerLocationmdf);
      } catch (Exception exception) {
        exception.printStackTrace();
        return interaction.targetBlock().toLocation(world).distance(player.getLocation()) >= 6;
      }
      boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;

      // first raytrace check
      MovingObjectPosition firstRaytraceResult = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
      boolean hitMiss = (firstRaytraceResult == null || firstRaytraceResult.hitVec == NativeVector.ZERO);
      BlockPosition raycastVector = hitMiss ? BlockPosition.ORIGIN : firstRaytraceResult.getBlockPos();
      Location raycastLocation = raycastVector.toLocation(world);
      Location targetLocation = interaction.targetBlock().toLocation(world);
      boolean raytraceFailed = hitMiss ||
        raycastLocation.distance(targetLocation) > 0 ||
        wrongBlockFace(interaction, firstRaytraceResult);

      if (firstRaytraceResult != null && interaction.hasFacing()) {
        float f = (float) (firstRaytraceResult.hitVec.xCoord - targetLocation.getX());
        float f1 = (float) (firstRaytraceResult.hitVec.yCoord - targetLocation.getY());
        float f2 = (float) (firstRaytraceResult.hitVec.zCoord - targetLocation.getZ());

        if (Math.abs(compressAndDecompress(f) - interaction.facingX()) > 0.01 ||
          Math.abs(compressAndDecompress(f1) - interaction.facingY()) > 0.01 ||
          Math.abs(compressAndDecompress(f2) - interaction.facingZ()) > 0.01) {
          raytraceFailed = true;
        }
      }

//      System.out.println("PREPROCESS raytraceFailed: " + raytraceFailed + " hitMiss: " + hitMiss + " raycastLocation: " + raycastLocation + " targetLocation: " + targetLocation + " wrongBlockFace: " + wrongBlockFace(interaction, firstRaytraceResult));
      // if first raytrace failed..
      if (raytraceFailed) {
        // ..try again with mouse delay fix toggled differently
        MovingObjectPosition secondRaytraceResult = estimateMouseDelayFix ? raycastResult : raycastResultmdf;
        boolean hitMiss2 = secondRaytraceResult == null || secondRaytraceResult.hitVec == NativeVector.ZERO;
        BlockPosition raycastVector2 = hitMiss2 ? BlockPosition.ORIGIN : secondRaytraceResult.getBlockPos();
        Location raycastLocation2 = raycastVector2.toLocation(world);
        raytraceFailed = hitMiss2 ||
          raycastLocation2.distance(targetLocation) > 0 ||
          wrongBlockFace(interaction, secondRaytraceResult);

        if (secondRaytraceResult != null && interaction.hasFacing()) {
          float f = (float) (secondRaytraceResult.hitVec.xCoord - targetLocation.getX());
          float f1 = (float) (secondRaytraceResult.hitVec.yCoord - targetLocation.getY());
          float f2 = (float) (secondRaytraceResult.hitVec.zCoord - targetLocation.getZ());
          if (Math.abs(compressAndDecompress(f) - interaction.facingX()) > 0.01 ||
            Math.abs(compressAndDecompress(f1) - interaction.facingY()) > 0.01 ||
            Math.abs(compressAndDecompress(f2) - interaction.facingZ()) > 0.01) {
            raytraceFailed = true;
          }
        }
      }
      if (IntaveControl.DEBUG_INTERACTION) {
        if (raytraceFailed) {
          player.sendMessage(ChatColor.GRAY + "Preprocess failed");
        } else {
          player.sendMessage(ChatColor.GREEN + "Preprocess succeeded");
        }
      }
      return !raytraceFailed;
    }
    if (IntaveControl.DEBUG_INTERACTION) {
      player.sendMessage(ChatColor.GREEN + "No target block, preprocess succeeded");
    }
    return true;
  }

  private void processInteraction(
    Interaction interaction,
    Location playerLocation,
    Location playerLocationmdf
  ) {
    if (interaction.entered()) {
      return;
    }
    interaction.enter();

    if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(interaction.player().getName())) {
      System.out.println("[Intave/DID] PROC/" + interaction.type() + " " + interaction.itemTypeInHand() + " " + interaction.digType());
    }

    World world = interaction.world();
    Player player = interaction.player();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationMetadata = meta.violationLevel();
    InteractionMeta interactionMeta = metaOf(player);
    boolean usableItemInHand = ItemProperties.canItemBeUsed(player, interaction.itemInHand())
      && !ItemProperties.isPotion(interaction.itemTypeInHand());

    if (interaction.digType() == START_DESTROY_BLOCK) {
      interactionMeta.remainingBlockStart--;
    }

    if (!interaction.hasTargetBlock()) {
      interactionEmulator.emulate(interaction);
//      player.sendMessage("No target block");
//      System.out.println("No target block");

      return;
    }

//    player.sendMessage("Real " + interaction.facingX() + " " + interaction.facingY() + " " + interaction.facingZ());
//    System.out.println("Real " + interaction.facingX() + " " + interaction.facingY() + " " + interaction.facingZ());

    MovingObjectPosition raycastResult;
    MovingObjectPosition raycastResultmdf;
    try {
      raycastResult = Raytracing.blockRayTrace(player, playerLocation);
      raycastResultmdf = Raytracing.blockRayTrace(player, playerLocationmdf);
    } catch (Exception exception) {
      exception.printStackTrace();
      if (interaction.targetBlock().toLocation(world).distance(player.getLocation()) < 6) {
        forwardInteractionToServer(interaction, null, interaction.targetBlock().toLocation(world), interaction.targetBlock().toLocation(world), false, false, false);
      }
      return;
    }
    boolean failedFacingCheck = false;
    boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;
    // first raytrace check
    MovingObjectPosition firstRaytraceResult = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
    boolean hitMiss = (firstRaytraceResult == null || firstRaytraceResult.hitVec == NativeVector.ZERO);
    BlockPosition raycastVector = hitMiss ? BlockPosition.ORIGIN : firstRaytraceResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    Location targetLocation = interaction.targetBlock().toLocation(world);
    boolean raytraceFailed = hitMiss ||
      raycastLocation.distance(targetLocation) > 0 ||
      wrongBlockFace(interaction, firstRaytraceResult);

    if (raytraceFailed) {
      if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(interaction.player().getName())) {
        System.out.println("[Intave/DID] PROC/" + interaction.type() + "/RAYTRACE_A_FAILED " + interaction.itemTypeInHand() + " " + interaction.digType() + " (" + hitMiss + "||" + (raycastLocation.distance(targetLocation) > 0) + "||" + wrongBlockFace(interaction, firstRaytraceResult) + "["+interaction.targetDirectionIndex()+"-"+firstRaytraceResult.sideHit.getIndex()+"])");
      }
    }

    if (firstRaytraceResult != null && interaction.hasFacing()) {
      float f = (float) (firstRaytraceResult.hitVec.xCoord - targetLocation.getX());
      float f1 = (float) (firstRaytraceResult.hitVec.yCoord - targetLocation.getY());
      float f2 = (float) (firstRaytraceResult.hitVec.zCoord - targetLocation.getZ());
      if (Math.abs(compressAndDecompress(f) - interaction.facingX()) > 0.01 ||
        Math.abs(compressAndDecompress(f1) - interaction.facingY()) > 0.01 ||
        Math.abs(compressAndDecompress(f2) - interaction.facingZ()) > 0.01) {
        failedFacingCheck = true;
      }
    }

    // if first raytrace failed..
    if (raytraceFailed) {
      // ..try again with mouse delay fix toggled differently
      MovingObjectPosition secondRaytraceResult = estimateMouseDelayFix ? raycastResult : raycastResultmdf;
      boolean hitMiss2 = secondRaytraceResult == null || secondRaytraceResult.hitVec == NativeVector.ZERO;
      BlockPosition raycastVector2 = hitMiss2 ? BlockPosition.ORIGIN : secondRaytraceResult.getBlockPos();
      Location raycastLocation2 = raycastVector2.toLocation(world);
      raytraceFailed = hitMiss2 ||
        raycastLocation2.distance(targetLocation) > 0 ||
        wrongBlockFace(interaction, secondRaytraceResult);

      if (raytraceFailed) {
        if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(interaction.player().getName())) {
          System.out.println("[Intave/DID] PROC/" + interaction.type() + "/RAYTRACE_B_FAILED " + interaction.itemTypeInHand() + " " + interaction.digType() + " (" + hitMiss2 + "||" + (raycastLocation2.distance(targetLocation) > 0) + "||" + wrongBlockFace(interaction, secondRaytraceResult) + ")");
        }
      }

      if (secondRaytraceResult != null && interaction.hasFacing() && failedFacingCheck) {
        float f = (float) (secondRaytraceResult.hitVec.xCoord - targetLocation.getX());
        float f1 = (float) (secondRaytraceResult.hitVec.yCoord - targetLocation.getY());
        float f2 = (float) (secondRaytraceResult.hitVec.zCoord - targetLocation.getZ());
        failedFacingCheck = Math.abs(compressAndDecompress(f) - interaction.facingX()) > 0.01 ||
          Math.abs(compressAndDecompress(f1) - interaction.facingY()) > 0.01 ||
          Math.abs(compressAndDecompress(f2) - interaction.facingZ()) > 0.01;
      }
      interactionMeta.estimateMouseDelayFix = raytraceFailed == interactionMeta.estimateMouseDelayFix;
    }

    if (failedFacingCheck && !user.meta().abilities().inGameModeIncludePending(CREATIVE)) {
      violationMetadata.facingFailedCounter++;
    } else {
      violationMetadata.facingFailedCounter = 0;
    }

    boolean flag, mustCancelPacket;
    if (!raytraceFailed) {
      if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(interaction.player().getName())) {
        System.out.println("[Intave/DID] PROC/" + interaction.type() + "/RAYTRACE_SUCCESS " + interaction.itemTypeInHand() + " " + interaction.digType());
      }
      // everything is fine
      decrementer.decrement(user, 0.25);
      boolean emulationFailed = interactionEmulator.emulate(interaction) == FAILED;
      flag = mustCancelPacket = emulationFailed;
//      mustCancelPacket = emulationFailed;
    } else {
      // raytrace failed
      MovingObjectPosition movingObjectPosition = estimateMouseDelayFix ? raycastResultmdf : raycastResult;
      Location location = estimateMouseDelayFix ? playerLocationmdf : playerLocation;
      boolean atLeastLookingAtBlock = movingObjectPosition != null && atLeastLookingAtBlock(user, location, targetLocation, movingObjectPosition);
      boolean isAbortDestroyBlock = interaction.digType() == ABORT_DESTROY_BLOCK;
      flag = enabled() && !isAbortDestroyBlock && performFlag(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, atLeastLookingAtBlock);
      mustCancelPacket = false;
      // As the interaction was not canceled for consumables, we have to do it now as the raytrace failed
      if (usableItemInHand && interaction.type() == InteractionType.INTERACT) {
        meta.inventory().releaseItemNextTick();
      }
      if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(interaction.player().getName())) {
        System.out.println("[Intave/DID] PROC/" + interaction.type() + "/RAYTRACE_FAILED " + interaction.itemTypeInHand() + " " + interaction.digType() + " flag:" + flag);
      }
    }
    if (!usableItemInHand || interaction.type() != InteractionType.INTERACT) {
      forwardInteractionToServer(interaction, raycastResult, targetLocation, raycastLocation, hitMiss, flag, mustCancelPacket);
    } else if (flag) {
      if (IntaveControl.DEBUG_INTERACTION) {
        player.sendMessage("Failed interaction with usableItemInHand item, but not forwarding");
      }
    }
  }

  private float compressAndDecompress(float f) {
    byte b = (byte) (int) (f * 16.0F);
    return (float) (b & 0xFF) / 16.0F;
  }

  private void forwardInteractionToServer(
    Interaction interaction,
    MovingObjectPosition raycastResult,
    Location targetLocation,
    Location raycastLocation,
    boolean hitMiss,
    boolean flag,
    boolean enforceCancel
  ) {
    Player player = interaction.player();
    User user = userOf(player);
    ResponseType response = interaction.type().response();
    ExtendedBlockStateCache blockStateAccess = user.blockStates();
    if (enforceCancel) {
      response = ResponseType.CANCEL;
    }
    if (user.meta().movement().awaitTeleport) {
      flag = true;
    }
    boolean refreshBlocks = interaction.type() != InteractionType.INTERACT;
    boolean canBeReceivedAsIsWithoutProblems = interaction.digType() == ABORT_DESTROY_BLOCK;

    if (response == ResponseType.RAYTRACE_CAST) {
      if (hitMiss || raycastResult == null) {
        if (refreshBlocks) {
          refreshBlocksAround(player, targetLocation);
          blockStateAccess.invalidateOverride(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
        }
        if (canBeReceivedAsIsWithoutProblems) {
          receiveExcludedPacket(player, interaction.thePacket());
        }
      } else {
        PacketContainer packet = interaction.thePacket();
        if (flag && !canBeReceivedAsIsWithoutProblems) {
          // check if player collides with placement location
          {
            World world = player.getWorld();
            Material material = user.meta().inventory().heldItemType();
            int dat = 0;
            boolean replace = BlockInteractionAccess.replacedOnPlacement(world, player, new com.comphenix.protocol.wrappers.BlockPosition(raycastLocation.toVector()));
            Location placementLocation = replace ? raycastLocation : raycastLocation.clone().add(raycastResult.sideHit.directionVector().convertToBukkitVec());
            boolean raytraceCollidesWithPosition = material.isBlock() && Collision.playerInImaginaryBlock(
              user, world, placementLocation.getBlockX(), placementLocation.getBlockY(), placementLocation.getBlockZ(),
              material, dat
            );
            if (raytraceCollidesWithPosition) {
              refreshBlocksAround(player, targetLocation);
              blockStateAccess.invalidateOverride(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());
              return;
            }
          }
          writeEnumDirection(packet, raycastResult.sideHit);
          com.comphenix.protocol.wrappers.BlockPosition bp =
            new com.comphenix.protocol.wrappers.BlockPosition(
              raycastLocation.getBlockX(),
              raycastLocation.getBlockY(),
              raycastLocation.getBlockZ()
            );
          writeBlockPosition(packet, bp);
        }
        receiveExcludedPacket(player, packet);
        if (refreshBlocks && flag) {
          refreshBlocksAround(player, targetLocation);
        }
      }
    } else {
      if (flag && !canBeReceivedAsIsWithoutProblems) {
        if (refreshBlocks) {
          refreshBlocksAround(player, targetLocation);
        }
      } else {
        receiveExcludedPacket(player, interaction.thePacket());
      }
    }
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    // add rate limit
    User user = userOf(player);
    ConnectionMetadata connection = user.meta().connection();
    RateLimiter refreshBlockRatelimit = connection.refreshBlockRatelimit;
    if (refreshBlockRatelimit.checkCooldownAndAcquire()) {
      Synchronizer.synchronize(() -> {
        player.updateInventory();
        refreshBlock(player, targetLocation);
        for (Direction direction : Direction.values()) {
          Location placedBlock = targetLocation.clone().add(direction.directionVecAsVector());
          refreshBlock(player, placedBlock);
        }
      });
    }
  }

  private void acknowledgeBlockChange(Player player, int sequenceNumber) {
    if (!MinecraftVersions.VER1_19.atOrAbove()) {
      return;
    }
    PacketContainer ack = new PacketContainer(PacketType.Play.Server.BLOCK_CHANGED_ACK);
    ack.getIntegers().write(0, sequenceNumber);
    PacketSender.sendServerPacket(player, ack);
  }

  private boolean performFlag(
    Interaction interaction,
    MovingObjectPosition raycastResult,
    Location targetLocation,
    Location raycastLocation,
    boolean hitMiss,
    boolean lookingAtBlock
  ) {
    Player player = interaction.player();
    User user = userOf(player);
    InteractionType type = interaction.type();
    Block targetLocationBlock = VolatileBlockAccess.blockAccess(targetLocation);
    Block raycastLocationBlock = VolatileBlockAccess.blockAccess(raycastLocation);
    Material targetLocationBlockType = BlockTypeAccess.typeAccess(targetLocationBlock);
    Material raycastLocationBlockType = BlockTypeAccess.typeAccess(raycastLocationBlock);
    if (targetLocationBlockType == Material.AIR || raycastLocationBlockType == Material.AIR) {
      return true;
    }
    double vl = 0;
    boolean mustFlag = false;
    String message, details;
    if (type == InteractionType.BREAK) {
      boolean longBreakDuration = BlockInteractionAccess.blockDamage(player, user.meta().inventory().heldItem(), interaction.targetBlock()) < 0.8;
      String typeName = shortenTypeName(targetLocationBlockType);
      String append = "";
      if (hitMiss || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
        append = "looking in air";
        vl = longBreakDuration ? 20 : 5;
      } else if (raycastLocation.distance(targetLocation) > 0) {
        String blockName = shortenTypeName(raycastLocationBlockType);
        if (raycastLocationBlockType == targetLocationBlockType) {
          blockName = "a different " + blockName;
        }
        append = "but looking at " + blockName + " block";
        vl = longBreakDuration ? 20 : 5;
      } else if (wrongBlockFace(interaction, raycastResult)) {
        append = "invalid block face";
        vl = longBreakDuration ? 20 : 15;
      }
      float blockDamage = BlockInteractionAccess.blockDamage(player, user.meta().inventory().heldItem(), interaction.targetBlock());
      boolean instantBreak = blockDamage >= 1.0f || user.meta().abilities().inGameMode(CREATIVE);
      if (instantBreak) {
        vl = 0;
      }
      if (lookingAtBlock) {
        double multiplier = trustFactorSetting("k-multiplier", player) / 100d;
        vl *= multiplier;
      }
      message = "performed invalid break";
      details = typeName + " block, " + append;
      mustFlag = true;
    } else if (type == InteractionType.PLACE) {
      String typeAgainstName = shortenTypeName(targetLocationBlockType);
      String typeName = shortenTypeName(user.meta().inventory().heldItemType());

      String append = "";
      if (hitMiss || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
        append = "looking in air";
        vl = 5;
      } else if (raycastLocation.distance(targetLocation) > 0) {
        String blockName = shortenTypeName(raycastLocationBlockType);
        if (raycastLocationBlockType == targetLocationBlockType) {
          blockName = "a different " + blockName;
        }
        append = "looking at " + blockName + " block";
        vl = 2.5;
      } else if (interaction.targetDirectionIndex() != raycastResult.sideHit.getIndex()) {
        append = "invalid block face";
        vl = 2.5;
      }
      if (lookingAtBlock) {
        double multiplier = trustFactorSetting("k-multiplier", player) / 100d;
        vl *= multiplier;
      }
      message = "performed invalid placement";
      details = typeName + " block on " + typeAgainstName + " block, " + append;
    } else {
      String typeAgainstName = shortenTypeName(targetLocationBlockType);
      message = "invalid interaction";
      details = typeAgainstName + " block";
      mustFlag = true;
      vl = 0;
    }
    if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      mustFlag = false;
    }
    if (user.meta().movement().awaitTeleport) {
      mustFlag = true;
    }
    Violation violation = Violation.builderFor(InteractionRaytrace.class)
      .forPlayer(player).withMessage(message).withDetails(details).withVL(vl)
      .build();
    ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
    return violationContext.shouldCounterThreat() || mustFlag;
  }

  @PacketSubscription(
    packetsOut = BLOCK_BREAK_ANIMATION
  )
  public void clearInvalidBreakingUpdates(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    EntityReader entityReader = PacketReaders.readerOf(packet);
    Entity entity = entityReader.entityBy(event);
    entityReader.release();

    if (entity instanceof Player && UserRepository.hasUser((Player) entity)) {
      User breakingUser = UserRepository.userOf((Player) entity);
      if (!metaOf(breakingUser).isBreakingBlock) {
        packet.getIntegers().write(1, 11);
      }
    }
  }

  private boolean wrongBlockFace(Interaction interaction, MovingObjectPosition rayTraceResult) {
    Player player = interaction.player();
    User user = UserRepository.userOf(player);
    ProtocolMetadata protocol = user.meta().protocol();

    int sentIndex = interaction.targetDirectionIndex();
    int computedIndex = rayTraceResult == null ? sentIndex : rayTraceResult.sideHit.getIndex();

    if (protocol.oppositeBlockVectorBehavior()
      && interactionInHead(user, interaction)
      && sentIndex == rayTraceResult.sideHit.getOpposite().getIndex()) {
      return false;
    }

    // they don't send a block face here, don't make unnecessary adjustments
    if (interaction.digType() == ABORT_DESTROY_BLOCK && sentIndex == 0) {
      return false;
    }

    return computedIndex != sentIndex;
  }

  private boolean interactionInHead(User user, Interaction interaction) {
    com.comphenix.protocol.wrappers.BlockPosition blockPosition = interaction.targetBlock();
    MovementMetadata movement = user.meta().movement();
    double xDiff = blockPosition.getX() - ClientMath.floor(movement.positionX);
    double yDiff = blockPosition.getY() - ClientMath.floor(movement.positionY + movement.eyeHeight());
    double zDiff = blockPosition.getZ() - ClientMath.floor(movement.positionZ);
    return xDiff == 0 && yDiff == 0 && zDiff == 0;
  }

  private boolean atLeastLookingAtBlock(User user, Location location, Location targetBlockLocation, MovingObjectPosition movingObjectPosition) {
    NativeVector hitVec = movingObjectPosition.hitVec;
    BoundingBox targetBlockBox = new BoundingBox(
      targetBlockLocation.getBlockX(),
      targetBlockLocation.getBlockY(),
      targetBlockLocation.getBlockZ(),
      targetBlockLocation.getBlockX() + 1,
      targetBlockLocation.getBlockY() + 1,
      targetBlockLocation.getBlockZ() + 1
    ).grow(0.1);
    NativeVector origin = Raytracing.resolvePositionEyes(location, location, user.meta().movement().eyeHeight(), 1f);
    NativeVector directionVector = hitVec.subtract(origin).normalize().scale(0.2);
    NativeVector itrVector = origin.scale(1);
    if (targetBlockBox.isVecInside(hitVec)) {
      return true;
    } else {
      int i = 0;
      while (origin.distanceTo(itrVector) < 4 && i < 50) {
        itrVector = itrVector.add(directionVector);
        if (targetBlockBox.isVecInside(itrVector)) {
          return true;
        }
        i++;
      }
    }
    return false;
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  private void refreshBlock(Player player, Location location) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) {
      return;
    }
    Block block = VolatileBlockAccess.blockAccess(location);
    Object handle = BlockVariantNativeAccess.nativeVariantAccess(block);
    WrappedBlockData blockData = WrappedBlockData.fromHandle(handle);
    com.comphenix.protocol.wrappers.BlockPosition position = new com.comphenix.protocol.wrappers.BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockData().write(0, blockData);
    packet.getBlockPositionModifier().write(0, position);
    PacketSender.sendServerPacket(player, packet);
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    userOf(player).ignoreNextInboundPacket();
    PacketSender.receiveClientPacketFrom(player, packet);
  }

  @Override
  public boolean performLinkage() {
    return true;
  }

  private static final boolean BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION = MinecraftVersions.VER1_14_0.atOrAbove();

  private void writeBlockPosition(PacketContainer packet, com.comphenix.protocol.wrappers.BlockPosition blockPosition) {
    if (BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION && !packet.getType().equals(PacketType.Play.Client.BLOCK_DIG)) {
      MovingObjectPositionBlock raytraceSent = packet.getMovingBlockPositions().readSafely(0);
      raytraceSent.setBlockPosition(blockPosition);
      packet.getMovingBlockPositions().write(0, raytraceSent);
    } else {
      packet.getBlockPositionModifier().write(0, blockPosition);
    }
  }

  private void writeEnumDirection(PacketContainer packet, Direction direction) {
    if (BLOCK_DATA_WRAPPED_IN_MOVING_OBJECT_POSITION && !packet.getType().equals(PacketType.Play.Client.BLOCK_DIG)) {
      MovingObjectPositionBlock raytraceSent = packet.getMovingBlockPositions().readSafely(0);
      raytraceSent.setDirection(direction.toDirection());
      packet.getMovingBlockPositions().write(0, raytraceSent);
    } else {
      if (packet.getDirections().size() > 0) {
        packet.getDirections().write(0, direction.toDirection());
      } else {
        packet.getIntegers().write(0, direction.getIndex());
      }
    }
  }

  public enum ResponseType {
    RAYTRACE_CAST,
    CANCEL
  }

  public static class InteractionMeta extends CheckCustomMetadata {
    final List<Interaction> interactionList = new CopyOnWriteArrayList<>();
    public boolean estimateMouseDelayFix = false;
    public boolean isBreakingBlock = false;
    public long remainingBlockStart = 0;
  }
}