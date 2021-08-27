package de.jpx3.intave.detect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.cleanup.ShutdownTasks;
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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link CheckService} initializes, holds and links implementation classes of class {@link Check}.
 * Every instance of the implementation class of class {@link Check} must be singleton throughout the entire
 * lifespan of our application - ensured by the utilizing of a class-as-key-to-instance principle.
 *
 * It will instantiate all known implementations of class {@link Check} with {@link CheckService#setup()}, following
 * command linkage, to find and link any subscriptions within the instantiated {@link Check}.<br>
 * For the lifespan of the application, a {@link CheckService} will hold these checks, and
 * as the references are mostly immutable, pre-render different access caches allowing fast {@link Check} lookups
 * via {@link CheckService#searchCheck(String)} and {@link CheckService#searchCheck(Class)}.
 * Once {@link CheckService#reset()} is called (when the application terminates), it will terminate all subscriptions and
 * clear all check-references.
 *
 * @see CheckLinker
 *
 * @see Check
 * @see CheckPart
 * @see MetaCheck
 * @see MetaCheckPart
 */
@Relocate
@HighOrderService
public final class CheckService {
  private final IntavePlugin plugin;
  private List<Check> checks = new ArrayList<>();
  private List<String> checkNames = new ArrayList<>();
  private Map<Class<?>, Check> classRequestCache = new HashMap<>();
  private Map<String, Check> nameRequestCache = new HashMap<>();

  private final CheckLinker checkLinker;

  public CheckService(IntavePlugin plugin) {
    this.plugin = plugin;
    checkLinker = new CheckLinker();
  }

  /**
   * Load known checks, bake quick access, and link packet- and bukkit-subscriptions
   */
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

    bakeQuickAccess();
    checkLinker.linkBukkitEventSubscriptions(checks);
    checkLinker.linkPacketEventSubscriptions(checks);

    ShutdownTasks.addBeforeAll(this::reset);
  }

  /**
   * Remove packet- and bukkit-subscriptions, reset quick access, remove checks
   */
  public void reset() {
    checkLinker.removeBukkitEventSubscriptions(checks);
    checkLinker.removePacketEventSubscriptions(checks);
    resetQuickAccess();
    checks = new CopyOnWriteArrayList<>();
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
  }

  private void addCheck(Class<? extends Check> checkClass) {
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

  private void addCheck(Check check) {
    checks.add(check);
  }

  private void bakeQuickAccess() {
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

  private void resetQuickAccess() {
    classRequestCache = new HashMap<>();
    nameRequestCache = new HashMap<>();
    checkNames = new ArrayList<>();
  }

  /**
   * Lookup a {@link Check} by its intrinsically unique {@code class}.
   * @param checkClass the corresponding check class
   * @param <T> the corresponding check type
   * @throws IllegalStateException when the check could not be found
   * @return the check
   */
  public <T extends Check> T searchCheck(Class<T> checkClass) {
    Check check = classRequestCache.get(checkClass);
    if (check == null) {
      for (Check checkSearch : checks) {
        if (checkSearch.getClass() == checkClass) {
          check = checkSearch;
        }
      }
      if (check == null) {
        throw new IllegalStateException("Unable to find check " + checkClass);
      }
    }
    //noinspection unchecked
    return (T) check;
  }

  /**
   * Lookup a {@link Check} by its name.
   * @param checkName the corresponding check name
   * @param <T> the corresponding check type
   * @throws IllegalStateException when the check could not be found
   * @return the check
   */
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

  /**
   * Checks whether a check with the given name exists in cache.
   * @param checkName the name of the check
   * @return {@code true} if it contains the check, {@code false} if it doesn't
   */
  public boolean hasCheck(String checkName) {
    return nameRequestCache.containsKey(checkName.toLowerCase());
  }

  /**
   * Retrieves a {@link Collection} of the instances of all implementations of the {@link Check} class.
   * @return all checks
   */
  public Collection<Check> checks() {
    return checks;
  }
}