package de.jpx3.intave.trustfactor;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.DefaultForwardingPermissionTrustFactorResolver;
import de.jpx3.intave.access.TrustFactor;
import de.jpx3.intave.access.TrustFactorResolver;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

public final class TrustFactorService implements BukkitEventSubscriber {

  private final IntavePlugin plugin;
  private TrustFactorResolver trustFactorResolver;
  private TrustFactorConfiguration trustFactorConfiguration;
  private TrustFactor defaultTrustFactor = TrustFactor.ORANGE;

  public TrustFactorService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    TrustFactorLoader trustFactorLoader = IntaveControl.USE_DEBUG_RESOURCES ? new DefaultTrustFactorLoader() : new DownloadingTrustFactorLoader();
    trustFactorConfiguration = trustFactorLoader.fetch();
    trustFactorResolver = new DefaultForwardingPermissionTrustFactorResolver(new DefaultTrustFactorResolver());

    plugin.eventLinker().registerEventsIn(this);
    Synchronizer.synchronize(() -> BackgroundExecutor.execute(this::resolveTrustFactorForAll));
  }

  @BukkitEventSubscription(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    Synchronizer.synchronize(() -> {
      if(!player.isOnline()) {
        return;
      }
      BackgroundExecutor.execute(() -> resolveTrustFactorFor(player));
    });
  }

  private void resolveTrustFactorForAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      resolveTrustFactorFor(onlinePlayer);
    }
  }

  private void resolveTrustFactorFor(Player player) {
    User user = UserRepository.userOf(player);
    user.setTrustFactor(defaultTrustFactor);

    if(IntaveControl.APPLY_LOWEST_TRUSTFACTOR) {
      user.setTrustFactor(TrustFactor.DARK_RED);
      return;
    }
    trustFactorResolver.lazyResolve(player,
      trustFactor -> {
        IntavePlugin.singletonInstance().logger().info("Assigned trust factor " + trustFactor + " to " + (user.hasOnlinePlayer() ? user.player().getName() : "null"));
        user.setTrustFactor(trustFactor);
      }
    );
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
