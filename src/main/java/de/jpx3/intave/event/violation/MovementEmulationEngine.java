package de.jpx3.intave.event.violation;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveBootFailureException;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.caller.CallerResolver;
import de.jpx3.intave.tools.caller.PluginInvocation;
import de.jpx3.intave.tools.client.EffectLogic;
import de.jpx3.intave.tools.client.MovementContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.wrapper.WrappedBlockPosition;
import de.jpx3.intave.world.wrapper.WrappedMathHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MovementEmulationEngine {
  private final static boolean WEIRD_BOOLEAN_IN_INVOKE = MinecraftVersions.VER1_17_0.atOrAbove();

  private final IntavePlugin plugin;
  private final Physics physicsCheck;
  private final Set<Object> teleportFlags = new HashSet<>();
  private Method internalTeleportMethod;

  public MovementEmulationEngine(IntavePlugin plugin) {
    this.plugin = plugin;
    this.physicsCheck = plugin.checkService().searchCheck(Physics.class);
    this.setup();
  }

  private void setup() {
    try {
      if (teleportFlags.isEmpty()) {
        teleportFlags.add(Lookup.serverField("PacketPlayOutPosition$EnumPlayerTeleportFlags", "X_ROT").get(null));
        teleportFlags.add(Lookup.serverField("PacketPlayOutPosition$EnumPlayerTeleportFlags", "Y_ROT").get(null));
      }
      Class<?> playerConnectionClass = Lookup.serverClass("PlayerConnection");
      if (WEIRD_BOOLEAN_IN_INVOKE) {
        internalTeleportMethod = playerConnectionClass.getDeclaredMethod("internalTeleport", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class, Boolean.TYPE);
      } else {
        internalTeleportMethod = playerConnectionClass.getDeclaredMethod("internalTeleport", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class);
      }
      if (!internalTeleportMethod.isAccessible()) {
        internalTeleportMethod.setAccessible(true);
      }
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  public void emulationSetBack(
    Player player,
    Vector motion,
    int ticks,
    boolean cancellable
  ) {
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ViolationMetadata violationLevelData = meta.violationLevel();
    if (violationLevelData.isInActiveTeleportBundle) {
      return;
    }

    if (movementData.emulationVelocity != null) {
      motion = movementData.emulationVelocity;
      movementData.emulationVelocity = null;
    }

    // starting conditions

    violationLevelData.isInActiveTeleportBundle = true;
    if (IntaveControl.DEBUG_EMULATION) {
      player.sendMessage("[E+] " + motion + " (" + ticks + " ticks)");
    }

    proceedEmulationTick(player, motion, ticks, ticks, cancellable);
  }

  public void emulationPushOutOfBlock(
    Player player, WrappedAxisAlignedBB boundingBox,
    double motionX, double motionY, double motionZ
  ) {
    User user = UserRepository.userOf(player);
    ViolationMetadata violationLevelData = user.meta().violationLevel();

    if (violationLevelData.isInActiveTeleportBundle) {
      return;
    }

    violationLevelData.isInActiveTeleportBundle = true;
    MovementMetadata movementData = user.meta().movement();
    movementData.physicsMotionX = motionX;
    movementData.physicsMotionY = motionY;
    movementData.physicsMotionZ = motionZ;
    movementData.setBoundingBox(boundingBox);
    proceedPushOutOfBlockEmulationTick(player);
  }

  private void proceedPushOutOfBlockEmulationTick(Player player) {
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronizeDelayed(() -> proceedPushOutOfBlockEmulationTick(player), 0);
      return;
    }

    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ViolationMetadata violationLevelData = meta.violationLevel();
    WrappedAxisAlignedBB boundingBox = movementData.boundingBox();

    boolean boundingBoxIntersection = Collision.checkBoundingBoxIntersection(user, boundingBox);
    if (boundingBoxIntersection) {
      double motionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
      double motionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
      double motionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
      Vector pushVector = resolvePushVector(player, motionX, motionY, motionZ);

      Location location = movementData.verifiedLocation().clone().add(pushVector);
      teleport(player, location);

      if (IntaveControl.DEBUG_EMULATION) {
        player.sendMessage("[E/] Push out of blocks emulation (? remaining) with " + MathHelper.formatMotion(pushVector));
      }
      Synchronizer.synchronizeDelayed(() -> proceedPushOutOfBlockEmulationTick(player), 1);
    } else {
      if (IntaveControl.DEBUG_EMULATION) {
        player.sendMessage("[E-] Player does no longer intersect with their bounding-box");
      }
      violationLevelData.isInActiveTeleportBundle = false;
    }
  }

  private void proceedEmulationTick(
    Player player,
    Vector motion,
    int ticks,
    int startingTicks,
    boolean cancellable
  ) {
    if (!Bukkit.isPrimaryThread()) {
      Vector finalMotion1 = motion;
      Synchronizer.synchronizeDelayed(() -> proceedEmulationTick(player, finalMotion1, ticks, startingTicks, cancellable), 0);
      return;
    }

    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }

    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ViolationMetadata violationLevelData = meta.violationLevel();

    // check motion status (velocity?)
    Location futurePosition = movementData.verifiedLocation();
    WrappedAxisAlignedBB boundingBox = WrappedAxisAlignedBB.createFromPosition(user, futurePosition);

    Vector emulationVelocity = movementData.emulationVelocity;
    if (emulationVelocity != null) {
      motion = motionProceed(emulationVelocity, user, boundingBox, true);
      movementData.emulationVelocity = null;
    } else {
      motion = motionProceed(motion, user, boundingBox, startingTicks > ticks);
    }

    // add y motion to falldistance
    if (motion.getY() < 0) {
      movementData.artificialFallDistance += -motion.getY();
    }

    if (!Collision.isInsideBlocks(player, WrappedAxisAlignedBB.createFromPosition(user, futurePosition.clone().add(motion)))) {
      futurePosition = futurePosition.clone().add(motion);
    }
    futurePosition.setYaw(movementData.rotationYaw);
    futurePosition.setPitch(movementData.rotationPitch);

    if ((Math.abs(motion.getX()) < 0.01 && Math.abs(motion.getZ()) < 0.01 && motion.getY() == 0.0 && cancellable) || ticks <= 0) {
      // velocity

      // fixes stuck in block below, please remove and fix me differently
      futurePosition.subtract(0, 0.02, 0);
      boundingBox = WrappedAxisAlignedBB.createFromPosition(user, futurePosition);
      futurePosition.add(0, Collision.resolve(player, boundingBox).isEmpty() ? 0.03 : 0.02, 0);
      boundingBox = WrappedAxisAlignedBB.createFromPosition(user, futurePosition);

      teleport(player, futurePosition);
      violationLevelData.isInActiveTeleportBundle = false;

      Vector futureMotion = motionProceed(motion, user, boundingBox, true);

      movementData.willReceiveSetbackVelocity = true;
      player.setVelocity(futureMotion);

      movementData.physicsMotionX = futureMotion.getX();
      movementData.physicsMotionY = futureMotion.getY();
      movementData.physicsMotionZ = futureMotion.getZ();

      if (movementData.onGround) {
        physicsCheck.applyFallDamageUpdate(user);
        movementData.artificialFallDistance = 0;
      }

      if (IntaveControl.DEBUG_EMULATION) {
        player.sendMessage("[E-] (" + ticks + " ticks remaining)");
      }
    } else {
      // teleport
      //player.teleport(futurePosition);
      teleport(player, futurePosition);

      if (IntaveControl.DEBUG_EMULATION) {
        String s = "[E/] " + MathHelper.formatMotion(motion) + " at " + MathHelper.formatPosition(futurePosition) + " (" + ticks + " ticks remaining)";
        player.sendMessage(s);
      }
      //   s += " @" + movementData.entityBoundingBox();

      Vector finalMotion = motion.clone();
      Synchronizer.synchronizeDelayed(() -> proceedEmulationTick(player, finalMotion, ticks - 1, startingTicks, cancellable), 1);

      // velocity
      Vector futureMotion = motionProceed(motion, user, boundingBox, true);
//      movementData.physicsMotionX = futureMotion.getX();
//      movementData.physicsMotionY = futureMotion.getY();
//      movementData.physicsMotionZ = futureMotion.getZ();

      movementData.willReceiveSetbackVelocity = true;
      movementData.setbackOverrideVelocity = futureMotion;
      // this is not the real setback motion motion - velocity will be altered later
      player.setVelocity(new Vector(0, 0, 0));
    }
  }

  private Vector motionProceed(Vector lastMotion, User user, WrappedAxisAlignedBB boundingBox, boolean applyPhysics) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();
    float rotationPitch = movementData.rotationPitch;
    Vector lookVector = movementData.lookVector;

    //
    // Pre Emulation
    //

    double motionX = lastMotion.getX();
    double motionY = lastMotion.getY();
    double motionZ = lastMotion.getZ();

    if (applyPhysics) {
      if (movementData.pose() == Pose.FALL_FLYING) {
        float f = rotationPitch * 0.017453292F;
        double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
        double dist2 = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
        float pitchCosine = WrappedMathHelper.cos(f);
        pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
        motionY += movementData.gravity * (-1 + pitchCosine * 0.75);

        if (motionY < 0.0D && rotationVectorDistance > 0.0D) {
          double d2 = motionY * -0.1D * (double) pitchCosine;
          motionY += d2;
          motionX += lookVector.getX() * d2 / rotationVectorDistance;
          motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
        }

        if (f < 0.0F && rotationVectorDistance > 0.0D) {
          double d9 = dist2 * (double) (-WrappedMathHelper.sin(f)) * 0.04D;
          motionY += d9 * 3.2D;
          motionX += -lookVector.getX() * d9 / rotationVectorDistance;
          motionZ += -lookVector.getZ() * d9 / rotationVectorDistance;
        }

        if (rotationVectorDistance > 0.0D) {
          motionX += (lookVector.getX() / rotationVectorDistance * dist2 - motionX) * 0.1D;
          motionZ += (lookVector.getZ() / rotationVectorDistance * dist2 - motionZ) * 0.1D;
        }

        motionX *= 0.99f;
        motionY *= 0.98f;
        motionZ *= 0.99f;
      } else {
        if (movementData.inWater) {
          motionY = lastMotion.getY() * 0.8f;
          motionY -= 0.02;
        } else {
          if (EffectLogic.isPotionLevitationActive(player)) {
            int levitationAmplifier = EffectLogic.effectAmplifier(player, EffectLogic.EFFECT_LEVITATION);
            motionY += (0.05D * (double) (levitationAmplifier + 1) - motionY) * 0.2D;
            user.meta().movement().artificialFallDistance = 0f;
          } else {
            motionY -= movementData.gravity;
          }
          motionY *= 0.98f;
        }
      }
    }

    //
    // Prepare next tick
    //

    Vector collisionVector = resolveCollisionVector(player, boundingBox, lastMotion.getX(), motionY, lastMotion.getZ());
    boolean onGround = motionY != collisionVector.getY() && motionY < 0.0;
    motionY = collisionVector.getY();
    double multiplier;
    if (applyPhysics && movementData.pose() != Pose.FALL_FLYING) {
      if (movementData.inWater) {
        multiplier = 0.8f;
      } else {
        multiplier = onGround ? 0.546f : 0.91f;
      }
    } else {
      multiplier = 1;
    }
    if (movementData.lastOnGround && !movementData.onGround) {
      multiplier *= 0.6f;
    }
    motionX *= multiplier;
    motionZ *= multiplier;
    if (applyPhysics) {
      if (movementData.inWeb) {
        motionX *= 0.25D;
        motionY *= 0.25f;
        motionZ *= 0.25D;
      }
      movementData.lastOnGround = movementData.onGround;
      movementData.onGround = onGround;
    }
    collisionVector = resolveCollisionVector(player, boundingBox, motionX, motionY, motionZ);

    // webs, water

    // Limit motion (motion cannot be greater than 4.0,
    // otherwise -> Excessive velocity set detected: tried to set velocity of entity #33 to ...)
    collisionVector.setX(limitMotionAxis(collisionVector.getX()));
    collisionVector.setY(limitMotionAxis(collisionVector.getY()));
    collisionVector.setZ(limitMotionAxis(collisionVector.getZ()));

    return collisionVector;
  }

  private double limitMotionAxis(double axis) {
    return WrappedMathHelper.clamp_double(axis, -4.0, 4.0);
  }

  private void teleport(Player player, Location teleportLocation) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

    WrappedAxisAlignedBB entityBoundingBox = WrappedAxisAlignedBB.createFromPosition(user, teleportLocation);
    movementData.setBoundingBox(entityBoundingBox);
    movementData.setVerifiedLocation(teleportLocation.clone(), "Emulation-Setback");
