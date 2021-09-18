package de.jpx3.intave.module.filter;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public final class Filters extends Module {
  private final List<Filter> filters = new ArrayList<>();

  public void setup() {
    setup(EquipmentFilter.class);
    setup(HealthFilter.class);
    setup(VanishFilter.class);
    linkEnabled();
  }

  private void setup(Class<? extends Filter> filterClass) {
    try {
      Constructor<? extends Filter> constructor = filterClass.getConstructor(IntavePlugin.class);
      filters.add(constructor.newInstance(plugin));
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new IntaveInternalException("Something went wrong setting up a filter", e);
    }
  }

  private void linkEnabled() {
    for (Filter filter : filters) {
      if (filter.enabled()) {
        Modules.linker().bukkitEvents().registerEventsIn(filter);
        Modules.linker().packetEvents().linkSubscriptionsIn(filter);
      }
    }
  }
}
