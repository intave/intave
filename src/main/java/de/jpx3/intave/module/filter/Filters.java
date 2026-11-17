/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.filter;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public final class Filters extends Module {
  private final List<Filter> filters = new ArrayList<>();

  public void enable() {
    setup(EquipmentFilter.class);
    setup(HealthFilter.class);
    setup(VanishFilter.class);
    setup(CommandFilter.class);
    setup(Log4JExploitFilter.class);
    setup(EntityIdFilter.class);
    linkEnabled();
  }

  private void setup(Class<? extends Filter> filterClass) {
    try {
      Constructor<? extends Filter> constructor = filterClass.getConstructor(IntavePlugin.class);
      filters.add(constructor.newInstance(plugin));
    } catch (Exception exception) {
      try {
        filters.add(filterClass.newInstance());
      } catch (Exception exception1) {
        throw new IntaveInternalException("Something went wrong setting up a filter", exception);
      }
    }
  }

  private void linkEnabled() {
    for (Filter filter : filters) {
      if (filter.enabled()) {
//        System.out.println("Linking filter " + filter.getClass().getSimpleName());
        Modules.linker().bukkitEvents().registerEventsIn(filter);
        Modules.linker().protocolLib().linkSubscriptionsIn(filter);
      }
    }
  }
}
