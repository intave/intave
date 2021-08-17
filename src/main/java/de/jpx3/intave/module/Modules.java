package de.jpx3.intave.module;

import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;
import de.jpx3.intave.tools.Shutdown;

public final class Modules {
  private final ModulePool pool = new ModulePool();
  private final ModuleLoader loader = new ModuleLoader();

  public void prepareModules() {
    loader.setup();
  }

  public void proceedBoot(BootSegment bootSegment) {
    loader.loadRequests().forEach(pool::loadModule);
    pool.bootRequests(bootSegment).forEach(pool::enableModule);

    Shutdown.addTask(this::shutdown);
  }

  public void shutdown() {
    pool.disableAll();
    pool.unloadAll();
  }

  // quick accessors

  public BukkitEventSubscriptionLinker bukkitEventLinker() {
    return find(BukkitEventSubscriptionLinker.class);
  }

  public PacketSubscriptionLinker packetSubscriptionLinker() {
    return find(PacketSubscriptionLinker.class);
  }

  public <T extends Module> T find(Class<T> moduleClass) {
    T module = pool.lookup(moduleClass);
    if (module == null) {
      throw new IllegalStateException("Unable to find module " + moduleClass + ", is it loaded?");
    }
    return module;
  }
}
