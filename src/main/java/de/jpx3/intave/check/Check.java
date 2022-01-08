package de.jpx3.intave.check;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.check.MitigationStrategy;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.check.movement.Physics;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * A {@link Check} provides a skeletal architecture for both detection algorithms and detection clusters.<br>
 * It is stored, linked, unlinked and deleted by a {@link CheckService}, where it is
 * made accessible to external modules. <br> All instances of implementation classes must be singleton,
 * only to be addressed and distinguished by their {@code class}.
 * They hold a unique name in {@link String} that must equal their original classname, a unique configuration key, an enabled variant,
 * a {@link CheckConfiguration} and {@link CheckStatistics}.
 * <br>
 * <br>
 * Instances of the class hold subordinate {@link CheckPart}s, as well as an {@link Check#appendCheckPart(CheckPart)}
 * method to append {@link CheckPart}s to themself.
 * A {@link Check} is either a <i>detection algorithm</i> itself or serves as a <i>detection cluster</i>.
 * Once a single {@link CheckPart} is added, the {@link Check} must become a <i>detection cluster</i> and must not contain code for detection though it still can (and probably should)
 * have code to interpret, delay, contextualize or analyse the gathered data, as long as it comes from the held {@link CheckPart}s.
 * <br>
 * <br>
 *
 * @see de.jpx3.intave.check.CheckPart
 * @see de.jpx3.intave.check.CheckService
 * @see de.jpx3.intave.check.CheckStatistics
 * @see de.jpx3.intave.check.CheckConfiguration
 * @see de.jpx3.intave.check.MetaCheck
 * @see de.jpx3.intave.check.MetaCheckPart
 */
 public abstract class Check implements EventProcessor {
  private final IntavePlugin plugin;
  private final String checkName;
  private final String configurationKey;
  private final CheckStatistics statistics = new CheckStatistics();
  private final Map<TrustFactor, CheckStatistics> perTrustFactorStatistics = new EnumMap<>(TrustFactor.class);
  private final List<CheckPart<?>> checkParts = new ArrayList<>();
  private final CheckConfiguration checkConfiguration = new CheckConfiguration(this);
  private final boolean enabled;
  private MitigationStrategy mitigationStrategy;
  private MitigationStrategy defaultMitigationStrategy = MitigationStrategy.NOT_SUPPORTED;

  public Check(String checkName, String configurationKey) {
    this.plugin = IntavePlugin.singletonInstance();
    this.checkName = checkName;
    this.configurationKey = configurationKey;
    this.enterConfiguration();
    this.enabled = checkConfiguration.settings().checkEnabled();
    this.mitigationStrategy = checkConfiguration.settings().mitigationStrategy();
  }

  private void enterConfiguration() {
    YamlConfiguration configuration = plugin.configurationService().configuration();
    String checkSectionPath = "check." + configurationKey();
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
   * loading and unloading sequence. When appending check parts, the implementation class must not
   * hold <b>any</b> code for detection, so the use of this method consequently constraints the class
   * to follow the standard of clear differentiation between <i>detection algorithm</i> and <i>detection cluster</i>.
   * @param checkPart the checkpart to append
   */
  protected void appendCheckPart(CheckPart<?> checkPart) {
    if (checkPart.parentCheck() != this) {
      throw new IntaveInternalException("Partial check lacks correct reference to parent check");
    }
    checkParts.add(checkPart);
  }

  protected void appendCheckParts(CheckPart<?>... checkParts) {
    for (CheckPart<?> checkPart : checkParts) {
      appendCheckPart(checkPart);
    }
  }

  /**
   * Retrieves a {@link TrustFactor} setting for a given key using the trustfactor of the given {@link Player}.
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
   * Retrieve a checks mitigation strategy.
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
   * Override the current mitigation strategy.
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
   * The {@link Physics} and {@link Timer} check override this method to always return {@code true},
   * as they must be enabled and therefore can't be disabled.
   * @return whether the check is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Returns whether the {@link CheckLinker} in the {@link CheckService}
   * should link the underlying {@link EventProcessor} and therefore whether all methods annotated with
   * {@link PacketSubscription} and {@link BukkitEventSubscription} should subscribe their corresponding frameworks.
   * The {@link InteractionRaytrace}, {@link Timer} and {@link Physics} check override this method to always return
   * {@code true}, as they are intrinsically required to be linked.
   * @return whether internal subscriptions should be linked
   */
  public boolean performLinkage() {
    return enabled;
  }

  List<CheckPart<?>> checkParts() {
    return checkParts;
  }
}