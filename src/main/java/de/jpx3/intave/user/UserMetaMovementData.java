package de.jpx3.intave.user;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.detect.checks.movement.physics.SimulationProcessor;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.reflect.ReflectiveHandleAccess;
import de.jpx3.intave.tools.annotate.Nullable;
import de.jpx3.intave.tools.client.*;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockphysic.BlockPhysics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
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

  public boolean swimming, elytraFlying, fireworkTolerant;

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
  private boolean sprintingAllowed, actualSneaking;
  private float yawSine, yawCosine, friction;
  public float rotationYaw, rotationPitch;
  public float lastRotationYaw, lastRotationPitch;
  private Pose movementPoseType = Pose.PLAYER;
  private final SimulationProcessor.IterativeSimulationContext iterativeSimulation = new SimulationProcessor.IterativeSimulationContext();

  // Timestamps
  public long lastSneakingTimestamps, lastJumpTimestamps;

  private volatile WrappedAxisAlignedBB boundingBox;
  public Vector emulationVelocity;
  public Vector setbackOverrideVelocity = new Vector(0,0,0);
  public Vector lastVelocity = new Vector();
  @Nullable
  private Vector motionMultiplier = null;
  public boolean canResetMotion;
  private double jumpMotion;
  private int pastClientFlyingPacket, pastFlyingPacketAccurate;
  private float aiMoveSpeed, jumpMovementFactor;
  public boolean inWater, eyesInWater;
  public boolean inWeb;
  public int pastPushedByWaterFlow = 100;
  public int pastElytraFlying = 100, pastVelocity = 100, pastExternalVelocity = 100, pastInWeb = 100, pastWaterMovement = 100;
  public int pastLongTeleport = 100;
  public int pastInventoryOpen = 100;
  public boolean onLadderLast;
  public boolean aquaticUpdateInLava;

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
    applySizeUpdate();
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
  }

  private void applyPlayerStats() {
    if (player == null) {
      return;
    }
    sprinting = player.isSprinting();
    sneaking = player.isSneaking();
  }

  public void applySizeUpdate() {
    widthRounded = Math.round(width * 50d) / 100d;
    heightRounded = Math.round(height * 100d) / 100d;
//    if (user.meta().clientData().roundEnvironmentNumbers()) {
//      widthRounded = Math.round(width * 500d) / 1000d;
//      heightRounded = Math.round(height * 10000d) / 10000d;
//    } else {
//      widthRounded = Math.round(width * 500000000000000d) / 1000000000000000d;
//      heightRounded = Math.round(height * 100000000000000d) / 100000000000000d;
//    }
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
    if (boundingBox == null) {
      setupDefaults();
    }

    jumpMotion = MovementContext.jumpMotionFor(player, jumpUpwardsMotion());
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;

    if (hasMovement) {
      StructureModifier<Double> modifier = packet.getDoubles();
      positionX = round(modifier.read(0));
      positionY = round(modifier.read(1));
      positionZ = round(modifier.read(2));

      motionX = positionX - verifiedPositionX;
      motionY = positionY - verifiedPositionY;
      motionZ = positionZ - verifiedPositionZ;

      swimming = PoseHelper.isSwimming(player);
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
    // I could not find a better solution :(
    updateEntityMovement();
  }

  private void updateEntityMovement() {
    UserMetaConnectionData userMetaConnectionData = user.meta().connectionData();
    Map<Integer, WrappedEntity> entityMap = userMetaConnectionData.synchronizedEntityMap();
    for (Map.Entry<Integer, WrappedEntity> entry : entityMap.entrySet()) {
      WrappedEntity entity = entry.getValue();
      entity.entityPlayerMoveUpdate();
    }
  }

  private double round(double input) {
    double factor = 100000000000000d;
    return Math.round(input * factor) / factor;
//    return input;
  }

  private float jumpUpwardsMotion() {
    return hasJumpFactor ? 0.42f * jumpFactor() : 0.42f;
  }

  private float jumpFactor() {
    World world = player.getWorld();
    float f = BlockPhysics.jumpFactor(user, BlockTypeAccess.typeAccess(BukkitBlockAccess.blockAccess(world, positionX, positionY, positionZ), player));
    float f1 = BlockPhysics.jumpFactor(user, BlockTypeAccess.typeAccess(BukkitBlockAccess.blockAccess(world, positionX, positionY - frictionPosSubtraction, positionZ), player));
    return (double) f == 1.0D ? f1 : f;
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
    if (player.isSleeping()) {
      return 0.2f;
    }
    if (swimming || elytraFlying /*|| spinAttack */) {
      return 0.4f;
    } else if (lastSneaking) {
      return 1.62f - user.meta().clientData().cameraSneakOffset();
    } else {
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
    boolean sneakingAllowed = sneaking && !inventoryData.inventoryOpen();
    if (clientData.delayedSneak()) {
      actualSneaking = lastSneaking;
    } else if (clientData.alternativeSneak()) {
      actualSneaking = lastSneaking || sneakingAllowed;
    } else {
      actualSneaking = sneakingAllowed;
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

  public Pose movementPoseType() {
    return movementPoseType;
  }

  public boolean sprintingAllowed() {
    return sprintingAllowed;
  }

  public boolean actualSneaking() {
    return actualSneaking;
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

  public void setMovementPoseType(Pose movementPoseType) {
    this.movementPoseType = movementPoseType;
  }

  public void setPastFlyingPacketAccurate(int pastFlyingPacketAccurate) {
    this.pastFlyingPacketAccurate = pastFlyingPacketAccurate;
  }

  public void setRidingEntity(WrappedEntity ridingEntity) {
    this.ridingEntity = ridingEntity;
  }
}