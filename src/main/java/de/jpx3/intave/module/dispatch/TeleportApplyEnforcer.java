package de.jpx3.intave.module.dispatch;

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
import de.jpx3.intave.packet.TeleportFlag;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.DROP_ITEM;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.TELEPORT_ACCEPT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;

public final class TeleportApplyEnforcer implements PacketEventSubscriber {
  private static final boolean NEW_TELEPORTATION = MinecraftVersions.VER1_9_0.atOrAbove();

  public void setup() {
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
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

    StructureModifier<Double> doubles = packet.getDoubles();
    Double positionX = doubles.read(0);
    Double positionY = doubles.read(1);
    Double positionZ = doubles.read(2);
    StructureModifier<Float> floats = packet.getFloat();
    Float yaw = floats.read(0);
    Float pitch = floats.read(1);

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
        return;
      }
    }

    boolean flagModification = false;

    if (relativeX) {
      positionX += user.meta().movement().verifiedPositionX();
      doubles.write(0, positionX);
      flags.remove(TeleportFlag.X);
      flagModification = true;
    }

    if (relativeY) {
      positionY += user.meta().movement().verifiedPositionY();
      doubles.write(1, positionY);
      flags.remove(TeleportFlag.Y);
      flagModification = true;
    }

    if (relativeZ) {
      positionZ += user.meta().movement().verifiedPositionZ();
      doubles.write(2, positionZ);
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

    /*
     * ViaBackwards messes up the order of teleportation packets, so we need to account for that
     */
    if (!user.meta().protocol().outdatedClient()) {
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
    movementData.outgoingTeleportCountdown = 5;
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
        randomLocation.setY(highestBlockAt.getY() + 1);
        player.teleport(randomLocation);
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
          if (System.currentTimeMillis() - movementData.lastRescueAttempt > 5000 && !MaterialMagic.blocksMovement(VolatileBlockAccess.typeAccess(user, location.clone().add(0, 1,0)))) {
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
          player.teleport(location, NETHER_PORTAL);
        });
      }
    }
    if (movementData.awaitOutgoingTeleport && movementData.outgoingTeleportCountdown-- < 0) {
      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        IntaveLogger.logger().printLine("[Intave] Resent outgoing teleport with shift to " + player.getName());
      }
      Synchronizer.synchronize(() -> {
        Location teleportLocation = movementData.teleportLocation;
        if (teleportLocation == null) {
          return;
        }
        Location location = teleportLocation.clone();
        if (System.currentTimeMillis() - movementData.lastRescueAttempt > 5000 && !MaterialMagic.blocksMovement(VolatileBlockAccess.typeAccess(user, location.clone().add(0, 1,0)))) {
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
        player.teleport(location, NETHER_PORTAL);
      });
    }
  }

  private void checkPotentialTeleport(Player player) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
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
//        Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] " + player.getName() + " accepted teleport"));
        System.out.println("[Intave] " + player.getName() + " accepted teleport");
        IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) " + player.getName() + " accepted teleport");
      }
    } else {
      double positionDeviation = MathHelper.distanceOf(
        positionX, positionY, positionZ,
        teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ()
      );
      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        String position = MathHelper.formatPosition(positionX, positionY, positionZ);
//        Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] Checking potential teleport accept of " + player.getName() + " on " + position));
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
//          Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] Additional rotation check on " + player.getName() + ", difference is " + yawDeviation + "/" + pitchDeviation));
          System.out.println("[Intave] Additional rotation check on " + player.getName() + ", difference is " + yawDeviation + "/" + pitchDeviation);
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) Additional rotation check on " + player.getName() + ", difference is " + yawDeviation + "/" + pitchDeviation);
        }
        if (validPosition) {
          movementData.expectTeleportWithRotation = false;
        }
      }

      if (IntaveControl.DEBUG_TELEPORT_LOCKS) {
        if (validPosition) {
//          Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] " + player.getName() + " accepted teleport request (release lock)"));
          System.out.println("[Intave] " + player.getName() + " accepted teleport request (release lock)");
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) " + player.getName() + " accepted teleport request (release lock)");
        } else {
//          Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] " + player.getName() + " did not accept the teleport request"));
          System.out.println("[Intave] " + player.getName() + " did not accept the teleport request");
          IntavePlugin.singletonInstance().logTransmittor().addPlayerLog(player, "(DEBUG/TELEPORT) " + player.getName() + " did not accept the teleport request");
        }
      }
      isTeleport = validPosition;
    }
    if (isTeleport) {
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

    movementData.baseMotionX = 0.0;
    movementData.baseMotionY = 0.0;
    movementData.baseMotionZ = 0.0;

    movementData.lastOnGround = false;
    movementData.setBoundingBox(BoundingBox.fromPosition(user, movementData, movementData.teleportLocation));
  }
}