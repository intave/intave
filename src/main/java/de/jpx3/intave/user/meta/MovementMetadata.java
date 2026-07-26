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

package de.jpx3.intave.user.meta;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MoveMetric;
import de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.evaluation.MaskedMotionTolerance;
import de.jpx3.intave.check.movement.physics.simulator.BoatSimulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.check.movement.physics.simulator.Simulator;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.check.movement.physics.update.TickAmbiguousUpdate;
import de.jpx3.intave.check.world.interaction.BlockTrustChain;
import de.jpx3.intave.executor.RateLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.*;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static de.jpx3.intave.IntaveControl.REPLACE_JOAP_SETBACK_WITH_CM;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.*;
import static de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics.resolveFriction;
import static de.jpx3.intave.player.attribute.AttributeModifier.Operation.ADD_PERCENTAGE;
import static de.jpx3.intave.share.ClientMath.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_15;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_8;

public final class MovementMetadata implements SimulationEnvironment {
  public static final AttributeModifier SPRINTING_MODIFIER = AttributeModifier.newBuilder(
    UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D")
  ).withAmount(0.3F).withOperation(ADD_PERCENTAGE).withName("Sprint Boost").build();
  private final Player player;
  private final User user;
  public final BlockTrustChain placementTrustChain = new BlockTrustChain();
  public final Map<String, Double> serverMovementDebugValues = new HashMap<>();
  public final Map<String, Double> clientMovementDebugValues = new HashMap<>();
  public final List<TickAmbiguousUpdate> tickAmbiguousUpdates = new LinkedList<>();
  public float width = 0.6f, height = 1.8f;
  public float stepHeight = 0.6f;
  public double stepHeightThisMove = 0d;
  public double widthRounded, heightRounded;
  public volatile boolean gliding;
  public volatile @Nullable BlockPosition sleepingBedPosition;
  public int fireworkRocketsPower = 1;
  public boolean onGround, lastOnGround, step, onGroundWithRiptide;
  public boolean collidedHorizontally, collidedVertically;
  public float artificialFallDistance;
  public boolean dealCustomFallDamage;
  public double gravity = 0.08;
  public boolean outsideBorder = true;
  public Vector lookVector = new Vector();
  public double verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ;
  public String verifiedPositionOrigin;
  public double lastPositionX, lastPositionY, lastPositionZ;
  public double positionX, positionY, positionZ;
  public boolean sprinting, lastSprinting, hasSprintSpeed, sneaking, lastSneaking;
  public int sprintSneakFaults;
  public boolean acceptSneakFaults = true;
  public float rotationYaw, rotationPitch;
  public float lastRotationYaw, lastRotationPitch;
  public long recordedMoves;
  public long invalidVehiclePositionTicks = 0;
  // Timestamps
  public long lastTimeSneaking, lastTimeJumped, lastRotation;
  public Motion emulationVelocity;
  public Motion sneakPatchVelocity;
  public Motion setbackOverrideVelocity = Motion.newEmpty();
  public Motion lastVelocity = Motion.newEmpty();
  public boolean canResetMotion;
  public float frictionMultiplier = 0.09998f;
  public int lastPositionUpdate;
  @Nullable
  public Fluid interactingFluid;
  public boolean inRespawnScreen;
  public boolean inWater;
  public boolean inWeb;
  public boolean checkWebStateAgainNextTick = false;
  public int reduceTicks = 0;
  public boolean onLadderLast;
  public boolean aquaticUpdateInLava;
  public AtomicInteger pendingVelocityPackets = new AtomicInteger();
  public int physicsPacketRelinkFlyVL; // In Air
  public boolean invalidMovement, suspiciousMovement;
  public boolean treatThisFlyPacketAsMovePacket;
  public double baseMotionX, baseMotionY, baseMotionZ; // base or last motion, exclusively for the physics check
  public double baseMotionXBeforeVelocity, baseMotionYBeforeVelocity, baseMotionZBeforeVelocity;
  private List<Motion> postTickMotionCandidates = Collections.emptyList();
  public double endMotionXOverride = Double.NaN, endMotionYOverride = Double.NaN, endMotionZOverride = Double.NaN;
  public int highestLocalRiptideLevel = 0;
  public boolean physicsResetMotionX, physicsResetMotionZ;
  public int keyForward, keyStrafe;
  public int lastKeyForward, lastKeyStrafe;
  public boolean ignoredAttackReduce = false;
  public int shulkerXToleranceRemaining;
  public int shulkerYToleranceRemaining;
  public int shulkerZToleranceRemaining;
  public int lowestShulkerY = Integer.MAX_VALUE, highestShulkerY = Integer.MIN_VALUE;
  public int pistonMotionToleranceRemaining;
  public double pistonHorizontalAllowance;
  public double pistonVerticalAllowance;
  public BoundingBox pistonCollisionArea;
  public List<BlockPosition> shulkers = new ArrayList<>();
  public Map<BlockPosition, ShulkerBox> shulkerData = new HashMap<>();
  public Map<Integer, ShulkerBox> shulkerDataHashCodeAccess = new HashMap<>();
  // Will be set to true if the player sends a flying packet and receives server velocity later
  public boolean physicsUnpredictableVelocityExpected;
  // Jump prevention
  public boolean physicsJumped;
  public double physicsJumpedOverrideVL;
  public boolean currentlyInBlock;
  // Entity collision
  public boolean enforceBoatStep;
  public volatile Location nearestBoatLocation = null;
  public float boatGlide;
  public double waterLevel;
  public BoatSimulator.Status boatStatus = BoatSimulator.Status.ON_LAND,
    previousBoatStatus = BoatSimulator.Status.ON_LAND;
  public boolean isTeleportConfirmationPacket;
  public boolean dropPostTickMotionProcessing;
  public boolean willReceiveSetbackVelocity;
  public boolean willReceiveFinalSetbackVelocity;
  public int teleportId;
  public volatile boolean awaitTeleport = false, expectTeleport = false, awaitOutgoingTeleport = false;
  public volatile boolean expectTeleportWithRotation = false;
  public volatile boolean transactionTeleportAllow = false;
  public boolean awaitClickMovementSkip;
  public Location teleportLocation;
  public Motion teleportMotion = new Motion();
  public Set<Relative> teleportRelatives = EnumSet.noneOf(Relative.class);
  public int teleportResendCountdown = 20;
  public int outgoingTeleportCountdown = 5;
  public long lastRescueAttempt;
  public long lastSimulationSprintResetAttempt;
  // States if an external entity push onto the player is estimated
  public boolean pushedByEntity;
  // Key inputs sent by the client
  public boolean legacyVehicleKeyInput = false;
  public int legacyVehicleForwardKey = 0;
  public int legacyVehicleStrafeKey = 0;
  public boolean clientPressedJump = false;
  public boolean forceCorrectReduce = false;
  // Count resets on start item-usage and increases if the simulation suspects the player ignored item-usage slowdown
  public int handItemSimulationFails = 0;
  private boolean hasJumpFactor;
  private double resetMotion, frictionPosSubtraction;
  private double motionX, motionY, motionZ;
  private boolean sprintingAllowed;
  private float yawSine = 0, yawCosine = 1, friction;
  private volatile Pose pose = Pose.STANDING;
  private Simulator simulator = Simulators.PLAYER;
  @Nullable
  public BlockPosition mainSupportingBlockPos = null;
  public boolean onGroundNoBlocks = false;
	private Material frictionMaterial = Material.AIR, previousFrictionMaterial = Material.AIR;
  private Material collideMaterial = Material.AIR, previousCollideMaterial = Material.AIR;

