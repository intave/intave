package de.jpx3.intave.module.emulate;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.check.CheckConfiguration.CheckSettings;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.player.Effects;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.math.MathHelper.minmax;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.REL_ENTITY_MOVE;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.REL_ENTITY_MOVE_LOOK;
import static de.jpx3.intave.share.Direction.Axis.*;
import static de.jpx3.intave.user.UserRepository.userOf;

public final class MovementEmulation extends Module {
  private boolean enabled;
  private int activationThreshold;
  private int repeatedActivationThreshold;
  private int repeatsRequired;
  private int ticks;
  private int ticksForHardSync;

  private int taskId;

  @Override
  public void enable() {
    CheckSettings configuration = plugin.checks().searchCheck(Physics.class)
      .configuration().settings();

    if (configuration.has("speculative-forward-emulation")) {
      enabled = configuration.boolBy("speculative-forward-emulation.enabled", false);
      activationThreshold = configuration.intBy("speculative-forward-emulation.activation-threshold", 0);
      repeatedActivationThreshold = configuration.intBy("speculative-forward-emulation.repeated-activation-threshold", 0);
      repeatsRequired = configuration.intBy("speculative-forward-emulation.repeats-required", 0);
      ticks = configuration.intBy("speculative-forward-emulation.ticks", 0);
      ticksForHardSync = configuration.intBy("speculative-forward-emulation.ticks-for-hard-sync", 0);
    } else {
      enabled = false;
    }

    if (enabled) {
      plugin.logger().info("Speculative forward emulation is enabled.");
      startTickTask();
    }
  }

  @Override
  public void disable() {
    if (enabled) {
      stopTickTask();
    }
  }

