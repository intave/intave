package de.jpx3.intave.module;

import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.module.actionbar.ActionBarDisplayer;
import de.jpx3.intave.module.event.CustomEvents;
import de.jpx3.intave.module.feedback.FeedbackReceiver;
import de.jpx3.intave.module.feedback.FeedbackSender;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.nayoro.NayoroEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;
import de.jpx3.intave.module.mitigate.CombatMitigator;
import de.jpx3.intave.module.mitigate.ReconDelayLimiter;
import de.jpx3.intave.module.mitigate.SetbackSimulator;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.player.StorageLoader;
import de.jpx3.intave.module.violation.ViolationProcessor;

public final class Modules {
  private static final ModulePool pool = new ModulePool();
  private static final ModuleLoader loader = new ModuleLoader();

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

  public static Nayoro nayoro() {
    return find(Nayoro.class);
  }

  public static StorageLoader storage() {
    return find(StorageLoader.class);
  }

  public static ViolationProcessor violationProcessor() {
    return find(ViolationProcessor.class);
  }

  public static CustomEvents eventInvoker() {
    return find(CustomEvents.class);
  }

  public static ActionBarDisplayer actionBar() {
    return find(ActionBarDisplayer.class);
  }

  @Deprecated
  public static FeedbackSender feedback() {
    return find(FeedbackSender.class);
  }

  @Deprecated
  public static FeedbackReceiver feedbackReceiver() {
    return find(FeedbackReceiver.class);
  }

  // categories

  private static final LinkerCategory LINKER_CATEGORY = new LinkerCategory();
  private static final DispatchCategory DISPATCH_CATEGORY = new DispatchCategory();
  private static final TrackerCategory TRACKER_CATEGORY = new TrackerCategory();
  private static final MitigateCategory MITIGATE_CATEGORY = new MitigateCategory();

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

    public NayoroEventSubscriptionLinker nayoroEvents() {
      return find(NayoroEventSubscriptionLinker.class);
    }
  }

  public static class MitigateCategory {
    public CombatMitigator combat() {
      return find(CombatMitigator.class);
    }

    public SetbackSimulator movement() {
      return find(SetbackSimulator.class);
    }

    public ReconDelayLimiter reconnectionLimiter() {
      return find(ReconDelayLimiter.class);
    }
  }
}
