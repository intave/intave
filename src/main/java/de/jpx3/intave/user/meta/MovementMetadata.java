package de.jpx3.intave.user.meta;

import com.comphenix.protocol.wrappers.BlockPosition;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.tick.ShulkerBox;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.physics.*;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.world.interaction.BlockTrustChain;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.RateLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.share.*;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static de.jpx3.intave.IntaveControl.REPLACE_JOAP_SETBACK_WITH_CM;
import static de.jpx3.intave.check.movement.physics.MoveMetric.*;
import static de.jpx3.intave.check.movement.physics.MovementCharacteristics.resolveFriction;
import static de.jpx3.intave.player.attribute.AttributeModifier.Operation.ADD_PERCENTAGE;
import static de.jpx3.intave.share.ClientMath.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.*;

public final class MovementMetadata implements SimulationEnvironment {
  public static final AttributeModifier SPRINTING_MODIFIER = AttributeModifier.newBuilder(
    UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D")
  ).withAmount(0.3F).withOperation(ADD_PERCENTAGE).withName("Sprint Boost").build();
  private final Player player;
  private final User user;
  public final BlockTrustChain placementTrustChain = new BlockTrustChain();
  public final Map<String, Double> serverMovementDebugValues = new HashMap<>();
  public final Map<String, Double> clientMovementDebugValues = new HashMap<>();
  public float width = 0.6f, height = 1.8f;
  public float stepHeight = 0.6f;
  public double stepHeightThisMove = 0d;
  public double widthRounded, heightRounded;
  public boolean elytraFlying;
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
  public long lastTimeSneaking, lastTimeJumped, lastMovement, lastRotation;
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
  public double baseMotionX, baseMotionY, baseMotionZ; // base or last motion, exclusively for the physics check
  public double baseMotionXBeforeVelocity, baseMotionYBeforeVelocity, baseMotionZBeforeVelocity;
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
  public float boatGlide, momentum;
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
  public boolean forceCorrectReduce = false;
  public double invalidReduceVL = 0;
  public double lastRespawnX, lastRespawnY, lastRespawnZ;
  public boolean allowRespawnLeniency = false;
  private boolean hasJumpFactor;
  private double resetMotion, frictionPosSubtraction;
  private double motionX, motionY, motionZ;
  private boolean sprintingAllowed;
  private float yawSine = 0, yawCosine = 1, friction;
  private Pose pose = Pose.STANDING;
  private Simulator simulator = Simulators.PLAYER;
  @Nullable
  public Position mainSupportingBlockPos = null;
	private Material frictionMaterial = Material.AIR, previousFrictionMaterial = Material.AIR;
  private Material collideMaterial = Material.AIR, previousCollideMaterial = Material.AIR;

  private ColliderResult beforeMoveCollider = null;

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
  private volatile Location verifiedLocation;
  public Input input = Input.none();
  public Input lastInput = Input.none();

  private final Map<MoveMetric, Integer> activeTracker = new EnumMap<>(MoveMetric.class);
  private final Map<MoveMetric, Integer> pastTracker = new EnumMap<>(MoveMetric.class);

  {
    for (MoveMetric value : MoveMetric.values()) {
      activeTracker.put(value, value.activeDefault());
      pastTracker.put(value, value.pastDefault());
    }
  }

  public MovementMetadata(Player player, User user) {
    this.player = player;
    this.user = user;
  }

  public void setup() {
    if (player != null) {
      Synchronizer.synchronize(user, () -> {
        if (player.hasMetadata("intave.testplayer.gliding")) {
          this.elytraFlying = player.getMetadata("intave.testplayer.gliding").get(0).asBoolean();
        } else {
          this.elytraFlying = flyingWithElytra(player);
        }
      });
    }
    applyPlayerStats();
    applyPlayerLocation();
  }

