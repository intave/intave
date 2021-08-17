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

public final class ModulePool {
  private final Map<Class<? extends Module>, Module> moduleClassMappings = new ConcurrentHashMap<>();

  public void loadModule(Module module) {
    moduleClassMappings.put(module.getClass(), module);
  }

  public Collection<Module> bootRequests(BootSegment bootSegment) {
    return new ArrayList<>(modulePick(module -> readyToBoot(module, bootSegment)));
  }

  private boolean readyToBoot(Module module, BootSegment bootSegment) {
    return module.settings().bootSegment().equals(bootSegment);
  }

  public void enableModule(Module module) {
    if (module.settings().shouldLinkSubscriptions()) {
      PacketSubscriptionLinker packetSubscriptionLinker = lookup(PacketSubscriptionLinker.class);
      BukkitEventSubscriptionLinker bukkitEventLinker = lookup(BukkitEventSubscriptionLinker.class);
      if (bukkitEventLinker != null) {
        bukkitEventLinker.registerEventsIn(module);
      }
      if (packetSubscriptionLinker != null) {
        packetSubscriptionLinker.linkSubscriptionsIn(module);
      }
    }
    module.enable();
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

  private void forEach(Consumer<Module> moduleConsumer) {
    moduleClassMappings.values().forEach(moduleConsumer);
  }

  public <T extends Module> T moduleOf(Class<T> moduleClass) {
    //noinspection unchecked
    return (T) moduleClassMappings.get(moduleClass);
  }

  private Collection<Module> modulePick(
    Predicate<Module> predicate
  ) {
    return moduleClassMappings.values()
      .stream().filter(predicate)
      .collect(Collectors.toList());
  }
}
