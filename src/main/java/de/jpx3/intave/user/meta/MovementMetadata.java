package de.jpx3.intave.user.meta;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedAttributeModifier;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.physics.*;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.entity.datawatcher.DataWatcherAccess;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.dispatch.MovementDispatcher;
import de.jpx3.intave.module.feedback.Superposition;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.comphenix.protocol.wrappers.WrappedAttributeModifier.Operation.ADD_PERCENTAGE;
import static de.jpx3.intave.check.movement.physics.MovementCharacteristics.resolveFriction;
import static de.jpx3.intave.reflect.access.ReflectiveHandleAccess.handleOf;
import static de.jpx3.intave.share.ClientMathHelper.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_14;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_15;

@Relocate
public final class MovementMetadata implements SimulationEnvironment {
  public static final WrappedAttributeModifier SPRINTING_MODIFIER = WrappedAttributeModifier.newBuilder(UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D")).amount(0.3F).operation(ADD_PERCENTAGE).name("Sprint Boost").build();
  private static final boolean ELYTRA_ENABLED = MinecraftVersions.VER1_9_0.atOrAbove();
  private final Player player;
  private final User user;
  // superposition
  private final Superposition<Motion> velocitySuperposition;
  private final List<Superposition<?>> superpositions;
  public boolean disabledFlying;
  public float width = 0.6f, height = 1.8f;
  public float stepHeight = 0.6f;
  public double widthRounded, heightRounded;
  public boolean elytraFlying;
  public int fireworkRocketsTicks = 100;
  public int fireworkRocketsPower = 1;
  public boolean onGround, lastOnGround, step, onGroundWithRiptide;
  public boolean collidedHorizontally, collidedVertically;
  public float artificialFallDistance;
  public boolean allowFallDamage;
  public double gravity;
  public boolean outsideBorder = true;
  public Motion motionProcessorContext = new Motion();
  public Vector lookVector = new Vector();
  public double verifiedPositionX, verifiedPositionY, verifiedPositionZ;
  public double lastPositionX, lastPositionY, lastPositionZ;
  public double positionX, positionY, positionZ;
  public boolean sprinting, lastSprinting, /*sprintMove, lastSprintMove,*/ hasSprintSpeed, sneaking, lastSneaking;
  public int sprintSneakFaults;
  public boolean acceptSneakFaults = true;
  public int sneakingTicks;
  public float rotationYaw, rotationPitch;
  public float lastRotationYaw, lastRotationPitch;
  public long recordedMoves;
  // Timestamps
  public long lastSneakingTimestamps, lastJump, lastMovement, lastRotation;
  public Vector emulationVelocity;
  public Vector sneakPatchVelocity;
  public Vector setbackOverrideVelocity = new Vector(0, 0, 0);
  public Vector lastVelocity = new Vector();
  public boolean canResetMotion;
  public int pastNearbyCollisionInaccuracy = 10;
  public float frictionMultiplier;
  public float genericMovementSpeedAttribute;
  public int lastPositionUpdate;
  @Nullable
  public Fluid interactingFluid;
  public boolean inRespawnScreen;
  public boolean inWater;
  public boolean inWeb;
  public boolean checkWebStateAgainNextTick = false;
  public int pastPushedByWaterFlow = 100;
  public int pastElytraFlying = 100, pastVelocity = 100, pastExternalVelocity = 100, pastExternalVelocityResetCache, pastInWeb = 100, pastWaterMovement = 100;
  public int pastLongTeleport = 100;
  public int pastInventoryOpen = 100;
  public int pastBlockPlacement = 100;
  public int pastEdgeSneak = 100;
  public int pastEntityUse = 100;
  public boolean onLadderLast;
  public boolean aquaticUpdateInLava;
  public boolean sprintResetNextTick;
  public AtomicInteger pendingVelocityPackets = new AtomicInteger();
  public int physicsPacketRelinkFlyVL; // In Air
  public boolean invalidMovement, suspiciousMovement;
  public double baseMotionX, baseMotionY, baseMotionZ; // base or last motion, exclusively for the physics check
  public double baseMotionXBeforeVelocity, baseMotionYBeforeVelocity, baseMotionZBeforeVelocity;
  public double baseMotionXResetCache, baseMotionYResetCache, baseMotionZResetCache;
  public double baseMotionXBeforeVelocityResetCache, baseMotionYBeforeVelocityResetCache, baseMotionZBeforeVelocityResetCache;
  public boolean endMotionXOverride, endMotionYOverride, endMotionZOverride;
  public double endMotionXOverrideValue, endMotionYOverrideValue, endMotionZOverrideValue;
  public int pastRiptideSpin = 100;
  public int highestLocalRiptideLevel = 0;
  public int pastPlayerAttackPhysics = 100;
  public int pastInPowderSnow = 100;
  public int pastEdgeSneakTickGrants;
  public boolean physicsResetMotionX, physicsResetMotionZ;
  public int keyForward, keyStrafe;
  public int lastKeyForward, lastKeyStrafe;
  public boolean ignoredAttackReduce = false;
  public int shulkerXToleranceRemaining;
  public int shulkerYToleranceRemaining;
  public int shulkerZToleranceRemaining;
  public int lowestShulkerY = Integer.MAX_VALUE, highestShulkerY = Integer.MIN_VALUE;
  public Set<Motion> toleratedPistonMotions = new HashSet<>();
  public int pistonMotionToleranceRemaining;
  public List<BlockPosition> shulkers = new ArrayList<>();
  public Map<BlockPosition, ShulkerBox> shulkerData = new HashMap<>();
  public Map<Integer, ShulkerBox> shulkerDataHashCodeAccess = new HashMap<>();
  // Will be set to true if the player sends a flying packet and receives server velocity later
  public boolean physicsUnpredictableVelocityExpected;
  // Jump prevention
  public boolean physicsJumped;
  public double physicsJumpedOverrideVL;
  // If the player changes his hotbar slot the slot change packet will be sent *after* the movement
  // To prevent a slot switch if the player changes his slot by itself we have to check if the movement is 2x wrong
  // If the player does not have an active use-item this field will be set to 0
  public int physicsEatingSlotSwitchVL;
  // Phase prevention
  public List<BoundingBox> phaseIntersectingBoundingBoxes;
  public boolean currentlyInBlock;
  // Entity collision
  public boolean enforceBoatStep;
  public volatile Location nearestBoatLocation = null;
  public int attachVehicleTicks = 100;
  public float boatGlide, momentum;
  public double waterLevel;
  public BoatSimulator.Status boatStatus = BoatSimulator.Status.ON_LAND,
  previousBoatStatus = BoatSimulator.Status.ON_LAND;
  public boolean isTeleportConfirmationPacket;
  public boolean dropPostTickMotionProcessing;
  public boolean willReceiveSetbackVelocity;
  public boolean willReceiveSetbackVelocityResetCache;
  public int lastTeleport;
  public int teleportId;
  public volatile boolean awaitTeleport = false, expectTeleport = false, awaitOutgoingTeleport = false;
  public volatile boolean expectTeleportWithRotation = false;
  public volatile boolean transactionTeleportAllow = false;
  public boolean awaitClickMovementSkip;
  public Location teleportLocation;
  public Vector teleportOffset = null;
  public int teleportResendCountdown = 20;
  public int outgoingTeleportCountdown = 5;
  public long lastRescueAttempt;
  public int speculativeTicks = 0;
  public Map<UUID, Integer> pendingSpeculativeMovementTicks = GarbageCollector.watch(new HashMap<>());
  public boolean inReceiveSpeculativePacketRoutine = false;
  public double speculativeMotionX, speculativeMotionY, speculativeMotionZ;
  public double speculativePositionX, speculativePositionY, speculativePositionZ;
  public boolean speculationEnded = false;
  public int speculativeLowThresholdOverflows;
  public boolean inSpeculation = false;
  // States if an external entity push onto the player is estimated
  public boolean pushedByEntity;
  // Key inputs sent by the client
  public boolean externalKeyApply = false;
  public int clientForwardKey = 0;
  public int clientStrafeKey = 0;
  public boolean clientPressedJump = false;
  private volatile WeakReference<Object> nmsWorld;
  private boolean hasJumpFactor;
  private double resetMotion, frictionPosSubtraction;
  private double motionX, motionY, motionZ;
  private boolean sprintingAllowed;
  private float yawSine, yawCosine, friction;
  private Pose pose = Pose.STANDING;
  private Simulator simulator = Simulators.PLAYER;
  private Material blockOnPosition = Material.AIR;
  private volatile BoundingBox boundingBox = BoundingBox.fromBounds(0, 0, 0, 0, 0, 0);
  private boolean boundingBoxSetup = false;
  @Nullable
  private Vector motionMultiplier = null;
  private double jumpMotion;
  private int pastClientFlyingPacket;
  public int pastFlyingPacketAccurate;
  private float aiMoveSpeed, jumpMovementFactor;
  private boolean eyesInWater;
  // Vehicle
  private Entity vehicle;
  private boolean vehicleCanBeRidden;
  private double attachMoveDistance;
  private volatile Location verifiedLocation;

  public MovementMetadata(Player player, User user) {
    this.player = player;
    this.user = user;
    this.velocitySuperposition = Superposition
      .builderFor(Motion.class)
      .apply(MovementDispatcher::applyVelocitySuperposition)
      .collapse(MovementDispatcher::collapseVelocitySuperposition)
      .reset(MovementDispatcher::resetVelocitySuperposition)
      .overrideMerge()
      .user(user)
      .timeout(2)
      .build();
    if (Physics.USE_SUPERPOSITIONS) {
      superpositions = ImmutableList.of(velocitySuperposition);
    } else {
      superpositions = ImmutableList.of();
    }
  }

  public void setup() {
    if (player != null) {
      Synchronizer.synchronize(() -> this.elytraFlying = flyingWithElytra(player));
    }
    applyPlayerStats();
    updateWorld();
    applyPlayerLocation();
  }

  public void setupDefaults() {
    ProtocolMetadata clientData = user.meta().protocol();
    int version = clientData.protocolVersion();
    this.resetMotion = version <= 47 ? 0.005 : 0.003;
    this.frictionMultiplier = version <= VER_1_15 ? 0.16277136f : 0.16277137F;
    this.frictionPosSubtraction = version <= VER_1_15 ? 1.0 : 0.5000001;
    this.hasJumpFactor = version >= VER_1_15;
    if (!boundingBoxSetup) {
      Location location = player.getLocation();
      boundingBox = BoundingBox.fromPosition(user, location.getX(), location.getY(), location.getZ());
      boundingBoxSetup = true;
      // just a default non-null value
      teleportLocation = location;
    }
  }

  private void applyPlayerLocation() {
    Location location;
    if (player == null) {
      location = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
    } else {
      location = player.getLocation();
      artificialFallDistance = player.getFallDistance();
    }
    verifiedLocation = location.clone();
    positionX = location.getX();
    positionY = location.getY();
    positionZ = location.getZ();
    verifiedPositionX = positionX;
    verifiedPositionY = positionY;
    verifiedPositionZ = positionZ;
    updateSize();
  }

  private void applyPlayerStats() {
    if (player == null) {
      return;
    }
    setSprinting(player.isSprinting());
    sneaking = player.isSneaking();
  }

  public void updateWorld() {
    if (player == null) {
      nmsWorld = new WeakReference<>(handleOf(Bukkit.getWorlds().get(0)));
      return;
    }
    nmsWorld = new WeakReference<>(handleOf(player.getWorld()));
  }

  @DispatchTarget
  public void updateMovement(
    PacketContainer packet,
    boolean hasMovement, boolean hasRotation
  ) {
    if (!boundingBoxSetup) {
      setupDefaults();
    }
    jumpMotion = MovementCharacteristics.jumpMotionFor(player, jumpUpwardsMotion());
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;
    if (sprintResetNextTick) {
      DataWatcherAccess.setDataWatcherFlag(player, DataWatcherAccess.WATCHER_SPRINT_ID, true);
      sprintResetNextTick = false;
    }
    if (hasMovement) {
      StructureModifier<Double> position = packet.getDoubles();
      positionX = position.read(0);
      positionY = position.read(1);
      positionZ = position.read(2);
      blockOnPosition = VolatileBlockAccess.typeAccess(user, player.getWorld(), positionX, positionY - frictionPosSubtraction, positionZ);
      motionX = positionX - verifiedPositionX;
      motionY = positionY - verifiedPositionY;
      motionZ = positionZ - verifiedPositionZ;
      boolean falling = motionY() <= 0.0D;
      if (falling && Effects.slowFallingEffectActive(player)) {
        artificialFallDistance = 0f;
        gravity = 0.01D;
      } else {
        gravity = 0.08D;
      }
      updateEntityActionStates();
      updateMovementMetaData();
    } else {
      pastClientFlyingPacket = 0;
      if (hasRotation) {
        motionX = positionX - verifiedPositionX;
        motionY = positionY - verifiedPositionY;
        motionZ = positionZ - verifiedPositionZ;
        blockOnPosition = VolatileBlockAccess.typeAccess(user, player.getWorld(), positionX, positionY - frictionPosSubtraction, positionZ);
        updateEntityActionStates();
        updateMovementMetaData();
      }
    }
    lastRotationYaw = rotationYaw;
    lastRotationPitch = rotationPitch;
    if (hasRotation) {
      StructureModifier<Float> rotation = packet.getFloat();
      rotationYaw = rotation.read(0);
      rotationPitch = rotation.read(1);
      lookVector = vectorForRotation(rotationYaw, rotationPitch);
      float rotationYawInRadians = rotationYaw * (float) Math.PI / 180.0F;
      yawSine = sin(rotationYawInRadians);
      yawCosine = cos(rotationYawInRadians);
    }
    recheckWebStateFromLastTick();
    updateEntityMovement();

    updatePose();
    updateSlotSwitch();
  }

  private void updateSlotSwitch() {
    InventoryMetadata inventory = user.meta().inventory();
    InventoryMetadata.SlotSwitchData slotSwitchData = inventory.slotSwitchData;
    if (slotSwitchData != null) {
      int slot = slotSwitchData.slot();
      ItemStack item = slotSwitchData.item();

      boolean handActive = ItemProperties.canItemBeUsed(player, item) && inventory.handActive();
      if (handActive) {
        inventory.activateHand();
      } else {
        inventory.deactivateHand();
      }
      inventory.setHeldItemSlot(slot);
      inventory.pastHotBarSlotChange = 0;

      inventory.slotSwitchData = null;
    }
  }

  private void recheckWebStateFromLastTick() {
    if (!checkWebStateAgainNextTick) {
      return;
    }
    checkWebStateAgainNextTick = false;
    // only check if we missed ticks
    if (!recentlyEncounteredFlyingPacket(6)) {
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

  private void updateEntityMovement() {
    ConnectionMetadata connectionMetadata = user.meta().connection();
    for (Entity value : connectionMetadata.entities()) {
      value.entityPlayerMoveUpdate();
    }
//    for (Map.Entry<Integer, WrappedEntity> entry : entityMap.entrySet()) {
//      WrappedEntity entity = entry.getValue();
//      entity.entityPlayerMoveUpdate();
//    }
  }

  public void updateEyesInWater() {
    double yPos = positionY + eyeHeight() - (double) 0.11111f;
    this.eyesInWater = interactingFluid != null && interactingFluid.isOfWater();
    this.interactingFluid = null;

    Fluid fluid = Fluids.fluidAt(user, positionX, yPos, positionZ);
    if (fluid.isOfWater()) {
      double d1 = (float) floor(yPos) + 1.0f;
      if (d1 > yPos) {
        this.interactingFluid = fluid;
      }
    }
  }

  public boolean areEyesInWater() {
    return this.eyesInWater;
  }

  public void updatePose() {
    boolean modernPose = user.protocolVersion() >= VER_1_14;
    Pose pose;
    if (modernPose) {
      if (this.isPoseClear(Pose.SWIMMING)) {
        if (isSwimming(user)) {
          pose = Pose.SWIMMING;
        } else if (elytraFlying) {
          pose = Pose.FALL_FLYING;
        } else if (player.isSleeping()) {
          pose = Pose.SLEEPING;
        } else if (poseSneaking(user)) {
          pose = Pose.CROUCHING;
        } else {
          pose = Pose.STANDING;
        }

        Pose pose1;
        if (!this.isPoseClear(pose)) {
          if (this.isPoseClear(Pose.CROUCHING)) {
            pose1 = Pose.CROUCHING;
          } else {
            pose1 = Pose.SWIMMING;
          }
        } else {
          pose1 = pose;
        }

        this.pose = pose1;
      }
    } else {
      if (isSwimming(user)) {
        pose = Pose.SWIMMING;
      } else if (player.isSleeping()) {
        pose = Pose.SLEEPING;
      } else if (elytraFlying) {
        pose = Pose.FALL_FLYING;
      } else if (poseSneaking(user)) {
        pose = Pose.CROUCHING;
      } else {
        pose = Pose.STANDING;
      }
      this.pose = pose;
    }

    updateSize();
  }

  private boolean flyingWithElytra(Player player) {
    return ELYTRA_ENABLED && canUseElytra(player) && player.isGliding();
  }

  private boolean canUseElytra(Player player) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    return clientData.canUseElytra();
  }

  private boolean isSwimming(User user) {
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    ProtocolMetadata protocol = meta.protocol();
    if (!protocol.swimmingMechanics()) {
      return false;
    }
    boolean sprinting = movement.lastSprinting;
    boolean swimming = movement.pose() == Pose.SWIMMING;
    if (swimming) {
      return sprinting && movement.inWater;
    } else {
      return sprinting && ((movement.pose() == Pose.FALL_FLYING && movement.inWater) || movement.areEyesInWater());
    }
  }

  public boolean poseSneaking(User user) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    InventoryMetadata inventoryData = meta.inventory();
    boolean sneakingAllowed = movementData.sneaking && !inventoryData.inventoryOpen();
    boolean actualSneaking;
    if (clientData.delayedSneak()) {
      actualSneaking = movementData.lastSneaking;
    } else if (clientData.alternativeSneak()) {
      actualSneaking = movementData.lastSneaking || sneakingAllowed;
    } else {
      actualSneaking = sneakingAllowed;
    }
    return actualSneaking;
  }

  public void setPose(Pose pose) {
    this.pose = pose;
    updatePose();
  }

  public void overridePose(Pose pose) {
    this.pose = pose;
  }

  private void updateSize() {
    width = pose.width(user);
    height = pose.height(user);
    widthRounded = Math.round(width * 500d) / 1000d;
    heightRounded = Math.round(height * 100d) / 100d;
  }

  private boolean isPoseClear(Pose pose) {
    return Collision.nonePresent(user.player(), pose.boundingBoxOf(user).shrink(0.0000001));
  }

  private float jumpUpwardsMotion() {
    return hasJumpFactor ? 0.42f * jumpFactor() : 0.42f;
  }

  private float jumpFactor() {
    World world = player.getWorld();
    float f = jumpFactorOf(VolatileBlockAccess.typeAccess(user, world, positionX, positionY, positionZ));
    float f1 = jumpFactorOf(blockOnPosition());
    return (double) f == 1.0D ? f1 : f;
  }

  private float jumpFactorOf(Material material) {
    return BlockProperties.of(material).jumpFactor();
  }

  public boolean collidedWithBoat() {
    return nearestBoatLocation != null && distanceToVerifiedLocation(nearestBoatLocation) < 2;
  }

  public double distanceToVerifiedLocation(Location location) {
    double xDiff = Math.abs(verifiedPositionX - location.getX());
    double yDiff = Math.abs(verifiedPositionY - location.getY());
    double zDiff = Math.abs(verifiedPositionZ - location.getZ());
    return Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
  }

  public float eyeHeight() {
    switch (pose) {
      case SWIMMING:
      case FALL_FLYING:
        return 0.4f;
      case SLEEPING:
        return 0.2f;
      case CROUCHING:
        return 1.62f - user.meta().protocol().cameraSneakOffset();
      default:
        return 1.62f;
    }
  }

  @DispatchTarget
  public void applyGroundInformationToPacket(PacketContainer packet) {
    packet.getBooleans().write(0, onGround);
  }

  private void updateMovementMetaData() {
    MetadataBundle meta = user.meta();
    AbilityMetadata abilityData = meta.abilities();
    jumpMovementFactor = 0.02f;
    aiMoveSpeed = (float) abilityData.attributeValue("generic.movementSpeed", AbilityMetadata.EXCLUDE_SPRINT_MODIFIER);
    if (lastSprinting) {
      jumpMovementFactor = (float) ((double) jumpMovementFactor + (double) 0.02f * 0.3d);
    }
  }

  public void refreshFriction(boolean sprinting) {
    friction = resolveFriction(user, sprinting, verifiedPositionX, verifiedPositionY, verifiedPositionZ);
  }

  public boolean blockOnPositionSoulSpeedAffected() {
    return BlockProperties.of(blockOnPosition()).soulSpeedAffected();
  }

  private void updateEntityActionStates() {
    MetadataBundle meta = user.meta();
    AbilityMetadata abilities = meta.abilities();
    ProtocolMetadata clientData = meta.protocol();
    InventoryMetadata inventoryData = meta.inventory();
    sprintingAllowed = sprinting;
//    sprintingAllowed = true;
    if (sneaking && !clientData.sprintWhenSneaking()) {
      sprintingAllowed = false;
    }
    if (inventoryData.inventoryOpen()) {
      sprintingAllowed = false;
    }
    if (abilities.foodLevel <= 6) {
      sprintingAllowed = false;
    }
  }

//  public boolean sprintingIsAllowed() {
//    MetadataBundle meta = user.meta();
//    AbilityMetadata abilities = meta.abilities();
//    ProtocolMetadata clientData = meta.protocol();
//    InventoryMetadata inventoryData = meta.inventory();
//    return (!sneaking || clientData.sprintWhenSneaking()) && !inventoryData.inventoryOpen() && abilities.foodLevel > 6;
//  }

  public boolean inLava() {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.waterUpdate()) {
      return aquaticUpdateInLava;
    } else {
      BoundingBox lavaBoundingBox = boundingBox.grow(
        -0.1f,
        -0.4000000059604645D,
        -0.1f
      );
      return MovementCharacteristics.isLavaInBB(user, player.getWorld(), lavaBoundingBox);
    }
  }

