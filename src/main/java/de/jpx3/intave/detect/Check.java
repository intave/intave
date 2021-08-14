package de.jpx3.intave.detect;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.check.MitigationStrategy;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.detect.checks.movement.Physics;
import de.jpx3.intave.detect.checks.movement.Timer;
import de.jpx3.intave.detect.checks.world.InteractionRaytrace;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A {@link Check} provides a skeletal architecture for both detection algorithms and detection clusters.<br>
 * It is stored, linked, unlinked and deleted by a {@link CheckService}, where it is
 * externally made accessible. All instances of complete implementation classes must be a singleton, as they will be addressed by {@code class} from the {@link CheckService}.
 * {@link Check}s have intrinsic properties, their unique name, their unique configuration key, whether the check is enabled,
 * a {@link CheckConfiguration} and {@link CheckStatistics}.
 * Instances hold a reference to the singleton instance of the {@link IntavePlugin} class and are equipped with a {@link Check#userOf(Player)} utility method.
 * It holds subordinate {@link CheckPart}s, as well as an {@link Check#appendCheckPart(CheckPart)} method to append them to themself.
 *
 * @see de.jpx3.intave.detect.CheckPart
 * @see de.jpx3.intave.detect.MetaCheck
 * @see de.jpx3.intave.detect.MetaCheckPart
 * @see de.jpx3.intave.detect.CheckStatistics
 * @see de.jpx3.intave.detect.CheckConfiguration
 * @see de.jpx3.intave.detect.CheckService
 * @see de.jpx3.intave.detect.EventProcessor
 */
 public abstract class Check implements EventProcessor {
  private final IntavePlugin plugin;
  private final String checkName;
  private final String configurationKey;
  private final CheckStatistics statistics;
  private final Map<TrustFactor, CheckStatistics> perTrustFactorStatistics;
  private final List<CheckPart<?>> checkParts = new ArrayList<>();
  private final CheckConfiguration checkConfiguration;
  private final boolean enabled;
  private MitigationStrategy mitigationStrategy;
  private MitigationStrategy defaultMitigationStrategy = MitigationStrategy.NOT_SUPPORTED;

  public Check(String checkName, String configurationKey) {
    this.plugin = IntavePlugin.singletonInstance();
    this.checkName = checkName;
    this.configurationKey = configurationKey;
    this.statistics = new CheckStatistics();
    this.checkConfiguration = new CheckConfiguration(this);
    this.perTrustFactorStatistics = new EnumMap<>(TrustFactor.class);
    plugin.checkService().enterConfiguration(checkConfiguration);
    this.enabled = checkConfiguration.settings().boolBy("enabled");
    this.mitigationStrategy = checkConfiguration.settings().mitigationStrategy();
  }

  /**
   * Performs a {@link User} lookup of a corresponding {@link Player}.
   * @param player the player search
   * @return a blank or corresponding user
   */
  protected User userOf(Player player) {
    return UserRepository.userOf(player);
  }

  /**
   * Append a {@link CheckPart} to the pool, making them affected by the {@link CheckService}
   * loading and unloading sequence. When appending check parts, it is definitely
   * not recommended that the implementation class holds <b>any</b> code for detection, so
   * the use of this method consequently constraints the class to follow the standard
   * of clear differentiation between <i>detection algorithm</i> and <i>detection cluster</i>.
   * @param checkPart the checkpart to append
   */
  protected void appendCheckPart(CheckPart<?> checkPart) {
    if (checkPart.parentCheck() != this) {
      throw new IntaveInternalException("Child lacks reference to parent");
    }
    checkParts.add(checkPart);
  }

  /**
   * Retrieves a trustfactor setting for a given key using the trustfactor of the given player.
   * @param key the trustfactor setting key
   * @param player the affected player
   * @return trustfactor setting
   */
  protected int trustFactorSetting(String key, Player player) {
    String checkKey = configurationKey + "." + key;
    return plugin.trustFactorService().trustFactorSetting(checkKey, player);
  }

  /**
   * Apply a change to the base statistics and all other statistics of abstract categories.
   * @param user the affected user
   * @param applier the player statistic applier
   */
  public void statisticApply(User user, Consumer<CheckStatistics> applier) {
    applier.accept(baseStatistics());
    applier.accept(statisticsFor(user.trustFactor()));
  }

  @Deprecated
  public CheckStatistics baseStatistics() {
    return statistics;
  }

  private CheckStatistics statisticsFor(TrustFactor trustFactor) {
    return perTrustFactorStatistics.computeIfAbsent(trustFactor, x -> new CheckStatistics());
  }

  /**
   * Retrieve the checks name.
   * @return the checks name
   */
  public String name() {
    return checkName;
  }

  /**
   * Retrieve the checks configuration key.
   * @return the checks configuration key
   */
  public String configurationKey() {
    return configurationKey;
  }

  /**
   * Access the checks configuration.
   * @return the checks configuraiton
   */
  public CheckConfiguration configuration() {
    return checkConfiguration;
  }

  /**
   * Retrieve a checks mitigation strategy
   * Will return {@link MitigationStrategy#NOT_SUPPORTED} when the child-class
   * does not support {@link MitigationStrategy}s.
   * @return the checks mitigation strategy
   */
  public MitigationStrategy mitigationStrategy() {
    if (mitigationStrategy == MitigationStrategy.NOT_SUPPORTED) {
      return mitigationStrategy = defaultMitigationStrategy;
    }
    return mitigationStrategy;
  }

  /**
   * Override the current mitigation strategy
   * @param mitigationStrategy the new mitigation strategy
   */
  public void setMitigationStrategy(MitigationStrategy mitigationStrategy) {
    this.mitigationStrategy = mitigationStrategy;
  }

  @Deprecated
  public void setDefaultMitigationStrategy(MitigationStrategy defaultMitigationStrategy) {
    this.defaultMitigationStrategy = defaultMitigationStrategy;
  }

  /**
   * Retrieve whether the check is enabled.
   * The {@link Physics} and {@link Timer} check always return {@code true}, as they can not be be disabled.
   * @return whether the check is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Returns whether the {@link CheckLinker} in the {@link CheckService},
   * should link the underlying {@link EventProcessor} and therefore whether all methods annotated with
   * {@link PacketSubscription} and {@link BukkitEventSubscription} should subscribe their corresponding frameworks.
   * The {@link InteractionRaytrace}, {@link Timer} and {@link Physics} check always returns {@code true}, as it *must* be linked.
   * @return whether internal subscriptions should be linked
   */
  public boolean performLinkage() {
    return enabled;
  }

  List<CheckPart<?>> checkParts() {
    return checkParts;
  }
}