package de.jpx3.intave.module.nayoro;

import java.util.HashMap;
import java.util.Map;

public final class EventRegistry {
  private final static Map<Class<? extends Event>, Integer> eventsToIds = new HashMap<>();
  private final static Map<Integer, Class<? extends Event>> idsToEvents = new HashMap<>();

  static {
    register(AttackEvent.class, 0);
    register(ClickEvent.class, 1);
    register(MoveEvent.class, 2);
    register(EntityMoveEvent.class, 3);
    register(SlotSwitchEvent.class, 4);
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
      //noinspection unchecked
      return (T) idsToEvents.get(id).newInstance();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }
}