  private SimulationResult beforeMoveCollider = SimulationResult.invalid();
  private MovementConfiguration lastMovementConfiguration = MovementConfiguration.blank();

  private volatile BoundingBox boundingBox = BoundingBox.fromBounds(0, 0, 0, 0, 0, 0);
  private boolean boundingBoxSetup = false;
  @Nullable
  private Vector motionMultiplier = null;
  private double jumpMotion;
  private float aiMoveSpeed, jumpMovementFactor;
  private boolean eyesInWater;
  // Vehicle
  private Entity vehicle;
  private boolean vehicleCanBeRidden;
  private double attachMoveDistance;
  // Flight disallow protection
  public int criticalFlyingDisallowStacks;
  public int criticalFlyingBlockMovementStacks;
  public boolean criticalFlyingDisallowWasTeleported;
  public double criticalEnterPosX, criticalEnterPosY, criticalEnterPosZ;
  public final RateLimiter criticalTeleportRateLimiter = new RateLimiter(10, 2, TimeUnit.SECONDS);
  public final RateLimiter simulationRateLimiter = new RateLimiter(100_000, 1_000, TimeUnit.SECONDS);
  private volatile Location verifiedLocation;
  public volatile Input input = Input.none();
  public volatile Input lastInput = Input.none();
  private @NotNull WorldBorder worldBorder = WorldBorder.createDefault();
  public final MaskedMotionTolerance maskedMotionTolerance = new MaskedMotionTolerance();

  // tick for causal constraint solving
  // must always be a client position packet
  // must not be implied flying or client_tick_end
  private long currentTick = 0;
  private long currentSequence = 0;
  private long activeSequence = 0;

  private final Map<MoveMetric, Integer> activeTracker = new EnumMap<>(MoveMetric.class);
  private final Map<MoveMetric, Integer> pastTracker = new EnumMap<>(MoveMetric.class);

  public final Map<String, Long> branchFrequency = new HashMap<>();
  public long branchFrequencyTrimCounter = 0;

  {
    for (MoveMetric value : MoveMetric.values()) {
      activeTracker.put(value, value.activeDefault());
      pastTracker.put(value, value.pastDefault());
    }
  }

  public SimulationEnvironment beforePreviousTickEnvironment;

  public MovementMetadata(Player player, User user) {
    this.player = player;
    this.user = user;
  }

  public void setup() {
    if (player != null) {
      if (player.hasMetadata("intave.testplayer.gliding")) {
        this.gliding = player.getMetadata("intave.testplayer.gliding").get(0).asBoolean();
      } else {
        Synchronizer.synchronize(() -> this.gliding = flyingWithElytra(player));
      }
    }
    applyPlayerStats();
    applyPlayerLocation();
  }

  public void setupDefaults() {
    ProtocolMetadata clientData = user.meta().protocol();
    int version = clientData.protocolVersion();
    this.resetMotion = version <= VER_1_8 ? 0.005 : 0.003;
    this.frictionMultiplier = version <= VER_1_15 ? 0.16277136f : 0.16277137F;
    this.frictionPosSubtraction = version <= VER_1_15 ? 1.0 : 0.500001;
    this.hasJumpFactor = version >= VER_1_15;
    if (!boundingBoxSetup) {
      Location location = player == null ? new Location(null, verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ) : player.getLocation();
      boundingBox = BoundingBox.fromPosition(user, this, location.getX(), location.getY(), location.getZ());
      boundingBoxSetup = true;
      // just a default non-null value
      teleportLocation = location;
    }
  }

  private void applyPlayerLocation() {
    Location location;
    if (player == null) {
      World world = null;
      if (Bukkit.getServer() != null) {
        world = Bukkit.getWorlds().get(0);
      }
      location = new Location(world, 0, 0, 0);
    } else {
      location = player.getLocation();
      artificialFallDistance = player.getFallDistance();
    }
    verifiedLocation = location.clone();
    positionX = location.getX();
    positionY = location.getY();
    positionZ = location.getZ();
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;
    verifiedLastPositionX = positionX;
    verifiedLastPositionY = positionY;
    verifiedLastPositionZ = positionZ;
    verifiedPositionOrigin = "initial";
    setRotation(0, 0);
    updateSize();
  }

  private void applyPlayerStats() {
    if (player == null) {
      return;
    }
    setSprinting(player.isSprinting());
    setSneaking(player.isSneaking());
  }

