package de.jpx3.intave.event.bukkit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.lib.asm.Type;
import de.jpx3.intave.reflect.irx.IRXFactory;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.AuthorNagException;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Relocate
public final class BukkitEventLinker {
  private final IntavePlugin plugin;
  private int totalLoaded = 0;
  private long totalLoad = 0;

  public BukkitEventLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public int totalLoaded() {
    return totalLoaded;
  }

  public long totalLoad() {
    return totalLoad;
  }

  public void registerEventsIn(BukkitEventSubscriber listener) {
    long start = System.nanoTime();
    processLinking(listener).forEach((key, value) -> eventListenersOf(key).registerAll(value));
    totalLoad += System.nanoTime() - start;
  }

  public void unregisterEventsIn(BukkitEventSubscriber listener) {
    HandlerList.unregisterAll(listener);
  }

  public void fireEvent(Event event) {
    for (RegisteredListener registration : event.getHandlers().getRegisteredListeners()) {
      if (registration.getPlugin().isEnabled()) {
        try {
          registration.callEvent(event);
        } catch (AuthorNagException | EventException exception) {
          exception.printStackTrace();
        }
      }
    }
  }

  private Map<Class<? extends Event>, Set<RegisteredListener>> processLinking(BukkitEventSubscriber listener) {
    Class<? extends BukkitEventSubscriber> listenerClass = listener.getClass();
    List<Method> methods = ImmutableList.copyOf(listenerClass.getDeclaredMethods());
    Map<Class<? extends Event>, Set<RegisteredListener>> ret = Maps.newConcurrentMap();

    int found = 0;
    for (Method method : methods) {
      BukkitEventSubscription eventHandler = method.getAnnotation(BukkitEventSubscription.class);
      if (eventHandler == null) {
        continue;
      }
      Class<?> checkClass;
      if (
        method.getParameterTypes().length == 1 &&
        Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])
      ) {
        if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
          throw new IntaveInternalException("Invalid linking for method " + method);
        }
        Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
        Set<RegisteredListener> registeredListeners = ret.computeIfAbsent(eventClass, k -> new HashSet<>());
        String listenerClassPath = listenerClass.getCanonicalName().replaceAll("\\.", "/");
        String eventClassPath = eventClass.getCanonicalName().replaceAll("\\.", "/");
        Class<EventExecutor> executorClass = IRXFactory.assembleCallerClass(
          BukkitEventLinker.class.getClassLoader(),
          EventExecutor.class,
          "<generated>",
          "execute",
          "(Lorg/bukkit/event/Listener;Lorg/bukkit/event/Event;)V",
          "(L"+listenerClassPath+";L"+ eventClassPath +";)V",
          listenerClassPath,
          method.getName(),
          Type.getMethodDescriptor(method),
          false,
          false
        );
        EventExecutor executor;
        try {
          executor = executorClass.newInstance();
        } catch (InstantiationException | IllegalAccessException exception) {
          throw new IntaveInternalException(exception);
        }
        IntaveRegisteredListener registeredListener = new IntaveRegisteredListener(
          plugin, listener, executor,
          eventClass, eventHandler
        );
        registeredListener.initialize();
        registeredListeners.add(registeredListener);
        found++;
      }
    }

    totalLoaded += found;
    return ret;
  }

  public void performShutdown() {
    HandlerList.unregisterAll(plugin);
  }

  private HandlerList eventListenersOf(Class<? extends Event> eventClass) {
    try {
      String handlerList = "getHandlerList";
      Method method = registrationClassOf(eventClass).getDeclaredMethod(handlerList);
      method.setAccessible(true);
      return (HandlerList) method.invoke(null);
    } catch (Exception exception) {
      throw new IllegalPluginAccessException(exception.toString());
    }
  }

  private Class<? extends Event> registrationClassOf(Class<? extends Event> clazz) {
    try {
      String handlerList = "getHandlerList";
      clazz.getDeclaredMethod(handlerList);
      return clazz;
    } catch (NoSuchMethodException var2) {
      if (clazz.getSuperclass() != null && !clazz.getSuperclass().equals(Event.class) && Event.class.isAssignableFrom(clazz.getSuperclass())) {
        return this.registrationClassOf(clazz.getSuperclass().asSubclass(Event.class));
      } else {
        throw new IllegalPluginAccessException("Unable to find handler list for event " + clazz.getName() + ". Static getHandlerList method required!");
      }
    }
  }
}
