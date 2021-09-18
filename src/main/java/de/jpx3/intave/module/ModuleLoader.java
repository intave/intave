package de.jpx3.intave.module;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Native;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ModuleLoader {
  private final Map<String, ModuleSettings> pendingModuleClasses = new HashMap<>();

  @Native
  public void setup() {
    // linker
    prepareModule("de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker", ModuleSettings.builder().doNotLinkSubscriptions().bootAt(BootSegment.STAGE_3).build());
    prepareModule("de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker", ModuleSettings.builder().doNotLinkSubscriptions().requireProtocolLib().requires(Requirements.intaveEnabled()).bootAt(BootSegment.STAGE_6).build());

    ModuleSettings defaultBoot = ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build();
    ModuleSettings lateBoot = ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_10).build();

    // feedbacks
    prepareModule("de.jpx3.intave.module.feedback.FeedbackSender", defaultBoot);
    prepareModule("de.jpx3.intave.module.feedback.FeedbackReceiver", defaultBoot);

    // tracker
    prepareModule("de.jpx3.intave.module.tracker.player.AbilityTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.AttributeTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.block.BlockUpdateTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.ConnectionTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.EffectTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.entity.EntityTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.InventoryTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.entity.LazyEntityCollisionService", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.entity.EntityCollisionDisabler", defaultBoot);

    // mitigate
    prepareModule("de.jpx3.intave.module.mitigate.CombatMitigator", defaultBoot);
    prepareModule("de.jpx3.intave.module.mitigate.MovementEmulator", lateBoot);
    prepareModule("de.jpx3.intave.module.mitigate.ReconDelayLimiter", lateBoot);

    // dispatch
    prepareModule("de.jpx3.intave.module.dispatch.AttackDispatcher", lateBoot);
    prepareModule("de.jpx3.intave.module.dispatch.MovementDispatcher", lateBoot);

    // misc
    prepareModule("de.jpx3.intave.module.warning.ClientWarningModule", defaultBoot);
    prepareModule("de.jpx3.intave.module.event.CustomEvents", defaultBoot);
    prepareModule("de.jpx3.intave.module.patcher.PacketResynchronizer", defaultBoot);
    prepareModule("de.jpx3.intave.module.violation.ViolationProcessor", defaultBoot);
    prepareModule("de.jpx3.intave.module.filter.Filters", defaultBoot);
    prepareModule("de.jpx3.intave.module.player.UserLifetimeService", defaultBoot);
    prepareModule("de.jpx3.intave.module.player.MiscBukkitEvents", defaultBoot);

//    prepareModule("de.jpx3.intave.module.patch.TimeoutHalter", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
  }

  private void prepareModule(String moduleClass) {
    pendingModuleClasses.put(moduleClass, ModuleSettings.def());
  }

  private void prepareModule(String moduleClass, ModuleSettings settings) {
    pendingModuleClasses.put(moduleClass, settings);
  }

  public Collection<Module> loadRequests(BootSegment bootSegment) {
    return classPick(segment -> readyToLoad(bootSegment, segment))
      .stream().map(this::instanceOf).map(o -> (Module) o)
      .peek(this::initiate).collect(Collectors.toList());
  }

  private boolean readyToLoad(BootSegment segment, ModuleSettings moduleSettings) {
    return segment.equals(moduleSettings.bootSegment()) && moduleSettings.requirementsFulfilled();
  }

  private void initiate(Module module) {
    module.setPlugin(IntavePlugin.singletonInstance());
    module.setModuleSettings(pendingModuleClasses.remove(module.getClass().getName()));
  }

  @SuppressWarnings("unchecked")
  private <T> T instanceOf(String className) {
    try {
      Class<?> klass = Class.forName(className);
      try {
        return (T) klass.getConstructor(IntavePlugin.class).newInstance(IntavePlugin.singletonInstance());
      } catch (InvocationTargetException internalException) {
        throw new IntaveInternalException(internalException);
      } catch (Exception exception) {
        return (T) klass.newInstance();
      }
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private Collection<String> classPick(
    Predicate<ModuleSettings> predicate
  ) {
    return pendingModuleClasses.entrySet().stream()
      .filter(entry -> predicate.test(entry.getValue()))
      .map(Map.Entry::getKey).collect(Collectors.toList());
  }
}
