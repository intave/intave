package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.clazz.Lookup;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.TELEPORT_ACCEPT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;

public final class TeleportApplyEnforcer implements PacketEventSubscriber {
  private final static boolean TELEPORTATION_DEBUG = false;
  final boolean NEW_TELEPORTATION = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);

  public void setup() {
    Modules.linker().packetEvents().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      POSITION
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

  @DispatchTarget
  void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    checkPacketFlow(event);
    if (!NEW_TELEPORTATION && movementData.awaitTeleport) {
      checkPotentialLegacyTeleportAccept(player);
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
          IntaveLogger.logger().printLine("[Intave] Resend teleport to " + player.getName());
        }
        Synchronizer.synchronize(() -> {
          Location location = movementData.teleportLocation;
          player.teleport(location, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        });
      }
    }
  }

  private void checkPotentialLegacyTeleportAccept(Player player) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;
    Location teleportLocation = movementData.teleportLocation;
    double positionDeviation = MathHelper.distanceOf(
      positionX, positionY, positionZ,
      teleportLocation.getX(), teleportLocation.getY(), teleportLocation.getZ()
    );
    if (TELEPORTATION_DEBUG) {
      Synchronizer.synchronize(() -> {
        String position = MathHelper.formatPosition(positionX, positionY, positionZ);
        Bukkit.broadcastMessage("[Intave] Checking potential legacy teleport accept of " + player.getName() + " on " + position);
      });
    }
    boolean validPosition = positionDeviation < 0.00001;
//    boolean validRotation = validTeleportRotation(teleportLocation, movementData.rotationYaw, movementData.rotationPitch);
    if (validPosition /*&& validRotation*/) {
      releaseAwaitTeleportLock(player);
      applyPositionConfirmationUpdate(player, positionX, positionY, positionZ);
      if (TELEPORTATION_DEBUG) {
        Synchronizer.synchronize(() -> {
          Bukkit.broadcastMessage("[Intave] " + player.getName() + " accepted teleport request (release lock)");
        });
      }

      double teleportLength = MathHelper.resolveHorizontalDistance(
        movementData.lastPositionX, movementData.lastPositionZ,
        teleportLocation.getX(), teleportLocation.getZ()
      );
      if (teleportLength > 20) {
        movementData.pastLongTeleport = 0;
      }

    } else {
      if (TELEPORTATION_DEBUG) {
        Synchronizer.synchronize(() -> {
          Bukkit.broadcastMessage("[Intave] " + player.getName() + " did not accept the teleport request");
        });
      }
    }
  }

  private void dispatchLegacyTeleportation(
    Player player,
    PacketContainer packet
  ) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

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
      IntaveLogger.logger().printLine("[Intave] Sent teleportation request to " + player.getName() + ": " + MathHelper.formatPosition(movementData.teleportLocation));
    }
    awaitTeleport(player);
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

    MovementMetadata movementData = user.meta().movement();
    Set<TeleportPositionFlagsHelper.PlayerTeleportFlag> flagsModifier = TeleportPositionFlagsHelper.flagsModifier(packet).read(0);

    StructureModifier<Double> doubles = packet.getDoubles();
    Double positionX = doubles.read(0);
    Double positionY = doubles.read(1);
    Double positionZ = doubles.read(2);

    // das geht nicht so wirklich, nein

    boolean relativeX = flagsModifier.contains(TeleportPositionFlagsHelper.PlayerTeleportFlag.X);
    boolean relativeY = flagsModifier.contains(TeleportPositionFlagsHelper.PlayerTeleportFlag.Y);
    boolean relativeZ = flagsModifier.contains(TeleportPositionFlagsHelper.PlayerTeleportFlag.Z);
    if (relativeX) {
      positionX += movementData.positionX;
    }
    if (relativeY) {
      positionY += movementData.positionY;
    }
    if (relativeZ) {
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
    MovementMetadata movementData = user.meta().movement();
    movementData.awaitTeleport = true;
    movementData.awaitOutgoingTeleport = false;
    movementData.teleportResendCountdown = 20;
    movementData.isTeleportConfirmationPacket = false;
  }

  private void releaseAwaitTeleportLock(Player player) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    movementData.awaitTeleport = false;
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

  private static final class TeleportPositionFlagsHelper {
    private static final Class<?> FLAGS_CLASS;

    static {
      if (MinecraftVersions.VER1_17_0.atOrAbove()) {
        FLAGS_CLASS = Lookup.serverClass("PacketPlayOutPosition$EnumPlayerTeleportFlags");
      } else {
        FLAGS_CLASS = MinecraftReflection.getMinecraftClass(
          "EnumPlayerTeleportFlags",
          "PacketPlayOutPosition$EnumPlayerTeleportFlags"
        );
      }
    }

    private static StructureModifier<Set<PlayerTeleportFlag>> flagsModifier(PacketContainer packet) {
      return packet.getSets(EnumWrappers.getGenericConverter(FLAGS_CLASS, PlayerTeleportFlag.class));
    }

    @KeepEnumInternalNames
    public enum PlayerTeleportFlag {
      X, Y, Z, Y_ROT, X_ROT
    }
  }
}