/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.WrappedParticle;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerTeleportReader;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.DROP_ITEM;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.LONG_TELEPORT;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TELEPORT_ACCEPT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN;

public final class TeleportController implements PacketEventSubscriber {
  private static final boolean NEW_TELEPORTATION = MinecraftVersions.VER1_9_0.atOrAbove();

  private boolean teleportFeedbackSyncEnforcement = true;

  public void setup() {
    YamlConfiguration settings = IntavePlugin.singletonInstance().settings();
    String path = "compatibility.position-feedback-sync-enforcement";

    Modules.linker().packetEvents().linkSubscriptionsIn(this);

    boolean defaultSetting = true;

    if (Bukkit.getName().contains("Airplane") || Bukkit.getName().contains("Guard")) {
      IntavePlugin.singletonInstance().logger().info("Detected GuardSpigot server, disabling position feedback sync enforcement");
      teleportFeedbackSyncEnforcement = false;
    } else {
      teleportFeedbackSyncEnforcement = settings.getBoolean(path, defaultSetting);
    }
  }

  @PacketSubscription(
      priority = ListenerPriority.LOW,
      packetsOut = {
          POSITION
      }
  )
  public void receiveOutgoingTeleport(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    PacketLogging logging = Modules.tracker().packetLogging();

    PlayerTeleportReader reader = PacketReaders.readerOf(packet);
    double positionX = reader.positionX();
    double positionY = reader.positionY();
    double positionZ = reader.positionZ();
    float yaw = reader.yaw();
    float pitch = reader.pitch();
    Set<Relative> flags = reader.flags();
    double rawPositionX = positionX;
    double rawPositionY = positionY;
    double rawPositionZ = positionZ;
    Set<Relative> rawFlags = new HashSet<>(flags);

    boolean relativeXPosition = flags.contains(Relative.X);
    boolean relativeYPosition = flags.contains(Relative.Y);
    boolean relativeZPosition = flags.contains(Relative.Z);
    boolean relativeXMotion = flags.contains(Relative.DELTA_X);
    boolean relativeYMotion = flags.contains(Relative.DELTA_Y);
    boolean relativeZMotion = flags.contains(Relative.DELTA_Z);
    boolean rotateDelta = flags.contains(Relative.ROTATE_DELTA);

    Boolean funkyBoolean = packet.getBooleans().readSafely(0);
    if (funkyBoolean == null) {
      funkyBoolean = false;
    }

    boolean flagModification = false;
    if (relativeXPosition) {
      positionX += user.meta().movement().verifiedLastPositionX();
      reader.setPositionX(positionX);
      flags.remove(Relative.X);
      flagModification = true;
    }

    if (relativeYPosition) {
      positionY += user.meta().movement().verifiedLastPositionY();
      reader.setPositionY(positionY);
      flags.remove(Relative.Y);
      flagModification = true;
    }

    if (relativeZPosition) {
      positionZ += user.meta().movement().verifiedLastPositionZ();
      reader.setPositionZ(positionZ);
      flags.remove(Relative.Z);
      flagModification = true;
    }

    if (flagModification) {
      reader.setFlags(flags);
    }

    boolean expectRotation = false;

    if (IntaveControl.DEBUG_TELEPORT_PACKET_STACKTRACE) {
      System.out.println("Teleporting " + player.getName() + " to " + positionX + ", " + positionY + ", " + positionZ + " with flags " + flags + " and funkyBoolean " + funkyBoolean);
      Thread.dumpStack();
    }
    // dump packet

    Location teleportLocation = new Location(player.getWorld(), positionX, positionY, positionZ, yaw, pitch);
    movementData.teleportLocation = teleportLocation;
    if (relativeXMotion || relativeYMotion || relativeZMotion) {
      movementData.teleportMotion.setTo(reader.motion());
    }
    movementData.teleportRelatives = new HashSet<>(flags);

    movementData.setVerifiedLocation(teleportLocation.clone());
    if (NEW_TELEPORTATION) {
      movementData.teleportId = packet.getIntegers().read(0);
    }
    long teleportGeneration = ++movementData.teleportGeneration;
    int teleportId = movementData.teleportId;
    movementData.awaitTeleport = true;
    movementData.awaitOutgoingTeleport = false;
    movementData.expectTeleport = false;
    movementData.transactionTeleportAllow = false;
    movementData.teleportResendCountdown = 20;
    movementData.isTeleportConfirmationPacket = false;
    movementData.activeTick(TELEPORT);

    double finalPositionX = positionX;
    double finalPositionY = positionY;
    double finalPositionZ = positionZ;
    boolean finalFunkyBoolean = funkyBoolean;
    logging.logSystemMessage(user, () ->
      "TELEPORT PACKET PREPARE raw=" + formatDouble(rawPositionX, 6) + " " +
        formatDouble(rawPositionY, 6) + " " + formatDouble(rawPositionZ, 6) +
        " raw_flags=" + rawFlags +
        " resolved=" + formatDouble(finalPositionX, 6) + " " +
        formatDouble(finalPositionY, 6) + " " + formatDouble(finalPositionZ, 6) +
        " flags=" + flags + " yaw=" + yaw + " pitch=" + pitch +
        " teleport_id=" + teleportId +
        " generation=" + teleportGeneration +
        " feedback_sync=" + teleportFeedbackSyncEnforcement +
        " funky=" + finalFunkyBoolean
    );

    if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
      IntaveLogger.logger().info("[Intave] Sent teleportation request to " + player.getName() + ": " + MathHelper.formatPosition(movementData.teleportLocation));
    }

