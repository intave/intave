package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
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
import de.jpx3.intave.packet.TeleportFlag;
import de.jpx3.intave.share.BoundingBox;
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
import org.bukkit.util.Vector;

import java.util.Set;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.DROP_ITEM;
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
  public void receiveOutgoingTeleport(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    boolean vectors = MinecraftVersions.VER1_21_4.atOrAbove();

    StructureModifier<Double> doubles = packet.getDoubles();
    Double positionX;
    Double positionY;
    Double positionZ;
    StructureModifier<Float> floats = packet.getFloat();
    Float yaw;
    Float pitch;
    if (vectors) {
      InternalStructure posRot = packet.getStructures().read(0);
      Vector position = posRot.getVectors().read(0);
      positionX = position.getX();
      positionY = position.getY();
      positionZ = position.getZ();
      yaw = posRot.getFloat().read(0);
      pitch = posRot.getFloat().read(1);
    } else {
      positionX = doubles.read(0);
      positionY = doubles.read(1);
      positionZ = doubles.read(2);
      yaw = floats.read(0);
      pitch = floats.read(1);
    }

    Set<TeleportFlag> flags = TeleportFlag.flagsFrom(packet);
    boolean relativeX = flags.contains(TeleportFlag.X);
    boolean relativeY = flags.contains(TeleportFlag.Y);
    boolean relativeZ = flags.contains(TeleportFlag.Z);

    Boolean funkyBoolean = packet.getBooleans().readSafely(0);
    if (funkyBoolean == null) {
      funkyBoolean = false;
    }

    if (relativeX || relativeY || relativeZ) {
      Vector teleportOffset = new Vector(positionX, positionY, positionZ);
      if (teleportOffset.length() == 0) {
        movementData.awaitTeleport = true;
        movementData.awaitOutgoingTeleport = false;
        return;
      }
    }

    boolean flagModification = false;

    if (relativeX) {
      positionX += user.meta().movement().verifiedPositionX();
      if (vectors) {
        Vector vec = packet.getStructures().read(0).getVectors().read(0);
        vec.setX(positionX);
      } else {
        doubles.write(0, positionX);
      }
      flags.remove(TeleportFlag.X);
      flagModification = true;
    }

    if (relativeY) {
      positionY += user.meta().movement().verifiedPositionY();
      if (vectors) {
        Vector vec = packet.getStructures().read(0).getVectors().read(0);
        vec.setY(positionY);
      } else {
        doubles.write(1, positionY);
      }
      flags.remove(TeleportFlag.Y);
      flagModification = true;
    }

    if (relativeZ) {
      positionZ += user.meta().movement().verifiedPositionZ();
      if (vectors) {
        Vector vec = packet.getStructures().read(0).getVectors().read(0);
        vec.setZ(positionZ);
      } else {
        doubles.write(2, positionZ);
      }
      flags.remove(TeleportFlag.Z);
      flagModification = true;
    }

    if (flagModification) {
      TeleportFlag.writeFlags(packet, flags);
    }

    boolean expectRotation = false;//!flags.contains(TeleportFlag.X_ROT) && !flags.contains(TeleportFlag.Y_ROT);

    if (IntaveControl.DEBUG_TELEPORT_PACKET_STACKTRACE) {
      System.out.println("Teleporting " + player.getName() + " to " + positionX + ", " + positionY + ", " + positionZ + " with flags " + flags + " and funkyBoolean " + funkyBoolean);
      Thread.dumpStack();
    }
    // dump packet

    Location teleportLocation = new Location(player.getWorld(), positionX, positionY, positionZ, yaw, pitch);
    movementData.teleportLocation = teleportLocation;
    movementData.setVerifiedLocation(teleportLocation.clone(), "Teleportation to " + teleportLocation);
    if (NEW_TELEPORTATION) {
      movementData.teleportId = packet.getIntegers().read(0);
    }
    movementData.lastTeleport = 0;

    if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
      IntaveLogger.logger().info("[Intave] Sent teleportation request to " + player.getName() + ": " + MathHelper.formatPosition(movementData.teleportLocation));
      IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) Sent teleportation request to " + MathHelper.formatPosition(movementData.teleportLocation));
    }

    if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
      player.sendMessage(IntavePlugin.prefix() + "You were instructed to teleport to " + MathHelper.formatPosition(movementData.teleportLocation));
    }

    /*
     * ViaBackwards messes up the order of teleportation packets, so we need to account for that
     */
    if (!user.meta().protocol().outdatedClient() && teleportFeedbackSyncEnforcement) {
      user.doubleTickFeedback(
          event,
          () -> movementData.transactionTeleportAllow = true,
          () -> movementData.transactionTeleportAllow = false
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
  public void receiveTeleportAccept(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

    PacketContainer packet = event.getPacket();
    Integer teleportId = packet.getIntegers().read(0);

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
    if (!IntaveControl.TELEPORT_FAR_AWAY_ON_Q_PRESS) {
      return;
    }
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    if (packet.getPlayerDigTypes().read(0) == DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR) {
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
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) Cancelled packet of " + player.getName() + " (Awaiting teleport accept)");
      }

      if (movementData.teleportResendCountdown-- < 0) {
        movementData.teleportResendCountdown = 20;
        if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
          IntaveLogger.logger().printLine("[Intave] Resent teleport to " + player.getName());
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) Resent teleport to " + player.getName());
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
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) " + player.getName() + " accepted teleport");
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
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) Checking potential teleport accept of " + player.getName() + " on " + position);
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
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) Additional rotation check on " + player.getName() + ", difference is " + yawDeviation + "/" + pitchDeviation);
        }
        if (validPosition) {
          movementData.expectTeleportWithRotation = false;
        }
      }

      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        if (validPosition) {
          System.out.println("[Intave] " + player.getName() + " accepted teleport request (release lock)");
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) " + player.getName() + " accepted teleport request (release lock)");
        } else {
          System.out.println("[Intave] " + player.getName() + " did not accept the teleport request");
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) " + player.getName() + " did not accept the teleport request");
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

    movementData.baseMotionX = 0.0;
    movementData.baseMotionY = 0.0;
    movementData.baseMotionZ = 0.0;

    PacketLogging logging = Modules.tracker().packetLogging();
    logging.logSystemMessage(user, () -> "MOTION LOGIC: Reset base motion to 0.0");
    movementData.lastOnGround = false;
    movementData.setBoundingBox(BoundingBox.fromPosition(user, movementData, movementData.teleportLocation));
  }
}