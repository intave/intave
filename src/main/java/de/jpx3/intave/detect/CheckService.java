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
import de.jpx3.intave.detect.checks.other.InventoryClickAnalysis;
import de.jpx3.intave.detect.checks.other.ProtocolScanner;
import de.jpx3.intave.detect.checks.world.BreakSpeedLimiter;
import de.jpx3.intave.detect.checks.world.InteractionRaytrace;
import de.jpx3.intave.detect.checks.world.PlacementAnalysis;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/**
 * A {@link CheckService} that initializes,
 */
@Relocate
public final class CheckService {
  private final IntavePlugin plugin;
  private List<Check> checks = new ArrayList<>();
  private List<String> checkNames = new ArrayList<>();
  private Map<Class<?>, Check> classRequestCache = new HashMap<>();
  private Map<String, Check> nameRequestCache = new HashMap<>();

  private final CheckLinker checkLinker;

  public CheckService(IntavePlugin plugin) {
    this.plugin = plugin;
    checkLinker = new CheckLinker(this.plugin);
  }

  public void setup() {
    addCheck(Physics.class);
    addCheck(InteractionRaytrace.class);
    addCheck(Heuristics.class);
    addCheck(AttackRaytrace.class);
    addCheck(Timer.class);
    addCheck(BreakSpeedLimiter.class);
    addCheck(ProtocolScanner.class);
    addCheck(PlacementAnalysis.class);
    addCheck(InventoryClickAnalysis.class);
    addCheck(ClickSpeedLimiter.class);
//    addCheck(WorkspaceCheck.class);

    bakeQuickAccess();
    checkLinker.linkBukkitEventSubscriptions(checks);
    checkLinker.linkPacketEventSubscriptions(checks);
  }

  public void reset() {
    checkLinker.removeBukkitEventSubscriptions(checks);
    checkLinker.removePacketEventSubscriptions(checks);
    resetQuickAccess();
    checks.clear();
    classRequestCache.clear();
    nameRequestCache.clear();
  }

  public void addCheck(Class<? extends Check> checkClass) {
    try {
      Check check;
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

  public void addCheck(Check check) {
    checks.add(check);
  }

  public void bakeQuickAccess() {
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
    checkNames = new ArrayList<>();
    for (Check check : checks) {
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


  public <T extends Check> T searchCheck(Class<T> checkClass) {
    Check check = classRequestCache.get(checkClass);
    if (check == null) {
      for (Check intaveCheck : checks) {
        if (intaveCheck.getClass() == checkClass) {
          //noinspection unchecked
          return (T) intaveCheck;
        }
      }
      throw new IllegalStateException("Unable to find check " + checkClass);
    }
    //noinspection unchecked
    return (T) check;
  }

  public <T extends Check> T searchCheck(String checkName) {
    Check check = nameRequestCache.get(checkName.toLowerCase());
    if (check == null) {
      for (Check intaveCheck : checks) {
        if (intaveCheck.name().equalsIgnoreCase(checkName)) {
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
    if (checkSection == null) {
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

  public List<Check> checks() {
    return checks;
  }
}