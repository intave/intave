package de.jpx3.intave.event.service;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveException;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.client.PlayerMovementHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserMetaViolationLevelData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.Collision;
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
  private final IntavePlugin plugin;

  public MovementEmulationEngine(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void emulationSetBack(Player player, Vector motion, int ticks) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    double jumpUpwardsMotion = meta.movementData().jumpUpwardsMotion();

    if (violationLevelData.isInActiveTeleportBundle) {
      return;
    }

    if (movementData.emulationVelocity != null) {
      motion = movementData.emulationVelocity;
//      Bukkit.broadcastMessage(player.getName() + ": velocity start apply " + motion);
      movementData.emulationVelocity = null;
    }

//    if (Math.abs(motion.getY() - jumpUpwardsMotion) < 0.01) {
//      motion.setX(motion.getX() / 3);
//      motion.setZ(motion.getZ() / 3);
//    }

    // starting conditions

    violationLevelData.isInActiveTeleportBundle = true;

    if (IntaveControl.DEBUG_EMULATION) {
      player.sendMessage("[E+] " + motion + " (" + ticks + " ticks)");
    }

    proceedEmulationTick(player, motion, ticks, ticks);
  }

  public void emulationPushOutOfBlock(Player player, WrappedAxisAlignedBB boundingBox) {
    User user = UserRepository.userOf(player);
    UserMetaViolationLevelData violationLevelData = user.meta().violationLevelData();

    if (violationLevelData.isInActiveTeleportBundle) {
      return;
    }

    violationLevelData.isInActiveTeleportBundle = true;

    user.meta().movementData().setBoundingBox(boundingBox);
    proceedPushOutOfBlockEmulationTick(player);
  }

  private void proceedPushOutOfBlockEmulationTick(Player player) {
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronizeDelayed(() -> proceedPushOutOfBlockEmulationTick(player), 0);
      return;
    }

    User user = UserRepository.userOf(player);
    if (!user.hasOnlinePlayer()) {
      return;
    }
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();
    WrappedAxisAlignedBB boundingBox = movementData.boundingBox();

    boolean boundingBoxIntersection = Collision.checkBoundingBoxIntersection(user, boundingBox);
    if (boundingBoxIntersection) {
      double positionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
      double positionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
      double positionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
      Vector pushVector = resolvePushVector(player, positionX, positionY, positionZ);

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

  private void proceedEmulationTick(Player player, Vector motion, int ticks, int startingTicks) {
    if (!Bukkit.isPrimaryThread()) {
      Vector finalMotion1 = motion;
      Synchronizer.synchronizeDelayed(() -> proceedEmulationTick(player, finalMotion1, ticks, startingTicks), 0);
      return;
    }

    User user = UserRepository.userOf(player);
    if (!user.hasOnlinePlayer()) {
      return;
    }

    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaViolationLevelData violationLevelData = meta.violationLevelData();

    // check motion status (velocity?)
    Location futurePosition = movementData.verifiedLocation();
    WrappedAxisAlignedBB boundingBox = Collision.boundingBoxOf(user, futurePosition);

    Vector emulationVelocity = movementData.emulationVelocity;
    if (emulationVelocity != null) {
//      Bukkit.broadcastMessage(player.getName() + ": velocity midair apply " + emulationVelocity);
      motion = motionProceed(emulationVelocity, user, boundingBox, false);
      movementData.emulationVelocity = null;
    } else {
      motion = motionProceed(motion, user, boundingBox, startingTicks > ticks);
    }

    // add y motion to falldistance
    if (motion.getY() < 0) {
      movementData.artificialFallDistance += -motion.getY();
    }

    futurePosition = futurePosition.clone().add(motion);
    futurePosition.setYaw(movementData.rotationYaw);
    futurePosition.setPitch(movementData.rotationPitch);

    if ((Math.abs(motion.getX()) < 0.01 && Math.abs(motion.getZ()) < 0.01 && motion.getY() == 0.0) || ticks <= 0) {
      // velocity

      teleport(player, futurePosition);
      violationLevelData.isInActiveTeleportBundle = false;

      Vector futureMotion = motionProceed(motion, user, boundingBox, true);

      movementData.willReceiveSetbackVelocity = true;
      player.setVelocity(futureMotion);

      movementData.physicsMotionX = futureMotion.getX();
      movementData.physicsMotionY = futureMotion.getY();
      movementData.physicsMotionZ = futureMotion.getZ();


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

      Vector finalMotion = motion;
      Synchronizer.synchronizeDelayed(() -> proceedEmulationTick(player, finalMotion, ticks - 1, startingTicks), 1);

      // velocity
      Vector futureMotion = motionProceed(motion, user, boundingBox, true);
//      movementData.physicsMotionX = futureMotion.getX();
//      movementData.physicsMotionY = futureMotion.getY();
//      movementData.physicsMotionZ = futureMotion.getZ();
      movementData.willReceiveSetbackVelocity = true;
      movementData.setbackOverrideVelocity = futureMotion;
      player.setVelocity(new Vector(0, 0, 0));
    }
  }

  private Vector motionProceed(Vector lastMotion, User user, WrappedAxisAlignedBB boundingBox, boolean applyPhysics) {
    Player player = user.player();
    UserMetaMovementData movementData = user.meta().movementData();
    double motionY = lastMotion.getY();
    if (applyPhysics) {
      // TODO: 01/07/21 ladder/vines

      if (movementData.inWater) {
        motionY = lastMotion.getY() * 0.8f;
        motionY -= 0.02;
      } else {
        motionY = (lastMotion.getY() - 0.08) * 0.98f;
      }
    }
    Vector collisionVector = resolveCollisionVector(player, boundingBox, lastMotion.getX(), motionY, lastMotion.getZ());
    boolean onGround = motionY != collisionVector.getY() && motionY < 0.0;
    motionY = collisionVector.getY();
    double multiplier;
    if (applyPhysics) {
      if (movementData.inWater) {
        multiplier = 0.8f;
      } else {
        multiplier = onGround ? 0.546f : 0.91f;
      }
    } else {
      multiplier = 1;
    }
    double motionX = lastMotion.getX() * multiplier;
    double motionZ = lastMotion.getZ() * multiplier;
    if (applyPhysics) {
      if (movementData.inWeb) {
        motionX *= 0.25D;
        motionY *= 0.05f;
        motionZ *= 0.25D;
      }
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
    UserMetaMovementData movementData = user.meta().movementData();
    WrappedAxisAlignedBB entityBoundingBox = Collision.boundingBoxOf(
      user, teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ()
    );
    movementData.setBoundingBox(entityBoundingBox);
    movementData.setVerifiedLocation(teleportLocation.clone(), "Emulation-Setback");
//    player.teleport(teleportLocation);
    rotationlessTeleport(player, teleportLocation, movementData.rotationYaw, movementData.rotationPitch);
    updateMovementStatus(user);
  }

  private void updateMovementStatus(User user) {
    Player player = user.player();
    World world = player.getWorld();
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.inWater = PlayerMovementHelper.isAnyLiquid(world, movementData.boundingBox());
  }

  private final static Set<Object> teleportFlags = new HashSet<>();

  private synchronized void rotationlessTeleport(Player player, Location to, float nativeYaw, float nativePitch) {
    PlayerTeleportEvent event = new PlayerTeleportEvent(player, player.getLocation().clone(), to.clone(), PlayerTeleportEvent.TeleportCause.SPECTATE);
    plugin.eventLinker().fireEvent(event);
    if (player.isDead() || player.getHealth() <= 0 || player.getPassenger() != null || !player.isOnline() || !UserRepository.hasUser(player)) {
      return;
    }
    if (!event.isCancelled()) {
      try {
        Object playerHandle = UserRepository.userOf(player).playerHandle();
        Object playerConnection = playerHandle.getClass().getField("playerConnection").get(playerHandle);
        Class<?> playerConnectionClass = ReflectiveAccess.lookupServerClass("PlayerConnection");
        Method internalTeleport = playerConnectionClass.getDeclaredMethod("internalTeleport", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class);
        if (!internalTeleport.isAccessible()) {
          internalTeleport.setAccessible(true);
        }
        Location dest = event.getTo();
        if (dest == null) {
          throw new IntaveException("Setback location can't be null");
        }
        if (Math.abs(nativeYaw) > 360f) {
          internalTeleport.invoke(playerConnection, dest.getX(), dest.getY(), dest.getZ(), nativeYaw % 360f, nativePitch, Collections.emptySet());
        } else {
          Class<?> entityClass = ReflectiveAccess.lookupServerClass("Entity");
          Field yawField = entityClass.getField("yaw");
          Field pitchField = entityClass.getField("pitch");
          float yaw = (float) yawField.get(playerHandle);
          float pitch = (float) pitchField.get(playerHandle);
          yawField.set(playerHandle, 0f);
          pitchField.set(playerHandle, 0f);
          if (teleportFlags.isEmpty()) {
            Class<?> playerTeleportFlags = ReflectiveAccess.lookupServerClass("PacketPlayOutPosition$EnumPlayerTeleportFlags");
            teleportFlags.add(playerTeleportFlags.getField("X_ROT").get(null));
            teleportFlags.add(playerTeleportFlags.getField("Y_ROT").get(null));
          }
          internalTeleport.invoke(playerConnection, dest.getX(), dest.getY(), dest.getZ(), 0, 0, teleportFlags);
          yawField.set(playerHandle, yaw);
          pitchField.set(playerHandle, pitch);
        }
      } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException | NoSuchFieldException e) {
        throw new IntaveInternalException(e);
      }
    }
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
    return vector;
  }

  private boolean isOpenBlockSpace(Player player, WrappedBlockPosition pos) {
    return hasEmptyCollisionBox(player, pos) && hasEmptyCollisionBox(player, pos.up());
  }

  private boolean hasEmptyCollisionBox(Player player, WrappedBlockPosition blockPosition) {
    return Collision.resolve(player, Collision.boundingBoxOf(blockPosition)).isEmpty();
  }
}