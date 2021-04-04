package de.jpx3.intave.detect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.detect.checks.combat.AttackRaytrace;
import de.jpx3.intave.detect.checks.combat.ClickSpeedLimiter;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.Timer;
import de.jpx3.intave.detect.checks.other.ProtocolScanner;
import de.jpx3.intave.detect.checks.world.BreakSpeedLimiter;
import de.jpx3.intave.detect.checks.world.InteractionRaytrace;
import de.jpx3.intave.event.bukkit.BukkitEventLinker;
import de.jpx3.intave.event.packet.PacketSubscriptionLinker;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

@Relocate
public final class CheckService {
  private final IntavePlugin plugin;
  private List<IntaveCheck> checks = new ArrayList<>();
  private List<String> checkNames = new ArrayList<>();
  private Map<Class<?>, IntaveCheck> classRequestCache = new HashMap<>();
  private Map<String, IntaveCheck> nameRequestCache = new HashMap<>();

  public CheckService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    addCheck(Physics.class);
    addCheck(InteractionRaytrace.class);
    addCheck(Heuristics.class);
    addCheck(AttackRaytrace.class);
    addCheck(Timer.class);
    addCheck(BreakSpeedLimiter.class);
    addCheck(ProtocolScanner.class);
//    addCheck(PlacementAnalysis.class);
//    addCheck(InventoryClickAnalysis.class);
    addCheck(ClickSpeedLimiter.class);
//    addCheck(WorkspaceCheck.class);

    bakeQuickAccess();
    linkBukkitEventSubscriptions();
    linkPacketEventSubscriptions();
  }

  public void reset() {
    checks.clear();
    classRequestCache.clear();
    nameRequestCache.clear();

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
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
    checkNames = new ArrayList<>();
    for (IntaveCheck check : checks) {
      checkNames.add(check.name());
      classRequestCache.put(check.getClass(), check);
      nameRequestCache.put(check.name().toLowerCase(Locale.ROOT), check);
    }
    classRequestCache = ImmutableMap.copyOf(classRequestCache);
    nameRequestCache = ImmutableMap.copyOf(nameRequestCache);
    checkNames = ImmutableList.copyOf(checkNames);
    checks = ImmutableList.copyOf(checks);
  }

  public void resetQuickAccess() {
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
    checkNames = new ArrayList<>();
  }

  public void linkPacketEventSubscriptions() {
    PacketSubscriptionLinker packetSubscriptionLinker = plugin.packetSubscriptionLinker();
    for (IntaveCheck check : checks) {
      if(!check.enabled()) {
        continue;
      }
      packetSubscriptionLinker.linkSubscriptionsIn(check);
      for (IntaveCheckPart<?> checkPart : check.checkParts()) {
        if (!checkPart.enabled()) {
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
      for (IntaveCheckPart<?> checkPart : check.checkParts()) {
        if (!checkPart.enabled()) {
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
      for (IntaveCheckPart<?> checkPart : check.checkParts()) {
        if (!checkPart.enabled()) {
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
      for (IntaveCheckPart<?> checkPart : check.checkParts()) {
        if (!checkPart.enabled()) {
          continue;
        }
        bukkitEventLinker.unregisterEventsIn(checkPart);
      }
    }
  }

  public <T extends IntaveCheck> T searchCheck(Class<T> checkClass) {
    IntaveCheck check = classRequestCache.get(checkClass);
    if (check == null) {
      for (IntaveCheck intaveCheck : checks) {
        if(intaveCheck.getClass() == checkClass) {
          //noinspection unchecked
          return (T) intaveCheck;
        }
      }
      throw new IllegalStateException("Unable to find check " + checkClass);
    }
    //noinspection unchecked
    return (T) check;
  }

  public <T extends IntaveCheck> T searchCheck(String checkName) {
    IntaveCheck check = nameRequestCache.get(checkName.toLowerCase());
    if (check == null) {
      for (IntaveCheck intaveCheck : checks) {
        if(intaveCheck.name().equalsIgnoreCase(checkName)) {
          //noinspection unchecked
          return (T) intaveCheck;
        }
      }
      throw new IllegalStateException("Unable to find check " + checkName);
    }
    //noinspection unchecked
    return (T) check;
  }

  public boolean hasCheck(String checkName) {
    return nameRequestCache.containsKey(checkName.toLowerCase());
  }

  public void enterConfiguration(CheckConfiguration checkConfiguration) {
    YamlConfiguration configuration = plugin.configurationService().configuration();
    String checkSectionPath = "check." + checkConfiguration.check().configurationKey();
    ConfigurationSection checkSection = configuration.getConfigurationSection(checkSectionPath);
    if(checkSection == null) {
      checkConfiguration.setSettings(new HashMap<>());
      return;
    }
    Map<String, Object> mappings = new HashMap<>();
    Set<String> keys = checkSection.getKeys(true);
    for (String key : keys) {
      mappings.putIfAbsent(key, checkSection.get(key));
    }
    checkConfiguration.setSettings(mappings);
  }

  public List<String> checkNames() {
    return checkNames;
  }

  public List<IntaveCheck> checks() {
    return checks;
  }
}