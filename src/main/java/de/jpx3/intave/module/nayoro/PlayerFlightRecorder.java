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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.EventSink;
import ac.intave.samples.event.FlyStateUpdateEvent;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.PrioritySlot;
import de.jpx3.intave.packet.reader.AbilityInReader;
import de.jpx3.intave.packet.reader.AbilityOutReader;
import de.jpx3.intave.packet.reader.GameStateChangeReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.Objects;
import java.util.function.BiConsumer;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ABILITIES_IN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.ABILITIES_OUT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.GAME_STATE_CHANGE;
import static de.jpx3.intave.share.GameStateChangeType.CHANGE_GAME_MODE;

/** Keeps client observations independent of the server's flight permission and state. */
public final class PlayerFlightRecorder implements PacketEventSubscriber, BukkitEventSubscriber {
  private final UserLocal<State> states = UserLocal.withInitial(State::new);
  private final BiConsumer<? super User, ? super FlyStateUpdateEvent> emitter;

  PlayerFlightRecorder(BiConsumer<? super User, ? super FlyStateUpdateEvent> emitter) {
    this.emitter = emitter;
  }

  @PacketSubscription(priority = ListenerPriority.MONITOR, prioritySlot = PrioritySlot.EXTERNAL,
    packetsIn = ABILITIES_IN, ignoreCancelled = false)
  public void clientAbilities(User user, AbilityInReader reader) {
    State state = states.get(user);
    synchronized (state) {
      // A rejected request is still a client observation, not server confirmation.
      state.clientFlying = reader.requestedFlying();
      emitIfChanged(user, state);
    }
  }

  @PacketSubscription(priority = ListenerPriority.MONITOR, prioritySlot = PrioritySlot.EXTERNAL,
    packetsOut = ABILITIES_OUT)
  public void serverAbilities(User user, Cancellable packet, AbilityOutReader reader) {
    if (packet.isCancelled()) {
      return;
    }
    State state = states.get(user);
    synchronized (state) {
      state.allowFlight = reader.flyingAllowed();
      state.flySpeed = finiteSpeed(reader.flyingSpeed());
      state.serverFlying = reader.flying();
      emitIfChanged(user, state);
    }
  }

  @PacketSubscription(priority = ListenerPriority.MONITOR, prioritySlot = PrioritySlot.EXTERNAL,
    packetsOut = GAME_STATE_CHANGE)
  public void gameMode(User user, Cancellable packet, GameStateChangeReader reader) {
    if (packet.isCancelled() || reader.type() != CHANGE_GAME_MODE) {
      return;
    }
    GameMode gameMode = GameMode.getByValue(reader.valueAsInt());
    updateGameMode(user, gameMode);
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void flight(PlayerToggleFlightEvent event) {
    if (event.isCancelled()) {
      return;
    }
    User user = UserRepository.userOf(event.getPlayer());
    State state = states.get(user);
    synchronized (state) {
      // Accepted client toggles need not produce an outgoing abilities packet.
      state.serverFlying = event.isFlying();
      emitIfChanged(user, state);
    }
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void gameMode(PlayerGameModeChangeEvent event) {
    if (!event.isCancelled()) {
      updateGameMode(UserRepository.userOf(event.getPlayer()), event.getNewGameMode());
    }
  }

  private void updateGameMode(User user, GameMode gameMode) {
    State state = states.get(user);
    synchronized (state) {
      state.gameMode = gameMode == null ? null : gameMode.name();
      emitIfChanged(user, state);
    }
  }

  /** Called on the player's owning thread, while the sink is still private. */
  void start(User user, EventSink sink, Runnable attachSink) {
    State state = states.get(user);
    synchronized (state) {
      Player player = user.player();
      state.allowFlight = player.getAllowFlight();
      state.flySpeed = finiteSpeed(player.getFlySpeed() / 2.0F);
      GameMode gameMode = player.getGameMode();
      state.gameMode = gameMode == null ? null : gameMode.name();
      state.serverFlying = player.isFlying();
      FlyStateUpdateEvent snapshot = state.snapshot();
      snapshot.accept(sink);
      state.lastEmitted = snapshot;
      attachSink.run();
    }
  }

  private void emitIfChanged(User user, State state) {
    FlyStateUpdateEvent next = state.snapshot();
    FlyStateUpdateEvent previous = state.lastEmitted;
    if (previous != null
      && Objects.equals(previous.allowFlight(), next.allowFlight())
      && Objects.equals(previous.flySpeed(), next.flySpeed())
      && Objects.equals(previous.gameMode(), next.gameMode())
      && Objects.equals(previous.clientFlying(), next.clientFlying())
      && Objects.equals(previous.serverFlying(), next.serverFlying())) {
      return;
    }
    state.lastEmitted = next;
    emitter.accept(user, next);
  }

  private static Float finiteSpeed(float speed) {
    return Float.isFinite(speed) ? speed : null;
  }

  private static final class State {
    private Boolean allowFlight;
    private Float flySpeed;
    private String gameMode;
    private Boolean clientFlying;
    private Boolean serverFlying;
    private FlyStateUpdateEvent lastEmitted;

    FlyStateUpdateEvent snapshot() {
      return new FlyStateUpdateEvent(allowFlight, flySpeed, gameMode, clientFlying, serverFlying);
    }
  }
}
