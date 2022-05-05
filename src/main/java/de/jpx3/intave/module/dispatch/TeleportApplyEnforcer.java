package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.TeleportFlag;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.TELEPORT_ACCEPT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;
import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;

public final class TeleportApplyEnforcer implements PacketEventSubscriber {
  private final static boolean TELEPORTATION_DEBUG = false;
  private final static boolean NEW_TELEPORTATION = MinecraftVersions.VER1_9_0.atOrAbove();

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

    Set<TeleportFlag> flags = TeleportFlag.flagSetFrom(packet);
    boolean relativeX = flags.contains(TeleportFlag.X);
    boolean relativeY = flags.contains(TeleportFlag.Y);
    boolean relativeZ = flags.contains(TeleportFlag.Z);

    if (relativeX || relativeY || relativeZ) {
      Vector teleportOffset = new Vector(positionX, positionY, positionZ);
      if (teleportOffset.length() == 0) {
        return;
      }
    }

    Location teleportLocation = new Location(player.getWorld(), positionX, positionY, positionZ);
    movementData.teleportLocation = teleportLocation;
    movementData.setVerifiedLocation(teleportLocation.clone(), "Teleportation (new)");
    if (NEW_TELEPORTATION) {
      movementData.teleportId = packet.getIntegers().read(0);
    } else {
      movementData.lastTeleport = 0;
    }

    if (TELEPORTATION_DEBUG) {
      IntaveLogger.logger().printLine("[Intave] Sent teleportation request to " + player.getName() + ": " + MathHelper.formatPosition(movementData.teleportLocation));
    }

    /*
     * ViaBackwards messes up the order of teleportation packets, so we need to account for that
     */
    if (!user.meta().protocol().outdatedClient()) {
      Modules.feedback().doubleSynchronize(player, event, null,
        (player1, target) -> movementData.transactionTeleportAllow = true,
        (player1, target) -> movementData.transactionTeleportAllow = false
      );
    } else {
      movementData.transactionTeleportAllow = true;
    }

    movementData.awaitTeleport = true;
    movementData.awaitOutgoingTeleport = false;
    movementData.teleportResendCountdown = 20;
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

  @DispatchTarget
  void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    checkPacketFlow(event);
    if (movementData.awaitTeleport && (!NEW_TELEPORTATION || movementData.expectTeleport)) {
      checkPotentialTeleport(player);
    }
  }

  private void checkPacketFlow(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    if (movementData.awaitTeleport) {
      if (TELEPORTATION_DEBUG) {
        IntaveLogger.logger().printLine("[Intave] Cancel packet of " + player.getName() + "(Awaiting teleport accept)");
      }
      if (movementData.teleportResendCountdown-- < 0) {
        if (TELEPORTATION_DEBUG) {
          IntaveLogger.logger().printLine("[Intave] Resent teleport to " + player.getName());
        }
        Synchronizer.synchronize(() -> {
          Location location = movementData.teleportLocation;
          player.teleport(location, NETHER_PORTAL);
        });
      }
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
    } else {
      double positionDeviation = MathHelper.distanceOf(
        positionX, positionY, positionZ,
        teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ()
      );
      if (TELEPORTATION_DEBUG) {
        String position = MathHelper.formatPosition(positionX, positionY, positionZ);
        Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] Checking potential teleport accept of " + player.getName() + " on " + position));
      }
      boolean validPosition = positionDeviation < 0.00001 && movementData.transactionTeleportAllow;
      if (validPosition) {
        if (TELEPORTATION_DEBUG) {
          Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] " + player.getName() + " accepted teleport request (release lock)"));
        }
      } else {
        if (TELEPORTATION_DEBUG) {
          Synchronizer.synchronize(() -> Bukkit.broadcastMessage("[Intave] " + player.getName() + " did not accept the teleport request"));
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

    movementData.physicsMotionX = 0.0;
    movementData.physicsMotionY = 0.0;
    movementData.physicsMotionZ = 0.0;

    movementData.lastOnGround = false;
    movementData.setBoundingBox(BoundingBox.fromPosition(user, movementData.teleportLocation));
  }
}