    if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
      player.sendMessage(IntavePlugin.prefix() + "You were instructed to teleport to " + MathHelper.formatPosition(movementData.teleportLocation) + " " + relativeXPosition + " " + relativeYPosition + " " + relativeZPosition);
    }

    /*
      We flush the reader here, since the doubleTickFeedback code below performs a
      copy of our packet to sandwich it between two feedback packets,
      we need this write operation before.
     */
    reader.flush();

    /*
     * ViaBackwards messes up the order of teleportation packets, so we need to account for that
     */
    if (/*!user.meta().protocol().outdatedClient() &&*/ teleportFeedbackSyncEnforcement) {
      user.doubleTickFeedback(
        event,
        () -> {
          boolean matchingTeleport = movementData.teleportGeneration == teleportGeneration &&
            movementData.teleportId == teleportId;
          if (movementData.awaitTeleport && matchingTeleport) {
            movementData.transactionTeleportAllow = true;
            logging.logSystemMessage(user, () ->
              "TELEPORT TRANSACTION WINDOW OPEN teleport_id=" + teleportId +
                " generation=" + teleportGeneration
            );
          } else if (!matchingTeleport) {
            logStaleFeedback(user, "OPEN", teleportId, teleportGeneration);
          }
        },
        () -> {
          boolean matchingTeleport = movementData.teleportGeneration == teleportGeneration &&
            movementData.teleportId == teleportId;
          if (!matchingTeleport) {
            logStaleFeedback(user, "CLOSE", teleportId, teleportGeneration);
            return;
          }
          if (!movementData.awaitTeleport) {
            logging.logSystemMessage(user, () ->
              "TELEPORT FEEDBACK BARRIER PASSED teleport_id=" + teleportId +
                " generation=" + teleportGeneration +
                " fallback=false"
            );
            return;
          }

          // The vanilla client sends the teleport accept and its position update before
          // replying to the feedback packet that follows the teleport. Finalize here as
          // a fallback when an earlier feedback response was skipped and emulated.
          if (!NEW_TELEPORTATION || movementData.expectTeleport) {
            checkPotentialTeleport(player);
          }

          if (!movementData.awaitTeleport) {
            logging.logSystemMessage(user, () ->
              "TELEPORT FEEDBACK BARRIER PASSED teleport_id=" + teleportId +
                " generation=" + teleportGeneration +
                " fallback=true"
            );
            return;
          }

          movementData.transactionTeleportAllow = false;
          logging.logSystemMessage(user, () ->
            "TELEPORT FEEDBACK BARRIER FAILED teleport_id=" + teleportId +
              " generation=" + teleportGeneration +
              " accept_received=" + movementData.expectTeleport +
              " retry=immediate"
          );
          resendAwaitedTeleport(player, user, "POST_FEEDBACK_RESEND", teleportId, teleportGeneration);
        }
      );
    } else {
      movementData.transactionTeleportAllow = true;
      logging.logSystemMessage(user, () -> "TELEPORT TRANSACTION WINDOW PERMANENT teleport_id=" + movementData.teleportId);
    }

    movementData.expectTeleportWithRotation = expectRotation;
