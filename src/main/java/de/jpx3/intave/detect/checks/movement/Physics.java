package de.jpx3.intave.detect.checks.movement;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.movement.physics.PhysicsSimulator;
import de.jpx3.intave.detect.checks.movement.physics.collision.block.BlockCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.EntityCollisionRepository;
import de.jpx3.intave.detect.checks.movement.physics.collision.entity.SimulationResult;
import de.jpx3.intave.detect.checks.movement.physics.pose.PhysicsCalculationPart;
import de.jpx3.intave.detect.checks.movement.physics.pose.PhysicsMovementPose;
import de.jpx3.intave.detect.checks.movement.physics.water.AquaticWaterMovementBase;
import de.jpx3.intave.detect.checks.movement.physics.water.WaterMovementLegacyResolver;
import de.jpx3.intave.detect.checks.movement.physics.water.aquatics.*;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.PlayerMovementPoseHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.BlockAccessor;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import static de.jpx3.intave.detect.checks.movement.physics.PhysicsHelper.resolveKeysFromInput;
import static de.jpx3.intave.tools.MathHelper.formatDouble;
import static de.jpx3.intave.tools.MathHelper.formatPosition;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_AQUATIC_UPDATE;

public final class Physics extends IntaveCheck {
  private final static double VL_DECREMENT_PER_VALID_MOVE = 0.05;

  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;
  private MethodHandle fallDamageInvokeMethod;

  private final PhysicsSimulator simulationService;
  private final AquaticWaterMovementBase aquaticWaterMovementBase;
  private final EntityCollisionRepository entityCollisionRepository;
  private final BlockCollisionRepository blockCollisionRepository;

