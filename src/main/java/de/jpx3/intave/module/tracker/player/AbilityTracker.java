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
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.AbilityInReader;
import de.jpx3.intave.packet.reader.AbilityOutReader;
import de.jpx3.intave.packet.reader.EntityReader;
import de.jpx3.intave.packet.reader.GameStateChangeReader;
import de.jpx3.intave.packet.view.AbilityView;
import de.jpx3.intave.packet.view.FeedbackHandle;
import de.jpx3.intave.packet.view.PacketEventsAbilityView;
import de.jpx3.intave.packet.view.PacketEventsFeedbackHandle;
import de.jpx3.intave.packet.view.ProtocolLibAbilityView;
import de.jpx3.intave.packet.view.ProtocolLibFeedbackHandle;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;
import static de.jpx3.intave.packet.reader.GameStateChangeReader.GameState.CHANGE_GAME_MODE;

public final class AbilityTracker extends Module {
  @PacketSubscription(packetsOut = CAMERA)
  public void receiveCamera(User user, EntityReader reader) {
    handleCamera(user, reader.entityId());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = CAMERA
  )
  public void receiveCamera(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    handleCamera(UserRepository.userOf(player), new WrapperPlayServerCamera(event).getCameraId());
  }

  /** Engine independent camera handling; the packet only carries the viewed entity's id. */
  private void handleCamera(User user, int entityId) {
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
    handleAbilities(user, reader.requestedFlying());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {ABILITIES_IN}
  )
  public void receiveAbilities(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    handleAbilities(
      UserRepository.userOf(player),
      new WrapperPlayClientPlayerAbilities(event).isFlying()
    );
  }

  /** Engine independent inbound abilities handling; only the requested flying flag is read. */
  private void handleAbilities(User user, boolean flying) {
    AbilityMetadata abilityData = user.meta().abilities();
    MovementMetadata movementData = user.meta().movement();
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
    // The view wraps the reader the linker already injected, so the pooling and release semantics
    // of this path are untouched: nothing here acquires or releases a reader.
    handleSentAbilities(
      user,
      new ProtocolLibAbilityView(event, reader),
      ProtocolLibFeedbackHandle.of(event)
    );
  }

