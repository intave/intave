package de.jpx3.intave.library.pledge;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.cleanup.ShutdownTasks;

import java.util.ArrayList;
import java.util.List;

public class TickEnd {
  private static final List<Runnable> tickEndSubscribers = new ArrayList<>();

  public static void start() {
    if (isFoliaServer()) {
      // Folia / regionized servers (e.g. CanvasMC) have no single global
      // tick loop, so the tickables-list reflection used by TickEndTask is
      // invalid and would attach to an arbitrary server-internal list. There
      // are currently no tick-end subscribers, so skipping is a safe no-op.
      return;
    }
    try {
      TickEndTask task = TickEndTask.create(() -> tickEndSubscribers.forEach(Runnable::run));
      ShutdownTasks.add(task::cancel);
    } catch (Throwable throwable) {
      // The reflective tick-end hook is best-effort; a mapping/field mismatch
      // on newer server versions must never abort plugin boot.
      IntaveLogger.logger().warn("Could not install tick-end hook: " + throwable);
    }
  }

  private static boolean isFoliaServer() {
    // https://docs.papermc.io/paper/dev/folia-support/
    try {
      Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public static void subscribe(Runnable runnable) {
    tickEndSubscribers.add(runnable);
  }

  public static void unsubscribe(Runnable runnable) {
    tickEndSubscribers.remove(runnable);
  }
}
