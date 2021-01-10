package de.jpx3.intave.detect;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.detect.checks.combat.AttackRaytrace;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.Timer;
import de.jpx3.intave.detect.checks.world.InteractionRaytrace;
import de.jpx3.intave.event.bukkit.BukkitEventLinker;
import de.jpx3.intave.event.packet.PacketSubscriptionLinker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CheckService {
  private final IntavePlugin plugin;
  private List<IntaveCheck> checks = new ArrayList<>();
  private Map<Class<?>, IntaveCheck> requestCache = new HashMap<>();

  public CheckService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    addCheck(Physics.class);
    addCheck(InteractionRaytrace.class);
    addCheck(Heuristics.class);
    addCheck(AttackRaytrace.class);
    addCheck(Timer.class);
//    addCheck(WorkspaceCheck.class);

    bakeQuickAccess();
    linkBukkitEventSubscriptions();
    linkPacketEventSubscriptions();
  }

  public void reset() {
    requestCache.clear();

    resetQuickAccess();
    removeBukkitEventSubscriptions();
    removePacketEventSubscriptions();
  }

  public void addCheck(Class<? extends IntaveCheck> checkClass) {
    try {

      IntaveCheck check;
      try {
        checkClass.getConstructor(IntavePlugin.class);
        check = checkClass.getConstructor(IntavePlugin.class).newInstance(plugin);
      } catch (NoSuchMethodException exception) {
        check = checkClass.newInstance();
      }

      addCheck(check);
    } catch (Exception e) {
      throw new IntaveInternalException("Unable to load check " + checkClass.getSimpleName(), e);
    }
  }

  public void addCheck(IntaveCheck check) {
    checks.add(check);
  }

  public void bakeQuickAccess() {
    requestCache = new HashMap<>();
    for (IntaveCheck check : checks) {
      requestCache.put(check.getClass(), check);
    }
    requestCache = ImmutableMap.copyOf(requestCache);
  }

  public void resetQuickAccess() {
    requestCache = new HashMap<>();
  }

  public void linkPacketEventSubscriptions() {
    PacketSubscriptionLinker packetSubscriptionLinker = plugin.packetSubscriptionLinker();
    for (IntaveCheck check : checks) {
      if(!check.enabled()) {
        continue;
      }
      packetSubscriptionLinker.linkSubscriptionsIn(check);
      for (IntaveCheckPart checkPart : check.checkParts()) {
        if(!checkPart.enabled()) {
          continue;
        }
        packetSubscriptionLinker.linkSubscriptionsIn(checkPart);
      }
    }
  }

  public void removePacketEventSubscriptions() {
    PacketSubscriptionLinker packetSubscriptionLinker = plugin.packetSubscriptionLinker();
    for (IntaveCheck check : checks) {
      if(!check.enabled()) {
        continue;
      }
      packetSubscriptionLinker.removeSubscriptionsOf(check);
      for (IntaveCheckPart checkPart : check.checkParts()) {
        if(!checkPart.enabled()) {
          continue;
        }
        packetSubscriptionLinker.removeSubscriptionsOf(checkPart);
      }
    }
  }

  public void linkBukkitEventSubscriptions() {
    BukkitEventLinker bukkitEventLinker = plugin.eventLinker();
    for (IntaveCheck check : checks) {
      if(!check.enabled()) {
        continue;
      }
      bukkitEventLinker.registerEventsIn(check);
      for (IntaveCheckPart checkPart : check.checkParts()) {
        if(!checkPart.enabled()) {
          continue;
        }
        bukkitEventLinker.registerEventsIn(checkPart);
      }
    }
  }

  public void removeBukkitEventSubscriptions() {
    BukkitEventLinker bukkitEventLinker = plugin.eventLinker();
    for (IntaveCheck check : checks) {
      if(!check.enabled()) {
        continue;
      }
      bukkitEventLinker.unregisterEventsIn(check);
      for (IntaveCheckPart checkPart : check.checkParts()) {
        if(!checkPart.enabled()) {
          continue;
        }
        bukkitEventLinker.unregisterEventsIn(checkPart);
      }
    }
  }

  public <T extends IntaveCheck> T searchCheck(Class<T> checkClass) {
    IntaveCheck check = requestCache.get(checkClass);
    if(check == null) {
      throw new IllegalStateException("Unable to find check " + checkClass);
    }
    //noinspection unchecked
    return (T) check;
  }
}