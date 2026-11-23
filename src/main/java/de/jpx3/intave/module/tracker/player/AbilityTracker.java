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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.*;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.event.Cancellable;

import static de.jpx3.intave.packet.ListenerPriority.LAST;
import static de.jpx3.intave.packet.ListenerPriority.LATER;
import static de.jpx3.intave.share.GameStateChangeType.CHANGE_GAME_MODE;

public final class AbilityTracker extends Module {
  @OnReceive(priority = LATER)
  public void onAbilities(User user, AbilityInPacket packet, Cancellable event) {
    AbilityMetadata abilityData = user.meta().abilities();
    boolean flying = packet.requestedFlying();
    // The client has already toggled flight when it sends this request.
    abilityData.acknowledgeClientFlying(flying);
    user.sendMessage("§aClient flying: " + flying);
    if (event.isCancelled() || !abilityData.allowFlying()) {
      return;
    }
    if (flying) {
      abilityData.setFlying(true);
    } else {
      abilityData.disabledFlying = true;
    }
  }

  @OnSend
  public void sentCamera(User user, CameraPacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    int entityId = packet.entityId();
    Modules.feedback().synchronize(user, event, () -> {
      user.meta().abilities().hasViewEntity = entityId != user.player().getEntityId();
    });
  }

  @OnSend(priority = LATER)
  public void sentAbilities(User user, AbilityOutPacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    float flyingSpeed = packet.flyingSpeed();
    float walkingSpeed = packet.walkingSpeed();
    boolean flying = packet.flying();
    boolean allowedFlight = packet.flyingAllowed();
    boolean critical = abilityData.allowFlying() && !allowedFlight && movement.criticalTeleportRateLimiter.tryAcquire();
    if (critical) {
      if (movement.criticalFlyingDisallowStacks++ == 0) {
        movement.criticalEnterPosX = movement.verifiedLastPositionX;
        movement.criticalEnterPosY = movement.verifiedLastPositionY;
        movement.criticalEnterPosZ = movement.verifiedLastPositionZ;
      }
    }
    Modules.feedback().synchronize(user, event, () -> {
      abilityData.setWalkSpeed(walkingSpeed);
      abilityData.setFlySpeed(flyingSpeed);
      abilityData.setAllowFlying(allowedFlight);
      abilityData.acknowledgeFlying(flying);
      if (critical) {
        if (movement.criticalFlyingDisallowStacks > 0) {
          movement.criticalFlyingDisallowStacks--;
        }
        movement.criticalFlyingBlockMovementStacks = 0;
      }
    });
  }

  @OnSend(priority = LAST)
  public void sentRespawn(User user, RespawnPacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    // Respawn creates fresh abilities, including when the packet keeps entity data or changes dimension.
    Modules.feedback().synchronize(user, event, () -> user.meta().abilities().setFlySpeed(0.05F));
  }

  @OnSend
  public void outgoingPositionUpdate(User user, PlayerTeleportPacket packet, Cancellable event) {
    if (event.isCancelled()) {
      return;
    }
    MovementMetadata movementData = user.meta().movement();
    movementData.criticalFlyingDisallowWasTeleported = movementData.criticalFlyingDisallowStacks == 1;
  }

  @OnSend
  public void outgoingGameModeUpdate(User user, GameStateChangePacket packet, PacketSendEvent event) {
    if (event.isCancelled() || packet.type() != CHANGE_GAME_MODE) {
      return;
    }
    GameMode gameMode = gameModeOf((int) (packet.value() + 0.5F));
    AbilityMetadata abilityData = user.meta().abilities();
    abilityData.setPendingGameMode(gameMode);
    Modules.feedback().synchronize(user, event, () -> abilityData.setGameMode(gameMode));
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
