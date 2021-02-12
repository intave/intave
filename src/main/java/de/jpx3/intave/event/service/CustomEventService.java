package de.jpx3.intave.event.service;

import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.*;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CustomEventService {
  private final IntavePlugin plugin;
  private final Map<Class<? extends AbstractIntaveExternalEvent>, ThreadLocal<AbstractIntaveExternalEvent>> eventAccess = Maps.newHashMap();

  public CustomEventService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    setupClass(IntaveViolationEvent.class, () -> IntaveViolationEvent.empty());
    setupClass(IntaveCommandExecutionEvent.class, () -> IntaveCommandExecutionEvent.empty());
    setupClass(IntaveCreateEmulatedEntityEvent.class, () -> IntaveCreateEmulatedEntityEvent.empty());
    setupClass(IntaveCreateEmulatedPlayerEvent.class, () -> IntaveCreateEmulatedPlayerEvent.empty());
  }

  private <T extends AbstractIntaveExternalEvent> void setupClass(Class<T> eventClass, Supplier<T> initializer) {
    eventAccess.put(eventClass, ThreadLocal.withInitial(initializer));
  }

  public <T extends AbstractIntaveExternalEvent> T invokeEvent(Class<T> eventClass, Consumer<T> applier) {
    T eventInstance = activeInstanceOf(eventClass);
    applier.accept(eventInstance);
    callEvent(eventInstance);
    eventInstance.refClear();
    return eventInstance;
  }

  private void callEvent(AbstractIntaveExternalEvent event) {
    plugin.eventLinker().fireEvent(event);
  }

  private <T extends AbstractIntaveExternalEvent> T activeInstanceOf(Class<T> eventClass) {
    ThreadLocal<AbstractIntaveExternalEvent> eventThreadLocal = eventAccess.get(eventClass);
    if(eventThreadLocal == null) {
      throw new IllegalStateException("Unable to locate thread local handle for event class " + eventClass.getName());
    }
    //noinspection unchecked
    return (T) eventThreadLocal.get();
  }
}
