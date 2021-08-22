package de.jpx3.intave.module;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriptionLinker;
import de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker;
import de.jpx3.intave.module.tracker.entity.EntityTracker;
import de.jpx3.intave.module.warning.ClientWarningModule;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ModuleLoader {
  private final Map<Class<? extends Module>, ModuleSettings> pendingModuleClasses = new HashMap<>();

  @Native
  public void setup() {
    prepareModule(BukkitEventSubscriptionLinker.class, ModuleSettings.builder().doNotLinkSubscriptions().bootAt(BootSegment.STAGE_3).build());
    prepareModule(PacketSubscriptionLinker.class, ModuleSettings.builder().doNotLinkSubscriptions().requiresProtocolLib().requires(Requirements.intaveEnabled()).bootAt(BootSegment.STAGE_5).build());
    prepareModule(EntityTracker.class, ModuleSettings.builder().requiresProtocolLib().bootAt(BootSegment.STAGE_7).build());
    prepareModule(ClientWarningModule.class, ModuleSettings.builder().requiresProtocolLib().bootAt(BootSegment.STAGE_7).build());
  }

  private void prepareModule(Class<? extends Module> moduleClass) {
    prepareModule(moduleClass, ModuleSettings.of());
  }

  private void prepareModule(Class<? extends Module> moduleClass, ModuleSettings settings) {
    pendingModuleClasses.put(moduleClass, settings);
  }

  public Collection<Module> loadRequests() {
    return classPick(this::readyToLoad).stream().map(this::instanceOf).peek(this::initiate).collect(Collectors.toList());
  }

  private void initiate(Module module) {
    module.setPlugin(IntavePlugin.singletonInstance());
    module.setModuleSettings(pendingModuleClasses.remove(module.getClass()));
  }

  private <T> T instanceOf(Class<T> klass) {
    try {
      try {
        return klass.getConstructor(IntavePlugin.class).newInstance(IntavePlugin.singletonInstance());
      } catch (InvocationTargetException internalException) {
        throw new IntaveInternalException(internalException);
      } catch (Exception exception) {
        return klass.newInstance();
      }
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private boolean readyToLoad(ModuleSettings moduleSettings) {
    return moduleSettings.requirementsFulfilled();
  }

  private Collection<Class<? extends Module>> classPick(
    Predicate<ModuleSettings> predicate
  ) {
    return pendingModuleClasses.entrySet().stream()
      .filter(entry -> predicate.test(entry.getValue()))
      .map(Map.Entry::getKey).collect(Collectors.toList());
  }
}