  public void setupDefaults() {
    ProtocolMetadata clientData = user.meta().protocol();
    int version = clientData.protocolVersion();
    this.resetMotion = version <= VER_1_8 ? 0.005 : 0.003;
    this.frictionMultiplier = version <= VER_1_15 ? 0.16277136f : 0.16277137F;
    this.frictionPosSubtraction = version <= VER_1_15 ? 1.0 : 0.5000001;
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
    sneaking = player.isSneaking();
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
      boolean falling = motionY() <= 0.0D;
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

  private void setRotation(float newRotationYaw, float newRotationPitch) {
    rotationYaw = newRotationYaw;
    rotationPitch = newRotationPitch;
    lookVector = vectorForRotation(rotationYaw, rotationPitch);
    float rotationYawInRadians = rotationYaw * (float) Math.PI / 180.0F;
    yawSine = sin(rotationYawInRadians);
    yawCosine = cos(rotationYawInRadians);
  }

  @Override
  public void checkSupportingBlock(Motion motion) {
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    if (clientData.trailsAndTailsUpdate()) {
      Position block;
      BoundingBox boundingBox = BoundingBox.fromPosition(user, this, positionX, positionY, positionZ);
      BoundingBox secondBoundingBox = new BoundingBox(
        boundingBox.minX, boundingBox.minY - 0.000001, boundingBox.minZ,
        boundingBox.maxX, boundingBox.minY, boundingBox.maxZ
      );
      block = findSupportingBlock(user, secondBoundingBox);
      if (block == null) {
        BoundingBox thirdBoundingBox = secondBoundingBox.move(-motion.motionX, 0.0, -motion.motionZ);
        block = findSupportingBlock(user, thirdBoundingBox);
      }
      mainSupportingBlockPos = block;
    }
  }

  public void compileSpecialBlocks() {
    previousCollideMaterial = collideMaterial;
    collideMaterial = compileCollideBlock();
    previousFrictionMaterial = frictionMaterial;
    frictionMaterial = compileFrictionBlock();
  }

  private Material compileCollideBlock() {
    return compileBlockBelow(0.2f);
  }

  private Material compileFrictionBlock() {
    return compileBlockBelow(frictionPosSubtraction);
  }

  // formally Entity#getOnPos
  private Material compileBlockBelow(double reduction) {
    if (player == null) {
      return Material.AIR;
    }

    World world = player.getWorld();
    int blockCollisionPosX = floor(positionX);
    int blockCollisionPosY = floor(positionY - reduction);
    int blockCollisionPosZ = floor(positionZ);
    if (mainSupportingBlockPos != null) {
      // 1.20
      Material blockType = VolatileBlockAccess.typeAccess(
        user, player.getWorld(), mainSupportingBlockPos
      );
      if (reduction > 0.00001f) {
        String typeName = blockType.name();
        if (reduction <= 0.5D && typeName.contains("FENCE")) {
          return blockType;
        }
        if (typeName.contains("FENCE") || typeName.contains("WALL")) {
          return blockType;
        }
        return VolatileBlockAccess.typeAccess(
          user, player.getWorld(),
          mainSupportingBlockPos.getBlockX(),
          blockCollisionPosY,
          mainSupportingBlockPos.getBlockZ()
        );
      } else {
        return blockType;
      }
    } else {
      // 1.8 - 1.19
      Material blockType = VolatileBlockAccess.typeAccess(
        user, player.getWorld(),
        positionX, positionY - reduction, positionZ
      );
      ProtocolMetadata clientData = user.meta().protocol();
      if (blockType == Material.AIR && !clientData.trailsAndTailsUpdate()) {
        Material blockBelow = VolatileBlockAccess.typeAccess(user, world, blockCollisionPosX, blockCollisionPosY, blockCollisionPosZ);
        if (blockBelow.name().contains("FENCE") || blockBelow.name().contains("WALL")) {
          blockType = blockBelow;
        }
      }
      return blockType;
    }
  }

  @Nullable
  private Position findSupportingBlock(
    User user, BoundingBox box
  ) {
    Position block = null;
    int blockX = 0, blockY = 0, blockZ = 0;
    double distance = Double.MAX_VALUE;

    int startX = ClientMath.floor(box.minX - 0.0000001) - 1;
    int endX = ClientMath.floor(box.maxX + 0.0000001) + 1;
    int startY = ClientMath.floor(box.minY - 0.0000001) - 1;
    int endY = ClientMath.floor(box.maxY + 0.0000001) + 1;
    int startZ = ClientMath.floor(box.minZ - 0.0000001) - 1;
    int endZ = ClientMath.floor(box.maxZ + 0.0000001) + 1;

    double positionX = positionX();
    double positionY = positionY();
    double positionZ = positionZ();

    CubeIterator iterator = new CubeIterator(startX, startY, startZ, endX, endY, endZ);
    while (iterator.advance()) {
      int x = iterator.nextX();
      int y = iterator.nextY();
      int z = iterator.nextZ();
      int type = iterator.nextType();
      if (type == CubeIterator.TYPE_CORNER) {
        continue;
      }
      BlockShape shape = user.blockCache().collisionShapeAt(x, y, z);
      if (shape.isEmpty()) {
        continue;
      } else if (shape.isCubic() && !box.intersectsWith(BoundingBox.fromBounds(x, y, z, x + 1, y + 1, z + 1))) {
        continue;
      } else if (!shape.isCubic() && !shape.intersectsWith(box)) {
        continue;
      }
      double distanceToCenter = distanceToCenter(x, y, z, positionX, positionY, positionZ);
      int comparison = compare(x, y, z, blockX, blockY, blockZ);
      if (distanceToCenter < distance || (distanceToCenter == distance && comparison < 0)) {
        blockX = x;
        blockY = y;
        blockZ = z;
        block = Position.of(blockX, blockY, blockZ);
        distance = distanceToCenter;
      }
    }
    return block;
  }

  private int compare(
    int alphaX, int alphaY, int alphaZ,
    int betaX, int betaY, int betaZ
  ) {
    if (alphaY == betaY) {
      return alphaZ == betaZ ? alphaX - betaX : alphaZ - betaZ;
    } else {
      return alphaY - betaY;
    }
  }

  private double distanceToCenter(
    int blockX, int blockY, int blockZ,
    double entityX, double entityY, double entityZ
  ) {
    double d0 = blockX + 0.5 - entityX;
    double d1 = blockY + 0.5 - entityY;
    double d2 = blockZ + 0.5 - entityZ;
    return d0 * d0 + d1 * d1 + d2 * d2;
  }

  private void updateSlotSwitch() {
    InventoryMetadata inventory = user.meta().inventory();
    InventoryMetadata.SlotSwitchData slotSwitchData = inventory.slotSwitchData;
    if (slotSwitchData != null) {
      int slot = slotSwitchData.slot();
      ItemStack item = slotSwitchData.item();

      boolean primaryItemUsable = ItemProperties.canItemBeUsed(player, item);
      boolean offhandItemUsage = ItemProperties.canItemBeUsed(player, inventory.offhandItem());
      boolean handActive = (primaryItemUsable || offhandItemUsage) && inventory.handActive();
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

  public void updateEyesInWater() {
    double yPos = positionY + eyeHeight() - (double) 0.11111f;
    this.eyesInWater = interactingFluid != null && interactingFluid.isOfWater();
    this.interactingFluid = null;

    Fluid fluid = VolatileBlockAccess.fluidAccess(user, positionX, yPos, positionZ);
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
    return MinecraftVersions.VER1_9_0.atOrAbove() && canUseElytra(player) && player.isGliding();
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

  public void manualPoseSet(Pose pose) {
    this.pose = pose;
    updatePose();
  }

  private void updateSize() {
    width = pose.width(user);
    height = pose.height(user);
    widthRounded = Math.round(width * 500d) / 1000d;
    heightRounded = Math.round(height * 100d) / 100d;
  }

  private boolean isPoseClear(Pose pose) {
    return Collision.nonePresent(user, this, pose.boundingBoxOf(user).shrink(0.0000001));
  }

  private float jumpUpwardsMotion() {
	  float jumpStrength = (float) user.meta().abilities().attributeValue("generic.jump_strength");
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

  private final Material bubbleColumnMaterial = Material.getMaterial("BUBBLE_COLUMN");

  // Entity.getBlockSpeedFactor @ 1.19
  public float blockSpeedFactor() {
    if (user.meta().protocol().trailsAndTailsUpdate()) {
      Material material = VolatileBlockAccess.typeAccess(user, positionX, positionY, positionZ);
      float f = blockSpeedFactorOf(material);
      if (!MaterialMagic.isWater(material) && material != bubbleColumnMaterial && material != null) {
        if (Math.abs(f - 1.0f) < 0.00001f) {
          return blockSpeedFactorOf(frictionMaterial());
        }
      }
      return f;
    } else {
      return blockSpeedFactorOf(frictionMaterial());
    }
  }

  private float blockSpeedFactorOf(Material material) {
    return BlockProperties.of(material).speedFactor();
  }

  @Override
  public void setBeforeMoveColliderResult(ColliderResult result) {
    this.beforeMoveCollider = result;
  }

  @Override
  public ColliderResult beforeMoveColliderResult() {
    return beforeMoveCollider;
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
    float output = 0;
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
        break;
    }
    double scale = user.meta().abilities().attributeValue("generic.scale");
    if (Double.isNaN(scale)) {
      scale = 1.0;
    }
    output *= scale;
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

  public void refreshFriction(boolean sprinting) {
    friction = resolveFriction(user, sprinting, verifiedLastPositionX, verifiedLastPositionY, verifiedLastPositionZ);
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

  private void updateEntityActionStates() {
    MetadataBundle meta = user.meta();
    AbilityMetadata abilities = meta.abilities();
    ProtocolMetadata protocol = meta.protocol();
    InventoryMetadata inventoryData = meta.inventory();
    sprintingAllowed = sprinting;
    if (sneaking && !protocol.canSprintWhileSneaking()) {
      sprintingAllowed = false;
    }
    boolean preventWaterSprint = protocol.waterUpdate() && inWater && !isSwimming(user);
    if (inventoryData.inventoryOpen() || abilities.foodLevel <= 6 || preventWaterSprint) {
      sprintingAllowed = false;
    }
  }

  public boolean inLava() {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.waterUpdate()) {
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
  public boolean collidedHorizontally() {
    return collidedHorizontally;
  }

  @Override
  public boolean collidedVertically() {
    return collidedVertically;
  }

  public boolean receivedFlyingPacketIn(int ticks) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.flyingPacketsAreSent()) {
      return ticksPast(FLYING_PACKET_CLIENT) <= ticks && ticksPast(FLYING_PACKET_ACCURATE) <= ticks;
    } else {
      return ticksPast(FLYING_PACKET_ACCURATE) <= ticks;
    }
  }

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
  public void resetPhysicsPacketRelinkFlyVL() {
    physicsPacketRelinkFlyVL = 0;
  }

  @Override
  public int ticks(MoveMetric metric) {
    return activeTracker.getOrDefault(metric, metric.activeDefault());
  }

	@Override
  public int ticksPast(MoveMetric metric) {
    return pastTracker.getOrDefault(metric, metric.pastDefault());
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
  public float width() {
    return width;
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
    ColliderResult collider = simulation.collider();
    onGround = collider.onGround();
    collidedHorizontally = collider.collidedHorizontally();
    collidedVertically = collider.collidedVertically();
    physicsResetMotionX = collider.resetMotionX();
    physicsResetMotionZ = collider.resetMotionZ();
    boolean step = collider.step();
	  stepHeightThisMove = step ? collider.stepHeightThisMove() : 0;
    if (step) {
      activeTick(STEP);
    }
    if (collider.edgeSneak()) {
      activeTick(EDGE_SNEAKING);
    }
    if (user.meta().protocol().newBlockEntityIntersectionLogic()) {
      setBeforeMoveColliderResult(collider);
    }
  }

  @Override
  public void tickComplete(
    boolean hasMovement,
    boolean hasRotation
  ) {
    step = false;
    reduceTicks = 0;
    invalidMovement = false;
    externalKeyApply = false;
    suspiciousMovement = false;
    ignoredAttackReduce = false;
    isTeleportConfirmationPacket = false;
    dropPostTickMotionProcessing = false;
    physicsUnpredictableVelocityExpected = false;
    lastSprinting = sprinting;
    lastSneaking = sneaking;

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

    tick(IN_WEB, inWeb());
    tick(IN_WATER, inWater());
    tick(SNEAKING, isSneaking());
    tick(SPRINTING, isSprinting());
    tick(TELEPORT, isTeleportConfirmationPacket);
    tick(ELYTRA_FLYING, elytraFlying);
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

    width = pose.width(user);
    height = pose.height(user);

    // misc
    if (ticks(SNEAKING) > 1) {
      lastTimeSneaking = System.currentTimeMillis();
    }
    if (physicsJumped) {
      lastTimeJumped = System.currentTimeMillis();
    }

    shulkerCleanup();
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

  public double motionX() {
    return motionX;
  }

  public double motionY() {
    return motionY;
  }

  public double motionZ() {
    return motionZ;
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
  public boolean motionZReset() {
    return physicsResetMotionZ;
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
  public void setJumpMotion(double jumpMotion) {
    this.jumpMotion = jumpMotion;
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
  public boolean isSprinting() {
    return sprinting;
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
  public void setJumpMovementFactor(float jumpMovementFactor, boolean sprinting) {
    this.jumpMovementFactor = jumpMovementFactor;
    refreshFriction(sprinting);
  }

  public Simulator simulator() {
    return simulator;
  }

  @Override
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

  public boolean sprintingAllowed() {
    return sprintingAllowed;
  }

  public float friction() {
    return friction;
  }

  @Override
  public double stepHeight() {
    return stepHeight;
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

  public void setVerifiedLocation(Location verifiedLocation) {
    this.verifiedLocation = verifiedLocation;
  }

  public void setSimulator(Simulator simulator) {
    this.simulator = simulator;
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
      Synchronizer.synchronize(user, () -> {
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

  private final SimulationEnvironment unmodifiableView = SimulationEnvironment.super.unmodifiable();

  @Override
  public SimulationEnvironment unmodifiable() {
    return unmodifiableView;
  }
}