  @Override
  public boolean inWeb() {
    return inWeb;
  }

  @Override
  public boolean onGround() {
    return onGround;
  }

  @Override
  public boolean lastOnGround() {
    return lastOnGround;
  }

  public boolean recentlyEncounteredFlyingPacket(int ticks) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.flyingPacketsAreSent()) {
      return pastClientFlyingPacket <= ticks && pastFlyingPacketAccurate <= ticks;
    } else {
      return pastFlyingPacketAccurate <= ticks;
    }
  }

  public boolean denyJump() {
    InventoryMetadata inventoryData = user.meta().inventory();
    if (inventoryData.inventoryOpen()) {
      return true;
    }
    int trustFactorSetting = user.trustFactorSetting("physics.joap-limit");
    return pastVelocity == 0 && sprinting && lastVelocityApplicableForJumpDenial() && physicsJumpedOverrideVL >= trustFactorSetting;
  }

  public boolean lastVelocityApplicableForJumpDenial() {
    return lastVelocity != null && lastVelocity.clone().setY(0).length() > 0.2;
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

  public void sprintReset() {
    InventoryMetadata inventoryData = user.meta().inventory();
    // really required
    if (player.getFoodLevel() >= 6 && !inventoryData.inventoryOpen()) {
      DataWatcherAccess.setDataWatcherFlag(player, DataWatcherAccess.WATCHER_SPRINT_ID, false);
      sprintResetNextTick = true;
    }
  }

  public void setSprinting(boolean sprinting) {
    this.sprinting = sprinting;
//    this.sprinting = false;
    AbilityMetadata abilities = user.meta().abilities();
    WrappedAttribute movementSpeed = abilities.findAttribute("generic.movementSpeed");

//    player.sendMessage(ChatColor.GOLD + "Sprint-toggle to: " + sprinting);

    List<WrappedAttributeModifier> movementSpeedModifiers = abilities.modifiersOf(movementSpeed);
    if (sprinting) {
      //
      if (!movementSpeedModifiers.contains(SPRINTING_MODIFIER)) {
//        player.sendMessage(ChatColor.RED + "Added Sprinting Modifier");
        movementSpeedModifiers.add(SPRINTING_MODIFIER);
      }
    } else {
//      player.sendMessage(ChatColor.RED + "Removed Sprinting Modifier");
      movementSpeedModifiers.remove(SPRINTING_MODIFIER);
    }
  }

  public Superposition<Motion> velocitySuperposition() {
    return velocitySuperposition;
  }

  public List<Superposition<?>> superpositions() {
    return superpositions;
  }

  public ShulkerBox shulkerBoxAt(int posX, int posY, int posZ) {
    if (shulkerData.isEmpty()) {
      return null;
    }
    int positionHash = posX << 12 | posY << 8 | posZ;
    if (shulkerDataHashCodeAccess.containsKey(positionHash)) {
      return shulkerDataHashCodeAccess.get(positionHash);
    }
    return shulkerData.get(new BlockPosition(posX, posY, posZ));
  }

  public void resetFlyingPacketAccurate() {
    pastFlyingPacketAccurate = 0;
  }

  public void increaseFlyingPacket() {
    pastFlyingPacketAccurate++;
    pastClientFlyingPacket++;
    pastNearbyCollisionInaccuracy++;
  }

  private Material blockOnPosition() {
    return blockOnPosition;
  }

  public boolean isInVehicle() {
    return vehicle != null;
  }

  public boolean isInRidingVehicle() {
    return vehicle != null && vehicleCanBeRidden;
  }

  public Entity ridingEntity() {
    return vehicle;
  }

  public Object nmsWorld() {
    return nmsWorld.get();
  }

  public Location verifiedLocation() {
    return verifiedLocation;
  }

  public double motionX() {
    return motionX;
  }

  public double motionY() {
    return motionY;
  }

  public double motionZ() {
    return motionZ;
  }

  public Motion motion() {
    return new Motion(motionX, motionY, motionZ);
  }

  public BoundingBox boundingBox() {
    return boundingBox;
  }

  public double resetMotion() {
    return resetMotion;
  }

  public double jumpMotion() {
    return jumpMotion;
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
  public boolean inWater() {
    return inWater;
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
  public void setJumpMovementFactor(float jumpMovementFactor, boolean sprinting) {
    this.jumpMovementFactor = jumpMovementFactor;
//    friction = MovementHelper.resolveFriction(user, sprinting, verifiedPositionX, verifiedPositionY, verifiedPositionZ);
    refreshFriction(sprinting);
  }

//  @Deprecated
//  public void setAiMoveSpeed(float aiMoveSpeed) {
//    this.aiMoveSpeed = aiMoveSpeed;
////    friction = MovementHelper.resolveFriction(user, sprinting, verifiedPositionX, verifiedPositionY, verifiedPositionZ);
//    refreshFriction(sprinting);
//  }

  public int pastFlyingPacketAccurate() {
    return pastFlyingPacketAccurate;
  }

  public Simulator simulator() {
    return simulator;
  }

  public Pose pose() {
    return pose;
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
  public double verifiedPositionX() {
    return verifiedPositionX;
  }

  @Override
  public double verifiedPositionY() {
    return verifiedPositionY;
  }

  @Override
  public double verifiedPositionZ() {
    return verifiedPositionZ;
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

  public boolean sprintingAllowed() {
    return sprintingAllowed;
  }

  public float friction() {
    return friction;
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

  public void setMotionMultiplier(Vector motionMultiplier) {
    this.artificialFallDistance = 0f;
    this.motionMultiplier = motionMultiplier;
  }

  public void resetMotionMultiplier() {
    this.motionMultiplier = null;
  }

  public void setVerifiedLocation(Location verifiedLocation, @SuppressWarnings("unused") String reason) {
/*    boolean boundingBoxIntersection = Collision.checkBoundingBoxIntersection(user, Collision.boundingBoxOf(user, verifiedLocation));
    if (boundingBoxIntersection) {
      Bukkit.broadcastMessage(ChatColor.DARK_RED + "Position was set into a block: " + reason);
    }*/
    this.verifiedLocation = verifiedLocation;
  }

  public void setSimulator(Simulator simulator) {
    this.simulator = simulator;
  }

  public void setPastFlyingPacketAccurate(int pastFlyingPacketAccurate) {
    this.pastFlyingPacketAccurate = pastFlyingPacketAccurate;
  }

  public double estimatedAttachMovement() {
    if (this.attachVehicleTicks > 1) {
      return 0;
    }
    return attachMoveDistance * 2;
  }

  public void setVehicle(Entity ridingEntity) {
    this.attachVehicleTicks = 0;
    this.attachMoveDistance = ridingEntity.distanceTo(lastPosition());
    this.vehicle = ridingEntity;

    String entityName = ridingEntity.entityName();
    this.vehicleCanBeRidden = entityName.contains("Boat") || entityName.contains("Minecart") || entityName.contains("Pig") || entityName.contains("Horse");

    if (IntaveControl.DEBUG_MOUNTING) {
      player.sendMessage(ChatColor.RED + "Mounting " + ridingEntity.entityName() + " " + MathHelper.formatDouble(attachMoveDistance, 4) + " blocks away");
    }
  }

  public void dismountRidingEntity() {
    dismountRidingEntity("Non reason specified");
  }

  public void dismountRidingEntity(String reason) {
    if (!isInVehicle()) {
      return;
    }
    if (IntaveControl.DEBUG_MOUNTING) {
      player.sendMessage(ChatColor.RED + "Dismounting " + vehicle.entityName() + " " + reason);
      System.out.println("Dismounting " + vehicle.entityName() + " " + reason);
      Thread.dumpStack();
    }
    setVerifiedLocation(player.getLocation(), "Entity dismount location");
    Synchronizer.synchronize(() -> {
      // player.getLocation() is assumed to be correct
      player.teleport(player.getLocation());
    });
    this.vehicle = null;
  }
}