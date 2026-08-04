package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.executor.FoliaSafeTeleport;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
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

import static de.jpx3.intave.check.movement.physics.MoveMetric.LONG_TELEPORT;
import static de.jpx3.intave.check.movement.physics.MoveMetric.TELEPORT;

public final class DesyncWatchdog extends Module {
  private final UserLocal<HistoryWindow<PositionBundle>> userLocalDesyncHistory =
    UserLocal.withInitial(() -> new HistoryWindow<>(10));
  private final UserLocal<AtomicInteger> violationCounter =
    UserLocal.withInitial(() -> new AtomicInteger());

  private static long lastActionIssued = System.currentTimeMillis();

	private Task watchdogTask;

  @Override
  public void enable() {
		if (watchdogTask != null) {
			watchdogTask.cancel();
		}
		watchdogTask = Tasks.periodicNamed("DesyncWatchdog.performDesyncCheck", () ->
      UserRepository.applyOnAll(user ->
        // getLocation() (the server position) is only valid on the player's own
        // region thread on Folia; run the per-user check there so the comparison
        // uses the authoritative position instead of a torn/stale off-region read.
        Synchronizer.synchronize(user, () -> performDesyncCheck(user))), 20, 20
		).startAsync();
  }

	@Override
	public void disable() {
		if (watchdogTask != null) {
			watchdogTask.cancel();
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

    PositionBundle positionBundle = positionBundleOf(user);
    AtomicInteger violationCounter = this.violationCounter.get(user);
    if (positionBundle.anyDesynced()) {
      int currentVL = violationCounter.incrementAndGet();
      if (currentVL > 1) {
        // Diagnostic: include the three positions so we can tell a false desync
        // (server vs intave read artifact) from a real tracking gap on Folia.
        IntaveLogger.logger().warn("Server and Intave don't seem to agree on position for " + user.player().getName() + " (" + (currentVL-1) + "/3)"
          + " server=" + positionBundle.serverPosition()
          + " intaveVerified=" + positionBundle.intaveAcceptedPosition()
          + " intavePending=" + positionBundle.prefilteredPendingPosition());
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
              user.sendMessage(IntavePlugin.prefix() + "You were instructed to teleport to " + MathHelper.formatPosition(location) + " due to desync.");
            }
            FoliaSafeTeleport.teleport(player, location);
          });
        }
      }
    } else {
      violationCounter.set(0);
    }
    userLocalDesyncHistory.get(user).add(positionBundle);
  }

  public static class PositionBundle {
    private static final double MAX_DESYNC_DISTANCE = 4;

    private final Position serverPosition;
    private final Position intaveAcceptedPosition;
    private final Position prefilteredPendingPosition;
    private boolean inVehicle;
    private final boolean pendingPositionMeaningful;

    public PositionBundle(
      Position serverPosition,
      Position intaveAcceptedPosition,
      Position prefilteredPendingPosition,
      boolean inVehicle,
      boolean pendingPositionMeaningful
    ) {
      this.serverPosition = serverPosition;
      this.intaveAcceptedPosition = intaveAcceptedPosition;
      this.prefilteredPendingPosition = prefilteredPendingPosition;
      this.inVehicle = inVehicle;
      this.pendingPositionMeaningful = pendingPositionMeaningful;
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
      // This is the signal the watchdog exists for: Intave's own idea of where the
      // player is has drifted away from the server's. It is always meaningful.
      if (serverAndIntaveAcceptedPositionDesynced()) {
        return true;
      }
      // The other two compare against the last position the CLIENT sent. While a
      // teleport is in flight that is legitimately stale -- the server has moved the
      // player, Intave has accepted the new position, and the client simply has not
      // reported from there yet. Treating that as a desync produced a warning every
      // second (and eventually a forced reset teleport) for a player nothing was wrong
      // with; the giveaway in those logs is server and intaveVerified matching to the
      // centimetre while only the pending position differs.
      if (!pendingPositionMeaningful) {
        return false;
      }
      return serverAndPrefilteredPendingPositionDesynced() ||
        intaveAcceptedAndPrefilteredPendingPositionDesynced();
    }
  }

  private PositionBundle positionBundleOf(User user) {
    return new PositionBundle(
      serverPositionOf(user),
      intaveAcceptedPositionOf(user),
      prefilteredPendingPositionOf(user),
      user.meta().movement().isInVehicle(),
      pendingPositionMeaningful(user)
    );
  }

  /**
   * Whether the last position the client sent can be compared against the server's at
   * all. It cannot while a teleport is in flight or has just landed: the server has
   * moved the player, but the client keeps reporting from where it was until it
   * processes the teleport and acknowledges it. The same holds right after joining,
   * before the first position exchange has happened.
   */
  private boolean pendingPositionMeaningful(User user) {
    MovementMetadata movement = user.meta().movement();
    if (movement.awaitTeleport || movement.expectTeleport || movement.awaitOutgoingTeleport) {
      return false;
    }
    if (user.justJoined()) {
      return false;
    }
    // A confirmed teleport still needs the client's next position packet to arrive
    // before the two can agree again; at 20 checks/minute that window is easily
    // several checks wide on a laggy connection.
    return movement.ticksPast(TELEPORT) > 40 && movement.ticksPast(LONG_TELEPORT) > 40;
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
