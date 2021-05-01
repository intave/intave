package de.jpx3.intave.event.bukkit;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostics.timings.Timing;
import de.jpx3.intave.diagnostics.timings.Timings;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;

public final class IntaveRegisteredListener extends RegisteredListener {
  private final IntavePlugin plugin;
  private final EventExecutor eventExecutor;
  private final BukkitEventSubscriber listener;
  private final Class<? extends Event> eventClass;
  private Timing timing;
  private boolean checkIfCancelled;

  public IntaveRegisteredListener(
    IntavePlugin plugin,
    BukkitEventSubscriber listener,
    EventExecutor eventExecutor,
    Class<? extends Event> eventClass,
    BukkitEventSubscription eventHandler
  ) {
    super(listener, null, eventHandler.priority(), plugin, true/*eventHandler.ignoreCancelled()*/);
    this.plugin = plugin;
    this.eventExecutor = eventExecutor;
    this.eventClass = eventClass;
    this.listener = listener;
  }

  public void initialize() {
    checkIfCancelled = !isIgnoringCancelled() && Cancellable.class.isAssignableFrom(eventClass);
  }

  @Override
  public void callEvent(Event event) throws EventException {
    if (!eventClass.isAssignableFrom(event.getClass()) ||
        checkIfCancelled && ((Cancellable) event).isCancelled()
    ) {
      return;
    }

    boolean asynchronous = !Bukkit.isPrimaryThread();
    if (!asynchronous) {
      Timings.EXE_SERVER.start();
      if(timing == null) {
        timing = Timings.eventTimingOf(event);
      }
      timing.start();
    }
    try {
      eventExecutor.execute(listener, event);
    } catch (RuntimeException ex) {
      ex.printStackTrace();
    }
    if (!asynchronous) {
      Timings.EXE_SERVER.stop();
      timing.stop();
    }
  }
}
