package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.executor.Synchronizer;
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
import org.bukkit.Bukkit;
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

  @Override
  public void enable() {
    Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
      UserRepository.applyOnAll(this::performDesyncCheck);
      testAction();
    }, 20, 20);
  }

  private void testAction() {
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      return;
    }
//    VoxelShape shape = VoxelShape.fromBox(0, 0, 0, 1, 1, 1);
//    VoxelShape topCutoff = VoxelShape.fromBox(0, 0.5, 0, 1, 1, 1);
//    BlockShape finalShape = shape.subtract(topCutoff);
//    BlockShape contextualized = finalShape.contextualized(5, 5, 5);
//    BoundingBox boundingBox = BoundingBox.fromBounds(0, 0, 0, 1, 2, 1).offset(5, 5.6, 5);
//    System.out.println(contextualized);
//    System.out.println(contextualized.boundingBoxes());
//    System.out.println(contextualized.allowedOffset(Direction.Axis.Y_AXIS, boundingBox, -0.5));
  }

  private void performDesyncCheck(User user) {
    PositionBundle positionBundle = positionBundleOf(user);
    AtomicInteger violationCounter = this.violationCounter.get(user);
    if (positionBundle.anyDesynced()) {
      int currentVL = violationCounter.incrementAndGet();
      if (currentVL > 2) {
        IntaveLogger.logger().error("Possible desync detected for " + user.player().getName() + " (" + currentVL + "/6)");
      }
      if (currentVL > 5) {
        Violation violation = Violation.builderFor(Physics.class)
          .forPlayer(user.player())
          .withMessage("seems to be desynced, trying to reset position [REPORT THIS TO US IMMEDIATELY]")
          .withDetails(
            "i/a: " + positionBundle.intaveAcceptedPosition() +
            ", s: " + positionBundle.serverPosition() +
            ", p: " + positionBundle.prefilteredPendingPosition())
          .withVL(0.5)
          .build();
        violationCounter.set(currentVL - 3);
        Modules.violationProcessor().processViolation(violation);

        if (System.currentTimeMillis() - lastActionIssued > 10_000) {
          lastActionIssued = System.currentTimeMillis();
          Synchronizer.synchronize(() -> {
            Player player = user.player();
            Location location = player.getLocation().clone();
            while (BlockTypeAccess.typeAccess(location.getBlock(), player) != Material.AIR) {
              location.add(0, 1, 0);
            }
            if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
              player.sendMessage(IntavePlugin.prefix() + "You were instructed to teleport to " + MathHelper.formatPosition(location) + " due to desync.");
            }
            player.teleport(location);
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
    return user.meta().movement().verifiedPosition();
  }

  private Position serverPositionOf(User user) {
    return Position.of(user.player().getLocation());
  }
}
