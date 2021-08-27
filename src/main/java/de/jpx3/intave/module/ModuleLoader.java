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

    // feedback
    prepareModule("de.jpx3.intave.module.feedback.FeedbackSender", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
    prepareModule("de.jpx3.intave.module.feedback.FeedbackReceiver", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());

    // tracker
    prepareModule("de.jpx3.intave.module.tracker.player.AbilityTracker", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
    prepareModule("de.jpx3.intave.module.tracker.player.AttributeTracker", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
    prepareModule("de.jpx3.intave.module.tracker.block.BlockUpdateTracker", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
    prepareModule("de.jpx3.intave.module.tracker.player.EffectTracker", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
    prepareModule("de.jpx3.intave.module.tracker.entity.EntityTracker", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
    prepareModule("de.jpx3.intave.module.tracker.player.InventoryTracker", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());

    // dispatch
    prepareModule("de.jpx3.intave.module.dispatch.AttackDispatcher", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_10).build());
    prepareModule("de.jpx3.intave.module.dispatch.MovementDispatcher", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_10).build());

    // misc
    prepareModule("de.jpx3.intave.module.warning.ClientWarningModule", ModuleSettings.builder().requireProtocolLib().bootAt(BootSegment.STAGE_7).build());
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
