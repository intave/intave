package de.jpx3.intave.detect.checks.movement;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.movement.physics.LegacyWaterPhysics;
import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.detect.checks.movement.physics.SimulationProcessor;
import de.jpx3.intave.detect.checks.movement.physics.simulators.PoseSimulator;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.event.service.violation.Violation;
import de.jpx3.intave.event.service.violation.ViolationContext;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.DispatchCrossCall;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.client.MovementContextHelper;
import de.jpx3.intave.tools.client.PoseHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.collider.Collider;
import de.jpx3.intave.world.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.world.collider.result.QuickColliderSimulationResult;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.waterflow.Waterflow;
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
import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.tools.MathHelper.formatDouble;
import static de.jpx3.intave.tools.MathHelper.formatPosition;
import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_AQUATIC_UPDATE;

@Relocate
public final class Physics extends IntaveCheck {
  private final static double VL_DECREMENT_PER_VALID_MOVE = 0.05;
  private final static double VELOCITY_VL_THRESHOLD = 6;

  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;
  private MethodHandle fallDamageInvokeMethod;
  private final SimulationProcessor simulationService;

  public Physics(IntavePlugin plugin) {
    super("Physics", "physics");
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, VL_DECREMENT_PER_VALID_MOVE * 20);
    this.simulationService = new SimulationProcessor();
    searchFallDamageApplier();
    linkCheckToPoseSimulators();
  }

  private void searchFallDamageApplier() {
    Class<?> entityLivingClass = ReflectiveAccess.lookupServerClass("EntityLiving");
    String methodName = "e";
    if (ProtocolLibAdapter.VILLAGE_UPDATE.atOrAbove()) {
      methodName = "b";
    } else if (ProtocolLibAdapter.AQUATIC_UPDATE.atOrAbove()) {
      methodName = "c";
    }
    try {
      fallDamageInvokeMethod = MethodHandles.publicLookup().findVirtual(entityLivingClass, methodName, MethodType.methodType(Void.TYPE, Float.TYPE, Float.TYPE));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private void linkCheckToPoseSimulators() {
    for (Pose pose : Pose.values()) {
      pose.simulator().checkLinkage(this);
    }
  }

  public void applyFallDamageUpdate(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    if (movementData.artificialFallDistance > 3.0F) {
      float fallDistance = movementData.artificialFallDistance;
      Synchronizer.synchronize(() -> {
        Object playerHandle = user.playerHandle();
        movementData.allowFallDamage = true;
        dealFallDamage(playerHandle, fallDistance);
        movementData.allowFallDamage = false;
      });
      movementData.artificialFallDistance = 0F;
    }
  }

  private void dealFallDamage(Object playerHandle, float fallDistance) {
    try {
      fallDamageInvokeMethod.invoke(playerHandle, fallDistance, 1.0f);
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
  }

  @DispatchCrossCall
  public void receiveMovement(User user) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = user.meta().clientData();

    movementData.setMovementPoseType(poseOf(user));

    if (clientData.waterUpdate() && movementData.sneaking && movementData.inWater) {
      handleSneakInWater(user);
    }

    updateAquatics(user);
    simulateMotionClamp(user);

    Timings.CHECK_PHYSICS_PROC_TOT.start();
    predictFlyingPacketBeforeVelocity(user);

    ComplexColliderSimulationResult predictedMovement = simulationService.simulate(user, movementData.movementPoseType());
//    movementData.entityMotionVector = predictedMovement.entityContext();
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

  private Pose poseOf(User user) {
    Player player = user.player();
    if (player.getVehicle() != null) {
      return Pose.VEHICLE;
    } else if (PoseHelper.flyingWithElytra(player)) {
      return Pose.ELYTRA;
    }
    return Pose.PLAYER;
  }

  @DispatchCrossCall
  public void endMovement(User user, boolean hasMovement) {
    UserMetaMovementData movementData = user.meta().movementData();
    double motionX = movementData.motionX();
    double motionY = movementData.motionY();
    double motionZ = movementData.motionZ();
    if (hasMovement) {
      Pose movementPoseType = movementData.movementPoseType();
      PoseSimulator calculationPart = movementPoseType.simulator();
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

  public void updateOnGroundIfFlying(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    double physicsMotionX = movementData.physicsMotionX;
    double physicsMotionY = movementData.physicsMotionY;
    double physicsMotionZ = movementData.physicsMotionZ;
    if (Math.abs(physicsMotionX) < movementData.resetMotion()) {
      physicsMotionX = 0;
    }
    if (Math.abs(physicsMotionY) < movementData.resetMotion()) {
      physicsMotionY = 0;
    }
    if (Math.abs(physicsMotionZ) < movementData.resetMotion()) {
      physicsMotionZ = 0;
    }
    double motionX = physicsMotionX * 0.91f;
    double motionY = (physicsMotionY - 0.08) * 0.98f;
    double motionZ = physicsMotionZ * 0.91f;
    QuickColliderSimulationResult colliderResult = Collider.simulateQuickCollision(
      user.player(),
      movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
      motionX, motionY, motionZ
    );
    movementData.onGround = colliderResult.onGround();
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
      QuickColliderSimulationResult colliderResult = Collider.simulateQuickCollision(
        user.player(),
        movementData.verifiedPositionX, movementData.verifiedPositionY, movementData.verifiedPositionZ,
        motionX, motionY, motionZ
      );
      motionX = colliderResult.motionX();
      motionY = colliderResult.motionY();
      motionZ = colliderResult.motionZ();

      if (colliderResult.onGround() || movementData.onGround) {
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

  private void handleSneakInWater(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.physicsMotionY -= 0.04F;
  }

  private void updateEyesInWater(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.eyesInWater = Waterflow.areEyesInFluid(user, movementData.positionX, movementData.positionY, movementData.positionZ);
  }

  private void updateInWater(User user) {
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    UserMetaMovementData movementData = meta.movementData();
    if (clientData.protocolVersion() >= PROTOCOL_VERSION_AQUATIC_UPDATE) {
      movementData.inWater = Waterflow.handleFluidAcceleration(user, movementData.boundingBox());
    } else {
      WrappedAxisAlignedBB entityBoundingBox = movementData.boundingBox();
      WrappedAxisAlignedBB checkableBoundingBox = entityBoundingBox
        .expand(0.0D, -0.4000000059604645D, 0.0D)
        .contract(0.001D, 0.001D, 0.001D);
      movementData.inWater = LegacyWaterPhysics.handleMaterialAcceleration(user, checkableBoundingBox);
    }
    if (movementData.inWater) {
      movementData.pastWaterMovement = 0;
      movementData.artificialFallDistance = 0;
    }
  }

  private void evaluateBestSimulation(User user, ComplexColliderSimulationResult expectedMovement) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    boolean spectator = player.getGameMode() == GameMode.SPECTATOR;

    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaAbilityData abilityData = meta.abilityData();
    MotionVector context = expectedMovement.context();

    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    boolean flying = abilityData.flying();
    String key = resolveKeysFromInput(keyForward, keyStrafe);

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

    boolean onLadderCurrent = MovementContextHelper.isOnLadder(user, positionX, positionY, positionZ);
    boolean onLadder = onLadderCurrent | movementData.onLadderLast;
    movementData.onLadderLast = onLadderCurrent;

    // Entity collision check
    boolean collidedWithBoat = movementData.collidedWithBoat();
    boolean skipVLCalculation = distance <= 1e-5;
    double verticalViolationIncrease = skipVLCalculation ? 0 : calculateVerticalViolationLevelIncrease(user, predictedY, onLadder, collidedWithBoat);
    double horizontalViolationIncrease = skipVLCalculation ? 0 : calculateHorizontalViolationIncrease(user, predictedX, predictedZ, onLadder, collidedWithBoat);

    if (onLadder) {
      movementData.artificialFallDistance = 0;
    }

    boolean velocityDetected = false;
    if (!skipVLCalculation && movementData.pastExternalVelocity < 10 && !movementData.recentlyEncounteredFlyingPacket(2)) {
      if (distance > 0.0005) {
        boolean aggressive = violationLevelData.physicsVelocityVL++ >= VELOCITY_VL_THRESHOLD;
        if (aggressive || distance > 0.01) {
          if (aggressive) {
            horizontalViolationIncrease = Math.max(2, horizontalViolationIncrease);
            velocityDetected = true;
          }
          horizontalViolationIncrease *= 10.0;
        }
      }
    }

    if (violationLevelData.physicsVelocityVL > 10) {
      violationLevelData.physicsVelocityVL = 10;
    }
    if (violationLevelData.physicsVelocityVL > 0) {
      violationLevelData.physicsVelocityVL -= 0.005;
    }

    double violationLevelIncrease = horizontalViolationIncrease + verticalViolationIncrease;
    if (distance > 1e-3) {
      movementData.suspiciousMovement = true;
      ComplexColliderSimulationResult entityCollisionResult = simulationService.simulateMovementWithoutKeyPress(user);
      MotionVector setbackContext = entityCollisionResult.context();
      predictedX = setbackContext.motionX;
      predictedY = setbackContext.motionY;
      predictedZ = setbackContext.motionZ;
    }

    if (flying || spectator) {
      violationLevelIncrease = 0;
    }

    if (violationLevelIncrease == 0 && violationLevelData.physicsVL > 0) {
      violationLevelData.physicsVL *= 0.990;
      violationLevelData.physicsVL -= 0.012;
    }

    Location verifiedLocation = movementData.verifiedLocation();
    List<WrappedAxisAlignedBB> intersectionBoundingBoxesLast = Collision.resolve(user.player(), WrappedAxisAlignedBB.createFromPosition(user, verifiedLocation.getX(), verifiedLocation.getY(), verifiedLocation.getZ()));
    WrappedAxisAlignedBB currentBoundingBox = WrappedAxisAlignedBB.createFromPosition(user, receivedPositionX, receivedPositionY, receivedPositionZ);
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
        Block block = BukkitBlockAccess.blockAccess(player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);
        boolean currentlyInOverride = user.boundingBoxAccess().currentlyInOverride(WrappedMathHelper.floor(blockPositionX), WrappedMathHelper.floor(blockPositionY), WrappedMathHelper.floor(blockPositionZ));

        String message = "moved into " + (currentlyInOverride ? "emulated" : "") + " " + shortenTypeName(block.getType()) + " block";
        boolean multipleBoxes = intersectionBoundingBoxesCurrent.size() > 1;
        String details = (multipleBoxes ? intersectionBoundingBoxesCurrent.size() : "one") + " box" + (multipleBoxes ? "es" : "");

        user.boundingBoxAccess().identityInvalidate();

        Violation violation = Violation.fromType(Physics.class)
          .withPlayer(player).withMessage(message).withDetails(details)
          .withVL(0)
          .build();
        plugin.violationProcessor().processViolation(violation);

        Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, 2);
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
          Block block = BukkitBlockAccess.blockAccess(player.getWorld(), blockPositionX, blockPositionY, blockPositionZ);

          String message = "moved into " + shortenTypeName(block.getType()) + " block whilst moving in another block";
          boolean multipleBoxes = intersectionBoundingBoxesCurrent.size() > 1;
          String details = (multipleBoxes ? intersectionBoundingBoxesCurrent.size() : "one") + " box" + (multipleBoxes ? "es" : "");
          Violation violation = Violation.fromType(Physics.class)
            .withPlayer(player).withMessage(message).withDetails(details)
            .withVL(0)
            .build();
          plugin.violationProcessor().processViolation(violation);
          WrappedAxisAlignedBB startPhaseBoundingBox = WrappedAxisAlignedBB.createFromPosition(user, movementData.verifiedLocation());
          plugin.eventService().emulationEngine().emulationPushOutOfBlock(player, startPhaseBoundingBox, predictedX, predictedY, predictedZ);
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
      violationLevelIncrease = Math.min(200.0, violationLevelIncrease);
      violationLevelIncrease = Math.max(1, violationLevelIncrease);
      violationLevelData.physicsVL += violationLevelIncrease;
      violationLevelData.physicsInvalidMovementsInRow++;
      user.boundingBoxAccess().invalidate();
      statistics().increaseFails();
    } else {
      violationLevelData.physicsInvalidMovementsInRow = 0;
      statistics().increasePasses();
    }

    if (!spectator && violationLevelData.physicsVL > 50 && violationLevelIncrease > 0) {
      String received = formatPosition(receivedMotionX, receivedMotionY, receivedMotionZ);
      String expected = formatPosition(predictedX, predictedY, predictedZ);

      Vector emulationMotion = new Vector(predictedX, predictedY, predictedZ);
      String message = "moved incorrectly";
      String details = received + " pred: " + expected;

      if (velocityDetected) {
        details += ", velocity";
      }

      user.boundingBoxAccess().identityInvalidate();

      Violation violation = Violation.fromType(Physics.class)
        .withPlayer(player).withMessage(message).withDetails(details)
        .withVL(violationLevelIncrease / 10d).build();
      ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);

      boolean setback = violationContext.shouldCounterThreat() || violationLevelData.physicsVL >= 75;
      if (setback) {
        int setbackTicks;
        if (movementData.pastExternalVelocity <= 8) {
          setbackTicks = 8;
        } else {
          setbackTicks = violationLevelData.physicsVL > 50 ? 3 : 2;
        }
        plugin.eventService().emulationEngine().emulationSetBack(player, emulationMotion, setbackTicks);
        movementData.invalidMovement = true;
      }
    }

    statistics().increaseTotal();

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
      debug += "(" + key + ")";
      debug += " " + violationLevelInfo;

//      debug += " (sneak " + movementData.sneaking + ")";
//      debug += " (size:" + movementData.width + "," + movementData.height + ")";
//      debug += "handActive=" + inventoryData.handActive();
//      debug += inventoryData.heldItem().getType().name();
//      debug += " flying:" + movementData.pastFlyingPacketAccurate;

      List<String> tags = new ArrayList<>();

      tags.add("dist=" + formatDouble(distance, 10));
      if (collidedWithBoat) {
        tags.add("boat");
      }
      if (violationLevelData.isInActiveTeleportBundle) {
        tags.add("atb");
      }
      if (movedIntoBlock) {
        tags.add("bb-intersection");
      }
      if (movementData.physicsJumped) {
        tags.add("jump");
      }

      debug += " " + String.join(" ", tags);

      String finalDebug = debug;
      player.sendMessage(finalDebug);
//      Synchronizer.synchronize(() -> player.sendMessage(finalDebug));
    }
  }

  private static String resolveKeysFromInput(int forward, int strafe) {
    String key = "";
    if (forward == 1) {
      key += "W";
    } else if (forward == -1) {
      key += "S";
    }
    if (strafe == 1) {
      key += "A";
    } else if (strafe == -1) {
      key += "D";
    }
    return key;
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

  private double calculateVerticalViolationLevelIncrease(
    User user,
    double predictedY,
    boolean onLadder,
    boolean collidedWithBoat
  ) {
    Player player = user.player();
    User.UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    UserMetaMovementData movementData = meta.movementData();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    boolean swimming = movementData.swimming;
    boolean elytraFlying = movementData.elytraFlying;
    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();
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

    if (collidedWithBoat && movementData.motionY() < 0.605) {
      if (movementData.enforceBoatStep) {
        if (movementData.motionY() < 0.1) {
          legitimateDeviation = Math.max(legitimateDeviation, 10);
        }
        movementData.enforceBoatStep = false;
      } else if (movementData.physicsMotionY < 0) {
        legitimateDeviation = Math.max(legitimateDeviation, 10);
        if (movementData.motionY() > movementData.jumpUpwardsMotion()) {
          movementData.enforceBoatStep = true;
        }
      }
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

    // Jump out of water
    if (movementData.pastWaterMovement <= 3) {
      double liquidPositionY;
      if (clientData.waterUpdate()) {
        liquidPositionY = receivedMotionY + 0.6f - movementData.positionY + movementData.verifiedPositionY;
      } else {
        liquidPositionY = receivedMotionY + 0.6f;
      }
      boolean offsetPositionInLiquid = MovementContextHelper.isOffsetPositionInLiquid(
        player, movementData.boundingBox(), receivedMotionX, liquidPositionY, receivedMotionZ
      );
      boolean maybeCollidedHorizontally = Collision.nearBySolidBlock(player.getWorld(), movementData.boundingBox().grow(0.2));
      if (maybeCollidedHorizontally && offsetPositionInLiquid && receivedMotionY < 0.4) {
        legitimateDeviation = Math.max(legitimateDeviation, 0.7f);
      }
    }

    double abuseVertically = Math.max(0, differenceY - legitimateDeviation);

    double multiplier = abuseVertically > 0.009 ? 305.0 : 10.0;
    if (criticalWeb) {
      multiplier *= 40;
    }

    if (onLadder && movementData.motionY() <= LADDER_UPWARDS_MOTION) {
      abuseVertically = 0;
    }

    // Long teleport
    if (movementData.pastLongTeleport <= 10 && movementData.motionY() < -0.097 && movementData.motionY() > -0.099) {
      double horizontalDistance = Math.hypot(receivedMotionX, receivedMotionZ);
      if (horizontalDistance < 0.2) {
        abuseVertically = 0;
      }
    }

    return abuseVertically * multiplier;
  }

  private double calculateHorizontalViolationIncrease(
    User user,
    double predictedX,
    double predictedZ,
    boolean onLadder,
    boolean collidedWithBoat
  ) {
    User.UserMeta meta = user.meta();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    UserMetaMovementData movementData = meta.movementData();

    Pose movementPoseType = movementData.movementPoseType();
    double motionX = movementData.motionX();
    double motionZ = movementData.motionZ();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    double predictedDistanceMoved = Math.hypot(predictedX, predictedZ);

    if (movementPoseType == Pose.VEHICLE) {

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
        boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
        legitimateDeviation = lessThanExpected ? 0.115 : 0.005;
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
    boolean inLiquid = (movementData.pastWaterMovement < 20 && movementData.pastPushedByWaterFlow > 5) || movementData.inLava();

    if (recentlySentFlying) {
      boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
      double baseSpeedMultiplier = inLiquid ? 0.3 : 0.7;
      if (lessThanExpected || distanceMoved < baseMoveSpeed * baseSpeedMultiplier) {
        legitimateDeviation = Math.max(legitimateDeviation, baseMoveSpeed * 0.7);
      }
    }

    if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (movementData.motionY() < 0 ? 0.4 : 0.2))) {
      legitimateDeviation = Math.max(distanceMoved, 0.2);
    }

    if (collidedWithBoat) {
      legitimateDeviation = Math.max(legitimateDeviation, 0.4);
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
    boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.0005;

    if (inLiquid) {
      movedTooQuickly = movedTooQuickly && distanceMoved > baseMoveSpeed;
    }

    boolean movedTooQuicklyCheckable = distanceMoved > 0.3 || violationLevelData.physicsInvalidMovementsInRow >= 8;

    if (movedTooQuickly && movedTooQuicklyCheckable) {
      //noinspection UnnecessaryLocalVariable
      double vl = abuseHorizontally > 0.2 ? 1000 : Math.max(0.1, abuseHorizontally) * 100;
//      Bukkit.broadcastMessage(user.player().getName() + " moved too quickly: vl+" + vl + " abuse:" + abuseHorizontally);
      return vl;
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

  public SimulationProcessor simulationService() {
    return simulationService;
  }

  @Override
  public boolean enabled() {
    return true;
  }
}