  @DispatchTarget
  public void updateMovement(
    double newPositionX, double newPositionY, double newPositionZ,
    float newRotationYaw, float newRotationPitch,
    boolean hasMovement, boolean hasRotation
  ) {
    if (!boundingBoxSetup) {
      setupDefaults();
    }
    jumpMotion = MovementCharacteristics.jumpMotionFor(user, jumpUpwardsMotion());
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;
    if (hasMovement) {
      positionX = newPositionX;
      positionY = newPositionY;
      positionZ = newPositionZ;
    } else {
      setPast(FLYING_PACKET_CLIENT, 0);
    }

    lastRotationYaw = rotationYaw;
    lastRotationPitch = rotationPitch;
    if (hasRotation) {
      setRotation(newRotationYaw, newRotationPitch);
    }

    if (hasMovement || hasRotation) {
      motionX = positionX - verifiedLastPositionX;
      motionY = positionY - verifiedLastPositionY;
      motionZ = positionZ - verifiedLastPositionZ;
      boolean falling = offsetMotionY() <= 0.0D;
      if (falling && Effects.slowFallingEffectActive(player)) {
        artificialFallDistance = 0f;
        gravity = 0.01D;
      } else {
        gravity = 0.08D;
      }
      updateEntityActionStates();
      updateMovementMetaData();
    }

    if (!user.meta().protocol().trailsAndTailsUpdate()) {
      compileSpecialBlocks();
    }

    recheckWebStateFromLastTick();
    if (hasMovement || hasRotation) {
      updatePose();
    }
  }

  @Override
  public void setPosition(double x, double y, double z) {
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;
    positionX = x;
    positionY = y;
    positionZ = z;
    setBoundingBox(BoundingBox.fromPosition(user, this, x, y, z));
  }

  @Override
  public void setRotation(float newRotationYaw, float newRotationPitch) {
    rotationYaw = newRotationYaw;
    rotationPitch = newRotationPitch;
    lookVector = vectorForRotation(rotationYaw, rotationPitch);
    float rotationYawInRadians = rotationYaw * (float) Math.PI / 180.0F;
    yawSine = sin(rotationYawInRadians);
    yawCosine = cos(rotationYawInRadians);
  }

  public void recheckWebStateFromLastTick() {
    if (!checkWebStateAgainNextTick) {
      return;
    }
    checkWebStateAgainNextTick = false;
    // only check if we missed ticks
    if (!receivedFlyingPacketIn(3)) {
      return;
    }
    // boundingbox from last tick!
    int blockPositionStartX = floor(boundingBox.minX + 0.001);
    int blockPositionStartY = floor(boundingBox.minY + 0.001);
    int blockPositionStartZ = floor(boundingBox.minZ + 0.001);
    int blockPositionEndX = floor(boundingBox.maxX - 0.001);
    int blockPositionEndY = floor(boundingBox.maxY - 0.001);
    int blockPositionEndZ = floor(boundingBox.maxZ - 0.001);

    inWeb = false;
    for (int x = blockPositionStartX; x <= blockPositionEndX; x++) {
      for (int y = blockPositionStartY; y <= blockPositionEndY; y++) {
        for (int z = blockPositionStartZ; z <= blockPositionEndZ; z++) {
          Material material = VolatileBlockAccess.typeAccess(user, x, y, z);
          if (material == BlockTypeAccess.WEB) {
            inWeb = true;
          }
        }
      }
    }
  }

  private Vector vectorForRotation(float yaw, float pitch) {
    float f = pitch * ((float) Math.PI / 180F);
    float f1 = -yaw * ((float) Math.PI / 180F);
    float f2 = cos(f1);
    float f3 = sin(f1);
    float f4 = cos(f);
    float f5 = sin(f);
    return new Vector(f3 * f4, -f5, (double) (f2 * f4));
  }

  public boolean hasElytraEquipped() {
    ItemStack plate = player.getInventory().getChestplate();
    //TODO: Check durability
    return plate != null && plate.getType() == Material.ELYTRA;
  }

  @Override
  public boolean areEyesInWater() {
    return this.eyesInWater;
  }

  @Override
  public void setEyesInWater(boolean eyesInWater) {
    this.eyesInWater = eyesInWater;
  }

  @Override
  public void setInteractingFluid(Fluid interactingFluid) {
    this.interactingFluid = interactingFluid;
  }

  private boolean flyingWithElytra(Player player) {
    return MinecraftVersions.VER1_9_0.atOrAbove() && canUseElytra(player) && player.isGliding();
  }

