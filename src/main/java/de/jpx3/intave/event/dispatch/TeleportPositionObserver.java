package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;

public final class TeleportPositionObserver implements PacketEventSubscriber {
  private final static boolean TELEPORTATION_DEBUG = false;
  final static boolean NEW_TELEPORTATION = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE);

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "POSITION")
    }
  )
  public void receiveTeleport(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    if (NEW_TELEPORTATION) {
      dispatchTeleportation(player, packet);
    } else {
      dispatchLegacyTeleportation(player, packet);
    }
  }

  void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    checkPacketFlow(event);
    if (!NEW_TELEPORTATION && movementData.awaitTeleport) {
      checkPotentialLegacyTeleportAccept(player);
    }
  }

  private void checkPacketFlow(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    if (movementData.awaitTeleport) {
      if (TELEPORTATION_DEBUG) {
        IntaveLogger.logger().globalPrintLn("[Intave] Cancel packet (Awaiting teleportation accept)");
      }
//      event.setCancelled(true);
      if (movementData.teleportResendCountdown-- < 0) {
        if (TELEPORTATION_DEBUG) {
          IntaveLogger.logger().globalPrintLn("[Intave] UPDATE POSITION BECAUSE OLD IS OUTDATED");
        }
        Synchronizer.synchronize(() -> {
          Location location = movementData.teleportLocation;
          player.teleport(location, PlayerTeleportEvent.TeleportCause.SPECTATE);
        });
      }
    }
  }

  private void checkPotentialLegacyTeleportAccept(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;
    Location teleportLocation = movementData.teleportLocation;
    double positionDeviation = MathHelper.resolveDistance(
      positionX, positionY, positionZ,
      teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ()
    );
    if (TELEPORTATION_DEBUG) {
      Bukkit.broadcastMessage("[Intave] Checking potential legacy teleportation accept of " + player.getName() + ": " + MathHelper.formatPosition(positionX, positionY, positionZ));
    }
    boolean validPosition = positionDeviation < 0.00001;
//    boolean validRotation = validTeleportRotation(teleportLocation, movementData.rotationYaw, movementData.rotationPitch);
    if (validPosition /*&& validRotation*/) {
      releaseAwaitTeleportLock(player);
      applyPositionConfirmationUpdate(player, positionX, positionY, positionZ);
      if (TELEPORTATION_DEBUG) {
        Bukkit.broadcastMessage("[Intave] " + player.getName() + " accepted teleportation request");
      }
    } else {
      if (TELEPORTATION_DEBUG) {
        Bukkit.broadcastMessage("[Intave] Potential teleportation requested of " + player.getName() + "was evaluated as invalid");
      }
    }
  }

  private void dispatchLegacyTeleportation(
    Player player,
    PacketContainer packet
  ) {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();

    StructureModifier<Double> doubles = packet.getDoubles();
    Double positionX = doubles.read(0);
    Double positionY = doubles.read(1);
    Double positionZ = doubles.read(2);

    StructureModifier<Float> floats = packet.getFloat();
    Float rotationYaw = floats.read(0);
    Float rotationPitch = floats.read(1);

    Location teleportLocation = new Location(player.getWorld(), positionX, positionY, positionZ, rotationYaw, rotationPitch);
    movementData.teleportLocation = teleportLocation;
    movementData.setVerifiedLocation(teleportLocation.clone(), "Teleportation (legacy)");
    //movementData.teleport = true;
    movementData.lastTeleport = 0;

//    player.sendMessage("Requested teleportation on ");
    if (TELEPORTATION_DEBUG) {
      IntaveLogger.logger().globalPrintLn("[Intave] Sent teleportation request to " + player.getName() + ": " + MathHelper.formatPosition(movementData.teleportLocation));
    }
    awaitTeleport(player);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "TELEPORT_ACCEPT")
    }
  )
  public void receiveTeleportAccept(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();

    PacketContainer packet = event.getPacket();
    Integer teleportId = packet.getIntegers().read(0);

    if (movementData.teleportId == teleportId) {
      Location teleportLocation = movementData.teleportLocation;
      double positionX = teleportLocation.getX();
      double positionY = teleportLocation.getY();
      double positionZ = teleportLocation.getZ();
      releaseAwaitTeleportLock(player);
      applyPositionConfirmationUpdate(player, positionX, positionY, positionZ);
    }
  }

  private void dispatchTeleportation(
    Player player,
    PacketContainer packet
  ) {
    User user = UserRepository.userOf(player);

    UserMetaMovementData movementData = user.meta().movementData();
    Set<TeleportPositionFlagsHelper.PlayerTeleportFlag> flagsModifier = TeleportPositionFlagsHelper.flagsModifier(packet).read(0);

    StructureModifier<Double> doubles = packet.getDoubles();
    Double positionX = doubles.read(0);
    Double positionY = doubles.read(1);
    Double positionZ = doubles.read(2);

    boolean relative = flagsModifier.contains(TeleportPositionFlagsHelper.PlayerTeleportFlag.X);
    if (relative) {
      positionX += movementData.positionX;
      positionY += movementData.positionY;
      positionZ += movementData.positionZ;
    }

    Integer teleportId = packet.getIntegers().read(0);
    Location teleportLocation = new Location(player.getWorld(), positionX, positionY, positionZ);
    movementData.teleportLocation = teleportLocation;
    movementData.setVerifiedLocation(teleportLocation.clone(), "Teleportation (new)");
    movementData.teleportId = teleportId;
    awaitTeleport(player);
  }

  private void awaitTeleport(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.awaitTeleport = true;
    movementData.teleportResendCountdown = 20;
    movementData.isTeleportConfirmationPacket = false;
  }

  private void releaseAwaitTeleportLock(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.awaitTeleport = false;
    movementData.isTeleportConfirmationPacket = true;
  }

  private void applyPositionConfirmationUpdate(
    Player player,
    double positionX, double positionY, double positionZ
  ) {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.positionX = positionX;
    movementData.positionY = positionY;
    movementData.positionZ = positionZ;
    movementData.verifiedPositionX = positionX;
    movementData.verifiedPositionY = positionY;
    movementData.verifiedPositionZ = positionZ;

    //if (movementData.onGround) {
      movementData.physicsMotionX = 0.0;
      movementData.physicsMotionY = 0.0;
      movementData.physicsMotionZ = 0.0;
    //}

    movementData.lastOnGround = false;

    Location teleportLocation = movementData.teleportLocation;
    double teleportLocationX = teleportLocation.getX();
    double teleportLocationY = teleportLocation.getY();
    double teleportLocationZ = teleportLocation.getZ();
    WrappedAxisAlignedBB boundingBox = Collision.boundingBoxOf(user, teleportLocationX, teleportLocationY, teleportLocationZ);
    movementData.setBoundingBox(boundingBox);
  }
}