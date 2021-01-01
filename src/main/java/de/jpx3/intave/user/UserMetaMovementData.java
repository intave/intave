package de.jpx3.intave.user;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.physics.CollisionHelper;
import de.jpx3.intave.reflect.Reflection;
import de.jpx3.intave.tools.client.PlayerEffectHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.client.PlayerMovementLocaleHelper;
import de.jpx3.intave.tools.client.PlayerRotationHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.BlockLiquidHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class UserMetaMovementData {
  private final Player player;
  private final User user;
  private volatile Object nmsWorld;

  public boolean disabledFlying;
  public float width = 0.6f, height = 1.8f;
  public boolean swimming, elytraFlying;

  public boolean onGround, lastOnGround;
  public boolean collidedHorizontally, collidedVertically;
  public double gravity;

  public Physics.PhysicsProcessorContext physicsProcessorContext = new Physics.PhysicsProcessorContext();
  public Vector lookVector;
  public double verifiedPositionX, verifiedPositionY, verifiedPositionZ;
  public double lastPositionX, lastPositionY, lastPositionZ;
  public double positionX, positionY, positionZ;
  public boolean sprinting, lastSprinting, sneaking, lastSneaking;
  public float rotationYaw, rotationPitch;
  public float lastRotationYaw, lastRotationPitch;

  private WrappedAxisAlignedBB boundingBox;
  private double resetMotion;
  private double jumpUpwardsMotion;
  public int pastFlyingPacketAccurate, pastWaterMovement;
  private float aiMoveSpeed, jumpMovementFactor;
  public boolean inWater, eyesInWater;
  public boolean inWeb;
  public int pastPushedByWaterFlow = 100;
  public int pastElytraFlying = 100, pastVelocity = 100;
  public boolean onLadderLast;

  public boolean invalidMovement, suspiciousMovement;
  public double physicsLastMotionX, physicsLastMotionY, physicsLastMotionZ;
  public int pastRiptideSpin;
  public int pastPlayerAttackPhysics;
  public boolean physicsResetMotionX, physicsResetMotionZ;
  public int keyForward, keyStrafe;

  public boolean teleport;
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
  }

  private void applyPlayerLocation() {
    Location location;
    if (player == null) {
      location = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
    } else {
      location = player.getLocation();
    }
    verifiedLocation = location.clone();
    positionX = location.getX();
    positionY = location.getY();
    positionZ = location.getZ();
    verifiedPositionX = positionX;
    verifiedPositionY = positionY;
    verifiedPositionZ = positionZ;
    //  entityBoundingBox = CollisionHelper.entityBoundingBoxOf(location.getX(), location.getY(), location.getZ());
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
      nmsWorld = Reflection.resolveWorldNMSHandle(Bukkit.getWorlds().get(0));
      return;
    }
    nmsWorld = Reflection.resolveWorldNMSHandle(player.getWorld());
  }

  public void updateMovement(
    PacketContainer packet,
    boolean hasMovement, boolean hasRotation
  ) {
    if (boundingBox == null) {
      UserMetaClientData clientData = user.meta().clientData();
      this.resetMotion = clientData.protocolVersion() <= 47 ? 0.005 : 0.003;

      Location location = player.getLocation();
      boundingBox = CollisionHelper.boundingBoxOf(user, location.getX(), location.getY(), location.getZ());
    }

    jumpUpwardsMotion = PlayerMovementHelper.jumpMotionFor(player);

    if (hasMovement) {
      StructureModifier<Double> modifier = packet.getDoubles();
      lastPositionX = positionX;
      lastPositionY = positionY;
      lastPositionZ = positionZ;
      positionX = modifier.read(0);
      positionY = modifier.read(1);
      positionZ = modifier.read(2);

      swimming = PlayerMovementLocaleHelper.isSwimming(player);
      elytraFlying = PlayerMovementLocaleHelper.flyingWithElytra(player);
      boolean falling = motionY() <= 0.0D;
      if (falling && PlayerEffectHelper.isPotionSlowFallingActive(player)) {
        gravity = 0.01D;
      } else {
        gravity = 0.08D;
      }
      updateMovementMetaData();
    }
    if (hasRotation) {
      StructureModifier<Float> modifier = packet.getFloat();
      lastRotationYaw = rotationYaw;
      lastRotationPitch = rotationPitch;
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

  public void setBoundingBox(WrappedAxisAlignedBB entityBoundingBox) {
    this.boundingBox = entityBoundingBox;
  }

  public void setVerifiedLocation(Location verifiedLocation, @SuppressWarnings("unused") String reason) {
    /*boolean boundingBoxIntersection = CollisionHelper.checkBoundingBoxIntersection(user, CollisionHelper.boundingBoxOf(user, verifiedLocation));
    if (boundingBoxIntersection) {
      Bukkit.broadcastMessage(ChatColor.DARK_RED + "Position was set into a block: " + reason);
    }*/
    this.verifiedLocation = verifiedLocation;
  }
}