package de.jpx3.intave.security.blacklist;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.resource.CachedResource;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@HighOrderService
public final class BlackListService implements BukkitEventSubscriber {
  private final IntavePlugin plugin;
  private final CachedResource resource = new CachedResource("blacklist", "https://service.intave.de/blacklist", TimeUnit.DAYS.toMillis(7));
  private BlackList blackList;

  public BlackListService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    if(!enabled()) {
      return;
    }
    try {
      loadFilterList();
      linkEvents();
      applyFilterToOnline();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  private void loadFilterList() {
    resource.prepareFile();
    blackList = BlackList.fromInputStream(resource.read());
  }

  private void linkEvents() {
    plugin.eventLinker().registerEventsIn(this);
  }

  private void applyFilterToOnline() {
    Bukkit.getOnlinePlayers().stream().filter(this::blacklisted).forEach(this::synchronizedDisconnect);
  }

  private void synchronizedDisconnect(Player player) {
    Synchronizer.synchronize(() -> disconnect(player));
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    if(blacklisted(player)) {
      disconnect(player);
    }
  }

  private boolean blacklisted(Player player) {
    String name = player.getName();
    UUID id = player.getUniqueId();
    return blackList != null && (blackList.nameBlacklisted(name) || blackList.idBlacklisted(id));
  }

  private final static String KICK_MESSAGE = ChatColor.RED + "You can't join this server";

  private void disconnect(Player player) {
    player.kickPlayer(KICK_MESSAGE);
  }

  public boolean enabled() {
    return plugin.configurationService().configuration().getBoolean("blacklist.enable");
  }
}
