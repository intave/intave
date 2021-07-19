package de.jpx3.intave.user;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.detect.checks.movement.physics.SimulationProcessor;
import de.jpx3.intave.detect.checks.movement.physics.SimulationEngines;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.reflect.ReflectiveDataWatcherAccess;
import de.jpx3.intave.reflect.ReflectiveHandleAccess;
import de.jpx3.intave.tools.annotate.Nullable;
import de.jpx3.intave.tools.client.*;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockphysic.BlockProperties;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.fluid.FluidTag;
import de.jpx3.intave.world.fluid.Fluids;
import de.jpx3.intave.world.fluid.WrappedFluid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

import static de.jpx3.intave.user.UserMetaClientData.VER_1_15;

public final class UserMetaMovementData {
  private final Player player;
  private final User user;
  private volatile Object nmsWorld;

  private boolean hasJumpFactor;

  public boolean disabledFlying;
  public float width = 0.6f, height = 1.8f;
  public double widthRounded, heightRounded;
  private double resetMotion, frictionPosSubtraction;

  @Deprecated
  // Use #pose for checking a player's pose
  public boolean elytraFlying;
  public boolean fireworkTolerant;

  public boolean onGround, lastOnGround, step;
  public boolean collidedHorizontally, collidedVertically;
  public float artificialFallDistance;
  public boolean allowFallDamage;
  public double gravity;
  public boolean outsideBorder = true;

  public MotionVector motionProcessorContext = new MotionVector();
  public Vector lookVector = new Vector();
  public double verifiedPositionX, verifiedPositionY, verifiedPositionZ;
  public double lastPositionX, lastPositionY, lastPositionZ;
  public double positionX, positionY, positionZ;
  private double motionX, motionY, motionZ;
  public boolean sprinting, lastSprinting, sneaking, lastSneaking;
  private boolean sprintingAllowed;
  private float yawSine, yawCosine, friction;
  public float rotationYaw, rotationPitch;
  public float lastRotationYaw, lastRotationPitch;
  private Pose pose = Pose.STANDING;
  private SimulationEngines simulationEngines = SimulationEngines.PLAYER;
  private final SimulationProcessor.IterativeSimulationContext iterativeSimulation = new SimulationProcessor.IterativeSimulationContext();
  private Material blockOnPosition = Material.AIR;

  // Timestamps
  public long lastSneakingTimestamps, lastJumpTimestamps;

  private volatile WrappedAxisAlignedBB boundingBox;
  public Vector emulationVelocity;
  public Vector setbackOverrideVelocity = new Vector(0, 0, 0);
  public Vector lastVelocity = new Vector();
  @Nullable
  private Vector motionMultiplier = null;
  public boolean canResetMotion;
  private double jumpMotion;
  private int pastClientFlyingPacket, pastFlyingPacketAccurate;
  private float aiMoveSpeed, jumpMovementFactor;
  public float genericMovementSpeedAttribute;
  @Nullable
  public WrappedFluid interactingFluid;
  public boolean inWater;
  public boolean inWeb;
  private boolean eyesInWater;
  public int pastPushedByWaterFlow = 100;
  public int pastElytraFlying = 100, pastVelocity = 100, pastExternalVelocity = 100, pastInWeb = 100, pastWaterMovement = 100;
  public int pastLongTeleport = 100;
  public int pastInventoryOpen = 100;
  public boolean onLadderLast;
  public boolean aquaticUpdateInLava;
  public boolean sprintResetNextTick;

  public int physicsPacketRelinkFlyVL; // In Air
  public boolean invalidMovement, suspiciousMovement;
  public double physicsMotionX, physicsMotionY, physicsMotionZ;
  public double physicsMotionXBeforeVelocity, physicsMotionYBeforeVelocity, physicsMotionZBeforeVelocity;
  public int pastRiptideSpin = 100;
  public int pastPlayerAttackPhysics = 100;
  public boolean physicsResetMotionX, physicsResetMotionZ;
  public int keyForward, keyStrafe;
  public int lastKeyForward, lastKeyStrafe;
  public boolean ignoredAttackReduce = false;

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
  public List<WrappedAxisAlignedBB> phaseIntersectingBoundingBoxes;
  public boolean currentlyInBlock;
  // Entity collision
  public boolean enforceBoatStep;
  public volatile Location nearestBoatLocation = null;
  // Vehicle
  private WrappedEntity ridingEntity;

