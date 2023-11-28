package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.IntaveBootFailureException;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.trace.Caller;
import de.jpx3.intave.klass.trace.PluginInvocation;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static de.jpx3.intave.math.MathHelper.minmax;
import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.share.Direction.Axis.*;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN;

public final class SetbackSimulator extends Module {
  private Physics physicsCheck;
  private InternalTeleportApplier teleportMethodContainer;
  private boolean closeInventoryOnDetection;

  @Override
  public void enable() {
    this.physicsCheck = plugin.checks().searchCheck(Physics.class);
    this.closeInventoryOnDetection = physicsCheck.closeInventoryOnDetection();
    this.teleportMethodContainer = new InternalTeleportApplier();
  }

  private static final Set<TeleportCause> BANNED_TELEPORT_CAUSES = new HashSet<>(
    Arrays.asList(NETHER_PORTAL /* Intave */, UNKNOWN /* Vanilla "AntiCheat" */)
  );

  @BukkitEventSubscription
  public void uponTeleport(PlayerTeleportEvent teleport) {
    if (BANNED_TELEPORT_CAUSES.contains(teleport.getCause())) {
      return;
    }
    Player player = teleport.getPlayer();
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    if (violationLevelData.isInActiveTeleportBundle) {
      if (IntaveControl.DEBUG_EMULATION) {
        player.sendMessage(ChatColor.DARK_PURPLE + "[E-] Exit by " + teleport.getCause() + " teleport event");
      }
//      violationLevelData.isInActiveTeleportBundle = false;
      violationLevelData.disableActiveTeleportBundleNextTick = true;
    }
  }

  public void emulationSetBack(
    Player player,
    Vector motion,
    int ticks,
    boolean cancellable
  ) {
    emulationSetBack(player, motion, ticks, 1, cancellable);
  }

  public void emulationSetBack(
    Player player,
    Vector motion,
    int ticks,
    int delay,
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
      player.sendMessage(ChatColor.DARK_PURPLE + "[E+] " + motion + " (" + ticks + " ticks)");
    }

    proceedEmulationTick(player, motion, ticks, ticks, delay, cancellable);
  }

//  public void emulationPushOutOfBlock(
//    Player player, BoundingBox boundingBox,
//    double motionX, double motionY, double motionZ
//  ) {
//    User user = UserRepository.userOf(player);
//    ViolationMetadata violationLevelData = user.meta().violationLevel();
//
//    if (violationLevelData.isInActiveTeleportBundle) {
//      return;
//    }
//
//    violationLevelData.isInActiveTeleportBundle = true;
//    MovementMetadata movementData = user.meta().movement();
//    movementData.physicsMotionX = motionX;
//    movementData.physicsMotionY = motionY;
//    movementData.physicsMotionZ = motionZ;
//    movementData.setBoundingBox(boundingBox);
//    proceedPushOutOfBlockEmulationTick(player);
//  }

