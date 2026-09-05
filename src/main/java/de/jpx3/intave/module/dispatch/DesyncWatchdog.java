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

package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.share.HistoryWindow;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicInteger;

public final class DesyncWatchdog extends Module {
  private final UserLocal<HistoryWindow<PositionBundle>> userLocalDesyncHistory =
    UserLocal.withInitial(() -> new HistoryWindow<>(10));
  private final UserLocal<AtomicInteger> violationCounter =
    UserLocal.withInitial(() -> new AtomicInteger());

  private static long lastActionIssued = System.currentTimeMillis();
  private Task watchdogTask;

  @Override
  public void enable() {
    watchdogTask = Tasks.periodicNamed(
      "DesyncWatchdog.check",
      () -> UserRepository.applyOnOnlineUsers(user ->
        Synchronizer.synchronize(user, () -> performDesyncCheck(user))
      ),
      20, 20
    ).startAsync();
  }

  @Override
  public void disable() {
    if (watchdogTask != null) {
      watchdogTask.cancel();
      watchdogTask = null;
    }
  }

  private void performDesyncCheck(User user) {
    // Spectators don't send any position packets when observing an entity
    if (user.player().getGameMode() == GameMode.SPECTATOR) {
      return;
    }

    if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      return;
    }

    AtomicInteger violationCounter = this.violationCounter.get(user);
    if (teleportPending(user)) {
      violationCounter.set(0);
      return;
    }

    PositionBundle positionBundle = positionBundleOf(user);
    if (positionBundle.anyDesynced()) {
      int currentVL = violationCounter.incrementAndGet();
      if (currentVL > 1) {
        IntaveLogger.logger().warn("Server and Intave don't seem to agree on position for " + user.player().getName() + " (" + (currentVL-1) + "/3)");
      }
      if (currentVL > 3) {
        Violation violation = Violation.builderFor(Physics.class)
          .forPlayer(user.player())
          .withMessage("apparently desynced, resetting")
          .withDetails(
            "intave/verified: " + positionBundle.intaveAcceptedPosition() +
            ", intave/nocheck: " + positionBundle.prefilteredPendingPosition() +
            ", server: " + positionBundle.serverPosition()
          )
          .withVL(0.5)
          .build();
        violationCounter.set(currentVL - 3);
        Modules.violationProcessor().processViolation(violation);

        if (System.currentTimeMillis() - lastActionIssued > 10_000) {
          lastActionIssued = System.currentTimeMillis();
          Synchronizer.synchronize(user, () -> {
            Player player = user.player();
            Location location = player.getLocation().clone();
            while (BlockTypeAccess.typeAccess(location.getBlock(), player) != Material.AIR) {
              location.add(0, 1, 0);
            }
            if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
              player.sendMessage(IntavePlugin.prefix() + "You were instructed to teleport to " + MathHelper.formatPosition(location) + " due to desync.");
            }
            PacketLogging logging = Modules.tracker().packetLogging();
            logging.logSystemMessage(user, () ->
              "TELEPORT ACTION source=DESYNC_WATCHDOG target=" + MathHelper.formatPosition(location) +
                " verified=" + positionBundle.intaveAcceptedPosition() +
                " nocheck=" + positionBundle.prefilteredPendingPosition() +
                " server=" + positionBundle.serverPosition()
            );
            boolean teleported = player.teleport(location);
            logging.logSystemMessage(user, () ->
              "TELEPORT ACTION RESULT source=DESYNC_WATCHDOG accepted=" + teleported
            );
          });
        }
      }
    } else {
      violationCounter.set(0);
    }
    userLocalDesyncHistory.get(user).add(positionBundle);
  }

  private boolean teleportPending(User user) {
    return teleportPending(user.meta().movement());
  }

  static boolean teleportPending(MovementMetadata movement) {
    return movement.awaitTeleport || movement.awaitOutgoingTeleport;
  }

  public static class PositionBundle {
    private static final double MAX_DESYNC_DISTANCE = 4;

    private final Position serverPosition;
    private final Position intaveAcceptedPosition;
    private final Position prefilteredPendingPosition;
    private boolean inVehicle;

    public PositionBundle(
      Position serverPosition,
      Position intaveAcceptedPosition,
      Position prefilteredPendingPosition,
      boolean inVehicle
    ) {
      this.serverPosition = serverPosition;
      this.intaveAcceptedPosition = intaveAcceptedPosition;
      this.prefilteredPendingPosition = prefilteredPendingPosition;
      this.inVehicle = inVehicle;
    }

    public Position serverPosition() {
      return serverPosition;
    }

    public Position intaveAcceptedPosition() {
      return intaveAcceptedPosition;
    }

    public Position prefilteredPendingPosition() {
      return prefilteredPendingPosition;
    }

    public boolean inVehicle() {
      return inVehicle;
    }

    public boolean serverAndIntaveAcceptedPositionDesynced() {
      double distance = serverPosition.distance(intaveAcceptedPosition);
      return distance > MAX_DESYNC_DISTANCE;
    }

    public boolean serverAndPrefilteredPendingPositionDesynced() {
      double distance = serverPosition.distance(prefilteredPendingPosition);
      return distance > MAX_DESYNC_DISTANCE;
    }

    public boolean intaveAcceptedAndPrefilteredPendingPositionDesynced() {
      double distance = intaveAcceptedPosition.distance(prefilteredPendingPosition);
      return distance > MAX_DESYNC_DISTANCE;
    }

    public boolean anyDesynced() {
      if (inVehicle) {
        return false;
      }
      return serverAndIntaveAcceptedPositionDesynced() ||
        serverAndPrefilteredPendingPositionDesynced() ||
        intaveAcceptedAndPrefilteredPendingPositionDesynced();
    }
  }

  private PositionBundle positionBundleOf(User user) {
    return new PositionBundle(
      serverPositionOf(user),
      intaveAcceptedPositionOf(user),
      prefilteredPendingPositionOf(user),
      user.meta().movement().isInVehicle()
    );
  }

  private Position prefilteredPendingPositionOf(User user) {
    return user.meta().movement().position();
  }

  private Position intaveAcceptedPositionOf(User user) {
    return user.meta().movement().verifiedLastPosition();
  }

  private Position serverPositionOf(User user) {
    return Position.of(user.player().getLocation());
  }
}
