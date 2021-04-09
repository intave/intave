package de.jpx3.intave.user;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.detect.checks.movement.physics.SimulationProcessor;
import de.jpx3.intave.reflect.ReflectiveHandleAccess;
import de.jpx3.intave.tools.client.*;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.trustfactor.TrustFactorService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BEE_UPDATE;

public final class UserMetaMovementData {
  private final Player player;
  private final User user;
  private volatile Object nmsWorld;

  public boolean disabledFlying;
  public float width = 0.6f, height = 1.8f;
  public double widthRounded, heightRounded;
  private double resetMotion, frictionPosSubtraction;

  public boolean swimming, elytraFlying;

  public boolean onGround, lastOnGround, step;
  public boolean collidedHorizontally, collidedVertically;
  public float artificialFallDistance;
  public boolean allowFallDamage;
  public double gravity;
  public boolean outsideBorder = true;

  public MotionVector motionVector = new MotionVector();
  public Vector lookVector;
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
  private SimulationProcessor.IterativeSimulationResult iterativeSimulation = new SimulationProcessor.IterativeSimulationResult();

  private volatile WrappedAxisAlignedBB boundingBox;
  public Vector emulationVelocity;
  public Vector setbackOverrideVelocity = new Vector(0,0,0);
  public Vector lastVelocity = new Vector();
  public boolean canResetMotion;
  private double jumpUpwardsMotion;
  private int pastClientFlyingPacket, pastFlyingPacketAccurate;
  private float aiMoveSpeed, jumpMovementFactor;
  public boolean inWater, eyesInWater;
  public boolean inWeb;
  public int pastPushedByWaterFlow = 100;
  public int pastElytraFlying = 100, pastVelocity = 100, pastExternalVelocity = 100, pastInWeb = 100, pastWaterMovement = 100;
  public int pastLongTeleport = 100;
  public boolean onLadderLast;

  public int physicsPacketRelinkFlyVL; // In Air
  public boolean invalidMovement, suspiciousMovement;
  public double physicsMotionX, physicsMotionY, physicsMotionZ;
  public double physicsMotionXBeforeVelocity, physicsMotionYBeforeVelocity, physicsMotionZBeforeVelocity;
  public int pastRiptideSpin;
  public int pastPlayerAttackPhysics = 100;
  public boolean physicsResetMotionX, physicsResetMotionZ;
  public int keyForward, keyStrafe;
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

  public boolean isTeleportConfirmationPacket;
  public boolean willReceiveSetbackVelocity;
  public int lastTeleport;
  public int teleportId;
  public volatile boolean awaitTeleport = false;
  public Location teleportLocation = null;
  private volatile Location verifiedLocation;
  public int teleportResendCountdown = 10;

  public UserMetaMovementData(Player player, User user) {
    this.player = player;
    this.user = user;
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
    this.frictionPosSubtraction = clientData.protocolVersion() <= PROTOCOL_VERSION_BEE_UPDATE ? 1.0 : 0.5000001;
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
    if(user.meta().clientData().roundEnvironmentNumbers()) {
      widthRounded = Math.round(width * 500d) / 1000d;
      heightRounded = Math.round(height * 10000d) / 10000d;
    } else {
      widthRounded = width / 2d;
      heightRounded = height;
    }
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

    jumpUpwardsMotion = MovementContextHelper.jumpMotionFor(player);
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;

    if (hasMovement) {
      StructureModifier<Double> modifier = packet.getDoubles();
      positionX = modifier.read(0);
      positionY = modifier.read(1);
      positionZ = modifier.read(2);

      motionX = positionX - verifiedPositionX;
      motionY = positionY - verifiedPositionY;
      motionZ = positionZ - verifiedPositionZ;

      swimming = PoseHelper.isSwimming(player);
      elytraFlying = PoseHelper.flyingWithElytra(player);
      boolean falling = motionY() <= 0.0D;
      if (falling && EffectLogic.isPotionSlowFallingActive(player)) {
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
  }

  public float eyeHeight() {
    if (player.isSleeping()) {
      return 0.2f;
    }
    if (swimming || elytraFlying /*|| spinAttack */) {
      return 0.4f;
    } else if (sneaking) {
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
    friction = MovementContextHelper.resolveFriction(user, verifiedPositionX, verifiedPositionY, verifiedPositionZ);
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
    WrappedAxisAlignedBB lavaBoundingBox = boundingBox.expand(
      -0.1f,
      -0.4000000059604645D,
      -0.1f
    );
    return MovementContextHelper.isLavaInBB(player.getWorld(), lavaBoundingBox);
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
    IntavePlugin plugin = IntavePlugin.singletonInstance();
    TrustFactorService trustFactorService = plugin.trustFactorService();
    int trustFactorSetting = trustFactorService.trustFactorSetting("physics.joap-limit", player);
    return pastVelocity == 0 && physicsJumpedOverrideVL >= trustFactorSetting;
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

  public void resetFlyingPacketAccurate() {
    pastFlyingPacketAccurate = 0;
  }

  public void increaseFlyingPacket() {
    pastFlyingPacketAccurate++;
    pastClientFlyingPacket++;
  }

  public boolean inVehicle() {
    return player != null && player.isInsideVehicle();
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

  public WrappedAxisAlignedBB boundingBox() {
    return boundingBox;
  }

  public SimulationProcessor.IterativeSimulationResult iterativeSimulation() {
    return iterativeSimulation;
  }

  public double resetMotion() {
    return resetMotion;
  }

  public double jumpUpwardsMotion() {
    return jumpUpwardsMotion;
  }

  public float aiMoveSpeed() {
    return aiMoveSpeed;
  }

  public float jumpMovementFactor() {
    return jumpMovementFactor;
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

  public void setBoundingBox(WrappedAxisAlignedBB entityBoundingBox) {
    if (this.boundingBox == null) {
      setupDefaults();
    }
    this.boundingBox = entityBoundingBox;
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
}