  public boolean isTeleportConfirmationPacket;
  public boolean dropPostTickMotionProcessing;
  public boolean willReceiveSetbackVelocity;
  public int lastTeleport;
  public int teleportId;
  public volatile boolean awaitTeleport = false, awaitOutgoingTeleport = false;
  public Location teleportLocation = null;
  private volatile Location verifiedLocation;
  public int teleportResendCountdown = 10;

  // States if an external entity push onto the player is estimated
  public boolean pushedByEntity;

  // Key inputs sent by the client
  public boolean applyClientKeys = false;
  public int clientInputKey = 0;
  public int clientStrafeKey = 0;
  public boolean clientPressedJump = false;

  public UserMetaMovementData(Player player, User user) {
    this.player = player;
    this.user = user;
    if (player != null) {
      this.elytraFlying = PoseHelper.flyingWithElytra(player);
    }
  }

  public void setup() {
    applyPlayerStats();
    updateWorld();
    applyPlayerLocation();
  }

  private void setupDefaults() {
    UserMetaClientData clientData = user.meta().clientData();
    this.resetMotion = clientData.protocolVersion() <= 47 ? 0.005 : 0.003;
    this.frictionPosSubtraction = clientData.protocolVersion() <= VER_1_15 ? 1.0 : 0.5000001;
    this.hasJumpFactor = clientData.protocolVersion() >= VER_1_15;
    Location location = player.getLocation();
    boundingBox = WrappedAxisAlignedBB.createFromPosition(user, location.getX(), location.getY(), location.getZ());
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
    sprinting = player.isSprinting();
    sneaking = player.isSneaking();
  }

  public void updateWorld() {
    if (player == null) {
      nmsWorld = ReflectiveHandleAccess.handleOf(Bukkit.getWorlds().get(0));
      return;
    }
    nmsWorld = ReflectiveHandleAccess.handleOf(player.getWorld());
  }

  public void updateMovement(
    PacketContainer packet,
    boolean hasMovement, boolean hasRotation
  ) {
    UserMetaClientData clientData = user.meta().clientData();
    if (boundingBox == null) {
      setupDefaults();
    }

    jumpMotion = MovementContext.jumpMotionFor(player, jumpUpwardsMotion());
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;

    if (sprintResetNextTick) {
      ReflectiveDataWatcherAccess.setDataWatcherFlag(player, ReflectiveDataWatcherAccess.WATCHER_SPRINT_ID, true);
      sprintResetNextTick = false;
    }

    if (hasMovement) {
      StructureModifier<Double> modifier = packet.getDoubles();
      positionX = modifier.read(0);
      positionY = modifier.read(1);
      positionZ = modifier.read(2);
      blockOnPosition = BukkitBlockAccess.cacheAppliedTypeAccess(user, player.getWorld(), positionX, positionY - frictionPosSubtraction, positionZ);

      motionX = positionX - verifiedPositionX;
      motionY = positionY - verifiedPositionY;
      motionZ = positionZ - verifiedPositionZ;

      boolean falling = motionY() <= 0.0D;
      if (falling && EffectLogic.isPotionSlowFallingActive(player)) {
        artificialFallDistance = 0f;
        gravity = 0.01D;
      } else {
        gravity = 0.08D;
      }
      updateEntityActionStates();
      updateMovementMetaData();
    } else {
      pastClientFlyingPacket = 0;
    }
    lastRotationYaw = rotationYaw;
    lastRotationPitch = rotationPitch;
    if (hasRotation) {
      StructureModifier<Float> modifier = packet.getFloat();
      rotationYaw = modifier.read(0);
      rotationPitch = modifier.read(1);
      lookVector = RotationHelper.vectorForRotation(rotationPitch, rotationYaw);
      yawSine = SinusCache.sin(rotationYaw * (float) Math.PI / 180.0F, false);
      yawCosine = SinusCache.cos(rotationYaw * (float) Math.PI / 180.0F, false);
    }
    updateEntityMovement();
    if (clientData.canUseElytra()) {
      updateElytra();
    }
    updatePose();
  }

  private void updateElytra() {
    if (elytraFlying && !this.onGround && !this.inWater && !EffectLogic.isPotionLevitationActive(player)) {
      elytraFlying = hasElytraEquipped();
    } else {
      elytraFlying = false;
    }
  }

  public boolean hasElytraEquipped() {
    ItemStack plate = player.getInventory().getChestplate();
    //TODO: Check durability
    return plate != null && plate.getType() == Material.ELYTRA;
  }

