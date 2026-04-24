package de.jpx3.intave.module.dispatch;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TELEPORT_ACCEPT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN;

public final class TeleportApplyEnforcer implements PacketEventSubscriber {
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
  public void receiveOutgoingTeleport(ProtocolPacketEvent event, WrapperPlayServerPlayerPositionAndLook packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

    double positionX = packet.getX();
    double positionY = packet.getY();
    double positionZ = packet.getZ();
    float yaw = packet.getYaw();
    float pitch = packet.getPitch();
    Set<Relative> flags = flagsFrom(packet.getRelativeFlags());

    boolean relativeX = flags.contains(Relative.X);
    boolean relativeY = flags.contains(Relative.Y);
    boolean relativeZ = flags.contains(Relative.Z);
    boolean relativeXMotion = flags.contains(Relative.DELTA_X);
    boolean relativeYMotion = flags.contains(Relative.DELTA_Y);
    boolean relativeZMotion = flags.contains(Relative.DELTA_Z);
    boolean rotateDelta = flags.contains(Relative.ROTATE_DELTA);

    Boolean funkyBoolean = packet.isDismountVehicle();

    boolean flagModification = false;
    if (relativeX) {
      positionX += user.meta().movement().verifiedPositionX();
      packet.setX(positionX);
      flags.remove(Relative.X);
      packet.setRelative(RelativeFlag.X, false);
      flagModification = true;
    }

    if (relativeY) {
      positionY += user.meta().movement().verifiedPositionY();
      packet.setY(positionY);
      flags.remove(Relative.Y);
      packet.setRelative(RelativeFlag.Y, false);
      flagModification = true;
    }

    if (relativeZ) {
      positionZ += user.meta().movement().verifiedPositionZ();
      packet.setZ(positionZ);
      flags.remove(Relative.Z);
      packet.setRelative(RelativeFlag.Z, false);
      flagModification = true;
    }

    if (flagModification) {
      event.markForReEncode(true);
    }

    boolean expectRotation = false;//!flags.contains(TeleportFlag.X_ROT) && !flags.contains(TeleportFlag.Y_ROT);

    if (IntaveControl.DEBUG_TELEPORT_PACKET_STACKTRACE) {
      System.out.println("Teleporting " + player.getName() + " to " + positionX + ", " + positionY + ", " + positionZ + " with flags " + flags + " and funkyBoolean " + funkyBoolean);
      Thread.dumpStack();
    }
    // dump packet

    Location teleportLocation = new Location(player.getWorld(), positionX, positionY, positionZ, yaw, pitch);
    movementData.teleportLocation = teleportLocation;
    if (relativeXMotion || relativeYMotion || relativeZMotion) {
      Vector3d deltaMovement = packet.getDeltaMovement();
      movementData.teleportMotion.setTo(new Motion(deltaMovement.getX(), deltaMovement.getY(), deltaMovement.getZ()));
    }
    movementData.teleportRelatives = new HashSet<>(flags);

    movementData.setVerifiedLocation(teleportLocation.clone(), "Teleportation to " + teleportLocation);
    if (NEW_TELEPORTATION) {
      movementData.teleportId = packet.getTeleportId();
    }
    movementData.lastTeleport = 0;

    if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
      IntaveLogger.logger().info("[Intave] Sent teleportation request to " + player.getName() + ": " + MathHelper.formatPosition(movementData.teleportLocation));
    }

    if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
      player.sendMessage(IntavePlugin.prefix() + "You were instructed to teleport to " + MathHelper.formatPosition(movementData.teleportLocation));
    }

    /*
     * ViaBackwards messes up the order of teleportation packets, so we need to account for that
     */
    if (/*!user.meta().protocol().outdatedClient() &&*/ teleportFeedbackSyncEnforcement) {
      user.doubleTickFeedback(
        event,
        () -> {
          movementData.transactionTeleportAllow = true;
        },
        () -> {
          movementData.transactionTeleportAllow = false;
        }
      );
    } else {
      movementData.transactionTeleportAllow = true;
    }

    movementData.awaitTeleport = true;
    movementData.awaitOutgoingTeleport = false;
    movementData.expectTeleportWithRotation = expectRotation;
    movementData.teleportResendCountdown = 20;
