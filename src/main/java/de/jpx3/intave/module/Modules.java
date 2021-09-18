package de.jpx3.intave.module;

import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.module.event.CustomEvents;
import de.jpx3.intave.module.feedback.FeedbackReceiver;
import de.jpx3.intave.module.feedback.FeedbackSender;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;
import de.jpx3.intave.module.mitigate.CombatMitigator;
import de.jpx3.intave.module.mitigate.MovementEmulator;
import de.jpx3.intave.module.mitigate.ReconDelayLimiter;
import de.jpx3.intave.module.violation.ViolationProcessor;

public final class Modules {
  private final static ModulePool pool = new ModulePool();
  private final static ModuleLoader loader = new ModuleLoader();

  @Native
  public static void prepareModules() {
    loader.setup();
    ShutdownTasks.add(Modules::shutdown);
  }

  @Native
  public static void proceedBoot(BootSegment bootSegment) {
    loader.loadRequests(bootSegment).forEach(pool::loadModule);
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

  public static ViolationProcessor violationProcessor() {
    return find(ViolationProcessor.class);
  }
  public static CustomEvents eventInvoker() {
    return find(CustomEvents.class);
  }
  public static FeedbackSender feedback() {
    return find(FeedbackSender.class);
  }
  @Deprecated
  public static FeedbackReceiver feedbackReceiver() {
    return find(FeedbackReceiver.class);
  }

  private final static LinkerCategory LINKER_CATEGORY = new LinkerCategory();
  private final static DispatchCategory DISPATCH_CATEGORY = new DispatchCategory();
  private final static TrackerCategory TRACKER_CATEGORY = new TrackerCategory();
  private final static MitigateCategory MITIGATE_CATEGORY = new MitigateCategory();

  public static LinkerCategory linker() {
    return LINKER_CATEGORY;
  }
  public static DispatchCategory dispatch() {
    return DISPATCH_CATEGORY;
  }
  public static TrackerCategory tracker() {
    return TRACKER_CATEGORY;
  }
  public static MitigateCategory mitigate() {
    return MITIGATE_CATEGORY;
  }

  public static class TrackerCategory {

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

  public static class MitigateCategory {
    public CombatMitigator combat() {
      return find(CombatMitigator.class);
    }

    public MovementEmulator movement() {
      return find(MovementEmulator.class);
    }

    public ReconDelayLimiter reconnectionLimiter() {
      return find(ReconDelayLimiter.class);
    }
  }
}