//  private void proceedPushOutOfBlockEmulationTick(Player player) {
//    if (!Bukkit.isPrimaryThread()) {
//      Synchronizer.synchronizeDelayed(() -> proceedPushOutOfBlockEmulationTick(player), 0);
//      return;
//    }
//
//    User user = UserRepository.userOf(player);
//    if (!user.hasPlayer()) {
//      return;
//    }
//
//    MetadataBundle meta = user.meta();
//    MovementMetadata movementData = meta.movement();
//    ViolationMetadata violationLevelData = meta.violationLevel();
//    BoundingBox boundingBox = movementData.boundingBox();
//
//    if (!violationLevelData.isInActiveTeleportBundle) {
//      return;
//    }
//
//    boolean boundingBoxIntersection = Collision.present(user.player(), boundingBox);
//    if (boundingBoxIntersection) {
//      double positionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
//      double positionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
//      double positionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
//      Vector pushVector = resolvePushVector(player, positionX, positionY, positionZ);
//
//      if (pushVector.length() < 0.005) {
//        violationLevelData.isInActiveTeleportBundle = false;
//        return;
//      }
//
//      Location location = movementData.verifiedLocation().clone().add(pushVector);
//      teleport(player, location);
//
//      if (IntaveControl.DEBUG_EMULATION) {
//        player.sendMessage(ChatColor.DARK_PURPLE + "[E/] Push out of blocks emulation (? remaining) with " + MathHelper.formatMotion(pushVector));
//      }
//      Synchronizer.synchronizeDelayed(() -> proceedPushOutOfBlockEmulationTick(player), 1);
//    } else {
//      if (IntaveControl.DEBUG_EMULATION) {
//        player.sendMessage(ChatColor.DARK_PURPLE + "[E-] Player is no longer inside blocks");
//      }
//      violationLevelData.isInActiveTeleportBundle = false;
//    }
//  }

  private void proceedEmulationTick(
    Player player,
    Vector motion,
    int ticks,
    int startingTicks,
    int delay,
    boolean cancellable
  ) {
    if (!Bukkit.isPrimaryThread()) {
      Vector finalMotion1 = motion;
      Synchronizer.synchronizeDelayed(() -> proceedEmulationTick(player, finalMotion1, ticks, startingTicks, delay, cancellable), 0);
      return;
    }

    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }

    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ViolationMetadata violationLevelData = meta.violationLevel();

    if (!violationLevelData.isInActiveTeleportBundle) {
      return;
    }

    // check motion status (velocity?)
    Location futurePosition = movementData.verifiedLocation();
    BoundingBox boundingBox = BoundingBox.fromPosition(user, movementData, futurePosition);

    Vector emulationVelocity = movementData.emulationVelocity;
    if (emulationVelocity != null) {
      motion = motionProceed(emulationVelocity, user, boundingBox, true);
      movementData.emulationVelocity = null;
    } else {
      motion = motionProceed(motion, user, boundingBox, startingTicks > ticks);
    }

    // add y-motion to fall distance
    if (motion.getY() < 0) {
      movementData.artificialFallDistance += -motion.getY();
    }

    if (!Collision.present(player, BoundingBox.fromPosition(user, movementData, futurePosition.clone().add(motion)))) {
      futurePosition = futurePosition.clone().add(motion);
    }
    futurePosition.setYaw(movementData.rotationYaw);
    futurePosition.setPitch(movementData.rotationPitch);

    boolean exitBundle = (Math.abs(motion.getX()) < 0.01 && Math.abs(motion.getZ()) < 0.01 && motion.getY() == 0.0 && cancellable) || ticks <= 0;

    if (exitBundle) {
      // velocity

      // fixes stuck in block below, please remove and fix me differently
      futurePosition.subtract(0, 0.02, 0);
      boundingBox = BoundingBox.fromPosition(user, movementData, futurePosition);
      futurePosition.add(0, Collision.nonePresent(player, boundingBox) ? 0.03 : 0.0201, 0);
      boundingBox = BoundingBox.fromPosition(user, movementData, futurePosition);

      /*
       * giving the client control over the atb variable is actually very bad,
       * because it would allow the client to disable the entire movement system.
       * however, we have a teleport packet after, requiring the client to respond
       */
      user.tickFeedback(
        () -> violationLevelData.disableActiveTeleportBundleNextTick = true
      );
      teleport(player, futurePosition);
//      violationLevelData.isInActiveTeleportBundle = false;

      Vector futureMotion = motionProceed(motion, user, boundingBox, true);

      movementData.willReceiveSetbackVelocity = true;
      player.setVelocity(futureMotion);

//      player.sendMessage("Setback velocity: " + MathHelper.formatMotion(futureMotion));
      movementData.baseMotionX = futureMotion.getX();
      movementData.baseMotionY = futureMotion.getY();
      movementData.baseMotionZ = futureMotion.getZ();

      if (movementData.onGround) {
        physicsCheck.applyFallDamageUpdate(user);
        movementData.artificialFallDistance = 0;
      }

      if (IntaveControl.DEBUG_EMULATION) {
        player.sendMessage(ChatColor.DARK_PURPLE + "[E-] (" + ticks + " ticks remaining)");
      }
    } else {
      // teleport
      //player.teleport(futurePosition);

      boundingBox = BoundingBox.fromPosition(user, movementData, futurePosition);
      boolean boundingBoxIntersection = Collision.present(user.player(), boundingBox);
      if (boundingBoxIntersection) {
        double positionX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        double positionY = (boundingBox.minY + boundingBox.maxY) / 2.0;
        double positionZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
        Vector pushVector = resolvePushVector(player, positionX, positionY, positionZ);
        futurePosition = futurePosition.add(pushVector);
      }

      teleport(player, futurePosition);

      if (IntaveControl.DEBUG_EMULATION) {
        String s = ChatColor.DARK_PURPLE + "[E/] " + MathHelper.formatMotion(motion) + (boundingBoxIntersection ? " (block-push)" : "") + " at " + MathHelper.formatPosition(futurePosition) + " (" + ticks + " ticks remaining)";
        player.sendMessage(s);
      }
      //   s += " @" + movementData.entityBoundingBox();

      Vector finalMotion = motion.clone();
      Synchronizer.synchronizeDelayed(() -> proceedEmulationTick(player, finalMotion, ticks - 1, startingTicks, delay, cancellable), delay);

      // velocity
      Vector futureMotion = motionProceed(motion, user, boundingBox, true);
//      movementData.physicsMotionX = futureMotion.getX();
//      movementData.physicsMotionY = futureMotion.getY();
//      movementData.physicsMotionZ = futureMotion.getZ();

      movementData.willReceiveSetbackVelocity = true;
      movementData.setbackOverrideVelocity = futureMotion;
      // this is not the real setback motion - velocity will be applied later
      player.setVelocity(new Vector(0, 0, 0));
    }
  }

  private Vector motionProceed(Vector lastMotion, User user, BoundingBox boundingBox, boolean applyPhysics) {
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
        float pitchCosine = ClientMath.cos(f);
        pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
        motionY += movementData.gravity * (-1 + pitchCosine * 0.75);

        if (motionY < 0.0D && rotationVectorDistance > 0.0D) {
          double d2 = motionY * -0.1D * (double) pitchCosine;
          motionY += d2;
          motionX += lookVector.getX() * d2 / rotationVectorDistance;
          motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
        }

        if (f < 0.0F && rotationVectorDistance > 0.0D) {
          double d9 = dist2 * (double) (-ClientMath.sin(f)) * 0.04D;
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
          if (Effects.levitationEffectActive(player)) {
            int levitationAmplifier = Effects.effectAmplifier(player, Effects.EFFECT_LEVITATION);
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
      int blockPositionStartX = floor(boundingBox.minX + 0.001);
      int blockPositionStartY = floor(boundingBox.minY + 0.001);
      int blockPositionStartZ = floor(boundingBox.minZ + 0.001);
      int blockPositionEndX = floor(boundingBox.maxX - 0.001);
      int blockPositionEndY = floor(boundingBox.maxY - 0.001);
      int blockPositionEndZ = floor(boundingBox.maxZ - 0.001);

      boolean manualWebCheck = false;
      for (int x = blockPositionStartX; x <= blockPositionEndX; x++) {
        for (int y = blockPositionStartY; y <= blockPositionEndY; y++) {
          for (int z = blockPositionStartZ; z <= blockPositionEndZ; z++) {
            Material material = VolatileBlockAccess.typeAccess(user, x, y, z);
            if (material == BlockTypeAccess.WEB) {
              manualWebCheck = true;
            }
          }
        }
      }

      if (movementData.inWeb || /* manual check */ manualWebCheck) {
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
    return ClientMath.clamp_double(axis, -4.0, 4.0);
  }

  private void teleport(Player player, Location teleportLocation) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

    BoundingBox entityBoundingBox = BoundingBox.fromPosition(user, movementData, teleportLocation);
    movementData.setBoundingBox(entityBoundingBox);
    movementData.setVerifiedLocation(teleportLocation.clone(), "Emulation-Setback");
//    player.teleport(teleportLocation);
    if (closeInventoryOnDetection && user.meta().inventory().inventoryOpen()) {
      player.closeInventory();
    }
    rotationlessTeleport(player, teleportLocation, movementData.rotationYaw, movementData.rotationPitch);
    updateMovementStatus(user);
  }

  private void updateMovementStatus(User user) {
    Player player = user.player();
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    movementData.inWater = Collision.rasterizedLiquidPresentSearch(user, movementData.boundingBox());
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
          teleportMethodContainer.teleport(player, dest, nativeYaw % 360f, nativePitch, false);
        } else {
          Field yawField = Lookup.serverField("Entity", "yaw");
          Field pitchField = Lookup.serverField("Entity", "pitch");
          float yaw = (float) yawField.get(playerHandle);
          float pitch = (float) pitchField.get(playerHandle);
          yawField.set(playerHandle, 0f);
          pitchField.set(playerHandle, 0f);
          teleportMethodContainer.teleport(player, dest, 0, 0, true);
          yawField.set(playerHandle, yaw);
          pitchField.set(playerHandle, pitch);
        }
      } catch (IllegalAccessException exception) {
        throw new IntaveInternalException(exception);
      }
    }
  }

  private PlayerTeleportEvent constructTeleportEvent(Player player, Location to) {
    return new PlayerTeleportEvent(player, player.getLocation().clone(), to.clone(), NETHER_PORTAL) {
      @Override
      public void setCancelled(boolean cancel) {
        if (IntaveControl.DEBUG_INTAVE_TELEPORT_EVENT_CANCELS && cancel) {
          PluginInvocation pluginInvocation = Caller.pluginInfo();
          if (pluginInvocation == null) {
            IntaveLogger.logger().printLine("[Intave] Intave's teleport event was cancelled anonymously");
          } else {
            IntaveLogger.logger().printLine("[Intave] " + pluginInvocation.pluginName() + " cancelled Intave's teleport event (" + pluginInvocation.className() + ": " + pluginInvocation.methodName() + ")");
          }
        }
        super.setCancelled(cancel);
      }
    };
  }

  public static Vector resolveCollisionVector(
    Player player,
    BoundingBox entityBoundingBox,
    double motionX, double motionY, double motionZ
  ) {
    motionX = minmax(-4, motionX, 4);
    motionY = minmax(-4, motionY, 4);
    motionZ = minmax(-4, motionZ, 4);

    BlockShape collisionBox = Collision.shape(player, entityBoundingBox.expand(motionX, motionY, motionZ));

    // motion y
    motionY = collisionBox.allowedOffset(Y_AXIS, entityBoundingBox, motionY);
    entityBoundingBox = (entityBoundingBox.offset(0.0D, motionY, 0.0D));

    // motion x
    motionX = collisionBox.allowedOffset(X_AXIS, entityBoundingBox, motionX);
    entityBoundingBox = entityBoundingBox.offset(motionX, 0.0D, 0.0D);

    // motion z
    motionZ = collisionBox.allowedOffset(Z_AXIS, entityBoundingBox, motionZ);

    return new Vector(motionX, motionY, motionZ);
  }

  private Vector resolvePushVector(Player player, double positionX, double positionY, double positionZ) {
    BlockPosition blockPosition = new BlockPosition(positionX, positionY, positionZ);
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
    if (isOpenBlockSpace(player, blockPosition.up()) && isOpenBlockSpace(player, blockPosition.up().up())) {
      vector.setY(f);
    }
    return vector;
  }

  private boolean isOpenBlockSpace(Player player, BlockPosition pos) {
    return hasEmptyCollisionBox(player, pos) && hasEmptyCollisionBox(player, pos.up());
  }

  private boolean hasEmptyCollisionBox(Player player, BlockPosition blockPosition) {
    User user = UserRepository.userOf(player);
    return Collision.nonePresent(player, BoundingBox.fromPosition(user, user.meta().movement(), blockPosition));
  }
}