//    movementData.outgoingTeleportCountdown = 5;
    movementData.isTeleportConfirmationPacket = false;
  }

  @PacketSubscription(
      priority = ListenerPriority.NORMAL,
      packetsIn = {
          TELEPORT_ACCEPT
      }
  )
  public void receiveTeleportAccept(ProtocolPacketEvent event, WrapperPlayClientTeleportConfirm packet) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

    Integer teleportId = packet.getTeleportId();

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
  public void clientClickUpdate(ProtocolPacketEvent event, WrapperPlayClientPlayerDigging packet) {
    if (!IntaveControl.TELEPORT_FAR_AWAY_ON_Q_PRESS) {
      return;
    }
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (packet.getAction() == DiggingAction.DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR) {
      Synchronizer.synchronize(() -> {
        Location randomLocation = player.getLocation().clone().add(Math.random() * 1000 - 500, 0, Math.random() * 1000 - 500);
        Block highestBlockAt = randomLocation.getWorld().getHighestBlockAt(randomLocation);
        randomLocation.setY(highestBlockAt.getY());
        player.teleport(randomLocation);

        if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
          player.sendMessage(IntavePlugin.prefix() + "Teleport to random " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " as " + ChatColor.RED + " it was command-requested");
        }
      });
    }
  }

  @DispatchTarget
  void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    resendIfLimitsExceeded(event);
    if (movementData.awaitTeleport && (!NEW_TELEPORTATION || movementData.expectTeleport)) {
      checkPotentialTeleport(player);
    }
  }

  private void resendIfLimitsExceeded(ProtocolPacketEvent event) {
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
        Synchronizer.synchronize(() -> {
          Location location = movementData.teleportLocation.clone();
          if (System.currentTimeMillis() - movementData.lastRescueAttempt > 5000 && !MaterialMagic.blocksMovement(VolatileBlockAccess.typeAccess(user, location.clone().add(0, 1, 0)))) {
            Material material = VolatileBlockAccess.typeAccess(user, location);
            int limit = 100;
            while (limit-- > 0 && ((material.isBlock() && material != Material.AIR) || MaterialMagic.blocksMovement(material))) {
              location.add(0, 0.01, 0);
              material = VolatileBlockAccess.typeAccess(user, location);
            }
            movementData.lastRescueAttempt = System.currentTimeMillis();
          }

          location.setYaw(movementData.rotationYaw());
          location.setPitch(movementData.rotationPitch());
          player.teleport(location, UNKNOWN);

          if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
            player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " since " + ChatColor.RED + " you are not responding to teleport requests");
          }
        });
      }
    }
    if (movementData.awaitOutgoingTeleport && movementData.outgoingTeleportCountdown-- < 0) {
      movementData.outgoingTeleportCountdown = 5;
      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        IntaveLogger.logger().printLine("[Intave] Resent outgoing teleport with shift to " + player.getName());
      }
      Synchronizer.synchronize(() -> {
        Location teleportLocation = movementData.teleportLocation;
        if (teleportLocation == null) {
          teleportLocation = player.getLocation();
        }
        Location location = teleportLocation.clone();
        if (System.currentTimeMillis() - movementData.lastRescueAttempt > 5000 && !MaterialMagic.blocksMovement(VolatileBlockAccess.typeAccess(user, location.clone().add(0, 1, 0)))) {
          Material material = VolatileBlockAccess.typeAccess(user, location);
          int limit = 100;
          while (limit-- > 0 && ((material.isBlock() && material != Material.AIR) || MaterialMagic.blocksMovement(material))) {
            location.add(0, 0.01, 0);
            material = VolatileBlockAccess.typeAccess(user, location);
          }
          movementData.lastRescueAttempt = System.currentTimeMillis();
        }
        location.setYaw(movementData.rotationYaw());
        location.setPitch(movementData.rotationPitch());
        player.teleport(location, UNKNOWN);

        if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
          player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " to " + ChatColor.RED + " since you are not responding to outgoing teleport requests");
        }
      });
    }
  }

  private void checkPotentialTeleport(Player player) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    ViolationMetadata violationMetadata = user.meta().violationLevel();
    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;
    Location teleportLocation = movementData.teleportLocation;

    boolean isTeleport;
    if (NEW_TELEPORTATION && movementData.expectTeleport && movementData.transactionTeleportAllow) {
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
      double positionDeviation = MathHelper.distanceOf(
          positionX, positionY, positionZ,
          teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ()
      );
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
    if (isTeleport) {
      PacketLogging logging = Modules.tracker().packetLogging();
      double finalPositionX = positionX, finalPositionY = positionY, finalPositionZ = positionZ;
      logging.logSystemMessage(user, () -> "Accepted teleport move to " + formatDouble(finalPositionX, 3) + " " + formatDouble(finalPositionY, 3) + " " + formatDouble(finalPositionZ, 3));
      if (violationMetadata.disableActiveTeleportBundleNextTeleportAccept) {
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
        movementData.pastLongTeleport = 0;
      }
    }
  }

  private void releaseAwaitTeleportLock(Player player) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
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
    movementData.verifiedPositionX = positionX;
    movementData.verifiedPositionY = positionY;
    movementData.verifiedPositionZ = positionZ;
    movementData.verifiedPositionOrigin = "Teleport";

    Motion teleportMotionModify = movementData.teleportMotion;
    Set<Relative> teleportRelatives = movementData.teleportRelatives;
    if (teleportMotionModify == null || teleportRelatives == null || teleportRelatives.isEmpty()) {
      movementData.baseMotionX = 0.0;
      movementData.baseMotionY = 0.0;
      movementData.baseMotionZ = 0.0;
    } else {
      Motion keepMotion = movementData.baseMotion().filtered(teleportRelatives);
      Motion newMotion = keepMotion.add(teleportMotionModify);
      movementData.setBaseMotion(newMotion);
      movementData.teleportMotion.setNull();
      movementData.teleportRelatives.clear();
    }

    PacketLogging logging = Modules.tracker().packetLogging();
    logging.logSystemMessage(user, () -> "MOTION LOGIC: Reset base motion to 0.0");
    movementData.lastOnGround = false;
    movementData.setBoundingBox(BoundingBox.fromPosition(user, movementData, movementData.teleportLocation));
  }

  private Set<Relative> flagsFrom(RelativeFlag packetFlags) {
    Set<Relative> flags = new HashSet<>();
    if (packetFlags.has(RelativeFlag.X)) {
      flags.add(Relative.X);
    }
    if (packetFlags.has(RelativeFlag.Y)) {
      flags.add(Relative.Y);
    }
    if (packetFlags.has(RelativeFlag.Z)) {
      flags.add(Relative.Z);
    }
    if (packetFlags.has(RelativeFlag.YAW)) {
      flags.add(Relative.Y_ROT);
    }
    if (packetFlags.has(RelativeFlag.PITCH)) {
      flags.add(Relative.X_ROT);
    }
    if (packetFlags.has(RelativeFlag.DELTA_X)) {
      flags.add(Relative.DELTA_X);
    }
    if (packetFlags.has(RelativeFlag.DELTA_Y)) {
      flags.add(Relative.DELTA_Y);
    }
    if (packetFlags.has(RelativeFlag.DELTA_Z)) {
      flags.add(Relative.DELTA_Z);
    }
    if (packetFlags.has(RelativeFlag.ROTATE_DELTA)) {
      flags.add(Relative.ROTATE_DELTA);
    }
    return flags;
  }
}
