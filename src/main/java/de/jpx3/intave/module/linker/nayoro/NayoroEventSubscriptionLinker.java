package de.jpx3.intave.module.linker.nayoro;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.nayoro.PlayerContainer;
import de.jpx3.intave.module.nayoro.event.Event;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NayoroEventSubscriptionLinker extends Module {
  private final IntavePlugin plugin;
  private int totalLoaded = 0;
  private long totalLoad = 0;
  private final Map<Class<? extends Event>, List<NayoroRegisteredListener>> eventListeners = Maps.newHashMap();

  public NayoroEventSubscriptionLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public int totalLoaded() {
    return totalLoaded;
  }

  public long totalLoad() {
    return totalLoad;
  }

  public void registerEventsIn(NayoroEventSubscriber listener) {
    long start = System.nanoTime();
    Map<Class<? extends Event>, List<NayoroRegisteredListener>> classListMap = processLinking(listener);
    for (Map.Entry<Class<? extends Event>, List<NayoroRegisteredListener>> classListEntry : classListMap.entrySet()) {
      eventListeners.computeIfAbsent(classListEntry.getKey(), k -> new ArrayList<>()).addAll(classListEntry.getValue());
    }
    totalLoad += System.nanoTime() - start;
  }

  public void unregisterEventsIn(NayoroEventSubscriber eventProcessor) {
    for (Map.Entry<Class<? extends Event>, List<NayoroRegisteredListener>> classListEntry : eventListeners.entrySet()) {
      List<NayoroRegisteredListener> value = classListEntry.getValue();
      value.removeIf(listener -> listener.subscriber() == eventProcessor);
    }
  }

  public void fireEvent(PlayerContainer player, Event event) {
    List<NayoroRegisteredListener> listeners = eventListeners.get(event.getClass());
    if (listeners == null || listeners.isEmpty()) {
      return;
    }
    for (NayoroRegisteredListener executor : listeners) {
      executor.execute(player, event);
    }
  }

  private Map<Class<? extends Event>, List<NayoroRegisteredListener>> processLinking(NayoroEventSubscriber listener) {
    Class<? extends NayoroEventSubscriber> listenerClass = listener.getClass();
    List<Method> methods = ImmutableList.copyOf(listenerClass.getDeclaredMethods());
    Map<Class<? extends Event>, List<NayoroRegisteredListener>> ret = Maps.newConcurrentMap();

    int found = 0;
    for (Method method : methods) {
      NayoroRelay relayAnnotation = method.getAnnotation(NayoroRelay.class);
      if (relayAnnotation == null) {
        continue;
      }
      Class<?> checkClass;
      if (method.getParameterTypes().length == 2 &&
        method.getParameterTypes()[0].equals(PlayerContainer.class) &&
        Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[1])
      ) {
//        System.out.println("Found method " + method);
        if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
          throw new IntaveInternalException("Invalid linking for method " + method);
        }
        Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
        List<NayoroRegisteredListener> registeredListeners = ret.computeIfAbsent(eventClass, k -> new ArrayList<>());
        method.setAccessible(true);
        NayoroEventExecutor executor = (subscriber, player, event) -> {
          try {
            method.invoke(subscriber, player, event);
          } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
          }
        };
        NayoroRegisteredListener registeredListener = new NayoroRegisteredListener(
          listener, executor
        );
        registeredListener.initialize();
        registeredListeners.add(registeredListener);
        found++;
      }
    }

    totalLoaded += found;
    return ret;
  }

  public void disable() {
    eventListeners.clear();
  }
}
