package de.jpx3.intave.trustfactor;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.*;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.diagnostic.ConsoleOutput;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageCategory;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.library.Python;
import de.jpx3.intave.module.Modules;
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
  private static final TrustFactorResolver DEFAULT_RESOLVER = new DefaultForwardingPermissionTrustFactorResolver(new EmptyTrustFactorResolver());
  private static final TrustFactorResolver AUTO_STORAGE_RESOLVER = new DefaultForwardingPermissionTrustFactorResolver(
    Python.available() ? new DynamicStorageTrustfactorResolver() : new StorageTrustfactorResolver()
  );
  private final IntavePlugin plugin;
  private TrustFactorResolver trustFactorResolver;
  private TrustFactorConfiguration trustFactorConfiguration;
  private TrustFactor defaultTrustFactor = TrustFactor.ORANGE;

  public TrustFactorService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    TrustFactorLoader trustFactorLoader = IntaveControl.USE_DEBUG_TRUSTFACTOR_RESOURCE ? new DebugYamlTrustFactorLoader() : new InternetYamlTrustFactorLoader();
    trustFactorConfiguration = trustFactorLoader.fetch();
    trustFactorResolver = DEFAULT_RESOLVER;

    plugin.eventLinker().registerEventsIn(this);
    Synchronizer.synchronize(() -> BackgroundExecutor.execute(this::resolveTrustFactorForAll));
  }

  @BukkitEventSubscription(priority = EventPriority.NORMAL)
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    BackgroundExecutor.execute(() -> resolveTrustFactorFor(player));
  }

  private void resolveTrustFactorForAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      resolveTrustFactorFor(onlinePlayer);
    }
  }

  private boolean hasEnabledAutoStorageTrustFactor() {
    return IntavePlugin.singletonInstance().settings().getBoolean("storage.auto-trustfactor", true);
  }

  private void resolveTrustFactorFor(Player player) {
    User user = UserRepository.userOf(player);
    user.setTrustFactor(defaultTrustFactor);
    if (IntaveControl.APPLY_GLOBAL_LOW_TRUSTFACTOR) {
      trustfactorApply(player, TrustFactor.RED, "Global low trustfactor setting");
      return;
    }
    if (trustFactorResolver == null) {
      trustFactorResolver = DEFAULT_RESOLVER;
    }
    if (trustFactorResolver == DEFAULT_RESOLVER && Modules.storage().hasStorageGateway() && hasEnabledAutoStorageTrustFactor()) {
      trustFactorResolver = AUTO_STORAGE_RESOLVER;
    }
    trustFactorResolver.resolve(
      player, (trustFactor) -> trustfactorApply(player, trustFactor, trustFactorResolver.toString())
    );
  }

  private void trustfactorApply(Player player, TrustFactor trustFactor, String source) {
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
    User user = UserRepository.userOf(player);
    user.setTrustFactor(trustFactor);

    if (ConsoleOutput.TRUSTFACTOR_DEBUG) {
      String message2 = ChatColor.RED + player.getName() + IntavePlugin.defaultColor() + " was assigned a " + trustFactor.coloredBaseName() + IntavePlugin.defaultColor() + " trustfactor by " + source;
      IntaveLogger.logger().info(message2);
    }
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

  public void setTrustFactorResolver(TrustFactorResolver trustFactorResolver) {
    this.trustFactorResolver = trustFactorResolver;
  }

  public TrustFactorConfiguration trustFactorConfiguration() {
    return trustFactorConfiguration;
  }
}
