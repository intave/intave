package de.jpx3.intave.module.event;

import com.google.common.collect.Maps;
import de.jpx3.intave.access.IntaveEvent;
import de.jpx3.intave.access.check.event.IntaveCommandExecutionEvent;
import de.jpx3.intave.access.check.event.IntaveViolationEvent;
import de.jpx3.intave.access.player.event.*;
import de.jpx3.intave.module.Module;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CustomEvents extends Module {
  private final Map<Class<? extends IntaveEvent>, ThreadLocal<IntaveEvent>> eventAccess = Maps.newHashMap();

  public void enable() {
    setupClass(IntaveViolationEvent.class, IntaveViolationEvent::empty);
    setupClass(IntaveCommandExecutionEvent.class, IntaveCommandExecutionEvent::empty);

    setupClass(IntaveCreateEmulatedEntityEvent.class, IntaveCreateEmulatedEntityEvent::empty);
    setupClass(IntaveCreateEmulatedPlayerEvent.class, IntaveCreateEmulatedPlayerEvent::empty);

    setupClass(AsyncIntaveBlockBreakPermissionEvent.class, AsyncIntaveBlockBreakPermissionEvent::empty);
    setupClass(AsyncIntaveBlockPlacePermissionEvent.class, AsyncIntaveBlockPlacePermissionEvent::empty);
    setupClass(AsyncIntaveInteractionPermissionEvent.class, AsyncIntaveInteractionPermissionEvent::empty);
    setupClass(AsyncIntaveBukkitActionPermissionEvent.class, AsyncIntaveBukkitActionPermissionEvent::empty);
  }

  private <T extends IntaveEvent> void setupClass(Class<T> eventClass, Supplier<T> initializer) {
    eventAccess.put(eventClass, ThreadLocal.withInitial(initializer));
  }

  public <T extends IntaveEvent> T invokeEvent(Class<T> eventClass, Consumer<T> applier) {
    T eventInstance = activeInstanceOf(eventClass);
    applier.accept(eventInstance);
    callEvent(eventInstance);
    eventInstance.referenceInvalidate();
    return eventInstance;
  }

  private void callEvent(IntaveEvent event) {
    plugin.eventLinker().fireEvent(event);
  }

  private <T extends IntaveEvent> T activeInstanceOf(Class<T> eventClass) {
    ThreadLocal<IntaveEvent> eventThreadLocal = eventAccess.get(eventClass);
    if (eventThreadLocal == null) {
      throw new IllegalStateException("Unable to locate thread local handle for event class " + eventClass.getName());
    }
    //noinspection unchecked
    return (T) eventThreadLocal.get();
  }
}
