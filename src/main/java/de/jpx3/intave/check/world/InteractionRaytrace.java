package de.jpx3.intave.check.world;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.block.access.BlockInteractionAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.check.world.interaction.*;
import de.jpx3.intave.executor.RateLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
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
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import de.jpx3.intave.world.raytrace.Raytracing;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.*;
import static de.jpx3.intave.check.world.interaction.InteractionType.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.BLOCK_BREAK_ANIMATION;
import static de.jpx3.intave.module.tracker.player.AbilityTracker.GameMode.CREATIVE;

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
      BLOCK_PLACE, USE_ITEM, USE_ITEM_ON
    }
  )
  public void receiveInteractionAndPlace(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    InteractionMeta interactionMeta = metaOf(user);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AbilityMetadata abilityMetadata = meta.abilities();
    InventoryMetadata inventory = meta.inventory();
    PacketContainer packet = event.getPacket();
    BlockInteractionReader reader = PacketReaders.readerOf(packet);

    inventory.lastBlockSequenceNumber = reader.sequenceNumber(user);

    try {
      com.comphenix.protocol.wrappers.BlockPosition blockPosition = reader.blockPosition();
      if (blockPosition == null || event.isCancelled() || movementData.isInVehicle()) {
        return;
      }
      int enumDirection = reader.enumDirection();
      if (enumDirection == 255) {
        // INTERACT IS EMPTY
      }

      float facingX = -1;
      float facingY = -1;
      float facingZ = -1;
      StructureModifier<Float> floatsInPacket = packet.getFloat();
      if (floatsInPacket.size() >= 3 && meta.protocol().sendsFacings()) {
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

      World world = player.getWorld();
      Material clickedType = VolatileBlockAccess.typeAccess(user, blockPosition.toLocation(world));
      boolean clickedIsInteractable = BlockInteractionAccess.isClickable(clickedType);

      if (clickedType == Material.WHEAT || clickedType == BlockTypeAccess.FARMLAND) {
        // not important
        return;
      }

      EnumWrappers.Hand handSlot = packet.getHands().readSafely(0);
      handSlot = handSlot == null ? EnumWrappers.Hand.MAIN_HAND : handSlot;

      ItemStack heldItem = inventory.heldItem();
      Material heldItemType = heldItem == null ? Material.AIR : heldItem.getType();
      Material offHandItemType = inventory.offhandItemType();
      Material typeUsedInHand = handSlot == EnumWrappers.Hand.MAIN_HAND ? heldItemType : offHandItemType;
      if (typeUsedInHand == null) {
        typeUsedInHand = Material.AIR;
      }

      Location placementLocation = interactionEmulator.placementLocation(
        world, player, user.blockCache(),
        typeUsedInHand, Direction.getFront(enumDirection),
        blockPosition.toLocation(world)
      );
      int blockX = placementLocation.getBlockX();
      int blockY = placementLocation.getBlockY();
      int blockZ = placementLocation.getBlockZ();

//      Material placedBlockType = VolatileBlockAccess.typeAccess(user, placementLocation);
      int variant = VolatileBlockAccess.variantIndexAccess(user, placementLocation);

      boolean raytraceCollidesWithPosition = typeUsedInHand.isBlock() && Collision.playerInImaginaryBlock(
        user, world,
        blockX, blockY, blockZ,
        typeUsedInHand, 0
      );

//      player.sendMessage(raytraceCollidesWithPosition + " " + blockX + " " + blockY + " " + blockZ + " " + typeUsedInHand + " " + variant);

      Material presentType = user.blockCache().typeAt(blockX, blockY, blockZ);

      boolean interactionIsPlacement = typeUsedInHand != Material.AIR
        && typeUsedInHand.isBlock()
        && !clickedIsInteractable
        && !raytraceCollidesWithPosition
        && typeUsedInHand != presentType
        && !abilityMetadata.inGameMode(GameMode.ADVENTURE);

//      if (lockedBlocks.get(user).contains(
//        new BlockPosition(blockX, blockY, blockZ)
//      )) {
//        interactionIsPlacement = false;
//      }

      InteractionType type = enumDirection == 255 ? EMPTY_INTERACT : (interactionIsPlacement ? InteractionType.PLACE : InteractionType.INTERACT);

      if (IntaveControl.DEBUG_INTERACTION) {
        player.sendMessage(type + " " + typeUsedInHand + " " + enumDirection + " " + presentType + "/" + typeUsedInHand);
      }

      Interaction interaction =
        new Interaction(
          interactionMeta.nextInteractionId++,
          packet.shallowClone(),
          world, player,
          blockPosition, enumDirection, type,
          typeUsedInHand,
          handSlot == EnumWrappers.Hand.MAIN_HAND
            ? heldItem
            : inventory.offhandItem(),
          handSlot, null, facingX, facingY, facingZ,
          reader.sequenceNumber(user)
        );

      boolean placementIs113Speculative = type == PLACE && meta.protocol().waterUpdate();

      if (placementIs113Speculative) {
        interactionMeta.speculativeInteraction = interaction;
      }

      boolean mustPostValidate = interactionMeta.remainingBlockStart > 0;// || !interactionMeta.interactionList.isEmpty();
      PreprocessResult result = mustPostValidate ? PreprocessResult.ENFORCE_ROUTING : preprocessInteraction(interaction);

      switch (result) {
        case FAILED_MINOR:
          interactionMeta.interactionList.add(interaction);
          interaction.doNotSendPacket();
        case OK:
          InteractionEmulator.EmulationResult emulate = interactionEmulator.emulate(interaction);
          if (emulate.denyForward()) {
            if (MinecraftVersions.VER1_19.atOrAbove()) {
              int sequenceNumber = packet.getIntegers().read(0);
              acknowledgeBlockChange(player, sequenceNumber);
            }
            if (blockPosition != null) {
              refreshBlocksAround(player, blockPosition.toLocation(world));
            }
            event.setCancelled(true);
            if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(player.getName())) {
              System.out.println("[Intave/DID] PLACE/INITIAL/PREPRO/EMU_FAILED " + type + " " + typeUsedInHand + " " + enumDirection + " " + blockPosition.getY());
            }
            Violation violation = Violation.builderFor(InteractionRaytrace.class)
              .forPlayer(player)
              .withVL(0)
              //            .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
              .withMessage("performed erroneous block placement")
              .withDetails("emulation failed for " + type + " with " + typeUsedInHand + " in hand, direction " + enumDirection + " at y " + blockPosition.getY())
              .build();
            Modules.violationProcessor().processViolation(violation);
          } else {
            if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(player.getName())) {
              System.out.println("[Intave/DID] PLACE/INITIAL/PREPRO/EMU_SUCCESS " + type + " " + typeUsedInHand + " " + enumDirection + " " + blockPosition.getY());
            }
          }
          break;
        case FAILED_CRITICAL:
        case ENFORCE_ROUTING:
          interactionMeta.interactionList.add(interaction);
          boolean usable = ItemProperties.canItemBeUsed(player, heldItem)
            && !ItemProperties.isPotion(interaction.itemTypeInHand());
          if (!usable || type == EMPTY_INTERACT) {
            if (MinecraftVersions.VER1_19.atOrAbove() && enumDirection != 255) {
              int sequenceNumber = packet.getIntegers().read(0);
              acknowledgeBlockChange(player, sequenceNumber);
            }
            event.setCancelled(true);
          } else {
            interaction.doNotSendPacket();
          }
          if (interaction.type() == InteractionType.PLACE) {
            interactionMeta.remainingBlockStart = 0;
          }
          break;
        default:
          break;
      }
      //       if (user.receives(MessageChannel.DEBUG_PACKET_HOLD)) {
      //        if (resendLater) {
      //          Synchronizer.synchronize(() -> {
      //            player.sendMessage("%PH " + ChatColor.RED + "Await attack at " + (System.currentTimeMillis() % 1000) + " since prelim ray failed");
      //          });
      //        } else {
      //          Synchronizer.synchronize(() -> {
      //            player.sendMessage("%PH " + ChatColor.GREEN + "Allowing attack without hold at " + (System.currentTimeMillis() % 1000));
      //          });
      //        }
      //      }
      if (user.receives(MessageChannel.DEBUG_PACKET_HOLD)) {
        if (!event.isCancelled()) {
          Synchronizer.synchronize(() -> {
            player.sendMessage("%PH " + ChatColor.GREEN + "Allowing " + interaction.type().name() + " without hold at " + (System.currentTimeMillis() % 1000));
          });
        } else {
          Synchronizer.synchronize(() -> {
            player.sendMessage("%PH " + ChatColor.RED + "Awaiting " + interaction.type().name() + " packet at " + (System.currentTimeMillis() % 1000) + ": prelim->"+ result);
          });
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
  public void receiveBreak(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    InteractionMeta interactionMeta = metaOf(user);
    MetadataBundle meta = user.meta();
    AttackMetadata attack = meta.attack();
    AbilityMetadata abilityData = meta.abilities();
    InventoryMetadata inventoryData = meta.inventory();
    ProtocolMetadata protocol = meta.protocol();

    receivedAnyTickContextPacket(user, false, "BLOCK_DIG");

    PacketContainer packet = event.getPacket();

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

    EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
    boolean blockInteraction = playerDigType == START_DESTROY_BLOCK || playerDigType == STOP_DESTROY_BLOCK || playerDigType == ABORT_DESTROY_BLOCK;
    ItemStack heldItemStack = inventoryData.heldItem();
    Material heldItemType = inventoryData.heldItemType();
    if (blockInteraction && ItemProperties.isSwordItem(heldItemStack) && user.meta().abilities().inGameMode(GameMode.CREATIVE)) {
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
      player.sendMessage(type + "@" + user.blockCache().typeAt(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ()) + "/" + blockDamage + " " + heldItemType + " " + playerDigType);
    }

    Interaction interaction = new Interaction(
      interactionMeta.nextInteractionId++,
      packet.shallowClone(), player.getWorld(), player,
      blockPosition, enumDirection, type,
      heldItemType, heldItemStack, EnumWrappers.Hand.MAIN_HAND, playerDigType,
      Float.NaN, Float.NaN, Float.NaN, -1
    );

    if (interactionMeta.interactionList.isEmpty()) {
      interactionMeta.remainingBlockStart = 0;
    }

    boolean enforceRouting = interactionMeta.remainingBlockStart > 0;// || !interactionMeta.interactionList.isEmpty();
    PreprocessResult preprocess = enforceRouting ? PreprocessResult.ENFORCE_ROUTING : preprocessInteraction(interaction);

    if (IntaveControl.DEBUG_INTERACTION) {
      player.sendMessage("receiveBreak " + preprocess + " " + interactionMeta.remainingBlockStart);
    }

    switch (preprocess) {
      case OK:
        interactionEmulator.emulate(interaction);
        break;
      case FAILED_MINOR:
        interactionEmulator.emulate(interaction);
        interaction.doNotSendPacket();
        interactionMeta.interactionList.add(interaction);
        break;
      case FAILED_CRITICAL:
      case ENFORCE_ROUTING:
        interactionMeta.interactionList.add(interaction);
        event.setCancelled(true);
        if (MinecraftVersions.VER1_19.atOrAbove()) {
          int sequenceNumber = packet.getIntegers().read(0);
          acknowledgeBlockChange(player, sequenceNumber);
        }
        if (type == START_BREAK) {
          interactionMeta.remainingBlockStart++;
        }
        break;
    }
    if (user.receives(MessageChannel.DEBUG_PACKET_HOLD)) {
      if (!event.isCancelled()) {
        Synchronizer.synchronize(() -> {
          player.sendMessage("%PH " + ChatColor.GREEN + "Allowing " + interaction.type().name() + " without hold at " + (System.currentTimeMillis() % 1000));
        });
      } else {
        Synchronizer.synchronize(() -> {
          player.sendMessage("%PH " + ChatColor.RED + "Awaiting " + interaction.type().name() + " packet at " + (System.currentTimeMillis() % 1000) + ": prelim->"+ preprocess);
        });
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
  public boolean receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    World world = player.getWorld();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    InteractionMeta interactionMeta = metaOf(user);
    receivedAnyTickContextPacket(user, false, "MOVEMENT");
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
      processInteraction(interaction, playerLocation.clone(), playerLocationmdf.clone());
    }
    interactionList.clear();
    return true;
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      ARM_ANIMATION,
      BLOCK_DIG,
      BLOCK_PLACE,
      TRANSACTION,
      PONG,
      USE_ITEM,
      USE_ENTITY,
      FLYING,
      POSITION,
      POSITION_LOOK,
      LOOK,
    }
  )
  public void receiveAnyTickContextPacket(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    receivedAnyTickContextPacket(user, event.getPacket().getType() == PacketType.Play.Client.ARM_ANIMATION, event.getPacketType().name());
  }

  private void receivedAnyTickContextPacket(
    User user, boolean isAnimation, String debugMessage
  ) {
    InteractionMeta interactionMeta = metaOf(user);
    if (interactionMeta.speculativeInteraction != null) {
      Interaction speculativeInteraction = interactionMeta.speculativeInteraction;
      interactionMeta.speculativeInteraction = null;
      if (isAnimation) {
        if (IntaveControl.DEBUG_INTERACTION) {
          user.player().sendMessage(ChatColor.GREEN + "Speculative interaction succeeded, emulated: " + speculativeInteraction.hasBeenEmulated() + "/" + speculativeInteraction.wasPlacementEmulated());
        }
        // all ok, nothing to do
      } else {
        // placement but no animation, undo
        if (IntaveControl.DEBUG_INTERACTION) {
          user.player().sendMessage(ChatColor.RED + "Speculative interaction failed was: " + debugMessage + ", emulated: " + speculativeInteraction.hasBeenEmulated() + "/" + speculativeInteraction.wasPlacementEmulated());
        }
        if (speculativeInteraction.hasBeenEmulated()) {
          interactionEmulator.undo(speculativeInteraction);
        } else {
          speculativeInteraction.overrideType(INTERACT);
        }
      }
    }
  }

  private PreprocessResult preprocessInteraction(Interaction interaction) {
    Player player = interaction.player();
    User user = userOf(player);
    MovementMetadata movementData = user.meta().movement();
    InteractionMeta interactionMeta = metaOf(user);

    if (interaction.type() == START_BREAK && interaction.digType() == ABORT_DESTROY_BLOCK) {
      // the block will be invalid regardless
      return PreprocessResult.OK;
    }

    if (interaction.hasTargetBlock()) {
      List<RaytraceEvaluation> evaluations = new CopyOnWriteArrayList<>();

      Location lastPlayerLocation = new Location(player.getWorld(), movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);
      Location currentLocation = new Location(player.getWorld(), movementData.positionX, movementData.positionY, movementData.positionZ);
      boolean highRisk = lastPlayerLocation.distance(currentLocation) > 1;

      for (int i = 0; i < (highRisk ? 1 : 2); i++) {
        Location playerLocation;
        switch (i) {
          case 0:
            playerLocation = lastPlayerLocation;
            break;
          case 1:
            playerLocation = currentLocation;
            break;
          default:
            throw new IllegalStateException("Unexpected value: " + i);
        }

        playerLocation.setYaw(movementData.rotationYaw);
        playerLocation.setPitch(movementData.rotationPitch);
        Location playerLocationmdf = playerLocation.clone();
        playerLocationmdf.setYaw(movementData.lastRotationYaw);

        double[] possibleXDisplacements = new double[]{0, 0.01, -0.01, 0.03, -0.03, 0.06, -0.06};
        double[] possibleZDisplacements = new double[]{0, 0.01, -0.01, 0.03, -0.03, 0.06, -0.06};
        int numChecks = possibleXDisplacements.length * possibleZDisplacements.length;

        boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;
        int index = 0;
        do {
          double xDisplacement = possibleXDisplacements[index / possibleZDisplacements.length];
          double zDisplacement = possibleZDisplacements[index % possibleZDisplacements.length];
          Location shiftedPlayerLocation = playerLocation.clone();
          Location shiftedPlayerLocationmdf = playerLocationmdf.clone();
          if (xDisplacement != 0 || zDisplacement != 0) {
            shiftedPlayerLocation.add(xDisplacement, 0, zDisplacement);
            shiftedPlayerLocationmdf.add(xDisplacement, 0, zDisplacement);
          }
          RaytraceEvaluation evaluation = singleRaytrace(user, interaction, estimateMouseDelayFix ? shiftedPlayerLocationmdf : shiftedPlayerLocation);
          if (evaluation == null) {
            return PreprocessResult.OK;
          }
          evaluations.add(evaluation);
          if (evaluation.isInvalid()) {
            // try again with mouse delay fix toggled differently
            estimateMouseDelayFix = !estimateMouseDelayFix;
            evaluation = singleRaytrace(user, interaction, estimateMouseDelayFix ? shiftedPlayerLocationmdf : shiftedPlayerLocation);
            if (evaluation == null) {
              // critical error, ignore the detection
              return PreprocessResult.OK;
            }
            evaluations.add(evaluation);
            if (evaluation.isValid()) {
//            interactionMeta.estimateMouseDelayFix = estimateMouseDelayFix;
              break;
            }
          } else {
            break;
          }
          index++;
          // Limit the amount of shifted locations granted when they are used excessively
          if (index > 8 && interactionMeta.speculativePositionsUsed > 8) {
            break;
          }
          if (index > 16 && interactionMeta.speculativePositionsUsed > 4) {
            break;
          }
        } while (index < numChecks);
      }

      RaytraceEvaluation bestRaytrace = evaluations.stream().filter(e -> !e.isInvalid()).findFirst().orElse(/* find random, is never empty and result is never null */ evaluations.get(0));
      boolean raytraceFailed = bestRaytrace.isInvalid();

      if (IntaveControl.DEBUG_INTERACTION) {
        if (raytraceFailed) {
          // check if target block appears in any raytrace
          boolean targetBlockAppears = evaluations.stream().anyMatch(e -> e.raycastResult() != null && e.raycastResult().getBlockPos().distanceTo(interaction.targetBlock().toVector()) < 0.1);
          boolean reverseRaytrace = reverseRaytrace(user, new Position(movementData.positionX, movementData.positionY + Raytracing.resolvePlayerEyeHeight(player), movementData.positionZ), interaction);
          boolean incorrectButNotBadBlockFace = placementWasBehindTargetedBlock(user, interaction.targetBlockAsPosition(), interaction.targetDirection().getIndex());
          boolean recentlyFlagged = System.currentTimeMillis() - metaOf(user).lastInteractionFlag < 10_000;

//          player.sendMessage(ChatColor.GRAY + "Preprocess failed " + (targetBlockAppears ? "but target block appears" : "and target block does not appear") + (reverseRaytrace ? " and reverse raytrace succeeded" : " and reverse raytrace failed"));

          if ((targetBlockAppears || reverseRaytrace || incorrectButNotBadBlockFace) && !recentlyFlagged) {
//            player.sendMessage(ChatColor.YELLOW + "Preprocess succeeded partially");
            return PreprocessResult.FAILED_MINOR;
          }

//          player.sendMessage(ChatColor.RED + "Preprocess failed");
        } else {
//          player.sendMessage(ChatColor.GREEN + "Preprocess succeeded");
        }
      }
      return raytraceFailed ? PreprocessResult.FAILED_CRITICAL : PreprocessResult.OK;
    }
    if (IntaveControl.DEBUG_INTERACTION) {
      player.sendMessage(ChatColor.GREEN + "No target block, preprocess succeeded");
    }
    return PreprocessResult.OK;
  }

  private enum PreprocessResult {
    OK,
    FAILED_MINOR,
    FAILED_CRITICAL,
    ENFORCE_ROUTING;

    public boolean checkPacketAfter() {
      return this == FAILED_MINOR || this == FAILED_CRITICAL || this == ENFORCE_ROUTING;
    }

    public boolean awaitMovement() {
      return this == FAILED_CRITICAL || this == ENFORCE_ROUTING;
    }
  }

  private boolean reverseRaytrace(User user, Position playerEyes, Interaction interaction) {
    if (!interaction.hasTargetBlock()) {
      return false;
    }
//    user.player().sendMessage(interaction.targetBlock() + " ");
    NativeVector playerEyesNativeVec = playerEyes.toNativeVec();
    List<Position> edges = edgesOf(interaction.targetBlockAsPosition());
    Player player = user.player();
    World world = player.getWorld();
    for (Position from : edges) {
      double naturalDistance = from.distance(playerEyes);
      MovingObjectPosition mop = Raytracing.blockRayTrace(world, player, from.toNativeVec(), playerEyesNativeVec);
      double raytraceDistance = mop == null ? Double.MAX_VALUE : mop.hitVec.distanceTo(from.toNativeVec());
      if (raytraceDistance > naturalDistance) {
        return true;
      }
    }
    return false;
  }

  private List<Position> edgesOf(BlockPosition block) {
    List<Position> edges = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      edges.add(new Position(
        block.getX() + (i & 1),
        block.getY() + (i >> 1 & 1),
        block.getZ() + (i >> 2 & 1)
      ));
    }
    return edges;
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

//    Synchronizer.synchronize(() -> {
//      player.sendMessage(ChatColor.GRAY + "Post processing " + interaction.type());
//    });

    User user = userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationMetadata = meta.violationLevel();
    InteractionMeta interactionMeta = metaOf(player);
    boolean usableItemInHand = ItemProperties.canItemBeUsed(player, interaction.itemInHand())
      && !ItemProperties.isPotion(interaction.itemTypeInHand());
    Location targetLocation = interaction.targetBlock().toLocation(world);

    if (interaction.digType() == STOP_DESTROY_BLOCK || interaction.digType() == ABORT_DESTROY_BLOCK) {
      interactionMeta.remainingBlockStart--;
    }
    if (!interaction.hasTargetBlock()) {
      interactionEmulator.emulate(interaction);
      if (interaction.shouldSendPacket()) {
        forwardInteractionToServer(interaction, null, null, null, false, false, false);
      }
      return;
    }

    if (interaction.shouldSendPacket() && user.receives(MessageChannel.DEBUG_PACKET_HOLD)) {
      Synchronizer.synchronize(() -> {
        player.sendMessage("%PH " + ChatColor.YELLOW + "Processing " + interaction.type().name() + " packet at " + (System.currentTimeMillis() % 1000));
      });
    }

    Pose currentPose = user.meta().movement().pose();
    boolean sneakUncertainty = user.meta().protocol().delayedSneak() && (currentPose == Pose.STANDING || currentPose == Pose.CROUCHING);
    List<RaytraceEvaluation> evaluations = new CopyOnWriteArrayList<>();
    double[] possibleXDisplacements = new double[]{0, 0.005, -0.005, 0.01, -0.01, 0.03, -0.03, 0.06, -0.06};
    double[] possibleZDisplacements = new double[]{0, 0.005, -0.005, 0.01, -0.01, 0.03, -0.03, 0.06, -0.06};
    boolean ratelimited = false;
    int numChecks = possibleXDisplacements.length * possibleZDisplacements.length;

    int index = 0;
    boolean estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;
    for (int poses = 0; poses < (sneakUncertainty ? 2 : 1); poses++)
      POSE:{
        Pose selectedPose = sneakUncertainty ? (poses == 0 ? Pose.STANDING : Pose.CROUCHING) : currentPose;
        estimateMouseDelayFix = interactionMeta.estimateMouseDelayFix;
        index = 0;
        do {
          double xDisplacement = possibleXDisplacements[index / possibleZDisplacements.length];
          double zDisplacement = possibleZDisplacements[index % possibleZDisplacements.length];
          Location shiftedPlayerLocation = playerLocation.clone();
          Location shiftedPlayerLocationmdf = playerLocationmdf.clone();
          if (xDisplacement != 0 || zDisplacement != 0) {
            shiftedPlayerLocation.add(xDisplacement, 0, zDisplacement);
            shiftedPlayerLocationmdf.add(xDisplacement, 0, zDisplacement);
          }
          RaytraceEvaluation evaluation = singleRaytrace(user, interaction, estimateMouseDelayFix ? shiftedPlayerLocationmdf : shiftedPlayerLocation, selectedPose);
          if (evaluation == null) {
            // critical error, ignore detection
            if (interaction.targetBlock().toLocation(world).distance(player.getLocation()) < 6) {
              forwardInteractionToServer(interaction, null, interaction.targetBlock().toLocation(world), interaction.targetBlock().toLocation(world), false, false, false);
            }
//          player.sendMessage(ChatColor.RED + "Critical error, interaction ignored");
            return;
          }
          evaluations.add(evaluation);
          if (evaluation.isInvalid()) {
            // try again with mouse delay fix toggled differently
            estimateMouseDelayFix = !estimateMouseDelayFix;
            evaluation = singleRaytrace(user, interaction, estimateMouseDelayFix ? shiftedPlayerLocationmdf : shiftedPlayerLocation, selectedPose);
            if (evaluation == null) {
              // critical error, ignore the detection
              if (interaction.targetBlock().toLocation(world).distance(player.getLocation()) < 6) {
                forwardInteractionToServer(interaction, null, interaction.targetBlock().toLocation(world), interaction.targetBlock().toLocation(world), false, false, false);
              }
//            player.sendMessage(ChatColor.RED + "Critical error, interaction ignored");
              return;
            }
            evaluations.add(evaluation);
            if (evaluation.isValid()) {
              interactionMeta.estimateMouseDelayFix = estimateMouseDelayFix;
              break POSE;
            }
          } else {
            break POSE;
          }
          index++;
          // Limit the amount of shifted locations granted when they are used excessively
          if (index > 8 && interactionMeta.speculativePositionsUsed > 8) {
            ratelimited = true;
            break;
          }
          if (index > 16 && interactionMeta.speculativePositionsUsed > 4) {
            ratelimited = true;
            break;
          }
        } while (index < numChecks);
      }

    if (index > 1) {
      // used a shifted location
      interactionMeta.speculativePositionsUsed += ratelimited || index == numChecks ? 0.005 : 1;
      if (System.currentTimeMillis() - interactionMeta.lastSpeculativePositionReset > 60_000) {
        interactionMeta.speculativePositionsUsed /= 2;
        interactionMeta.lastSpeculativePositionReset = System.currentTimeMillis();
      }
    }

    RaytraceEvaluation bestRaytrace = evaluations.stream().filter(e -> !e.isInvalid()).findFirst().orElse(/* find random, is never empty and result is never null */ evaluations.get(0));
    int bestIndex = evaluations.indexOf(bestRaytrace);
    interaction.setRaytraceResult(bestRaytrace.raycastResult());
    // this is bogus
    if (bestRaytrace.isValid() && bestRaytrace.facingCheckFailed() && !user.meta().abilities().inGameModeIncludePending(CREATIVE)) {
      violationMetadata.facingFailedCounter++;
    } else {
      violationMetadata.facingFailedCounter = 0;
    }

//    float yaw = playerLocation.getYaw();
//    float pitch = playerLocation.getPitch();

    boolean flag, mustCancelPacket;
    if (bestRaytrace.isValid()) {
      if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(interaction.player().getName())) {
        System.out.println("[Intave/DID] PROC/" + interaction.type() + "/RAYTRACE_SUCCESS " + interaction.itemTypeInHand() + " " + interaction.digType());
      }
      // everything is fine
      decrementer.decrement(user, 0.25);
      boolean emulationFailed = interactionEmulator.emulate(interaction).denyForward();
      flag = mustCancelPacket = emulationFailed;
//      mustCancelPacket = emulationFailed;
//      Synchronizer.synchronize(() -> {
//        player.sendMessage(ChatColor.GREEN + "" + interaction.type() + "@"+user.blockStates().typeAt(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ())+ "   RAYCAST:" + formatPosition(bestRaytrace.raycastLocation) + " TOLD" + formatPosition(targetLocation) + " " +yaw + " " +pitch );
//      });
    } else {
      // raytrace failed
      MovingObjectPosition movingObjectPosition = bestRaytrace.raycastResult();
      Location location = estimateMouseDelayFix ? playerLocationmdf : playerLocation;
      boolean atLeastLookingAtBlock = movingObjectPosition != null && atLeastLookingAtBlock(user, location, targetLocation, movingObjectPosition);
      boolean doNotFlagType = interaction.digType() == ABORT_DESTROY_BLOCK || interaction.type() == EMPTY_INTERACT;
      flag = enabled() && !doNotFlagType && performFlag(
        interaction, movingObjectPosition, targetLocation, bestRaytrace, atLeastLookingAtBlock, index
      );
      if (flag && IntaveControl.DUMP_BLOCK_HITBOX_ON_RIGHT_CLICK) {
        Block block = interaction.targetBlock().toLocation(world).getBlock();
        BlockCache blockStateAccess = user.blockCache();
        Material type = blockStateAccess.typeAt(block.getX(), block.getY(), block.getZ());
        int variant = blockStateAccess.variantIndexAt(block.getX(), block.getY(), block.getZ());
        BlockVariant properties = BlockVariantRegister.variantOf(type, variant);
        String propertyString = "{" + properties.propertyNames().stream().map(s -> s + ": " + properties.propertyOf(s)).collect(Collectors.joining(", ")) + "}";

//      Fluid fluid = Fluids.fluidAt(userOf(player), block.getX(), block.getY(), block.getZ());
        Fluid fluid = Fluids.fluidAt(userOf(player), block.getX(), block.getY(), block.getZ());
        player.sendMessage(ChatColor.GOLD + "" + type + "/" + variant + "." + propertyString + " f" + fluid + " -> " + blockStateAccess.collisionShapeAt(block.getX(), block.getY(), block.getZ()) + "/" + blockStateAccess.outlineShapeAt(block.getX(), block.getY(), block.getZ()));
      }
      mustCancelPacket = false;
      // As the interaction was not canceled for consumables, we have to do it now as the raytrace failed
      if (usableItemInHand && interaction.type() == InteractionType.INTERACT) {
        meta.inventory().releaseItemNextTick();

        if (user.receives(MessageChannel.DEBUG_ITEM_RESETS)) {
          user.player().sendMessage(IntavePlugin.prefix() + "Requesting item usage reset as " + ChatColor.RED + "raytrace failed ");
        }
      }
//      Synchronizer.synchronize(() -> {
//        player.sendMessage(ChatColor.RED + "" + interaction.type() + "@"+user.blockStates().typeAt(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ())+ "!!"+bestIndex+"   RAYCAST:" + formatPosition(bestRaytrace.raycastLocation) + " TOLD" + formatPosition(targetLocation) + " " + bestRaytrace.wrongBlockFace() + " " + yaw + " " + pitch);
//      });
      if (IntaveControl.DEBUG_INTERACTION_DISCREET && IntaveControl.INTERACTION_DEBUG_NAMES.contains(interaction.player().getName())) {
        System.out.println("[Intave/DID] PROC/" + interaction.type() + "/RAYTRACE_FAILED " + interaction.itemTypeInHand() + " " + interaction.digType() + " flag:" + flag);
      }
    }
    if (interaction.shouldSendPacket()/*!usableItemInHand || interaction.type() != InteractionType.INTERACT*/) {
      forwardInteractionToServer(interaction, bestRaytrace.raycastResult(), targetLocation, bestRaytrace.raycastLocation(), bestRaytrace.hitMiss(), flag, mustCancelPacket);
    } else if (flag) {
      if (IntaveControl.DEBUG_INTERACTION) {
//        player.sendMessage("Failed interaction with usableItemInHand item, but not forwarding");
      }
    }
  }

  public RaytraceEvaluation singleRaytrace(User user, Interaction interaction, Location playerLocation) {
    return singleRaytrace(user, interaction, playerLocation, user.meta().movement().pose());
  }

  public RaytraceEvaluation singleRaytrace(User user, Interaction interaction, Location playerLocation, Pose pose) {
    World world = interaction.world();
    Player player = interaction.player();
    MovingObjectPosition raycastResult;
    try {
      raycastResult = Raytracing.blockRayTrace(player, playerLocation, pose);
    } catch (Exception exception) {
      return null;
    }
    boolean hitMiss = (raycastResult == null || raycastResult.hitVec == NativeVector.ZERO);
    BlockPosition raycastVector = hitMiss ? BlockPosition.ORIGIN : raycastResult.getBlockPos();
    Location raycastLocation = raycastVector.toLocation(world);
    Location targetLocation = interaction.targetBlock().toLocation(world);
    return new RaytraceEvaluation(this, interaction, raycastResult, hitMiss, raycastLocation, targetLocation);
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
    BlockCache blockStateAccess = user.blockCache();
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
        if (refreshBlocks && (targetLocation != null)) {
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
    if (refreshBlockRatelimit.tryAcquire()) {
      Synchronizer.synchronize(() -> {
        if (IntaveControl.DEBUG_INTERACTION_REFRESHES) {
          player.sendMessage("Refreshed blocks around " + targetLocation);
        }
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
    RaytraceEvaluation raytraceEval,
    boolean lookingAtBlock,
    int searches
  ) {
    Player player = interaction.player();
    User user = userOf(player);
    InteractionType type = interaction.type();
    Location raycastLocation = raytraceEval.raycastLocation();
    boolean hitMiss = raytraceEval.hitMiss();
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
      } else if (raytraceEval.wrongBlockFace()) {
        append = "invalid block face";
        vl = longBreakDuration ? 20 : 15;
      }
//      float blockDamage = BlockInteractionAccess.blockDamage(player, user.meta().inventory().heldItem(), interaction.targetBlock());
//      boolean instantBreak = blockDamage >= 1.0f || user.meta().abilities().inGameMode(CREATIVE);
//      if (instantBreak) {
//        vl = 0;
//      }
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
      boolean impossibleFacing = placementWasBehindTargetedBlock(user, targetLocationBlock, interaction.targetDirectionIndex());
      String append = "";
      if (hitMiss || raycastResult == null || (raycastLocation.getBlockX() == 0 && raycastLocation.getBlockY() == 0 && raycastLocation.getBlockZ() == 0)) {
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
        vl = impossibleFacing ? 5 : 2.5;
        append = impossibleFacing ? "impossible block face" : "invalid block face";
      }
      if (lookingAtBlock && !impossibleFacing) {
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

    Map<String, String> granulars = new LinkedHashMap<>();
    granulars.put("type", type.name());
    granulars.put("rays", String.valueOf(searches));
    granulars.put("expected_block", MathHelper.formatPosition(targetLocation));
    granulars.put("expected_direction", interaction.targetDirection().name());
    granulars.put("actual_block", MathHelper.formatPosition(raycastLocation));
    granulars.put("actual_direction", raycastResult == null ? "null" : raycastResult.sideHit.name());

    boolean shouldCounterFlag = false;
    if (vl >= 0.1) {
      Violation violation = Violation.builderFor(InteractionRaytrace.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withVL(vl).withGranulars(granulars).build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      shouldCounterFlag = violationContext.shouldCounterThreat();
    }
    metaOf(user).lastInteractionFlag = System.currentTimeMillis();
    return shouldCounterFlag || mustFlag;
  }

  private boolean placementWasBehindTargetedBlock(User user, Block targetBlock, int index) {
    Position playerPos = user.meta().movement().position();
    if (playerPos == null || index > 10) {
      return false;
    }
    BlockPosition targetBlockPos = new BlockPosition(targetBlock.getLocation().clone());
    return placementWasBehindTargetedBlock(user, targetBlockPos, index);
  }

  private boolean placementWasBehindTargetedBlock(User user, BlockPosition targetBlockPos, int index) {
    Position playerPos = user.meta().movement().position();
    if (playerPos == null || index > 10) {
      return false;
    }
    Direction direction = Direction.getFront(index);
    BlockPosition placedBlockPos = targetBlockPos.offset(direction);
    return horizontalDistance(placedBlockPos, playerPos) > horizontalDistance(targetBlockPos, playerPos) ||
      verticalDistance(placedBlockPos, playerPos) > verticalDistance(targetBlockPos, playerPos);
  }

  private double horizontalDistance(BlockPosition pos, Position playerPos) {
    return Math.sqrt(Math.pow(pos.getX() - playerPos.getX(), 2) + Math.pow(pos.getZ() - playerPos.getZ(), 2));
  }

  private double verticalDistance(BlockPosition pos, Position playerPos) {
    return Math.abs(pos.getY() - playerPos.getY());
  }

  @PacketSubscription(
    packetsOut = BLOCK_BREAK_ANIMATION
  )
  public void clearInvalidBreakingUpdates(ProtocolPacketEvent event) {
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

  @BukkitEventSubscription(
    priority = EventPriority.MONITOR
  )
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BlockTrustChain trustChain = user.meta().movement().placementTrustChain;
    BlockPosition blockPosition = new BlockPosition(event.getBlockPlaced().getLocation());
    // keep invalidated anchors until next tick, then remove them
    // this avoids problems when a player places a block multiple times in a single tick
    boolean requireFeedbackTimedRemoval = trustChain.collapseState(blockPosition, !event.isCancelled());
    if (requireFeedbackTimedRemoval) {
      // next tick
      Synchronizer.synchronize(() ->
        user.tickFeedback(() -> trustChain.removeCollapsedState(blockPosition)));
    }
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
    if (IntaveControl.DEBUG_INTERACTION_PACKET_ROUTING) {
      System.out.println("[Intave/DIPR] REFRESHED BLOCK " + location);
    }
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    if (IntaveControl.DEBUG_INTERACTION_PACKET_ROUTING) {
      System.out.println("[Intave/DIPR] ROUTED PACKET " + packet.getType() + " " + packet.getHandle().getClass().getSimpleName());
    }
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
    public double speculativePositionsUsed = 0;
    public long lastSpeculativePositionReset = 0;

    public long nextInteractionId;
    public long lastProcessedInteractionId;

    public long lastInteractionFlag = 0;

    public Interaction speculativeInteraction = null;
  }
}