  public Physics(IntavePlugin plugin) {
    super("Physics", "physics");
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, VL_DECREMENT_PER_VALID_MOVE * 20);
    this.simulationService = new PhysicsSimulator();
    this.entityCollisionRepository = new EntityCollisionRepository();
    this.blockCollisionRepository = new BlockCollisionRepository();
    this.aquaticWaterMovementBase = resolveAquaticMovement();
    linkFallDamageInvokeMethod();
    setupPoseTypes();
  }

  private void linkFallDamageInvokeMethod() {
    Class<?> entityLivingClass = ReflectiveAccess.lookupServerClass("EntityLiving");
    String methodName = "e";
    if (ProtocolLibAdapter.VILLAGE_UPDATE.atOrAbove()) {
      methodName = "b";
    } else if (ProtocolLibAdapter.AQUATIC_UPDATE.atOrAbove()) {
      methodName = "c";
    }
    try {
      fallDamageInvokeMethod =
        MethodHandles
          .publicLookup()
          .findVirtual(entityLivingClass, methodName, MethodType.methodType(Void.TYPE, Float.TYPE, Float.TYPE));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private void setupPoseTypes() {
    for (PhysicsMovementPose pose : PhysicsMovementPose.values()) {
      setupPose(pose.calculationPart());
    }
  }

  private void setupPose(PhysicsCalculationPart poseType) {
    poseType.setup(this);
  }

  private AquaticWaterMovementBase resolveAquaticMovement() {
    MinecraftVersion minecraftVersion = ProtocolLibAdapter.serverVersion();
    AquaticWaterMovementBase aquaticWaterMovement;
    if (minecraftVersion.isAtLeast(ProtocolLibAdapter.NETHER_UPDATE)) {
      aquaticWaterMovement = new AquaticNetherUpdateMovementResolver();
    } else if (minecraftVersion.isAtLeast(ProtocolLibAdapter.BEE_UPDATE)) {
      aquaticWaterMovement = new AquaticBeeUpdateMovementResolver();
    } else if (minecraftVersion.isAtLeast(ProtocolLibAdapter.VILLAGE_UPDATE)) {
      aquaticWaterMovement = new AquaticVillageUpdateMovementResolver();
    } else if (minecraftVersion.isAtLeast(ProtocolLibAdapter.AQUATIC_UPDATE)) {
      aquaticWaterMovement = new AquaticAquaticUpdateMovementResolver();
    } else {
      aquaticWaterMovement = new AquaticUnknownMovementResolver();
    }
    return aquaticWaterMovement;
  }

  public void dealFallDamage(Object playerHandle, float fallDistance) {
    try {
      fallDamageInvokeMethod.invoke(playerHandle, fallDistance, 1.0f);
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
  }

  public void receiveMovement(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    PhysicsMovementPose movementPoseType = resolveMovementPose(user);
    movementData.setMovementPoseType(movementPoseType);
    processMovement(user);
  }

  private PhysicsMovementPose resolveMovementPose(User user) {
    Player player = user.player();
    if (player.getVehicle() != null) {
      return PhysicsMovementPose.PHYSICS_VEHICLE_MOVEMENT;
    } else if (PlayerMovementPoseHelper.flyingWithElytra(player)) {
      return PhysicsMovementPose.PHYSICS_ELYTRA_MOVEMENT;
    }
    return PhysicsMovementPose.PHYSICS_NORMAL_MOVEMENT;
  }

  public void endMovement(User user, boolean hasMovement) {
    UserMetaMovementData movementData = user.meta().movementData();
    double motionX = movementData.motionX();
    double motionY = movementData.motionY();
    double motionZ = movementData.motionZ();
    if (hasMovement) {
      PhysicsMovementPose movementPoseType = movementData.movementPoseType();
      PhysicsCalculationPart calculationPart = movementPoseType.calculationPart();

      if (movementData.pastVelocity == 0) {
        if (movementData.physicsJumped) {
          movementData.physicsJumpedOverrideVL++;
        } else if (movementData.physicsJumpedOverrideVL > 0) {
          movementData.physicsJumpedOverrideVL--;
        }
      }

      calculationPart.prepareNextTick(user, movementData.positionX, movementData.positionY, movementData.positionZ, motionX, motionY, motionZ);
    }
  }

  private void processMovement(User user) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    updateAquatics(user);
    simulateMotionClamp(user);

    Timings.CHECK_PHYSICS_PROC_TOT.start();
    predictFlyingPacketBeforeVelocity(user);

    SimulationResult predictedMovement = simulationService.simulate(user, movementData.movementPoseType());
    movementData.onGround = predictedMovement.onGround();
    movementData.collidedHorizontally = predictedMovement.collidedHorizontally();
    movementData.collidedVertically = predictedMovement.collidedVertically();
    movementData.physicsResetMotionX = predictedMovement.resetMotionX();
    movementData.physicsResetMotionZ = predictedMovement.resetMotionZ();
    movementData.step = predictedMovement.step();
    Timings.CHECK_PHYSICS_PROC_TOT.stop();
    Timings.CHECK_PHYSICS_EVAL.start();
    evaluateBestSimulation(user, predictedMovement);
    Timings.CHECK_PHYSICS_EVAL.stop();
    movementData.pastRiptideSpin++;
  }

  private void predictFlyingPacketBeforeVelocity(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    if (movementData.pastVelocity != 0) {
      return;
    }
    double motionX = movementData.physicsMotionXBeforeVelocity * 0.91f;
    double motionY = (movementData.physicsMotionYBeforeVelocity - 0.08) * 0.98f;
    double motionZ = movementData.physicsMotionZBeforeVelocity * 0.91f;
    if (motionX != 0 && motionY != 0 && motionZ != 0) {
      Collision.CollisionResult collisionResult = Collision.resolveQuickCollisions(
        user.player(),
        movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
        motionX, motionY, motionZ
      );
      motionX = collisionResult.motionX();
      motionY = collisionResult.motionY();
      motionZ = collisionResult.motionZ();

      if (collisionResult.onGround() || movementData.onGround) {
        double distance = motionX * motionX + motionY * motionY + motionZ * motionZ;
        if (distance < 0.009) {
          movementData.physicsUnpredictableVelocityExpected = true;
          movementData.setPastFlyingPacketAccurate(0);
        }
      }
    }
  }

  public void updateAquatics(User user) {
    updateInWater(user);
    updateEyesInWater(user);
  }

  private void updateEyesInWater(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.eyesInWater = aquaticWaterMovementBase.areEyesInFluid(user, movementData.positionX, movementData.positionY, movementData.positionZ);
  }

  private void updateInWater(User user) {
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    UserMetaMovementData movementData = meta.movementData();
    if (clientData.protocolVersion() >= PROTOCOL_VERSION_AQUATIC_UPDATE) {
      movementData.inWater = aquaticWaterMovementBase.handleFluidAcceleration(user, movementData.boundingBox());
    } else {
      WrappedAxisAlignedBB entityBoundingBox = movementData.boundingBox();
      WrappedAxisAlignedBB checkableBoundingBox = entityBoundingBox
        .expand(0.0D, -0.4000000059604645D, 0.0D)
        .contract(0.001D, 0.001D, 0.001D);
      movementData.inWater = WaterMovementLegacyResolver.handleMaterialAcceleration(user, checkableBoundingBox);
    }
    if (movementData.inWater) {
      movementData.pastWaterMovement = 0;
      movementData.artificialFallDistance = 0;
    }
  }

  private void evaluateBestSimulation(User user, SimulationResult expectedMovement) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    boolean spectator = player.getGameMode() == GameMode.SPECTATOR;

    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    UserMetaAbilityData abilityData = meta.abilityData();
    PhysicsProcessorContext context = expectedMovement.context();

    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    boolean flying = abilityData.flying();
    String key = resolveKeysFromInput(keyForward, keyStrafe);
    if (movementData.physicsJumped) {
      key += key.isEmpty() ? "SPACE" : " | SPACE";
    }

    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();
    double receivedPositionX = movementData.positionX;
    double receivedPositionY = movementData.positionY;
    double receivedPositionZ = movementData.positionZ;
    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;
    double predictedX = context.motionX;
    double predictedY = context.motionY;
    double predictedZ = context.motionZ;

    double differenceX = predictedX - receivedMotionX;
    double differenceY = predictedY - receivedMotionY;
    double differenceZ = predictedZ - receivedMotionZ;
    double distance = MathHelper.resolveDistance(differenceX, differenceY, differenceZ);

    boolean onLadder = PlayerMovementHelper.isOnLadder(user, positionX, positionY + 1.5, positionZ);
    onLadder |= PlayerMovementHelper.isOnLadder(user, positionX, positionY - 0.5, positionZ);
    onLadder |= PlayerMovementHelper.isOnLadder(user, positionX, positionY, positionZ);
    boolean onLadderLast = movementData.onLadderLast;
    movementData.onLadderLast = onLadder;
    onLadder = movementData.onLadderLast || onLadderLast;

    double verticalViolationIncrease = calculateVerticalViolationLevelIncrease(user, predictedY, onLadder);
    double horizontalViolationIncrease = calculateHorizontalViolationIncrease(user, predictedX, predictedZ, onLadder);

    if (movementData.pastVelocity < 10) {
      if (horizontalViolationIncrease > 0) {
        horizontalViolationIncrease = Math.max(horizontalViolationIncrease, 1.0);
      }
      // Could be smaller (testing required)
      if (distance > 0.005) {
        horizontalViolationIncrease *= 10.0;
      }
    }

    double violationLevelIncrease = horizontalViolationIncrease + verticalViolationIncrease;

    if (distance > 1e-3) {
      movementData.suspiciousMovement = true;
      SimulationResult entityCollisionResult = simulationService.simulateMovementWithoutKeyPress(user);
      PhysicsProcessorContext setbackContext = entityCollisionResult.context();
      predictedX = setbackContext.motionX;
      predictedY = setbackContext.motionY;
      predictedZ = setbackContext.motionZ;
    }

    if (flying || spectator) {
      violationLevelIncrease = 0;
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL > 0) {
      violationLevelData.physicsVL *= 0.980;
      violationLevelData.physicsVL -= 0.012;
    }

    Location verifiedLocation = movementData.verifiedLocation();

    List<WrappedAxisAlignedBB> intersectionBoundingBoxesLast = Collision.resolve(user.player(), Collision.boundingBoxOf(user, verifiedLocation.getX(), verifiedLocation.getY(), verifiedLocation.getZ()));
    WrappedAxisAlignedBB currentBoundingBox = Collision.boundingBoxOf(user, receivedPositionX, receivedPositionY, receivedPositionZ);
    List<WrappedAxisAlignedBB> intersectionBoundingBoxesCurrent = Collision.resolve(user.player(), currentBoundingBox);

    boolean boundingBoxIntersectionLast = !intersectionBoundingBoxesLast.isEmpty();
    boolean boundingBoxIntersectionCurrent = !intersectionBoundingBoxesCurrent.isEmpty();

    boolean movedIntoBlock = !boundingBoxIntersectionLast && boundingBoxIntersectionCurrent;

    if (boundingBoxIntersectionCurrent && !spectator) {
      if (movedIntoBlock) {
        movementData.invalidMovement = true;

        WrappedAxisAlignedBB boundingBox = intersectionBoundingBoxesCurrent.get(0);

        double blockPositionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        double blockPositionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
        double blockPositionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
        Block block = BlockAccessor.blockAccess(player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);
        boolean currentlyInOverride = user.boundingBoxAccess().currentlyInOverride(WrappedMathHelper.floor(blockPositionX), WrappedMathHelper.floor(blockPositionY), WrappedMathHelper.floor(blockPositionZ));

        String message = "moved into " + (currentlyInOverride ? "<emulated>" : shortenTypeName(block.getType())) + " block";
        boolean multipleBoxes = intersectionBoundingBoxesCurrent.size() > 1;
        String details = (multipleBoxes ? intersectionBoundingBoxesCurrent.size() : "one") + " box" + (multipleBoxes ? "es" : "");

        user.boundingBoxAccess().identityInvalidate();

        plugin.violationProcessor().processViolation(player, 0, "Physics", message, details);
        Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, 8);
      } else {
        // Phase Check
        if (!movementData.currentlyInBlock) {
          movementData.currentlyInBlock = true;
          movementData.phaseIntersectingBoundingBoxes = ImmutableList.copyOf(intersectionBoundingBoxesCurrent);
        }

        // Prevents players from walking in other blocks
        boolean startBoundingBoxInList = false;
        for (WrappedAxisAlignedBB intersectingBoundingBox : movementData.phaseIntersectingBoundingBoxes) {
          boolean containsAny = containsBoundingBoxAny(intersectionBoundingBoxesCurrent, intersectingBoundingBox);
          if (containsAny) {
            startBoundingBoxInList = true;
            break;
          }
        }

        if (!startBoundingBoxInList) {
          movementData.invalidMovement = true;
          user.boundingBoxAccess().identityInvalidate();

          WrappedAxisAlignedBB boundingBox = intersectionBoundingBoxesCurrent.get(0);
          double blockPositionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
          double blockPositionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
          double blockPositionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
          Block block = BlockAccessor.blockAccess(player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);

          String message = "moved into " + shortenTypeName(block.getType()) + " block whilst moving in another block";
          boolean multipleBoxes = intersectionBoundingBoxesCurrent.size() > 1;
          String details = (multipleBoxes ? intersectionBoundingBoxesCurrent.size() : "one") + " box" + (multipleBoxes ? "es" : "");

          plugin.violationProcessor().processViolation(player, 0, "Physics", message, details);
          Location phaseStartLocation = new Location(player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);
          WrappedAxisAlignedBB startPhaseBoundingBox = Collision.boundingBoxOf(user, phaseStartLocation);
          plugin.eventService().emulationEngine().emulationPushOutOfBlock(player, startPhaseBoundingBox);
        }
      }
    }

    if (!boundingBoxIntersectionCurrent && !boundingBoxIntersectionLast) {
      movementData.currentlyInBlock = false;
    }

    // Update the player's verified location
    if (spectator || violationLevelIncrease == 0 && !boundingBoxIntersectionCurrent) {
      Location location = new Location(player.getWorld(), receivedPositionX, receivedPositionY, receivedPositionZ, movementData.rotationYaw, movementData.rotationPitch);
      movementData.setVerifiedLocation(location, "Movement validation (normal)");
    }

    if (violationLevelIncrease > 0) {
      violationLevelIncrease = Math.min(60.0, violationLevelIncrease);
      violationLevelIncrease = Math.max(1, violationLevelIncrease);
      violationLevelData.physicsVL += violationLevelIncrease;
      violationLevelData.physicsInvalidMovementsInRow++;
      user.boundingBoxAccess().invalidate();
    } else {
      violationLevelData.physicsInvalidMovementsInRow = 0;
    }

    if (!spectator && violationLevelData.physicsVL > 20 && violationLevelIncrease > 0) {
      String received = formatPosition(receivedMotionX, receivedMotionY, receivedMotionZ);
      String expected = formatPosition(predictedX, predictedY, predictedZ);

      Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
      String message = "moved incorrectly";
      String details = received + " e: " + expected;

      user.boundingBoxAccess().identityInvalidate();

      boolean setback = plugin.violationProcessor().processViolation(player, violationLevelIncrease / 20d, "Physics", message, details) || violationLevelData.physicsVL >= 60;
      if (setback) {
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, movementData.pastExternalVelocity <= 8 ? 8 : 1);
      }
      if (setback) {
        movementData.invalidMovement = true;
      }
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL < 1) {
      decrementer.decrement(user, VL_DECREMENT_PER_VALID_MOVE);
    }

    violationLevelData.physicsVL = MathHelper.minmax(0, violationLevelData.physicsVL, 100);

    if (movementData.onLadderLast) {
      movementData.artificialFallDistance = 0;
    }

    if (movementData.inLava()) {
      movementData.artificialFallDistance *= 0.5;
    }

    if (IntaveControl.DEBUG_MOVEMENT) {
      ChatColor chatColor = violationLevelIncrease == 0 ? ChatColor.GRAY : ChatColor.YELLOW;
      String motion = MathHelper.formatPositionAsInt(positionX, positionY, positionZ);
      String displayPhysicsVL = formatDouble(violationLevelData.physicsVL, 4);
      String displayHorizontalVL = formatDouble(horizontalViolationIncrease, 3);
      String displayVerticalVL = formatDouble(verticalViolationIncrease, 3);
      String displayViolationIncrease = formatDouble(violationLevelIncrease, 3);

      String violationLevelInfo;
      if (violationLevelIncrease > 0) {
        violationLevelInfo = "g:" + displayPhysicsVL + ",c:" + displayViolationIncrease + "(" + displayHorizontalVL + "," + displayVerticalVL + ")";
      } else {
        violationLevelInfo = "g:" + displayPhysicsVL;
      }
      String debug = chatColor + motion + " ";
      if (movementData.recentlyEncounteredFlyingPacket(0)) {
        debug += "f";
      }
      debug += "(" + key + ") " + " " + violationLevelInfo;

//      debug += " (sneak " + movementData.sneaking + ")";
//      debug += " (size:" + movementData.width + "," + movementData.height + ")";
//      debug += "handActive=" + inventoryData.handActive();
//      debug += inventoryData.heldItem().getType().name();
//      debug += " flying:" + movementData.pastFlyingPacketAccurate;
      debug += " dist=" + formatDouble(distance, 10);
//      debug += " inventoryOpen=" + inventoryData.inventoryOpen();
      debug += " " + (violationLevelData.isInActiveTeleportBundle ? "+" : "-");
      if (movedIntoBlock) {
        debug += " bb-intersection";
      }

      String finalDebug = debug + " p " + movementData.pastExternalVelocity;
      player.sendMessage(finalDebug);
//      Synchronizer.synchronize(() -> player.sendMessage(finalDebug));
    }
  }

  private boolean containsBoundingBoxAny(
    List<WrappedAxisAlignedBB> intersectionBoundingBoxesCurrent,
    WrappedAxisAlignedBB compareBoundingBox
  ) {
    for (WrappedAxisAlignedBB boundingBox : intersectionBoundingBoxesCurrent) {
      boolean sameX = boundingBox.minX == compareBoundingBox.minX && boundingBox.maxX == compareBoundingBox.maxX;
      boolean sameY = boundingBox.minY == compareBoundingBox.minY && boundingBox.maxY == compareBoundingBox.maxY;
      boolean sameZ = boundingBox.minZ == compareBoundingBox.minZ && boundingBox.maxZ == compareBoundingBox.maxZ;
      if (sameX && sameY && sameZ) {
        return true;
      }
    }
    return false;
  }

  private String shortenTypeName(Material type) {
    return type.name().toLowerCase().replace("_", "").replace("block", "");
  }

  private final static double LADDER_UPWARDS_MOTION = (0.2 - 0.08) * 0.98005f;

  private double calculateVerticalViolationLevelIncrease(User user, double predictedY, boolean onLadder) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    boolean swimming = movementData.swimming;
    boolean elytraFlying = movementData.elytraFlying;
    double receivedMotionY = movementData.motionY();
    double differenceY = Math.abs(receivedMotionY - predictedY);
    boolean accountedSkippedMovement = movementData.recentlyEncounteredFlyingPacket(2);
    double legitimateDeviation = accountedSkippedMovement ? 1e-2 : 1e-5;
    // MotionY calculations with sin/cos (FastMath affected)
    if (swimming || elytraFlying) {
      legitimateDeviation = 0.001;
    }

    if ((movementData.pastPushedByWaterFlow < 10 || movementData.inLava()) && distanceMoved < 0.2) {
      legitimateDeviation = 0.02;
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    if (movementData.recentlyEncounteredFlyingPacket(3) && differenceY > 1e-3) {
      boolean inLiquid = movementData.pastWaterMovement <= 10 || movementData.inLava();
      if (inLiquid || movementData.physicsPacketRelinkFlyVL++ <= 1) {
        legitimateDeviation = Math.max(legitimateDeviation, inLiquid ? 0.1 : 0.03);
      }
    }

    if (movementData.physicsUnpredictableVelocityExpected) {
      double velocityY = movementData.lastVelocity.getY();
      legitimateDeviation = Math.max(legitimateDeviation, velocityY * 1.2 - differenceY);
    }

    boolean criticalWeb = receivedMotionY > -0.01
      && movementData.inWeb
      && movementData.positionY % 1 > 0.1
      && movementData.pastExternalVelocity != 0;

    if (movementData.inWeb) {
      legitimateDeviation = criticalWeb ? 1e-6 : 0.13;
    }

    if (movementData.pastInWeb < 10 && !movementData.inWeb && differenceY < 0.1) {
      legitimateDeviation = 0.1;
    }

    if (movementData.recentlyEncounteredFlyingPacket(1) && movementData.pastExternalVelocity <= 4) {
      legitimateDeviation = 0.03;
    }

    double abuseVertically = Math.max(0, differenceY - legitimateDeviation);

    // Jump out of water
    if (movementData.inWater && abuseVertically > 1e-5 && receivedMotionY > 0.0 && receivedMotionY < 0.35) {
      Location location = new Location(player.getWorld(), movementData.positionX, movementData.positionY, movementData.positionZ);
      if (Collision.nearBySolidBlock(location, 0.4)) {
        boolean airAbove = !PlayerMovementHelper.isAllLiquid(player.getWorld(), movementData.boundingBox());
        if (airAbove) {
          abuseVertically = 0;
        }
      }
    }

    double multiplier = abuseVertically > 0.009 ? 205.0 : 10.0;
    if (criticalWeb) {
      multiplier *= 40;
    }

    if (onLadder && movementData.motionY() <= LADDER_UPWARDS_MOTION) {
      abuseVertically = 0;
    }

    return abuseVertically * multiplier;
  }

  private double calculateHorizontalViolationIncrease(User user, double predictedX, double predictedZ, boolean onLadder) {
    User.UserMeta meta = user.meta();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaMovementData movementData = meta.movementData();

    PhysicsMovementPose movementPoseType = movementData.movementPoseType();
    double motionX = movementData.motionX();
    double motionZ = movementData.motionZ();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    double predictedDistanceMoved = Math.hypot(predictedX, predictedZ);

    if (movementPoseType == PhysicsMovementPose.PHYSICS_VEHICLE_MOVEMENT) {

//      user.player().sendMessage(distanceMoved + " " + predictedDistanceMoved);

      if (distanceMoved < predictedDistanceMoved) {
        return 0;
      }
    }

    boolean pushedByWaterFlow = movementData.pastPushedByWaterFlow <= 20;
    double legitimateDeviation = movementData.pastPlayerAttackPhysics <= 1 ? 0.01 : 0.0007;

    if (movementData.collidedHorizontally && movementData.pastVelocity < 20) {
      legitimateDeviation = 0.027;
    }

    if (pushedByWaterFlow) {
      legitimateDeviation = 0.018;
    }

    if (movementData.currentlyInBlock && predictedDistanceMoved < distanceMoved * 1.3) {
      legitimateDeviation = predictedDistanceMoved;
    }

    // Flying packet
    if (movementData.recentlyEncounteredFlyingPacket(2)) {
      if (movementData.onGround) {
        //TODO: Check if last block-placement did not happen recently
        boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
        legitimateDeviation = lessThanExpected ? 0.115 : 0.005;
//        legitimateDeviation = 0.05;
      } else {
        legitimateDeviation = 0.05;
      }
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    boolean recentlySentFlying = movementData.recentlyEncounteredFlyingPacket(2);
    boolean recentlyVelocity = movementData.pastVelocity <= 1;
    double baseMoveSpeed = movementData.baseMoveSpeed();

    if (recentlySentFlying) {
      boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
      if (lessThanExpected || distanceMoved < baseMoveSpeed * 0.7) {
        legitimateDeviation = Math.max(legitimateDeviation, baseMoveSpeed * 0.7);
      }
    }

    if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (movementData.motionY() < 0 ? 0.4 : 0.2))) {
      legitimateDeviation = Math.max(distanceMoved, 0.2);
    }

    double distance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    if (movementData.physicsUnpredictableVelocityExpected) {
      Vector lastVelocity = movementData.lastVelocity;
      double velocityDistance = Math.hypot(lastVelocity.getX(), lastVelocity.getZ());
      distance -= velocityDistance;
      legitimateDeviation = Math.max(legitimateDeviation, velocityDistance * 1.2 - distanceMoved);
    }

    if (movementData.sneaking || movementData.lastSneaking) {
      if (Math.abs(movementData.motionX()) < 0.05 || Math.abs(movementData.motionZ()) < 0.05) {
        legitimateDeviation = Math.max(legitimateDeviation, 0.1);
      }
    }

    double abuseHorizontally = Math.max(0, distance - legitimateDeviation);
    boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.0005 && distanceMoved > baseMoveSpeed;
    boolean movedTooQuicklyCheckable = distanceMoved > 0.15 || violationLevelData.physicsInvalidMovementsInRow >= 8;

    if (movedTooQuickly && movedTooQuicklyCheckable && abuseHorizontally > 0 && !recentlyVelocity) {
//      double vl = Math.max(abuseHorizontally, 0.3) * 100.0;
//      Bukkit.broadcastMessage(user.player().getName() + " moved too quickly: vl+" + vl);
      return Math.max(abuseHorizontally, 0.3) * 100.0;
    }

    double multiplier = abuseHorizontally > 0.1 ? 20.0 : 10.0;
    return abuseHorizontally * multiplier;
  }

  private final static double RIPTIDE_TOLERANCE = 3.005;
  private final static double RIPTIDE_TOLERANCE_2 = 0.05;
  private final static double RIPTIDE_GROUND_TOLERANCE_2 = 2.5;

  private double resolveRiptideDeviation(UserMetaMovementData movementData) {
    double riptideTolerance;
    if (movementData.onGround) {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_GROUND_TOLERANCE_2;
    } else {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_TOLERANCE_2;
    }
    return riptideTolerance;
  }

  private void simulateMotionClamp(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    double resetMotion = movementData.resetMotion();
    if (Math.abs(movementData.physicsMotionX) < resetMotion) {
      movementData.physicsMotionX = 0.0;
    }
    if (Math.abs(movementData.physicsMotionY) < resetMotion) {
      movementData.physicsMotionY = 0.0;
    }
    if (Math.abs(movementData.physicsMotionZ) < resetMotion) {
      movementData.physicsMotionZ = 0.0;
    }
  }

  public AquaticWaterMovementBase aquaticWaterMovementBase() {
    return aquaticWaterMovementBase;
  }

  public EntityCollisionRepository entityCollisionRepository() {
    return entityCollisionRepository;
  }

  public BlockCollisionRepository blockCollisionRepository() {
    return blockCollisionRepository;
  }

  public static final class PhysicsProcessorContext {
    public double motionX;
    public double motionY;
    public double motionZ;

    public PhysicsProcessorContext() {
      this(0.0, 0.0, 0.0);
    }

    public PhysicsProcessorContext(double motionX, double motionY, double motionZ) {
      this.motionX = motionX;
      this.motionY = motionY;
      this.motionZ = motionZ;
    }

    public void reset(double x, double y, double z) {
      this.motionX = x;
      this.motionY = y;
      this.motionZ = z;
    }

    public void resetTo(UserMetaMovementData data) {
      reset(data.physicsMotionX, data.physicsMotionY, data.physicsMotionZ);
    }

    public static PhysicsProcessorContext from(PhysicsProcessorContext context) {
      return new PhysicsProcessorContext(context.motionX, context.motionY, context.motionZ);
    }
  }
}