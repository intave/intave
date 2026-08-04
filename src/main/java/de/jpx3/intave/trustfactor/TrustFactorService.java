package de.jpx3.intave.trustfactor;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.DefaultForwardingPermissionTrustFactorResolver;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.access.player.trust.TrustFactorResolver;
import de.jpx3.intave.access.player.trust.StorageTrustfactorResolver;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.cleanup.StartupTasks;
import de.jpx3.intave.config.ConfiguredFlag;
import de.jpx3.intave.connect.cloud.LogTransmittor;
import de.jpx3.intave.diagnostic.ConsoleOutput;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageCategory;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

@HighOrderService
public final class TrustFactorService implements BukkitEventSubscriber {
  /**
   * Option deciding what a player without any {@code intave.trust.*} permission gets.
   * <p>
   * On: their trust factor is derived from their own history — playtime, joins and past
   * violations ({@link StorageTrustfactorResolver}). Off: nothing is issued and they keep
   * {@link #defaultTrustFactor}, which is what this did before the option existed.
   */
  public static final String AUTO_TRUST_FACTOR_PATH = "trustfactor.automatic";

  private static final ConfiguredFlag AUTO_TRUST_FACTOR = new ConfiguredFlag(AUTO_TRUST_FACTOR_PATH, true);

  private static TrustFactorResolver defaultResolver() {
    // An explicit permission always wins; the fallback only answers for players who have
    // none, so granting intave.trust.* keeps overriding whatever the history says.
    return new DefaultForwardingPermissionTrustFactorResolver(
      AUTO_TRUST_FACTOR.enabled()
        ? new StorageTrustfactorResolver()
        : new EmptyTrustFactorResolver()
    );
  }

	private final IntavePlugin plugin;
  private TrustFactorResolver trustFactorResolver, customTrustFactorResolver;
  private TrustFactorConfiguration trustFactorConfiguration;
  private TrustFactor defaultTrustFactor = TrustFactor.ORANGE;

  public TrustFactorService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    TrustFactorLoader trustFactorLoader = IntaveControl.USE_DEBUG_TRUSTFACTOR_RESOURCE ? new DebugYamlTrustFactorLoader() : new InternetYamlTrustFactorLoader();
    trustFactorConfiguration = trustFactorLoader.fetch();

    if (floodgatePresent()) {
      trustFactorResolver = new GeyserTrustFactorResolver(defaultResolver());
    } else {
      trustFactorResolver = defaultResolver();
    }

    plugin.eventLinker().registerEventsIn(this);
    StartupTasks.add(() -> BackgroundExecutors.execute(this::resolveTrustFactorForAll));
  }

  private boolean floodgatePresent() {
    try {
      Class.forName("org.geysermc.floodgate.api.FloodgateApi");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @BukkitEventSubscription(priority = EventPriority.NORMAL)
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    BackgroundExecutors.execute(() -> resolveTrustFactorFor(player));
  }

  private void resolveTrustFactorForAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      resolveTrustFactorFor(onlinePlayer);
    }
  }

  private void resolveTrustFactorFor(Player player) {
    User user = UserRepository.userOf(player);
    user.setTrustFactor(defaultTrustFactor);
    if (IntaveControl.APPLY_GLOBAL_LOW_TRUSTFACTOR) {
      trustfactorApply(player, TrustFactor.RED, "Global low trustfactor setting");
      return;
    }
    if (trustFactorResolver == null) {
      trustFactorResolver = defaultResolver();
    }
    trustFactorResolver.resolve(
      player, (trustFactor) -> trustfactorApply(player, trustFactor, trustFactorResolver.toString())
    );
  }

  private void trustfactorApply(Player player, TrustFactor trustFactor, String source) {
    User user = UserRepository.userOf(player);

    if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      String playerName = player.getName();
      String message = source + " tried to assign trust factor " + trustFactor.coloredBaseName() + IntavePlugin.defaultColor() + " to " + ChatColor.RED + playerName + IntavePlugin.defaultColor() + " but BYPASS is active.";
      String shortMessage = playerName + " now " + trustFactor.coloredBaseName();
      DebugBroadcast.broadcast(
        player,
        MessageCategory.TRUSTSET,
        MessageSeverity.LOW,
        message,
        shortMessage
      );
      if (ConsoleOutput.TRUSTFACTOR_DEBUG) {
        String message2 = ChatColor.RED + player.getName() + IntavePlugin.defaultColor() + " was assigned a " + trustFactor.coloredBaseName() + IntavePlugin.defaultColor() + " trustfactor by " + source + " but BYPASS is active and kept.";
        IntaveLogger.logger().info(message2);
      }
      return;
    }

    user.setTrustFactor(trustFactor);

    String playerName = player.getName();
    String message = source + " assigned trust factor " + trustFactor.coloredBaseName() + IntavePlugin.defaultColor() + " to " + ChatColor.RED + playerName;
    String shortMessage = playerName + " now " + trustFactor.coloredBaseName();
    DebugBroadcast.broadcast(
      player,
      MessageCategory.TRUSTSET,
      MessageSeverity.LOW,
      message,
      shortMessage
    );

    if (ConsoleOutput.TRUSTFACTOR_DEBUG) {
      String message2 = ChatColor.RED + player.getName() + IntavePlugin.defaultColor() + " was assigned a " + trustFactor.coloredBaseName() + IntavePlugin.defaultColor() + " trustfactor by " + source;
      IntaveLogger.logger().info(message2);
    }

    LogTransmittor logTransmittor = IntavePlugin.singletonInstance().logTransmittor();
    logTransmittor.addPlayerLog(player, "(TRUST) " + message);
  }

  public int trustFactorSetting(String key, Player player) {
    return trustFactorConfiguration.resolveSetting(key, UserRepository.userOf(player).trustFactor());
  }

  public TrustFactor defaultTrustFactor() {
    return defaultTrustFactor;
  }

  public void setDefaultTrustFactor(TrustFactor defaultTrustFactor) {
    this.defaultTrustFactor = defaultTrustFactor;
  }

  public TrustFactorResolver trustFactorResolver() {
    return trustFactorResolver;
  }

  public void setDirectTrustFactorResolver(TrustFactorResolver trustFactorResolver) {
    this.trustFactorResolver = trustFactorResolver;
  }

  public void setCustomTrustFactorResolver(TrustFactorResolver trustFactorResolver) {
    this.trustFactorResolver = trustFactorResolver;
    this.customTrustFactorResolver = trustFactorResolver;
  }

  public TrustFactorResolver customTrustFactorResolver() {
    return customTrustFactorResolver;
  }

  public TrustFactorConfiguration trustFactorConfiguration() {
    return trustFactorConfiguration;
  }
}
