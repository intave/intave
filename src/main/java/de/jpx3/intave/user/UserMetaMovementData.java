package de.jpx3.intave.user;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.physics.CollisionHelper;
import de.jpx3.intave.detect.checks.movement.physics.pose.PhysicsMovementPoseType;
import de.jpx3.intave.reflect.ReflectiveHandleAccess;
import de.jpx3.intave.tools.client.PlayerEffectHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.PlayerMovementPoseHelper;
import de.jpx3.intave.tools.client.PlayerRotationHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.trustfactor.TrustFactorService;
import de.jpx3.intave.world.BlockLiquidHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public final class UserMetaMovementData {
  private final Player player;
  private final User user;
  private volatile Object nmsWorld;

  public boolean disabledFlying;
  public float width = 0.6f, height = 1.8f;
  public double widthRounded, heightRounded;

  public boolean swimming, elytraFlying;

  public boolean onGround, lastOnGround, step;
  public boolean collidedHorizontally, collidedVertically;
  public float artificialFallDistance;
  public boolean allowFallDamage;
  public double gravity;

  public Physics.PhysicsProcessorContext physicsProcessorContext = new Physics.PhysicsProcessorContext();
  public Vector lookVector;
  public double verifiedPositionX, verifiedPositionY, verifiedPositionZ;
  public double lastPositionX, lastPositionY, lastPositionZ;
  public double positionX, positionY, positionZ;
  public boolean sprinting, lastSprinting, sneaking, lastSneaking;
  public float rotationYaw, rotationPitch;
  public float lastRotationYaw, lastRotationPitch;
  private PhysicsMovementPoseType movementPoseType = PhysicsMovementPoseType.PHYSICS_NORMAL_MOVEMENT;

  private volatile WrappedAxisAlignedBB boundingBox;
  public Vector emulationVelocity;
  public Vector setbackOverrideVelocity = new Vector(0,0,0);
  public Vector lastVelocity = new Vector();
  public boolean canResetMotion;
  private double resetMotion;
  private double jumpUpwardsMotion;
  public int pastWaterMovement;
  private int pastClientFlyingPacket, pastFlyingPacketAccurate;
  private float aiMoveSpeed, jumpMovementFactor;
  public boolean inWater, eyesInWater;
  public boolean inWeb;
  public int pastPushedByWaterFlow = 100;
  public int pastElytraFlying = 100, pastVelocity = 100, pastExternalVelocity = 100;
  public boolean onLadderLast;

  public int physicsPacketRelinkFlyVL; // In Air
  public boolean invalidMovement, suspiciousMovement;
  public double physicsLastMotionX, physicsLastMotionY, physicsLastMotionZ;
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
    applyPlayerStats();
    updateWorld();
    applyPlayerLocation();
    applySizeUpdate();
  }

  private void initializeBoundingBox() {
    UserMetaClientData clientData = user.meta().clientData();
    this.resetMotion = clientData.protocolVersion() <= 47 ? 0.005 : 0.003;
    Location location = player.getLocation();
    boundingBox = CollisionHelper.boundingBoxOf(user, location.getX(), location.getY(), location.getZ());
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
    widthRounded = Math.round(width * 50000d) / 100000d;
    heightRounded = Math.round(height * 100000d) / 100000d;
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
      initializeBoundingBox();
    }

    jumpUpwardsMotion = PlayerMovementHelper.jumpMotionFor(player);
    lastPositionX = positionX;
    lastPositionY = positionY;
    lastPositionZ = positionZ;

    if (hasMovement) {
      StructureModifier<Double> modifier = packet.getDoubles();
      positionX = modifier.read(0);
      positionY = modifier.read(1);
      positionZ = modifier.read(2);

      swimming = PlayerMovementPoseHelper.isSwimming(player);
      elytraFlying = PlayerMovementPoseHelper.flyingWithElytra(player);
      boolean falling = motionY() <= 0.0D;
      if (falling && PlayerEffectHelper.isPotionSlowFallingActive(player)) {
        gravity = 0.01D;
      } else {
        gravity = 0.08D;
      }
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
      lookVector = PlayerRotationHelper.vectorForRotation(rotationPitch, rotationYaw);
    }
  }

  public float eyeHeight() {
    float f = 1.62F;
    if (player.isSleeping()) {
      f = 0.2F;
    } else if (!swimming && !elytraFlying && height != 0.6F) {
      if (sneaking || height == 1.65F) {
        f -= 0.08F;
      }
    } else {
      f = 0.4F;
    }

    return f;
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
    if (sprinting) {
      aiMoveSpeed *= 1.3f;
    }
    if (lastSprinting) {
      jumpMovementFactor = (float) ((double) jumpMovementFactor + (double) 0.02f * 0.3d);
    }
    if (abilityData.flying()) {
      this.jumpMovementFactor = abilityData.flySpeed() * (float) (lastSprinting ? 2 : 1);
    }
  }

  public boolean inLava() {
    WrappedAxisAlignedBB lavaBoundingBox = boundingBox.expand(
      -0.1f,
      -0.4000000059604645D,
      -0.1f
    );
    return BlockLiquidHelper.isLavaInBB(player.getWorld(), lavaBoundingBox);
  }

  public boolean recentlyEncounteredFlyingPacket(int ticks) {
    UserMetaClientData clientData = user.meta().clientData();
    if (clientData.flyingPacketStream()) {
      return pastClientFlyingPacket <= ticks && pastFlyingPacketAccurate <= ticks;
    } else {
      return pastFlyingPacketAccurate <= ticks;
    }
  }

  public boolean exceededJumpPrevention() {
    IntavePlugin plugin = IntavePlugin.singletonInstance();
    TrustFactorService trustFactorService = plugin.trustFactorService();
    int trustFactorSetting = trustFactorService.trustFactorSetting("physics.joap-limit", player);
    return pastVelocity == 0 && physicsJumpedOverrideVL >= trustFactorSetting;
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
    return positionX - verifiedPositionX;
  }

  public double motionY() {
    return positionY - verifiedPositionY;
  }

  public double motionZ() {
    return positionZ - verifiedPositionZ;
  }

  public WrappedAxisAlignedBB boundingBox() {
    return boundingBox;
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

  public PhysicsMovementPoseType movementPoseType() {
    return movementPoseType;
  }

  public void setBoundingBox(WrappedAxisAlignedBB entityBoundingBox) {
    if (this.boundingBox == null) {
      initializeBoundingBox();
    }
    this.boundingBox = entityBoundingBox;
  }

  public void setVerifiedLocation(Location verifiedLocation, @SuppressWarnings("unused") String reason) {
   /* boolean boundingBoxIntersection = CollisionHelper.checkBoundingBoxIntersection(user, CollisionHelper.boundingBoxOf(user, verifiedLocation));
    if (boundingBoxIntersection) {
      Bukkit.broadcastMessage(ChatColor.DARK_RED + "Position was set into a block: " + reason);
    }*/
    this.verifiedLocation = verifiedLocation;
  }

  public void setMovementPoseType(PhysicsMovementPoseType movementPoseType) {
    this.movementPoseType = movementPoseType;
  }

  public void setPastFlyingPacketAccurate(int pastFlyingPacketAccurate) {
    this.pastFlyingPacketAccurate = pastFlyingPacketAccurate;
  }
}