//    player.teleport(teleportLocation);
    if (user.meta().inventory().inventoryOpen()) {
      player.closeInventory();
    }
    rotationlessTeleport(player, teleportLocation, movementData.rotationYaw, movementData.rotationPitch);
    updateMovementStatus(user);
  }

  private void updateMovementStatus(User user) {
    Player player = user.player();
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    movementData.inWater = MovementContext.isAnyLiquid(world, user, movementData.boundingBox());
  }

  private synchronized void rotationlessTeleport(Player player, Location to, float nativeYaw, float nativePitch) {
    PlayerTeleportEvent event = constructTeleportEvent(player, to);
    plugin.eventLinker().fireEvent(event);
    if (player.isDead() || player.getHealth() <= 0 || player.getPassenger() != null || !player.isOnline() || !UserRepository.hasUser(player)) {
      return;
    }
    if (!event.isCancelled()) {
      try {
        User user = UserRepository.userOf(player);
        if (!user.hasPlayer()) {
          return;
        }
        Object playerHandle = user.playerHandle();
        Location dest = event.getTo();
        if (dest == null) {
          throw new IntaveBootFailureException("Setback location cannot be null");
        }
        if (Math.abs(nativeYaw) > 360f) {
          internalTeleportExecution(player, dest,  nativeYaw % 360f, nativePitch, false);
        } else {
          Field yawField = Lookup.serverField("Entity", "yaw");
          Field pitchField = Lookup.serverField("Entity", "pitch");
          float yaw = (float) yawField.get(playerHandle);
          float pitch = (float) pitchField.get(playerHandle);
          yawField.set(playerHandle, 0f);
          pitchField.set(playerHandle, 0f);
          internalTeleportExecution(player, dest, 0, 0, true);
          yawField.set(playerHandle, yaw);
          pitchField.set(playerHandle, pitch);
        }
      } catch (IllegalAccessException exception) {
        throw new IntaveInternalException(exception);
      }
    }
  }

  private void internalTeleportExecution(Player player, Location dest, float yaw, float pitch, boolean rotationFlags) {
    try {
      User user = UserRepository.userOf(player);
      if (!user.hasPlayer()) {
        return;
      }
      Object playerConnection = user.playerConnection();
      Set<Object> rFlags = rotationFlags ? teleportFlags : Collections.emptySet();
      double posX = dest.getX();
      double posY = dest.getY();
      double posZ = dest.getZ();
      if (WEIRD_BOOLEAN_IN_INVOKE) {
        internalTeleportMethod.invoke(playerConnection, posX, posY, posZ, yaw, pitch, rFlags, false);
      } else {
        internalTeleportMethod.invoke(playerConnection, posX, posY, posZ, yaw, pitch, rFlags);
      }
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }

  private PlayerTeleportEvent constructTeleportEvent(Player player, Location to) {
    return new PlayerTeleportEvent(player, player.getLocation().clone(), to.clone(), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
      @Override
      public void setCancelled(boolean cancel) {
        if (IntaveControl.DEBUG_INTAVE_TELEPORT_EVENT_CANCELS && cancel) {
          PluginInvocation pluginInvocation = CallerResolver.callerPluginInfo();
          if (pluginInvocation == null) {
            IntaveLogger.logger().pushPrintln("[Intave] Intaves teleport event was cancelled anonymously");
          } else {
            IntaveLogger.logger().pushPrintln("[Intave] " + pluginInvocation.pluginName() + " cancelled Intave's teleport event (" + pluginInvocation.className() + ": " + pluginInvocation.methodName() + ")");
          }
        }
        super.setCancelled(cancel);
      }
    };
  }

  public static Vector resolveCollisionVector(
    Player player,
    WrappedAxisAlignedBB entityBoundingBox,
    double motionX, double motionY, double motionZ
  ) {
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(player, entityBoundingBox.addCoord(motionX, motionY, motionZ));

    // motion y
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionY = collisionBox.calculateYOffset(entityBoundingBox, motionY);
    }
    entityBoundingBox = (entityBoundingBox.offset(0.0D, motionY, 0.0D));

    // motion x
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionX = collisionBox.calculateXOffset(entityBoundingBox, motionX);
    }
    entityBoundingBox = entityBoundingBox.offset(motionX, 0.0D, 0.0D);

    // motion z
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionZ = collisionBox.calculateZOffset(entityBoundingBox, motionZ);
    }

    return new Vector(motionX, motionY, motionZ);
  }

  private Vector resolvePushVector(Player player, double positionX, double positionY, double positionZ) {
    WrappedBlockPosition blockPosition = new WrappedBlockPosition(positionX, positionY, positionZ);
    double d0 = positionX - blockPosition.xCoord;
    double d1 = positionZ - blockPosition.zCoord;
    Vector vector = new Vector();
    int i = -1;
    double d2 = 9999.0D;
    if (isOpenBlockSpace(player, blockPosition.west()) && d0 < d2) {
      d2 = d0;
      i = 0;
    }
    if (isOpenBlockSpace(player, blockPosition.east()) && 1.0D - d0 < d2) {
      d2 = 1.0D - d0;
      i = 1;
    }
    if (isOpenBlockSpace(player, blockPosition.north()) && d1 < d2) {
      d2 = d1;
      i = 4;
    }
    if (isOpenBlockSpace(player, blockPosition.south()) && 1.0D - d1 < d2) {
      i = 5;
    }
    float f = 0.1F;
    if (i == 0) {
      vector.setX(-f);
    }
    if (i == 1) {
      vector.setX(f);
    }
    if (i == 4) {
      vector.setZ(-f);
    }
    if (i == 5) {
      vector.setZ(f);
    }
    if (isOpenBlockSpace(player, blockPosition.up())) {
      vector.setY(f);
    }
    return vector;
  }

  private boolean isOpenBlockSpace(Player player, WrappedBlockPosition pos) {
    return hasEmptyCollisionBox(player, pos) && hasEmptyCollisionBox(player, pos.up());
  }

  private boolean hasEmptyCollisionBox(Player player, WrappedBlockPosition blockPosition) {
    return Collision.resolve(player, WrappedAxisAlignedBB.createFromPosition(blockPosition)).isEmpty();
  }
}