package de.jpx3.intave.module;

import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.cleanup.Shutdown;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;

public final class Modules {
  private final static ModulePool pool = new ModulePool();
  private final static ModuleLoader loader = new ModuleLoader();

  @Native
  public static void prepareModules() {
    loader.setup();
    Shutdown.addTask(Modules::shutdown);
  }

  @Native
  public static void proceedBoot(BootSegment bootSegment) {
    loader.loadRequests().forEach(pool::loadModule);
    pool.bootRequests(bootSegment).forEach(pool::enableModule);
  }

  public static void shutdown() {
    pool.disableAll();
    pool.unloadAll();
  }

  public static <T extends Module> T find(Class<T> moduleClass) {
    T module = pool.lookup(moduleClass);
    if (module == null) {
      throw new IllegalStateException("Unable to find module " + moduleClass + ", is it loaded?");
    }
    return module;
  }

  // quick accessors

  private final static LinkerCategory LINKER_CATEGORY = new LinkerCategory();
  private final static DispatchCategory DISPATCH_CATEGORY = new DispatchCategory();

  public static LinkerCategory linker() {
    return LINKER_CATEGORY;
  }

  public static DispatchCategory dispatch() {
    return DISPATCH_CATEGORY;
  }

  public static class DispatchCategory {
    // empty
  }

  public static class LinkerCategory {
    public BukkitEventSubscriptionLinker bukkitEvents() {
      return find(BukkitEventSubscriptionLinker.class);
    }

    public PacketSubscriptionLinker packetEvents() {
      return find(PacketSubscriptionLinker.class);
    }
  }
}