  private void startTickTask() {
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,
      () -> Bukkit.getOnlinePlayers().forEach(this::tick),
      0, 1);
    TaskTracker.begun(taskId);
  }

  private void tick(Player observer) {
    User user = userOf(observer);
    MovementMetadata movement = user.meta().movement();
    ConnectionMetadata connection = user.meta().connection();
    long lastMovementPacket = connection.lastMovementPacket();
    boolean baseTimeout = System.currentTimeMillis() - lastMovementPacket >= activationThreshold * 50L;
    boolean lowTimeout = System.currentTimeMillis() - lastMovementPacket >= repeatedActivationThreshold * 50L;
    if (lowTimeout && movement.speculativeTicks == 0) {
      lowTimeout = ++movement.speculativeLowThresholdOverflows >= repeatsRequired;
    }
    boolean timeout = baseTimeout || lowTimeout;
    boolean hadMovement = Math.abs(movement.baseMotionX) > 0.1 || Math.abs(movement.baseMotionZ) > 0.1 || Math.abs(movement.baseMotionY) > 0.1;

    if (timeout && hadMovement && !movement.speculationEnded) {
      movement.inSpeculation = true;
      movement.speculativeTicks++;
      double positionX;
      double positionY;
      double positionZ;
      Vector motion;
      if (movement.speculativeTicks > 1) {
        motion = new Vector(movement.speculativeMotionX, movement.speculativeMotionY, movement.speculativeMotionZ);
        positionX = movement.speculativePositionX;
        positionY = movement.speculativePositionY;
        positionZ = movement.speculativePositionZ;
      } else {
        motion = new Vector(movement.baseMotionX, movement.baseMotionY, movement.baseMotionZ);
        positionX = movement.positionX();
        positionY = movement.positionY();
        positionZ = movement.positionZ();
      }
      Vector vector = motionProceed(motion, user, BoundingBox.fromPosition(user, positionX, positionY, positionZ), true);
      double motionX = vector.getX();
      double motionY = vector.getY();
      double motionZ = vector.getZ();
      double newPositionX = positionX + motionX;
      double newPositionY = positionY + motionY;
      double newPositionZ = positionZ + motionZ;
      movement.speculativeMotionX = motionX;
      movement.speculativeMotionY = motionY;
      movement.speculativeMotionZ = motionZ;
      movement.speculativePositionX = newPositionX;
      movement.speculativePositionY = newPositionY;
      movement.speculativePositionZ = newPositionZ;
      if (Math.abs(motionY) == 0 && Math.abs(motionX) < 0.005 && Math.abs(motionZ) < 0.005) {
        movement.speculationEnded = true;
      }
      PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
      System.out.println("Speculative forward emulation for " + observer.getName() + ": " + motionX + ", " + motionY + ", " + motionZ);
      packet.getIntegers().write(0, observer.getEntityId());
      packet.getIntegers().write(1, floor(newPositionX * 32.0));
      packet.getIntegers().write(2, floor(newPositionY * 32.0));
      packet.getIntegers().write(3, floor(newPositionZ * 32.0));
      packet.getBytes().write(0, (byte) (int) (movement.rotationYaw() * 256.0F / 360.0F));
      packet.getBytes().write(1, (byte) (int) (movement.rotationPitch() * 256.0F / 360.0F));

      movement.inReceiveSpeculativePacketRoutine = true;
      for (Entity entity : observer.getNearbyEntities(8, 4, 8)) {
        if (entity instanceof Player) {
          Player player = (Player) entity;
          if (player.getEntityId() == observer.getEntityId()) {
            continue;
          }
          MovementMetadata otherMovement = userOf(player).meta().movement();
          Map<UUID, Integer> pendingSpeculativeMovementTicks = otherMovement.pendingSpeculativeMovementTicks;
          pendingSpeculativeMovementTicks.put(observer.getUniqueId(), Math.min(4, pendingSpeculativeMovementTicks.getOrDefault(observer.getUniqueId(), 0) + 1));
          PacketSender.sendServerPacket((Player) entity, packet);
        }
      }
      movement.inReceiveSpeculativePacketRoutine = false;
    } else {
      movement.speculativeTicks = 0;
    }
  }

  @PacketSubscription(
    packetsIn = {FLYING, POSITION, POSITION_LOOK, LOOK}
  )
  public void receiveFlyingPacket(PacketEvent event) {
    User user = userOf(event.getPlayer());
    MovementMetadata movement = user.meta().movement();
    movement.inSpeculation = false;
  }

  @PacketSubscription(
    packetsOut = {/*ENTITY_TELEPORT, ENTITY_MOVE_LOOK,*/ REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK},
    priority = ListenerPriority.LOWEST
  )
  public void onMovementUpdate(PacketEvent event) {
    if (enabled) {
      Player player = event.getPlayer();
      User user = userOf(player);

      PacketContainer packet = event.getPacket();
      Entity entity = EntityLookup.findEntity(player.getWorld(), packet.getIntegers().read(0));
      if (entity instanceof Player) {
        User entityUser = userOf((Player) entity);
        MovementMetadata movement = entityUser.meta().movement();
        if (movement.inReceiveSpeculativePacketRoutine) {
          return;
        }
        Map<UUID, Integer> pendingSpeculativeMovementTicks = user.meta().movement().pendingSpeculativeMovementTicks;
        boolean tickOverflow = pendingSpeculativeMovementTicks.getOrDefault(entity.getUniqueId(), 0) > 0;
        if (movement.speculativeTicks > 0 || tickOverflow) {
          System.out.println("Skipped packet " + packet.getType() + " for " + player.getName() + " because of pending speculative movement ticks.");
          if (tickOverflow) {
            pendingSpeculativeMovementTicks.put(entity.getUniqueId(), pendingSpeculativeMovementTicks.get(entity.getUniqueId()) - 1);
          }
          event.setCancelled(true);
        }
      }
    }
  }

  private static int floor(double var0) {
    int var2 = (int)var0;
    return var0 < (double)var2 ? var2 - 1 : var2;
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
        float pitchCosine = ClientMathHelper.cos(f);
        pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
        motionY += movementData.gravity * (-1 + pitchCosine * 0.75);

        if (motionY < 0.0D && rotationVectorDistance > 0.0D) {
          double d2 = motionY * -0.1D * (double) pitchCosine;
          motionY += d2;
          motionX += lookVector.getX() * d2 / rotationVectorDistance;
          motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
        }

        if (f < 0.0F && rotationVectorDistance > 0.0D) {
          double d9 = dist2 * (double) (-ClientMathHelper.sin(f)) * 0.04D;
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

  public static Vector resolveCollisionVector(
    Player player,
    BoundingBox entityBoundingBox,
    double motionX, double motionY, double motionZ
  ) {
    motionX = minmax(-4, motionX, 4);
    motionY = minmax(-4, motionY, 4);
    motionZ = minmax(-4, motionZ, 4);

    BlockShape collisionBox = Collision.collisionShape(player, entityBoundingBox.expand(motionX, motionY, motionZ));

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

  private double limitMotionAxis(double axis) {
    return ClientMathHelper.clamp_double(axis, -4.0, 4.0);
  }

  private void stopTickTask() {
    Bukkit.getScheduler().cancelTask(taskId);
    TaskTracker.stopped(taskId);
  }
}
