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

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.AbilityInReader;
import de.jpx3.intave.packet.reader.AbilityOutReader;
import de.jpx3.intave.packet.reader.EntityReader;
import de.jpx3.intave.packet.reader.GameStateChangeReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.event.Cancellable;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.share.GameStateChangeType.CHANGE_GAME_MODE;

public final class AbilityTracker extends Module {
  @PacketSubscription(priority = ListenerPriority.MONITOR, packetsIn = ABILITIES_IN)
  public void recordClientAbilities(User user, AbilityInReader reader, Cancellable cancellable) {
    if (cancellable.isCancelled()) {
      return;
    }
    boolean flying = reader.requestedFlying();
    user.meta().abilities().acknowledgeClientFlying(flying);
  }

  @PacketSubscription(packetsOut = CAMERA)
  public void receiveCamera(User user, EntityReader reader) {
    int entityId = reader.entityId();
    user.tickFeedback(() -> synchronizedCameraUpdate(user, entityId));
  }

  private void synchronizedCameraUpdate(User user, int entityId) {
    AbilityMetadata abilityData = user.meta().abilities();
    abilityData.hasViewEntity = entityId != user.player().getEntityId();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {ABILITIES_IN}
  )
  public void receiveAbilities(User user, AbilityInReader reader) {
    AbilityMetadata abilityData = user.meta().abilities();
	  boolean flying = reader.requestedFlying();
    if (abilityData.allowFlying()) {
      if (flying) {
        abilityData.setFlying(true);
      } else {
        abilityData.disabledFlying = true;
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ABILITIES_OUT
    }
  )
  public void sentAbilities(User user, AbilityOutReader reader, PacketEvent event) {
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    float flyingSpeed = reader.flyingSpeed();
    float walkingSpeed = reader.walkingSpeed();
    boolean flying = reader.flying();
    boolean allowedFlight = reader.flyingAllowed();
    boolean critical = abilityData.allowFlying() && !allowedFlight && movement.criticalTeleportRateLimiter.tryAcquire();
    if (critical) {
      if (movement.criticalFlyingDisallowStacks++ == 0) {
        movement.criticalEnterPosX = movement.verifiedLastPositionX;
        movement.criticalEnterPosY = movement.verifiedLastPositionY;
        movement.criticalEnterPosZ = movement.verifiedLastPositionZ;
      }
    }
    user.packetTickFeedback(event, () -> {
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
    User user, GameStateChangeReader reader
  ) {
    if (reader.type() != CHANGE_GAME_MODE) {
      return;
    }
    GameMode gameMode = gameModeOf(reader.valueAsInt());
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
