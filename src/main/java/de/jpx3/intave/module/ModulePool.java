package de.jpx3.intave.module;

import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class ModulePool {
  private final Map<Class<? extends Module>, Module> moduleClassMappings = new ConcurrentHashMap<>();

  public void loadModule(Module module) {
    moduleClassMappings.put(module.getClass(), module);
  }

  public Collection<Module> bootRequests(BootSegment bootSegment) {
    return new ArrayList<>(modulePick(module -> module.settings().readyForEnable(bootSegment)));
  }

  public void enableModule(Module module) {
    // first enable the module, then link subscriptions
    module.enable();
    if (module.settings().shouldLinkSubscriptions()) {
      BukkitEventSubscriptionLinker bukkitEventLinker = lookup(BukkitEventSubscriptionLinker.class);
      if (bukkitEventLinker != null) {
        bukkitEventLinker.registerEventsIn(module);
      }
      PacketSubscriptionLinker packetSubscriptionLinker = lookup(PacketSubscriptionLinker.class);
      if (packetSubscriptionLinker != null) {
        packetSubscriptionLinker.linkSubscriptionsIn(module);
      }
    }
  }

  public void disableAll() {
    forEach(this::disableModule);
  }

  public void disableModule(Module module) {
    module.disable();
  }

  public void unloadAll() {
    moduleClassMappings.clear();
  }

  public void unloadModule(Module module) {
    moduleClassMappings.remove(module.getClass());
  }

  public <T extends Module> T lookup(Class<T> moduleClass) {
    //noinspection unchecked
    return (T) moduleClassMappings.get(moduleClass);
  }

  private void forEach(Consumer<? super Module> moduleConsumer) {
    moduleClassMappings.values().forEach(moduleConsumer);
  }

  private Collection<Module> modulePick(
    Predicate<? super Module> predicate
  ) {
    return moduleClassMappings.values()
      .stream().filter(predicate)
      .collect(Collectors.toList());
  }
}
