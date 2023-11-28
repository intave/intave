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

final class ModuleLoader {
  private final Map<String, ModuleSettings> pendingModuleClasses = new HashMap<>();

  @Native
  public void setup() {
    ModuleSettings eventBoot = ModuleSettings.builder().doNotLinkSubscriptions().bootBeforeIntave().build();
    ModuleSettings packetBoot = ModuleSettings.builder()
      .doNotLinkSubscriptions().requireProtocolLib()
      .andRequire(Requirements.intaveEnabled())
      .bootAt(BootSegment.STAGE_6)
      .build();
    ModuleSettings defaultBoot = ModuleSettings.builder().requireProtocolLib().bootUsually().build();
    ModuleSettings lateBoot = ModuleSettings.builder().requireProtocolLib().bootAfterIntave().build();

    // linker
    prepareModule("de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker", eventBoot);
    prepareModule("de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker", packetBoot);
    prepareModule("de.jpx3.intave.module.linker.nayoro.NayoroEventSubscriptionLinker", packetBoot);

    // feedback
    prepareModule("de.jpx3.intave.module.feedback.FeedbackSender", lateBoot);
    prepareModule("de.jpx3.intave.module.feedback.FeedbackAnalysis", lateBoot);
    prepareModule("de.jpx3.intave.module.feedback.FeedbackReceiver", defaultBoot);
    prepareModule("de.jpx3.intave.module.feedback.PacketDelayer", lateBoot);

    // tracker
    prepareModule("de.jpx3.intave.module.tracker.player.AbilityTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.AttributeTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.ConnectionTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.EffectTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.InventoryTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.ItemCrashTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.MovementDebugTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.PlayerHandTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.player.SettingsTracker", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.entity.EntityTracker", lateBoot);
    prepareModule("de.jpx3.intave.module.tracker.entity.LazyEntityCollisionService", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.entity.EntityCollisionDisabler", defaultBoot);
    prepareModule("de.jpx3.intave.module.tracker.block.BlockUpdateTracker", defaultBoot);

    // mitigate
    prepareModule("de.jpx3.intave.module.mitigate.CombatMitigator", defaultBoot);
    prepareModule("de.jpx3.intave.module.mitigate.SetbackSimulator", lateBoot);
    prepareModule("de.jpx3.intave.module.mitigate.ReconDelayLimiter", lateBoot);

    // dispatch
    prepareModule("de.jpx3.intave.module.dispatch.AttackDispatcher", lateBoot);
    prepareModule("de.jpx3.intave.module.dispatch.MovementDispatcher", lateBoot);

    // emulate
//    prepareModule("de.jpx3.intave.module.emulate.MovementEmulation", lateBoot);

    // misc
    prepareModule("de.jpx3.intave.module.nayoro.Nayoro", defaultBoot);
    prepareModule("de.jpx3.intave.module.warning.ClientWarningModule", defaultBoot);
    prepareModule("de.jpx3.intave.module.event.CustomEvents", defaultBoot);
    prepareModule("de.jpx3.intave.module.patcher.PacketResynchronizer", defaultBoot);
    prepareModule("de.jpx3.intave.module.patcher.ChunkAccessPatcher", defaultBoot);
    prepareModule("de.jpx3.intave.module.violation.ViolationProcessor", lateBoot);
    prepareModule("de.jpx3.intave.module.filter.Filters", lateBoot);
    prepareModule("de.jpx3.intave.module.player.UserLifetimeService", defaultBoot);
    prepareModule("de.jpx3.intave.module.player.StorageLoader", defaultBoot);
    prepareModule("de.jpx3.intave.module.player.PlaytimeUpdater", lateBoot);
    prepareModule("de.jpx3.intave.module.player.MiscBukkitEvents", defaultBoot);
    prepareModule("de.jpx3.intave.module.player.AccountCheck", defaultBoot);
    prepareModule("de.jpx3.intave.module.actionbar.ActionBarDisplayer", defaultBoot);
  }

  private void prepareModule(String moduleClass) {
    prepareModule(moduleClass, ModuleSettings.def());
  }

  private void prepareModule(String moduleClass, ModuleSettings settings) {
    pendingModuleClasses.put(moduleClass, settings);
  }

  public Collection<Module> loadRequests(BootSegment bootSegment) {
    return classPick(settings -> settings.readyToLoad(bootSegment))
      .stream().map(this::instantiateModule)
      .map(obj -> (Module) obj)
      .peek(this::initiate)
      .collect(Collectors.toList());
  }

  private Collection<String> classPick(
    Predicate<? super ModuleSettings> predicate
  ) {
    return pendingModuleClasses.entrySet().stream()
      .filter(entry -> predicate.test(entry.getValue()))
      .map(Map.Entry::getKey).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private <T> T instantiateModule(String className) {
    try {
      Class<?> klass = Class.forName(className);
      try {
        return (T) klass.getConstructor(IntavePlugin.class).newInstance(IntavePlugin.singletonInstance());
      } catch (InvocationTargetException internalException) {
        throw new IntaveInternalException(internalException);
      } catch (Exception methodNotFound) {
        return (T) klass.newInstance();
      }
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void initiate(Module module) {
    module.setPlugin(IntavePlugin.singletonInstance());
    module.setModuleSettings(pendingModuleClasses.remove(module.getClass().getName()));
  }
}
