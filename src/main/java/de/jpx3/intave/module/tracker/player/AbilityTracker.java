package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class AbilityTracker extends Module {
  @PacketSubscription(packetsOut = CAMERA)
  public void receiveCamera(User user, WrapperPlayServerCamera packet) {
    int entityId = packet.getCameraId();
    user.tickFeedback(() -> synchronizedCameraUpdate(user, entityId));
  }

  private void synchronizedCameraUpdate(User user, int entityId) {
    AbilityMetadata abilityData = user.meta().abilities();
    abilityData.hasViewEntity = entityId != user.player().getEntityId();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ABILITIES_IN
    }
  )
  public void receiveAbilities(User user, WrapperPlayClientPlayerAbilities packet) {
    AbilityMetadata abilityData = user.meta().abilities();
    MovementMetadata movementData = user.meta().movement();
    boolean flying = packet.isFlying();
    if (abilityData.allowFlying()) {
      if (flying) {
        abilityData.setFlying(true);
      } else {
        movementData.disabledFlying = true;
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ABILITIES_OUT
    }
  )
  public void sentAbilities(User user, WrapperPlayServerPlayerAbilities packet, ProtocolPacketEvent event) {
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    float flyingSpeed = packet.getFlySpeed();
    float walkingSpeed = packet.getFOVModifier();
    boolean allowedFlight = packet.isFlightAllowed();
    boolean critical = abilityData.allowFlying() && !allowedFlight && movement.criticalTeleportRateLimiter.tryAcquire();
    if (critical /*&& movement.lastTeleport < 20*/) {
      // Teleport again to force transaction synchronization
      Synchronizer.synchronizeDelayed(() -> {
        MovementMetadata moovement = user.meta().movement();
        if (moovement.criticalFlyingDisallowStacks > 0) {
          Location position = moovement.verifiedLocation().clone();
          Player player = user.player();
          position.setWorld(player.getWorld());
          player.teleport(position);
          moovement.criticalFlyingBlockMovementStacks++;
          if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
            player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " as " + ChatColor.RED + " not responding to critical flight disallow");
          }
        }
      }, 20);
    }
    if (critical) {
      if (movement.criticalFlyingDisallowStacks++ == 0) {
        movement.criticalEnterPosX = movement.verifiedPositionX;
        movement.criticalEnterPosY = movement.verifiedPositionY;
        movement.criticalEnterPosZ = movement.verifiedPositionZ;
      }
    }
    user.packetTickFeedback(event, () -> {
      abilityData.setWalkSpeed(walkingSpeed);
      abilityData.setFlySpeed(flyingSpeed);
      abilityData.setAllowFlying(allowedFlight);
      if (critical) {
        if (movement.criticalFlyingDisallowStacks > 0) {
          movement.criticalFlyingDisallowStacks--;
        }
        movement.criticalFlyingBlockMovementStacks = 0;
      }
    });
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {FLYING, PacketId.Client.POSITION, LOOK, POSITION_LOOK}
  )
  public void incomingFlyingUpdate(User user, Player player) {
    MovementMetadata movementData = user.meta().movement();
    if (movementData.criticalFlyingDisallowStacks > 0 &&
      !movementData.criticalFlyingDisallowWasTeleported
    ) {
      double deltaX = movementData.verifiedPositionX - movementData.criticalEnterPosX;
      double deltaY = movementData.verifiedPositionY - movementData.criticalEnterPosY;
      double deltaZ = movementData.verifiedPositionZ - movementData.criticalEnterPosZ;
      double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
      if (distance > 3 && movementData.criticalTeleportRateLimiter.tryAcquire()) {
        Vector setback = new Vector(0, 0, 0);
        Modules.mitigate().movement().emulationSetBack(player, setback, 3, 2, false);
        if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
          player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " for " + ChatColor.RED + " critical flying disallow protection");
        }
        movementData.criticalFlyingDisallowStacks = 0;
      }
    } else if (movementData.criticalFlyingBlockMovementStacks > 0 && movementData.criticalTeleportRateLimiter.tryAcquire()) {
      Synchronizer.synchronize(() -> {
        player.teleport(player.getLocation());
      });
      if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
        player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " for " + ChatColor.RED + " critical flying disallow protection (movement block)");
      }
    }
  }

  @PacketSubscription(
    packetsOut = PacketId.Server.POSITION
  )
  public void outgoingPositionUpdate(User user) {
    MovementMetadata movementData = user.meta().movement();
    movementData.criticalFlyingDisallowWasTeleported = movementData.criticalFlyingDisallowStacks == 1;
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsOut = {
      GAME_STATE_CHANGE
    }
  )
  public void outgoingGameModeUpdate(
    User user, WrapperPlayServerChangeGameState packet
  ) {
    if (packet.getReason() != WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE) {
      return;
    }
    GameMode gameMode = gameModeOf((int) packet.getValue());
    AbilityMetadata abilityData = user.meta().abilities();
    abilityData.setPendingGameMode(gameMode);
    user.tickFeedback(() -> abilityData.setGameMode(gameMode));
  }

  private GameMode gameModeOf(int id) {
    for (GameMode value : GameMode.values()) {
      if (value.id == id) {
        return value;
      }
    }
    throw new IllegalStateException("Unable to resolve gamemode with id " + id);
  }

  public enum GameMode {
    NOT_SET(-1),
    SURVIVAL(0),
    CREATIVE(1),
    ADVENTURE(2),
    SPECTATOR(3);

    private final int id;

    GameMode(int id) {
      this.id = id;
    }

    public static GameMode fromBukkit(org.bukkit.GameMode gameMode) {
      switch (gameMode) {
        case SURVIVAL:
          return SURVIVAL;
        case CREATIVE:
          return CREATIVE;
        case ADVENTURE:
          return ADVENTURE;
        case SPECTATOR:
          return SPECTATOR;
        default:
          throw new IllegalArgumentException("Unable to resolve game mode " + gameMode);
      }
    }

    public int id() {
      return id;
    }
  }
}