//    movementData.outgoingTeleportCountdown = 5;

    logging.logSystemMessage(user, () ->
      "TELEPORT LOCK ARMED teleport_id=" + teleportId +
        " generation=" + teleportGeneration +
        " await=" + movementData.awaitTeleport +
        " await_outgoing=" + movementData.awaitOutgoingTeleport +
        " resend_countdown=" + movementData.teleportResendCountdown +
        " target=" + MathHelper.formatPosition(movementData.teleportLocation)
    );

    reader.release();
  }

  private void logStaleFeedback(
    User user,
    String phase,
    int callbackTeleportId,
    long callbackGeneration
  ) {
    MovementMetadata movement = user.meta().movement();
    Modules.tracker().packetLogging().logSystemMessage(user, () ->
      "TELEPORT TRANSACTION WINDOW STALE phase=" + phase +
        " callback_id=" + callbackTeleportId +
        " callback_generation=" + callbackGeneration +
        " current_id=" + movement.teleportId +
        " current_generation=" + movement.teleportGeneration +
        " await=" + movement.awaitTeleport
    );
  }

  @PacketSubscription(
      priority = ListenerPriority.NORMAL,
      packetsIn = {
          TELEPORT_ACCEPT
      }
  )
  public void receiveTeleportAccept(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

    PacketContainer packet = event.getPacket();
    Integer teleportId = packet.getIntegers().read(0);
    PacketLogging logging = Modules.tracker().packetLogging();
    logging.logSystemMessage(user, () ->
      "TELEPORT ACCEPT PACKET received_id=" + teleportId +
        " expected_id=" + movementData.teleportId +
        " matched=" + (movementData.teleportId == teleportId) +
        " await=" + movementData.awaitTeleport
    );

    if (movementData.teleportId == teleportId) {
//      Location teleportLocation = movementData.teleportLocation;
//      double positionX = teleportLocation.getX();
//      double positionY = teleportLocation.getY();
//      double positionZ = teleportLocation.getZ();
//      releaseAwaitTeleportLock(player);
//      applyPositionConfirmationUpdate(player, positionX, positionY, positionZ);
      movementData.expectTeleport = true;
    }
  }

  @PacketSubscription(
      priority = ListenerPriority.HIGH,
      packetsIn = {
          BLOCK_DIG
      }
  )
  public void clientClickUpdate(PacketEvent event) {

    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    if (packet.getPlayerDigTypes().read(0) == DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR) {
      if (IntaveControl.TELEPORT_FAR_AWAY_ON_Q_PRESS) {
        Synchronizer.synchronize(user, () -> {
          Location from = player.getLocation().clone();
          Location randomLocation = player.getLocation().clone().add(Math.random() * 1000 - 500, 0, Math.random() * 1000 - 500);
          Block highestBlockAt = randomLocation.getWorld().getHighestBlockAt(randomLocation);
          randomLocation.setY(highestBlockAt.getY());
          PacketLogging logging = Modules.tracker().packetLogging();
          logging.logSystemMessage(user, () ->
            "TELEPORT ACTION source=Q_PRESS from=" + MathHelper.formatPosition(from) +
              " requested=" + MathHelper.formatPosition(randomLocation) +
              " destination_block=" + highestBlockAt.getType() +
              " destination_block_y=" + highestBlockAt.getY()
          );
          boolean teleported = player.teleport(randomLocation);
          logging.logSystemMessage(user, () ->
            "TELEPORT ACTION RESULT source=Q_PRESS accepted=" + teleported +
              " server_position=" + MathHelper.formatPosition(player.getLocation())
          );

          if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
            player.sendMessage(IntavePlugin.prefix() + "Teleport to random " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " as " + ChatColor.RED + " it was command-requested");
          }
        });
      }

      if (IntaveControl.GIVE_VELOCITY_ON_Q_PRESS) {
        Synchronizer.synchronize(user, () -> {
//          Vector randomVelocity = new Vector(Math.random() * 2 - 1, Math.random() * 2 - 1, Math.random() * 2 - 1);
//          player.setVelocity(new Vector(3, 0.4, 0.3));
          Vector randomVelocity = player.getLocation().getDirection().clone();
          randomVelocity.setY(0.4);
//          Vector randomVelocity = new Vector(0, 0.01, 0);
          player.setVelocity(randomVelocity);
          player.setFallDistance(0.0f);
          if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
            player.sendMessage(IntavePlugin.prefix() + "Set random velocity " + randomVelocity.getX() + " " + randomVelocity.getY() + " " + randomVelocity.getZ() + " as " + ChatColor.RED + " it was command-requested");
          }

//          Synchronizer.synchronizeDelayed(() -> {
//            // send explosion packet
//            sendExplosion(player, player.getLocation(), 4.0f, new Vector(0, -1, 0));
//          }, 2);
        });
      }

      if (IntaveControl.EXTREME_VELOCITY_ON_Q_PRESS) {
        Synchronizer.synchronize(user, () -> {
          Vector extremeVelocity = player.getLocation().getDirection().normalize().multiply(8.0);
          Vector transmittedVelocity = sendVelocityPacket(player, extremeVelocity);
          player.setFallDistance(0.0f);
          if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
            player.sendMessage(IntavePlugin.prefix() + "Sent extreme velocity " + transmittedVelocity.getX() + " " + transmittedVelocity.getY() + " " + transmittedVelocity.getZ() + " as " + ChatColor.RED + " it was command-requested");
          }
        });
      }
    }
  }

  private static Vector sendVelocityPacket(Player player, Vector velocity) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager()
      .createPacket(PacketType.Play.Server.ENTITY_VELOCITY);
    packet.getIntegers().write(0, player.getEntityId());
    Vector transmittedVelocity = velocity;
    if (packet.getVectors().size() > 0) {
      packet.getVectors().write(0, velocity);
    } else {
      int motionX = encodeLegacyVelocity(velocity.getX());
      int motionY = encodeLegacyVelocity(velocity.getY());
      int motionZ = encodeLegacyVelocity(velocity.getZ());
      packet.getIntegers().write(1, motionX);
      packet.getIntegers().write(2, motionY);
      packet.getIntegers().write(3, motionZ);
      transmittedVelocity = new Vector(
        motionX / 8000.0D,
        motionY / 8000.0D,
        motionZ / 8000.0D
      );
    }
    ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    return transmittedVelocity;
  }

  private static int encodeLegacyVelocity(double velocity) {
    return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, velocity * 8000.0D));
  }


  public static void sendExplosion(
    Player player,
    Location location,
    float radius,
    Vector knockback
  ) {
    if (!MinecraftVersions.VER1_21_4.atOrAbove()) {
      return;
    }

    PacketContainer packet = ProtocolLibrary.getProtocolManager()
      .createPacket(PacketType.Play.Server.EXPLOSION);

    // Explosion center (Vec3)
    packet.getVectors().write(0, location.toVector());

    // Radius
    packet.getFloat().write(0, radius);

    // Number of block debris particles
    packet.getIntegers().write(0, 0);

    // Optional player knockback
    packet.getOptionals(BukkitConverters.getVectorConverter())
      .write(0, Optional.ofNullable(knockback));

    // Main explosion particle
    packet.getNewParticles().write(
      0,
      WrappedParticle.create(Particle.CLOUD, null)
    );

    // Explosion sound
    packet.getSoundEffects().write(
      0,
      Sound.ENTITY_GENERIC_EXPLODE
    );

    // 1.21.11: block particle WeightedList
    packet.getModifier().write(6, createEmptyWeightedList(packet));

    ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
  }


  private static Object createEmptyWeightedList(PacketContainer packet) {
    try {
      // Field 6 is the WeightedList of block explosion particles
      Class<?> type = packet.getModifier().getField(6).getType();

      Method emptyFactory = Arrays.stream(type.getDeclaredMethods())
        .filter(method -> Modifier.isStatic(method.getModifiers()))
        .filter(method -> method.getParameterCount() == 0)
        .filter(method -> method.getReturnType() == type)
        .findFirst()
        .orElseThrow(() ->
          new IllegalStateException(
            "Cannot find empty WeightedList factory"
          )
        );

      emptyFactory.setAccessible(true);
      return emptyFactory.invoke(null);

    } catch (ReflectiveOperationException exception) {
      throw new RuntimeException(
        "Could not create explosion block particle list",
        exception
      );
    }
  }

  @DispatchTarget
  void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    resendIfLimitsExceeded(event);
    if (movementData.awaitTeleport && (!NEW_TELEPORTATION || movementData.expectTeleport)) {
      checkPotentialTeleport(player);
    }
  }

  private void resendIfLimitsExceeded(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    if (movementData.awaitTeleport) {
      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        IntaveLogger.logger().printLine("[Intave] Cancelled packet of " + player.getName() + " (Awaiting teleport accept)");
      }

      if (movementData.teleportResendCountdown-- < 0) {
        movementData.teleportResendCountdown = 20;
        if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
          IntaveLogger.logger().printLine("[Intave] Resent teleport to " + player.getName());
        }
        resendAwaitedTeleport(
          player, user, "AWAIT_ACCEPT_RESEND",
          movementData.teleportId, movementData.teleportGeneration
        );
      }
    }
    if (movementData.awaitOutgoingTeleport && movementData.outgoingTeleportCountdown-- < 0) {
      movementData.outgoingTeleportCountdown = 5;
      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        IntaveLogger.logger().printLine("[Intave] Resent outgoing teleport with shift to " + player.getName());
      }
      Synchronizer.synchronize(user, () -> {
        Location teleportLocation = movementData.teleportLocation;
        if (teleportLocation == null) {
          teleportLocation = player.getLocation();
        }
        Location location = teleportLocation.clone();
        Location originalLocation = location.clone();
        location.setYaw(movementData.rotationYaw());
        location.setPitch(movementData.rotationPitch());
        Location resendLocation = location.clone();
        PacketLogging logging = Modules.tracker().packetLogging();
        logging.logSystemMessage(user, () ->
          "TELEPORT ACTION source=AWAIT_OUTGOING_RESEND original=" + MathHelper.formatPosition(originalLocation) +
            " target=" + MathHelper.formatPosition(resendLocation)
        );
        boolean teleported = player.teleport(location, UNKNOWN);
        logging.logSystemMessage(user, () ->
          "TELEPORT ACTION RESULT source=AWAIT_OUTGOING_RESEND accepted=" + teleported
        );

        if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
          player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " to " + ChatColor.RED + " since you are not responding to outgoing teleport requests");
        }
      });
    }
  }

  private void resendAwaitedTeleport(
    Player player,
    User user,
    String source,
    int expectedTeleportId,
    long expectedTeleportGeneration
  ) {
    Synchronizer.synchronizeDelayed(user, () -> {
      MovementMetadata movementData = user.meta().movement();
      boolean matchingTeleport = movementData.teleportId == expectedTeleportId &&
        movementData.teleportGeneration == expectedTeleportGeneration;
      if (!player.isOnline() || !movementData.awaitTeleport || !matchingTeleport) {
        Modules.tracker().packetLogging().logSystemMessage(user, () ->
          "TELEPORT RESEND SKIPPED source=" + source +
            " expected_id=" + expectedTeleportId +
            " expected_generation=" + expectedTeleportGeneration +
            " current_id=" + movementData.teleportId +
            " current_generation=" + movementData.teleportGeneration +
            " await=" + movementData.awaitTeleport
        );
        return;
      }

      Location location = movementData.teleportLocation.clone();
      Location originalLocation = location.clone();
      location.setYaw(movementData.rotationYaw());
      location.setPitch(movementData.rotationPitch());
      Location resendLocation = location.clone();
      PacketLogging logging = Modules.tracker().packetLogging();
      logging.logSystemMessage(user, () ->
        "TELEPORT ACTION source=" + source +
          " original=" + MathHelper.formatPosition(originalLocation) +
          " target=" + MathHelper.formatPosition(resendLocation)
      );
      boolean teleported = player.teleport(location, UNKNOWN);
      logging.logSystemMessage(user, () ->
        "TELEPORT ACTION RESULT source=" + source + " accepted=" + teleported
      );

      if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
        player.sendMessage(
          IntavePlugin.prefix() + "Teleport to " +
            player.getLocation().getBlockX() + " " +
            player.getLocation().getBlockY() + " " +
            player.getLocation().getBlockZ() + " since " +
            ChatColor.RED + "you are not responding to teleport requests"
        );
      }
    }, 2);
  }

  private void checkPotentialTeleport(Player player) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    ViolationMetadata violationMetadata = user.meta().violationLevel();
    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;
    Location teleportLocation = movementData.teleportLocation;
    PacketLogging logging = Modules.tracker().packetLogging();
    double positionDeviation = MathHelper.distanceOf(
      positionX, positionY, positionZ,
      teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ()
    );
    double candidatePositionX = positionX;
    double candidatePositionY = positionY;
    double candidatePositionZ = positionZ;
    logging.logSystemMessage(user, () ->
      "TELEPORT CONFIRM CANDIDATE received=" + formatDouble(candidatePositionX, 6) + " " +
        formatDouble(candidatePositionY, 6) + " " + formatDouble(candidatePositionZ, 6) +
        " expected=" + MathHelper.formatPosition(teleportLocation) +
        " deviation=" + positionDeviation +
        " transaction_allowed=" + movementData.transactionTeleportAllow +
        " expect_packet=" + movementData.expectTeleport +
        " require_rotation=" + movementData.expectTeleportWithRotation
    );

    boolean isTeleport;
    String confirmationMode;
    if (NEW_TELEPORTATION && movementData.expectTeleport && movementData.transactionTeleportAllow) {
      confirmationMode = "teleport_id";
      positionX = teleportLocation.getX();
      positionY = teleportLocation.getY();
      positionZ = teleportLocation.getZ();
      isTeleport = true;
      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        System.out.println("[Intave] " + player.getName() + " accepted teleport");
      }
      if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
        player.sendMessage(IntavePlugin.prefix() + "Movement matched teleport request to " + MathHelper.formatPosition(teleportLocation));
      }
    } else {
      confirmationMode = "position";
      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        String position = MathHelper.formatPosition(positionX, positionY, positionZ);
        System.out.println("[Intave] Checking potential teleport accept of " + player.getName() + " on " + position);
      }
      boolean validPosition = positionDeviation < 0.00001 && movementData.transactionTeleportAllow;
      if (validPosition && movementData.expectTeleportWithRotation) {
        float yaw = movementData.rotationYaw();
        float pitch = movementData.rotationPitch();
        float yawDeviation = MathHelper.distanceInDegrees(yaw, teleportLocation.getYaw());
        float pitchDeviation = MathHelper.distanceInDegrees(pitch, teleportLocation.getPitch());
        validPosition = yawDeviation < 0.001 && pitchDeviation < 0.001;
        if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
          System.out.println("[Intave] Additional rotation check on " + player.getName() + ", difference is " + yawDeviation + "/" + pitchDeviation);
        }
        if (validPosition) {
          movementData.expectTeleportWithRotation = false;
        }
      }

      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        if (validPosition) {
          System.out.println("[Intave] " + player.getName() + " accepted teleport request (release lock)");
        } else {
          System.out.println("[Intave] " + player.getName() + " did not accept the teleport request");
        }
      }
      isTeleport = validPosition;
      if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
        player.sendMessage(
            IntavePlugin.prefix() + "Movement " + (isTeleport ? "matched" : "did not match")
                + " teleport request to " + MathHelper.formatPosition(teleportLocation) +
                " (dev: " + positionDeviation + ", rrot: " + movementData.expectTeleportWithRotation +
                ", tra: " + movementData.transactionTeleportAllow + ")"
        );
      }
    }
    boolean accepted = isTeleport;
    logging.logSystemMessage(user, () ->
      "TELEPORT CONFIRM DECISION accepted=" + accepted +
        " mode=" + confirmationMode +
        " deviation=" + positionDeviation +
        " transaction_allowed=" + movementData.transactionTeleportAllow +
        " require_rotation=" + movementData.expectTeleportWithRotation +
        " teleport_id=" + movementData.teleportId
    );
    if (isTeleport) {
      double finalPositionX = positionX, finalPositionY = positionY, finalPositionZ = positionZ;
      logging.logSystemMessage(user, () -> "Accepted teleport move to " + formatDouble(finalPositionX, 3) + " " + formatDouble(finalPositionY, 3) + " " + formatDouble(finalPositionZ, 3));
      if (violationMetadata.disableActiveTeleportBundleNextTeleportAccept) {
        logging.logSystemMessage(user, () -> "TELEPORT BUNDLE RELEASE active_before=" + violationMetadata.isInActiveTeleportBundle);
        violationMetadata.disableActiveTeleportBundleNextTeleportAccept = false;
        violationMetadata.isInActiveTeleportBundle = false;
      }
      releaseAwaitTeleportLock(player);
      applyPositionConfirmationUpdate(player, positionX, positionY, positionZ);
      double teleportLength = MathHelper.resolveHorizontalDistance(
          movementData.lastPositionX, movementData.lastPositionZ,
          teleportLocation.getX(), teleportLocation.getZ()
      );
      if (teleportLength > 20) {
        movementData.activeTick(LONG_TELEPORT);
      }
    }
  }

  private void releaseAwaitTeleportLock(Player player) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    Modules.tracker().packetLogging().logSystemMessage(user, () ->
      "TELEPORT LOCK RELEASE teleport_id=" + movementData.teleportId +
        " await=" + movementData.awaitTeleport +
        " expect=" + movementData.expectTeleport +
        " transaction_allowed=" + movementData.transactionTeleportAllow
    );
    movementData.awaitTeleport = false;
    movementData.expectTeleport = false;
    movementData.transactionTeleportAllow = false;
    movementData.isTeleportConfirmationPacket = true;
  }

  private void applyPositionConfirmationUpdate(
    Player player,
    double positionX, double positionY, double positionZ
  ) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    movementData.positionX = positionX;
    movementData.positionY = positionY;
    movementData.positionZ = positionZ;
    movementData.verifiedLastPositionX = positionX;
    movementData.verifiedLastPositionY = positionY;
    movementData.verifiedLastPositionZ = positionZ;
    movementData.verifiedPositionOrigin = "Teleport";

    Motion teleportMotionModify = movementData.teleportMotion;
    Set<Relative> teleportRelatives = movementData.teleportRelatives;
    Motion previousMotion = movementData.mutableBaseMotionCopy();
    Motion packetMotion = teleportMotionModify == null ? null : teleportMotionModify.copy();
    Set<Relative> packetRelatives = teleportRelatives == null ? null : new HashSet<>(teleportRelatives);
    if (teleportMotionModify == null || teleportRelatives == null || teleportRelatives.isEmpty()) {
      movementData.baseMotionX = 0.0;
      movementData.baseMotionY = 0.0;
      movementData.baseMotionZ = 0.0;
    } else {
      Motion keepMotion = movementData.mutableBaseMotionCopy().filtered(teleportRelatives);
      Motion newMotion = keepMotion.add(teleportMotionModify);
      movementData.setBaseMotion(newMotion);
      movementData.clearPostTickMotionCandidates();
      movementData.teleportMotion.setNull();
      movementData.teleportRelatives.clear();
    }

    PacketLogging logging = Modules.tracker().packetLogging();
    Motion confirmedMotion = movementData.mutableBaseMotionCopy();
    logging.logSystemMessage(user, () ->
      "TELEPORT MOTION APPLY previous=" + MathHelper.formatMotion(previousMotion) +
        " packet_motion=" + packetMotion +
        " relatives=" + packetRelatives +
        " result=" + MathHelper.formatMotion(confirmedMotion)
    );
    movementData.lastOnGround = false;
    movementData.setBoundingBox(BoundingBox.fromPosition(user, movementData, movementData.teleportLocation));
  }
}