  private void updateEntityMovement() {
    UserMetaConnectionData userMetaConnectionData = user.meta().connectionData();
    Map<Integer, WrappedEntity> entityMap = userMetaConnectionData.synchronizedEntityMap();
    for (Map.Entry<Integer, WrappedEntity> entry : entityMap.entrySet()) {
      WrappedEntity entity = entry.getValue();
      entity.entityPlayerMoveUpdate();
    }
  }

  public void updateEyesInWater() {
    double yPos = positionY + eyeHeight() - (double) 0.11111f;
    this.eyesInWater = interactingFluid != null && interactingFluid.isIn(FluidTag.WATER);
    this.interactingFluid = null;

    WrappedFluid fluid = Fluids.fluidAt(user, positionX, yPos, positionZ);
    if (fluid.isIn(FluidTag.WATER)) {
      double d1 = (float) WrappedMathHelper.floor(yPos) + 1.0f;
      if (d1 > yPos) {
        this.interactingFluid = fluid;
      }
    }
  }

  public boolean areEyesInWater() {
    return this.eyesInWater;
  }

  public void updatePose() {
    // Beautiful
    if (this.isPoseClear(Pose.SWIMMING)) {
      Pose pose;
      if (PoseHelper.isSwimming(user)) {
        pose = Pose.SWIMMING;
      } else if (player.isSleeping()) {
        pose = Pose.SLEEPING;
      } else if (elytraFlying) {
        pose = Pose.FALL_FLYING;
      } else if (PoseHelper.poseSneaking(user)) {
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

    updateSize();
  }

  public void setPose(Pose pose) {
    this.pose = pose;
    updatePose();
  }

  private void updateSize() {
    Pose pose = pose();
    width = pose.width(user);
    height = pose.height(user);
    widthRounded = Math.round(width * 50d) / 100d;
    heightRounded = Math.round(height * 100d) / 100d;
  }

  protected boolean isPoseClear(Pose pose) {
    return Collision.hasNoCollisions(user, pose.boundingBoxOf(user).shrink(1.0E-7D));
  }

  private float jumpUpwardsMotion() {
    return hasJumpFactor ? 0.42f * jumpFactor() : 0.42f;
  }

  private float jumpFactor() {
    World world = player.getWorld();
    float f = jumpFactorOf(BukkitBlockAccess.cacheAppliedTypeAccess(user, world, positionX, positionY, positionZ));
    float f1 = jumpFactorOf(blockOnPosition());
    return (double) f == 1.0D ? f1 : f;
  }

  private float jumpFactorOf(Material material) {
    return BlockProperties.ofType(material).jumpFactor();
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
        return 1.62f - user.meta().clientData().cameraSneakOffset();
      default:
        return 1.62f;
    }
  }

  public void applyGroundInformationToPacket(PacketContainer packet) {
    packet.getBooleans().write(0, onGround);
  }

  private void updateMovementMetaData() {
    UserMetaAbilityData abilityData = user.meta().abilityData();
    UserMetaPotionData potionData = user.meta().potionData();

    aiMoveSpeed = abilityData.walkSpeed();
    jumpMovementFactor = 0.02f;
    float slowdownAmplifier = potionData.potionEffectSlownessAmplifier();
    float speedAmplifier = potionData.potionEffectSpeedAmplifier();
    aiMoveSpeed *= 1f + (-0.15f * slowdownAmplifier);
    aiMoveSpeed *= 1f + (0.2f * speedAmplifier);
    aiMoveSpeed += genericMovementSpeedAttribute;
    if (sprintingAllowed) {
      aiMoveSpeed *= 1.3f;
    }
    if (lastSprinting) {
      jumpMovementFactor = (float) ((double) jumpMovementFactor + (double) 0.02f * 0.3d);
    }
    if (abilityData.flying()) {
      this.jumpMovementFactor = abilityData.flySpeed() * (float) (lastSprinting ? 2 : 1);
    }
    friction = MovementContext.resolveFriction(user, verifiedPositionX, verifiedPositionY, verifiedPositionZ);
  }

  public boolean blockOnPositionSoulSpeedAffected() {
    return BlockProperties.ofType(blockOnPosition()).soulSpeedAffected();
  }

  private void updateEntityActionStates() {
    UserMetaClientData clientData = user.meta().clientData();
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    sprintingAllowed = sprinting;
    if (sneaking && !clientData.sprintWhenSneaking()) {
      sprintingAllowed = false;
    }
    if (inventoryData.inventoryOpen()) {
      sprintingAllowed = false;
    }
    if (player.getFoodLevel() <= 5) {
      sprintingAllowed = false;
    }
  }

  public boolean inLava() {
    UserMetaClientData clientData = user.meta().clientData();
    if (clientData.waterUpdate()) {
      return aquaticUpdateInLava;
    } else {
      WrappedAxisAlignedBB lavaBoundingBox = boundingBox.expand(
        -0.1f,
        -0.4000000059604645D,
        -0.1f
      );
      return MovementContext.isLavaInBB(player.getWorld(), lavaBoundingBox);
    }
  }

  public boolean recentlyEncounteredFlyingPacket(int ticks) {
    UserMetaClientData clientData = user.meta().clientData();
    if (clientData.flyingPacketStream()) {
      return pastClientFlyingPacket <= ticks && pastFlyingPacketAccurate <= ticks;
    } else {
      return pastFlyingPacketAccurate <= ticks;
    }
  }

  public boolean denyJump() {
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
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
    UserMetaPotionData potionData = user.meta().potionData();
    int speedAmplifier = potionData.potionEffectSpeedAmplifier();
    double baseSpeed = 0.271;
    if (speedAmplifier != 0) {
      baseSpeed *= 1.0 + (0.2 * speedAmplifier);
    }
    if (sneaking) {
      baseSpeed *= 0.2;
    }
    return baseSpeed;
  }

  public void sprintReset() {
    UserMetaInventoryData inventoryData = user.meta().inventoryData();
    if (player.getFoodLevel() >= 6 && !inventoryData.inventoryOpen()) {
      ReflectiveDataWatcherAccess.setDataWatcherFlag(player, ReflectiveDataWatcherAccess.WATCHER_SPRINT_ID, false);
      sprintResetNextTick = true;
    }
  }

  public void dismountRidingEntity() {
    if (!hasRidingEntity()) {
      return;
    }
    this.ridingEntity = null;
  }

  public void resetFlyingPacketAccurate() {
    pastFlyingPacketAccurate = 0;
  }

  public void increaseFlyingPacket() {
    pastFlyingPacketAccurate++;
    pastClientFlyingPacket++;
  }

  private Material blockOnPosition() {
    return blockOnPosition;
  }

  public boolean hasRidingEntity() {
    return ridingEntity != null;
  }

  public WrappedEntity ridingEntity() {
    return ridingEntity;
  }

  public Object nmsWorld() {
    return nmsWorld;
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

  public MotionVector motion() {
    return new MotionVector(motionX, motionY, motionZ);
  }

  public WrappedAxisAlignedBB boundingBox() {
    return boundingBox;
  }

  public SimulationProcessor.IterativeSimulationContext iterativeSimulation() {
    return iterativeSimulation;
  }

  public double resetMotion() {
    return resetMotion;
  }

  public double jumpMotion() {
    return jumpMotion;
  }

  public float aiMoveSpeed() {
    return aiMoveSpeed;
  }

  public float jumpMovementFactor() {
    return jumpMovementFactor;
  }

  // Override on vehicle movement
  public void setJumpMovementFactor(float jumpMovementFactor) {
    this.jumpMovementFactor = jumpMovementFactor;
    friction = MovementContext.resolveFriction(user, verifiedPositionX, verifiedPositionY, verifiedPositionZ);
  }

  public void setAiMoveSpeed(float aiMoveSpeed) {
    this.aiMoveSpeed = aiMoveSpeed;
    friction = MovementContext.resolveFriction(user, verifiedPositionX, verifiedPositionY, verifiedPositionZ);
  }

  public int pastFlyingPacketAccurate() {
    return pastFlyingPacketAccurate;
  }

  public SimulationEngines simulationService() {
    return simulationEngines;
  }

  public Pose pose() {
    return pose;
  }

  public boolean sprintingAllowed() {
    return sprintingAllowed;
  }

  public float friction() {
    return friction;
  }

  public float yawSine() {
    return yawSine;
  }

  public float yawCosine() {
    return yawCosine;
  }

  public double frictionPosSubtraction() {
    return frictionPosSubtraction;
  }

  @Nullable
  public Vector motionMultiplier() {
    return motionMultiplier;
  }

  public void setBoundingBox(WrappedAxisAlignedBB entityBoundingBox) {
    if (this.boundingBox == null) {
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

  public void setMovementPoseType(SimulationEngines movementSimulationEnginesType) {
    this.simulationEngines = movementSimulationEnginesType;
  }

  public void setPastFlyingPacketAccurate(int pastFlyingPacketAccurate) {
    this.pastFlyingPacketAccurate = pastFlyingPacketAccurate;
  }

  public void setRidingEntity(WrappedEntity ridingEntity) {
    this.ridingEntity = ridingEntity;
  }
}