package de.jpx3.intave.module.nayoro.event;

import java.util.HashMap;
import java.util.Map;

public final class EventRegistry {
  private static final Map<Class<? extends Event>, Integer> eventsToIds = new HashMap<>();
  private static final Map<Integer, Class<? extends Event>> idsToEvents = new HashMap<>();

  static {
    register(AttackEvent.class, 0);
    register(ClickEvent.class, 1);
    register(PlayerMoveEvent.class, 2);
    register(EntityMoveEvent.class, 3);
    register(SlotSwitchEvent.class, 4);
    register(PropertiesEvent.class, 5);
    register(PlayerInitEvent.class, 6);
    register(EntitySpawnEvent.class, 7);
    register(EntityRemoveEvent.class, 8);
  }

  private static void register(Class<? extends Event> eventClass, int id) {
    eventsToIds.put(eventClass, id);
    idsToEvents.put(id, eventClass);
  }

  public static int idOf(Event event) {
    return idOf(event.getClass());
  }

  public static int idOf(Class<? extends Event> eventClass) {
    return eventsToIds.get(eventClass);
  }

  public static <T extends Event> T eventOf(int id) {
    try {
      Class<? extends Event> eventClass = idsToEvents.get(id);
      if (eventClass == null) {
        throw new IllegalArgumentException("Unknown event id: " + id);
      }
      //noinspection unchecked
      return (T) eventClass.newInstance();
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }
}