  private boolean canUseElytra(Player player) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    return clientData.canUseElytra();
  }

  public void manualPoseSet(Pose pose) {
    this.pose = pose;
    updatePose();
  }

  @Override
  public void setPose(Pose pose) {
    this.pose = pose;
    updateSize();
  }

  private float jumpUpwardsMotion() {
	  float jumpStrength = (float) user.meta().abilities().jumpStrength();
    if (Float.isNaN(jumpStrength) || jumpStrength < 0 || jumpStrength > 32) {
      jumpStrength = 0.42f;
    }
    return hasJumpFactor ? jumpStrength * jumpFactor() : jumpStrength;
  }

  private float jumpFactor() {
    float f = jumpFactorOf(VolatileBlockAccess.typeAccess(user, positionX, positionY, positionZ));
    float f1 = jumpFactorOf(frictionMaterial());
    return (double) f == 1.0D ? f1 : f;
  }

  private float jumpFactorOf(Material material) {
    return BlockProperties.of(material).jumpFactor();
  }

  @Override
  public void setSimulationResult(SimulationResult result) {
    this.beforeMoveCollider = result;
  }

  @Override
  public SimulationResult simulationResult() {
    return beforeMoveCollider;
  }

  @Override
  public MovementConfiguration lastMovementConfiguration() {
    return lastMovementConfiguration;
  }

  @Override
  public void setLastMovementConfiguration(MovementConfiguration configuration) {
    this.lastMovementConfiguration = configuration;
  }

  public boolean collidedWithBoat() {
    return nearestBoatLocation != null && distanceToVerifiedLocation(nearestBoatLocation) < 2;
  }

  public double distanceToVerifiedLocation(Location location) {
    double xDiff = Math.abs(verifiedLastPositionX - location.getX());
    double yDiff = Math.abs(verifiedLastPositionY - location.getY());
    double zDiff = Math.abs(verifiedLastPositionZ - location.getZ());
    return Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
  }

  public float eyeHeight() {
    return eyeHeight(pose);
  }

  public float eyeHeight(Pose pose) {
    float output;
    switch (pose) {
      case SWIMMING:
      case FALL_FLYING:
        output = 0.4f;
        break;
      case SLEEPING:
        output = 0.2f;
        break;
      case CROUCHING:
        output = 1.62f - user.meta().protocol().cameraSneakOffset();
        break;
      default:
        output = 1.62f;
        if (user.meta().movement().isInRidingVehicle()) {
          Entity vehicle = user.meta().movement().vehicle;
          if (vehicle.hasTypeData() && vehicle.typeData().isBoat()) {
	          output = (float) (output - (vehicle.typeData().mountedYOffset() + 0.35));
          }
        }
        break;
    }
    double scale = user.meta().abilities().scale();
    if (Double.isNaN(scale)) {
      scale = 1.0;
    }
	  output = (float) (output * scale);
    return output;
  }

  private void updateMovementMetaData() {
    MetadataBundle meta = user.meta();
    AbilityMetadata abilityData = meta.abilities();
    jumpMovementFactor = 0.02f;
    aiMoveSpeed = (float) abilityData.attributeValue("generic.movementSpeed", AbilityMetadata.EXCLUDE_SPRINT_MODIFIER);
    boolean factorAdditionRequired = meta.protocol().protocolVersion() >= 762 ? sprinting : lastSprinting;
    if (factorAdditionRequired) {
      jumpMovementFactor = (float) ((double) jumpMovementFactor + (double) 0.02f * 0.3d);
    }
  }

  public boolean blockOnPositionSoulSpeedAffected() {
    return BlockProperties.of(frictionMaterial()).soulSpeedAffected();
  }

  @Override
  public double fallDistance() {
    return artificialFallDistance;
  }

  @Override
  public void resetFallDistance() {
    artificialFallDistance = 0;
  }

  @Override
  public void addFallDistance(double fallDistance) {
    artificialFallDistance += (float) fallDistance;
  }

  private void updateEntityActionStates() {
    MetadataBundle meta = user.meta();
    AbilityMetadata abilities = meta.abilities();
    ProtocolMetadata protocol = meta.protocol();
    InventoryMetadata inventoryData = meta.inventory();
    sprintingAllowed = sprinting;
    if (sneaking && !protocol.canSprintWhileSneaking()) {
      sprintingAllowed = false;
    }
    boolean preventWaterSprint = protocol.aquaticUpdate() && inWater() && !shouldHaveSwimmingPose();
    if (inventoryData.inventoryOpen() || abilities.foodLevel <= 6 || preventWaterSprint) {
      sprintingAllowed = false;
    }
  }

  public boolean inLava() {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.aquaticUpdate()) {
      return aquaticUpdateInLava;
    } else {
      BoundingBox lavaBoundingBox = boundingBox().grow(
        -0.1f,
        -0.4000000059604645D,
        -0.1f
      );
      return Collision.rasterizedLiquidSearch(user, lavaBoundingBox, Fluid::isOfLava);
    }
  }

  @Override
  public boolean inWeb() {
    return inWeb;
  }

  @Override
  public void resetInWeb() {
    inWeb = false;
  }

  @Override
  public boolean onGround() {
    return onGround;
  }

  @Override
  public boolean lastOnGround() {
    return lastOnGround;
  }

  @Override
  public void setLastOnGround(boolean lastOnGround) {
    this.lastOnGround = lastOnGround;
  }

  @Override
  public boolean collidedHorizontally() {
    return collidedHorizontally;
  }

  @Override
  public boolean collidedVertically() {
    return collidedVertically;
  }

  public boolean receivedFlyingPacketIn(int ticks) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.emptyFlyingPacketsAreExplicitlySent()) {
      return ticksPast(FLYING_PACKET_CLIENT) <= ticks && ticksPast(FLYING_PACKET_ACCURATE) <= ticks;
    } else {
      return ticksPast(FLYING_PACKET_ACCURATE) <= ticks;
    }
  }

  @Deprecated
  @Override
  public boolean denyJump() {
    InventoryMetadata inventoryData = user.meta().inventory();
    if (inventoryData.inventoryOpen()) {
      return true;
    }
    // disable for 1.15+ clients
    if (user.meta().protocol().beeUpdate()) {
      return false;
    }
    int trustFactorSetting = user.trustFactorSetting("physics.joap-limit") + (REPLACE_JOAP_SETBACK_WITH_CM ? 1 : 0);
    return ticksPast(VELOCITY) == 0 && sprinting && lastVelocityApplicableForJumpDenial() && physicsJumpedOverrideVL >= trustFactorSetting;
  }

  public boolean lastVelocityApplicableForJumpDenial() {
    return lastVelocity != null && lastVelocity.horizontalLength() > 0.2;
  }

  public double baseMoveSpeed() {
    EffectMetadata potionData = user.meta().potions();
    int speedAmplifier = potionData.potionEffectSpeedAmplifier();
    double baseSpeed = 0.271;
    if (speedAmplifier != 0) {
      baseSpeed *= 1.0 + (0.4 * speedAmplifier);
    }
    if (sneaking) {
      baseSpeed *= 0.2;
    }
    return baseSpeed;
  }

  public void setSprinting(boolean sprinting) {
    this.sprinting = sprinting;
    activeTick(SPRINT_CHANGE);
//    this.sprinting = false;
    AbilityMetadata abilities = user.meta().abilities();
    Attribute movementSpeed = abilities.findAttribute("generic.movementSpeed");

    List<AttributeModifier> movementSpeedModifiers = abilities.modifiersOf(movementSpeed);
    if (sprinting) {
      if (!movementSpeedModifiers.contains(SPRINTING_MODIFIER)) {
        movementSpeedModifiers.add(SPRINTING_MODIFIER);
      }
    } else {
      movementSpeedModifiers.remove(SPRINTING_MODIFIER);
    }
  }

  public ShulkerBox shulkerBoxAt(int posX, int posY, int posZ) {
    if (shulkerData.isEmpty()) {
      return null;
    }
    int positionHash = posX << 12 | posY << 8 | posZ;
    ShulkerBox shulkerBox = shulkerDataHashCodeAccess.get(positionHash);
    if (shulkerBox != null) {
      return shulkerBox;
    }
    return shulkerData.get(new BlockPosition(posX, posY, posZ));
  }

  @Override
  public void activeTick(MoveMetric metric) {
    activeTracker.put(metric, ticks(metric) + 1);
    pastTracker.put(metric, 0);
  }

  @Override
  public void inactiveTick(MoveMetric metric) {
    activeTracker.put(metric, 0);
    pastTracker.put(metric, ticksPast(metric) + 1);
  }

  @Override
  public int reduceTicks() {
    return reduceTicks;
  }

  @Override
  public void resetPhysicsPacketRelinkFlyVL() {
    physicsPacketRelinkFlyVL = 0;
  }

  @Override
  public int ticks(MoveMetric metric) {
    return activeTracker.getOrDefault(metric, metric.activeDefault());
  }

	@Override
  public int ticksPast(MoveMetric metric) {
    return pastTracker.computeIfAbsent(metric, MoveMetric::pastDefault);
  }

	public void setPast(MoveMetric metric, int ticks) {
		pastTracker.put(metric, ticks);
	}

  @Override
  public void aquaticUpdateLavaReset() {
    aquaticUpdateInLava = false;
  }

  @Override
  public float height() {
    return height;
  }

  @Override
  public double heightRounded() {
    return heightRounded;
  }

  @Override
  public void setHeight(float height) {
    this.height = height;
    this.heightRounded = Math.round(height * 500d) / 1000d;
  }

  @Override
  public float width() {
    return width;
  }

  @Override
  public void setWidth(float width) {
    this.width = width;
    this.widthRounded = Math.round(width * 500d) / 1000d;
  }

  @Override
  public double widthRounded() {
    return widthRounded;
  }

  @Override
  public Fluid interactingFluid() {
    return interactingFluid;
  }

  @Override
  public void assumeOccurred(Simulation simulation) {
    SimulationResult collider = simulation.result();
    onGround = collider.onGround();
    collidedHorizontally = collider.collidedHorizontally();
    collidedVertically = collider.collidedVertically();
    physicsResetMotionX = collider.resetMotionX();
    physicsResetMotionZ = collider.resetMotionZ();

    MovementConfiguration configuration = simulation.configuration();
    keyForward = configuration.forward();
    keyStrafe = configuration.strafe();
    physicsJumped = configuration.isJumping();

    boolean step = collider.step();
	  stepHeightThisMove = step ? collider.stepHeightThisMove() : 0;
    if (step) {
      activeTick(STEP);
    }
    if (collider.edgeSneak()) {
      activeTick(EDGE_SNEAKING);
    }
    double physicsUncertainty = user.meta().protocol().flyingPacketUncertaintyRadius();
    if (simulation.resultsInFlyingPacket(this, physicsUncertainty)) {
      activeTick(FLYING_PACKET_ACCURATE);
    }
//    if (user.meta().protocol().newBlockEntityIntersectionLogic()) {
//    }
    // <tolerances>
    Motion offsetMotion = simulation.offsetMotion();
    Motion actualMotion = simulation.actualMotion();
    if (offsetMotion.horizontalDistance(actualMotion) > 0.1) {
      maskedMotionTolerance.set(
        actualMotion.motionX,
        actualMotion.motionZ
      );
    }
    // </tolerances>
    // <performance>
    branchFrequency.merge(simulation.branchIdentifier(), 1L, Long::sum);
    if (branchFrequencyTrimCounter++ % 10000 == 0) {
      branchFrequency.replaceAll((s, aLong) -> {
        aLong = aLong / 3;
        return aLong <= 0 ? 1L : aLong;
      });
      branchFrequency.entrySet().removeIf(entry -> entry.getValue() <= 10);
    }

    // </performance>
    setLastMovementConfiguration(configuration);
    setSimulationResult(collider);
  }

  @Override
  public void tickComplete(
    boolean hasMovement,
    boolean hasRotation,
    boolean isRealClientTick
  ) {
    step = false;
    reduceTicks = 0;
    invalidMovement = false;
    treatThisFlyPacketAsMovePacket = false;
    legacyVehicleKeyInput = false;
    suspiciousMovement = false;
    ignoredAttackReduce = false;
    isTeleportConfirmationPacket = false;
    dropPostTickMotionProcessing = false;
    physicsUnpredictableVelocityExpected = false;
    lastSprinting = sprinting;
    lastSneaking = sneaking;
    if (isRealClientTick) {
      currentTick++;
      tickAmbiguousUpdates.removeIf(tau -> tau.expired(this));
      worldBorder.tick();
    }

    // <tolerances>

    if (shulkerXToleranceRemaining > 0) {
      shulkerXToleranceRemaining--;
    }
    if (shulkerYToleranceRemaining > 0) {
      shulkerYToleranceRemaining--;
      if (shulkerYToleranceRemaining == 0) {
        highestShulkerY = Integer.MIN_VALUE;
        lowestShulkerY = Integer.MAX_VALUE;
      }
    }
    if (shulkerZToleranceRemaining > 0) {
      shulkerZToleranceRemaining--;
    }

    if (pistonMotionToleranceRemaining > 0) {
      pistonMotionToleranceRemaining--;
    }

    maskedMotionTolerance.afterTick(
      offsetMotionX(),
      offsetMotionZ()
    );

    // </tolerances>

    activeTick(
      ALIVE
    );

    tick(IN_WEB, inWeb());
    tick(IN_WATER, inWater());
    tick(SNEAKING, isSneaking());
    tick(SPRINTING, isSprinting());
    tick(TELEPORT, isTeleportConfirmationPacket);
    tick(ELYTRA_FLYING, gliding);
    tick(INVENTORY_OPEN, user.meta().inventory().inventoryOpen());

    inactiveTick(
      STEP,
      IN_LAVA,
      VELOCITY,
      EDGE_SNEAKING,
      SPRINT_CHANGE,
      LONG_TELEPORT,
      BLOCK_PLACEMENT,
      FIREWORK_ROCKETS,
      VEHICLE_ATTACHMENT,
      VEHICLE_DETACHMENT,
      RECEIVED_VELOCITY_PACKET
    );

    if (hasMovement || hasRotation) {
      inactiveTick(EXTERNAL_VELOCITY);
    }

    updateSize();

    // misc
    if (ticks(SNEAKING) > 1) {
      lastTimeSneaking = System.currentTimeMillis();
    }
    if (physicsJumped) {
      lastTimeJumped = System.currentTimeMillis();
    }

    shulkerCleanup();
  }

  @Override
  public long currentTick() {
    return currentTick;
  }

  public void queueTickAmbiguousUpdate(TickAmbiguousUpdate update) {
    tickAmbiguousUpdates.add(update);
  }

  @Override
  public List<TickAmbiguousUpdate> allTickAmbiguousUpdates() {
    return Collections.unmodifiableList(tickAmbiguousUpdates);
  }

  @Override
  public long activeSequence() {
    return activeSequence;
  }

  @Override
  public void setActiveSequence(long activeSequence) {
    this.activeSequence = activeSequence;
  }

  private final Lock currentSequenceLock = new ReentrantLock();

  public long newSequenceNumber() {
    try {
      currentSequenceLock.lock();
      currentSequence++;
      return currentSequence;
    } finally {
      currentSequenceLock.unlock();
    }
  }

  @Override
  public void setTreatThisFlyPacketAsMovePacket(boolean treatThisFlyPacketAsMovePacket) {
    this.treatThisFlyPacketAsMovePacket = treatThisFlyPacketAsMovePacket;
  }

  @Override
  public BlockPosition mainSupportingBlockPos() {
    return mainSupportingBlockPos;
  }

  @Override
  public void setMainSupportingBlockPos(BlockPosition mainSupportingBlockPos) {
    this.mainSupportingBlockPos = mainSupportingBlockPos;
  }

  @Override
  public boolean onGroundNoBlocks() {
    return onGroundNoBlocks;
  }

  @Override
  public void setOnGroundNoBlocks(boolean onGroundNoBlocks) {
    this.onGroundNoBlocks = onGroundNoBlocks;
  }

  private void shulkerCleanup() {
    if (!shulkerData.isEmpty()) {
      int shulkerLimit = 2048;
      for (Iterator<BlockPosition> iterator = shulkers.iterator(); iterator.hasNext(); ) {
        if (shulkerLimit-- <= 0) {
          break;
        }
        BlockPosition shulkerBlock = iterator.next();
        ShulkerBox shulkerBox = shulkerData.get(shulkerBlock);
        if (shulkerBox == null) {
          iterator.remove();
          continue;
        }
        if (shulkerBox.complete()) {
          iterator.remove();
          shulkerData.remove(shulkerBlock);
          shulkerDataHashCodeAccess.remove(shulkerBox.hashCode());
        } else if (shulkerBox.shouldTick()) {
          shulkerBox.tick();
        }
      }
    }
  }

  @Override
  public Material collideMaterial() {
    return collideMaterial;
  }

  @Override
  public Material frictionMaterial() {
    return frictionMaterial;
  }

  @Override
  public Material previousCollideMaterial() {
    return previousCollideMaterial;
  }

  @Override
  public Material previousFrictionMaterial() {
    return previousFrictionMaterial;
  }

  @Override
  public void setCollideMaterial(Material collideMaterial) {
    this.collideMaterial = collideMaterial;
  }

  @Override
  public void setFrictionMaterial(Material frictionMaterial) {
    this.frictionMaterial = frictionMaterial;
  }

  @Override
  public void setPreviousCollideMaterial(Material previousCollideMaterial) {
    this.previousCollideMaterial = previousCollideMaterial;
  }

  @Override
  public void setPreviousFrictionMaterial(Material previousFrictionMaterial) {
    this.previousFrictionMaterial = previousFrictionMaterial;
  }

  public boolean isInVehicle() {
    return vehicle != null;
  }

  public Entity vehicle() {
    return vehicle;
  }

  public boolean isInRidingVehicle() {
    return vehicle != null && vehicleCanBeRidden;
  }

  public boolean isRiding(int entityId) {
    return vehicle != null && vehicle.entityId() == entityId;
  }

  public Entity ridingEntity() {
    return vehicle;
  }

  @Deprecated
  public Location verifiedLocation() {
    return verifiedLocation;
  }

  public double offsetMotionX() {
    return motionX;
  }

  public double offsetMotionY() {
    return motionY;
  }

  public double offsetMotionZ() {
    return motionZ;
  }

  @Override
  public List<Motion> postTickMotionCandidates() {
    return Collections.unmodifiableList(postTickMotionCandidates);
  }

  @Override
  public void setPostTickMotionCandidates(@NotNull List<Motion> postTickMotionCandidates) {
    this.postTickMotionCandidates = postTickMotionCandidates;
  }

  @Override
  public Motion mutableBaseMotionCopy() {
    return new Motion(baseMotionX, baseMotionY, baseMotionZ);
  }

  @Override
  public double baseMotionX() {
    return baseMotionX;
  }

  @Override
  public double baseMotionY() {
    return baseMotionY;
  }

  @Override
  public double baseMotionZ() {
    return baseMotionZ;
  }

  @Override
  public void setBaseMotion(Motion baseMotion) {
    this.baseMotionX = baseMotion.motionX();
    this.baseMotionY = baseMotion.motionY();
    this.baseMotionZ = baseMotion.motionZ();
  }

  @Override
  public void setBaseMotion(double baseMotionX, double baseMotionY, double baseMotionZ) {
    this.baseMotionX = baseMotionX;
    this.baseMotionY = baseMotionY;
    this.baseMotionZ = baseMotionZ;
  }

  @Override
  public boolean motionXReset() {
    return physicsResetMotionX;
  }

  @Override
  public void setMotionResetX(boolean reset) {
    physicsResetMotionX = reset;
  }

  @Override
  public boolean motionZReset() {
    return physicsResetMotionZ;
  }

  @Override
  public void setMotionResetZ(boolean reset) {
    physicsResetMotionZ = reset;
  }

  public Motion sentOffsetMotion() {
    return new Motion(motionX, motionY, motionZ);
  }

  public BoundingBox boundingBox() {
    return boundingBox;
  }

  public double resetMotion() {
    return resetMotion;
  }

  @Override
  public int fireworkRocketsPower() {
    return fireworkRocketsPower;
  }

  @Override
  public int shulkerXToleranceRemaining() {
    return shulkerXToleranceRemaining;
  }

  @Override
  public int shulkerYToleranceRemaining() {
    return shulkerYToleranceRemaining;
  }

  @Override
  public int shulkerZToleranceRemaining() {
    return shulkerZToleranceRemaining;
  }

  @Override
  public int lowestShulkerY() {
    return lowestShulkerY;
  }

  @Override
  public int highestShulkerY() {
    return highestShulkerY;
  }

  @Override
  public int pistonMotionToleranceRemaining() {
    return pistonMotionToleranceRemaining;
  }

  @Override
  public double pistonVerticalAllowance() {
    return pistonVerticalAllowance;
  }

  @Override
  public double pistonHorizontalAllowance() {
    return pistonHorizontalAllowance;
  }

  @Override
  public BoundingBox pistonCollisionArea() {
    return pistonCollisionArea;
  }

  @Override
  public boolean physicsUnpredictableVelocityExpected() {
    return physicsUnpredictableVelocityExpected;
  }

  @Override
  public boolean enforceBoatStep() {
    return enforceBoatStep;
  }

  @Override
  public void setEnforceBoatStep(boolean enforceBoatStep) {
    this.enforceBoatStep = enforceBoatStep;
  }

  @Override
  public BoatSimulator.Status boatStatus() {
    return boatStatus;
  }

  @Override
  public void setBoatStatus(BoatSimulator.Status boatStatus) {
    this.boatStatus = boatStatus;
  }

  @Override
  public BoatSimulator.Status previousBoatStatus() {
    return previousBoatStatus;
  }

  @Override
  public void setPreviousBoatStatus(BoatSimulator.Status previousBoatStatus) {
    this.previousBoatStatus = previousBoatStatus;
  }

  @Override
  public float boatGlide() {
    return boatGlide;
  }

  @Override
  public void setBoatGlide(float boatGlide) {
    this.boatGlide = boatGlide;
  }

  @Override
  public double boatWaterLevel() {
    return waterLevel;
  }

  @Override
  public void setBoatWaterLevel(double boatWaterLevel) {
    this.waterLevel = boatWaterLevel;
  }

  @Override
  public int physicsPacketRelinkFlyVL() {
    return physicsPacketRelinkFlyVL;
  }

  @Override
  public void setPhysicsPacketRelinkFlyVL(int physicsPacketRelinkFlyVL) {
    this.physicsPacketRelinkFlyVL = physicsPacketRelinkFlyVL;
  }

  @Override
  public boolean lastSneaking() {
    return lastSneaking;
  }

  @Override
  public boolean currentlyInBlock() {
    return currentlyInBlock;
  }

  @Override
  public int highestLocalRiptideLevel() {
    return highestLocalRiptideLevel;
  }

  @Override
  public boolean onGroundWithRiptide() {
    return onGroundWithRiptide;
  }

  public double jumpMotion() {
    return jumpMotion;
  }

  @Override
  public void setJumpMotion(double jumpMotion) {
    this.jumpMotion = jumpMotion;
  }

  @Override
  public boolean isJumping() {
    return physicsJumped;
  }

  @Override
  public double gravity() {
    return gravity;
  }

  @Override
  public boolean isSneaking() {
    return sneaking;
  }

  @Override
  public void setSneaking(boolean sneaking) {
    this.sneaking = sneaking;
  }

  @Override
  public boolean isSprinting() {
    return sprinting;
  }

  @Override
  public boolean lastSprinting() {
    return lastSprinting;
  }

  @Override
  public void setLastSprinting(boolean lastSprinting) {
    this.lastSprinting = lastSprinting;
  }

  @Override
  public boolean isSleeping() {
    return sleepingBedPosition != null;
  }

  @Override
  public void setSleeping(boolean sleeping) {
    if (!sleeping) {
      sleepingBedPosition = null;
    }
  }

  @Override
  public boolean hasSprintSpeed() {
    return hasSprintSpeed;
  }

  @Override
  public boolean inWater() {
    return inWater;
  }

  @Override
  public void setInWater(boolean inWater) {
    this.inWater = inWater;
    if (inWater) {
      artificialFallDistance = 0;
    }
  }

  @Deprecated
  public float aiMoveSpeed() {
    return aiMoveSpeed;
  }

  public float aiMoveSpeed(boolean sprinting) {
    return sprinting ? aiMoveSpeed * 1.3f : aiMoveSpeed;
  }

  public float jumpMovementFactor() {
    return jumpMovementFactor;
  }

  @Deprecated
  // Override on vehicle movement
  public void setJumpMovementFactor(float jumpMovementFactor) {
    this.jumpMovementFactor = jumpMovementFactor;
  }

  @Override
  public Simulator simulator() {
    return simulator;
  }

  @Override
  public void setSimulator(Simulator simulator) {
    this.simulator = simulator;
  }

  @Override
  public Pose pose() {
    return pose;
  }

  @Override
  public User user() {
    return user;
  }

  @Override
  public double positionX() {
    return positionX;
  }

  @Override
  public double positionY() {
    return positionY;
  }

  @Override
  public double positionZ() {
    return positionZ;
  }

  @Override
  public double verifiedLastPositionX() {
    return verifiedLastPositionX;
  }

  @Override
  public double verifiedLastPositionY() {
    return verifiedLastPositionY;
  }

  @Override
  public double verifiedLastPositionZ() {
    return verifiedLastPositionZ;
  }

  @Override
  public void setVerifiedLastPosition(Position position, String reason) {
    this.verifiedLastPositionX = position.getX();
    this.verifiedLastPositionY = position.getY();
    this.verifiedLastPositionZ = position.getZ();
    this.verifiedPositionOrigin = reason;
  }

  @Override
  public double lastPositionX() {
    return lastPositionX;
  }

  @Override
  public double lastPositionY() {
    return lastPositionY;
  }

  @Override
  public double lastPositionZ() {
    return lastPositionZ;
  }

  @Override
  public void setLastPosition(Position position) {
    this.lastPositionX = position.getX();
    this.lastPositionY = position.getY();
    this.lastPositionZ = position.getZ();
  }

  @Override
  public void setLastPosition(double x, double y, double z) {
    this.lastPositionX = x;
    this.lastPositionY = y;
    this.lastPositionZ = z;
  }

  @Override
  public boolean sprintingAllowed() {
    return sprintingAllowed;
  }

  @Override
  public boolean shouldHaveFallFlyingPose() {
    return gliding;
  }

  public float friction(boolean sprinting) {
    return resolveFriction(user, this, sprinting, verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ);
  }

  @Override
  public double stepHeight() {
    return stepHeight;
  }

  @Override
  public void setStepHeight(float stepHeight) {
    this.stepHeight = stepHeight;
  }

  public float frictionMultiplier() {
    return frictionMultiplier;
  }

  public Rotation rotation() {
    return new Rotation(rotationYaw, rotationPitch);
  }

  @Override
  public float rotationYaw() {
    return rotationYaw;
  }

  public float yawSine() {
    return yawSine;
  }

  public float yawCosine() {
    return yawCosine;
  }

  @Override
  public float rotationPitch() {
    return rotationPitch;
  }

  public Rotation lastRotation() {
    return new Rotation(lastRotationYaw, lastRotationPitch);
  }

  public float lastRotationYaw() {
    return lastRotationYaw;
  }

  public float lastRotationPitch() {
    return lastRotationPitch;
  }

  @Override
  public void setLastRotation(float lastRotationYaw, float lastRotationPitch) {
    this.lastRotationYaw = lastRotationYaw;
    this.lastRotationPitch = lastRotationPitch;
  }

  @Override
  public Vector lookVector() {
    return lookVector;
  }

  public double frictionPosSubtraction() {
    return frictionPosSubtraction;
  }

  @Nullable
  public Vector motionMultiplier() {
    return motionMultiplier;
  }

  public void setBoundingBox(BoundingBox entityBoundingBox) {
    if (!boundingBoxSetup) {
      setupDefaults();
    }
    this.boundingBox = entityBoundingBox;
  }

  @Override
  public void setMotionMultiplier(Vector motionMultiplier) {
    this.artificialFallDistance = 0f;
    this.motionMultiplier = motionMultiplier;
  }

  public void resetMotionMultiplier() {
    this.motionMultiplier = null;
  }

  @Override
  public @NotNull WorldBorder border() {
    return worldBorder;
  }

  @Override
  public void setWorldBorder(@NotNull WorldBorder worldBorder) {
	  this.worldBorder = worldBorder;
  }

  public void setVerifiedLocation(Location verifiedLocation) {
    this.verifiedLocation = verifiedLocation;
  }

  public double estimatedAttachMovement() {
    if (ticksPast(VEHICLE_ATTACHMENT) > 1) {
      return 0;
    }
    return attachMoveDistance * 1.25;
  }

  public void setVehicle(Entity ridingEntity) {
    activeTick(VEHICLE_ATTACHMENT);
    this.invalidVehiclePositionTicks = 0;
    this.attachMoveDistance = ridingEntity.distanceTo(lastPosition().toBukkitVec());
    this.vehicle = ridingEntity;

    String entityName = ridingEntity.entityName();
    List<String> rideableVehicleNames = Arrays.asList("Boat", "Minecart", "Pig", "Horse", "Camel", "Llama");
    this.vehicleCanBeRidden = rideableVehicleNames.stream().anyMatch(s -> entityName.toLowerCase().contains(s.toLowerCase()));

    if (IntaveControl.DEBUG_MOUNTING) {
      player.sendMessage(ChatColor.RED + "Mounting " + ridingEntity.entityName() + " " + MathHelper.formatDouble(attachMoveDistance, 4) + " blocks away");
    }

    if (user.receives(MessageChannel.DEBUG_MOUNTS)) {
      player.sendMessage(IntavePlugin.prefix() + "Mounting " + ridingEntity.entityName() + " " + MathHelper.formatDouble(attachMoveDistance, 4) + " blocks away");
    }
  }

  public void dismountRidingEntity() {
    dismountRidingEntity("Non reason specified");
  }

  public void dismountRidingEntity(String reason) {
    dismountRidingEntity(reason, true);
  }

  public void dismountRidingEntity(String reason, boolean positionReset) {
    if (!isInVehicle()) {
      return;
    }
    if (IntaveControl.DEBUG_MOUNTING) {
      player.sendMessage(ChatColor.RED + "Dismounting " + vehicle.entityName() + " " + reason);
      System.out.println("Dismounting " + vehicle.entityName() + " " + reason);
      Thread.dumpStack();
    }
    setVerifiedLocation(player.getLocation());
    if (positionReset) {
      Synchronizer.synchronize(() -> {
        // player.getLocation() is assumed to be correct
        player.teleport(player.getLocation());
        if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
          player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " because " + ChatColor.RED + " you dismounted a vehicle");
        }
      });
    }
    if (user.receives(MessageChannel.DEBUG_MOUNTS)) {
      player.sendMessage(IntavePlugin.prefix() + "Unmounting " + vehicle.entityName() + " for " + reason.toLowerCase() + " " + (positionReset ? "(with position reset)" : ""));
    }
    activeTick(VEHICLE_DETACHMENT);
    this.vehicle = null;
  }

  @Override
  public void setPushedByEntity(boolean pushedByEntity) {
    this.pushedByEntity = pushedByEntity;
  }

  @Override
  public boolean pushedByEntity() {
    return pushedByEntity;
  }

  private final SimulationEnvironment unmodifiableView = SimulationEnvironment.super.immutableView();

  @Override
  public SimulationEnvironment immutableView() {
    return unmodifiableView;
  }
}