  /**
   * PacketEvents entry point for the same packet.
   * <p>
   * The three fields this handler reads - fly speed, walk speed and the flight allowed flag - are
   * exactly the outbound half of {@link AbilityView}, so
   * {@link PacketEventsAbilityView} serves them without any per-version branching. The tick
   * feedback is requested through the engine neutral {@link FeedbackHandle}; the PacketEvents
   * handle reports no bundling target, so the transaction is sent unbundled, exactly as the
   * ProtocolLib path does on every server below 1.19.4.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ABILITIES_OUT
    }
  )
  public void sentAbilities(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    PacketEventsAbilityView view = PacketEventsAbilityView.of(event);
    if (view == null) {
      return;
    }
    handleSentAbilities(
      UserRepository.userOf(player),
      view,
      PacketEventsFeedbackHandle.of(event)
    );
  }

  /** Engine independent outbound ability handling; every packet read goes through the view. */
  private void handleSentAbilities(User user, AbilityView view, FeedbackHandle handle) {
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    float flyingSpeed = view.flyingSpeed();
    float walkingSpeed = view.walkingSpeed();
    boolean allowedFlight = view.flyingAllowed();
    boolean critical = abilityData.allowFlying() && !allowedFlight && movement.criticalTeleportRateLimiter.tryAcquire();
    if (critical /*&& movement.lastTeleport < 20*/) {
      // Teleport again to force transaction synchronization
      Synchronizer.synchronizeDelayed(user, () -> {
        MovementMetadata moovement = user.meta().movement();
        if (moovement.criticalFlyingDisallowStacks > 0) {
          Location position = moovement.verifiedLocation().clone();
          Player player = user.player();
          position.setWorld(player.getWorld());
          Modules.tracker().packetLogging().logSystemMessage(user, () ->
            "TELEPORT ACTION source=FLIGHT_DISALLOW_TIMEOUT target=" + position
          );
          boolean teleported = player.teleport(position);
          Modules.tracker().packetLogging().logSystemMessage(user, () ->
            "TELEPORT ACTION RESULT source=FLIGHT_DISALLOW_TIMEOUT accepted=" + teleported
          );
          moovement.criticalFlyingBlockMovementStacks++;
          if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
            player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " as " + ChatColor.RED + " not responding to critical flight disallow");
          }
        }
      }, 20);
    }
    if (critical) {
      if (movement.criticalFlyingDisallowStacks++ == 0) {
        movement.criticalEnterPosX = movement.verifiedLastPositionX;
        movement.criticalEnterPosY = movement.verifiedLastPositionY;
        movement.criticalEnterPosZ = movement.verifiedLastPositionZ;
      }
    }
    user.packetTickFeedback(handle, () -> {
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
    handleIncomingFlyingUpdate(user, player);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {FLYING, PacketId.Client.POSITION, LOOK, POSITION_LOOK}
  )
  public void incomingFlyingUpdate(Player player) {
    if (player == null) {
      return;
    }
    handleIncomingFlyingUpdate(UserRepository.userOf(player), player);
  }

  /** Engine independent flying update handling; nothing of the packet body is read. */
  private void handleIncomingFlyingUpdate(User user, Player player) {
    MovementMetadata movementData = user.meta().movement();
    if (movementData.criticalFlyingDisallowStacks > 0 &&
      !movementData.criticalFlyingDisallowWasTeleported
    ) {
      double deltaX = movementData.verifiedLastPositionX - movementData.criticalEnterPosX;
      double deltaY = movementData.verifiedLastPositionY - movementData.criticalEnterPosY;
      double deltaZ = movementData.verifiedLastPositionZ - movementData.criticalEnterPosZ;
      double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
      if (distance > 3 && movementData.criticalTeleportRateLimiter.tryAcquire()) {
        Modules.mitigate().movement().emulationSetBack(player, Motion.newEmpty(), 3, 2, false);
        if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
          player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " for " + ChatColor.RED + " critical flying disallow protection");
        }
        movementData.criticalFlyingDisallowStacks = 0;
      }
    } else if (movementData.criticalFlyingBlockMovementStacks > 0 && movementData.criticalTeleportRateLimiter.tryAcquire()) {
      Synchronizer.synchronize(user, () -> {
        Location target = player.getLocation();
        Modules.tracker().packetLogging().logSystemMessage(user, () ->
          "TELEPORT ACTION source=FLIGHT_DISALLOW_MOVEMENT_BLOCK target=" + target
        );
        boolean teleported = player.teleport(target);
        Modules.tracker().packetLogging().logSystemMessage(user, () ->
          "TELEPORT ACTION RESULT source=FLIGHT_DISALLOW_MOVEMENT_BLOCK accepted=" + teleported
        );
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
    handleOutgoingPositionUpdate(user);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = PacketId.Server.POSITION
  )
  public void outgoingPositionUpdate(Player player) {
    if (player == null) {
      return;
    }
    handleOutgoingPositionUpdate(UserRepository.userOf(player));
  }

  /** Engine independent outbound teleport handling; nothing of the packet body is read. */
  private void handleOutgoingPositionUpdate(User user) {
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
    handleGameModeUpdate(user, reader.valueAsInt());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.NORMAL,
    packetsOut = {
      GAME_STATE_CHANGE
    }
  )
  public void outgoingGameModeUpdate(PacketSendEvent event, Player player) {
    if (player == null) {
      return;
    }
    WrapperPlayServerChangeGameState wrapper = new WrapperPlayServerChangeGameState(event);
    if (wrapper.getReason() != WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE) {
      return;
    }
    // The reader rounds the float value the same way before resolving the game mode id.
    handleGameModeUpdate(UserRepository.userOf(player), (int) (wrapper.getValue() + 0.5F));
  }

  /** Engine independent game mode handling; the packet only carries the new mode's id. */
  private void handleGameModeUpdate(User user, int gameModeId) {
    GameMode gameMode = gameModeOf(gameModeId);
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
