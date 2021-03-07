package de.jpx3.intave.event.context;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.placeholder.Placeholders;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReconDelayLimiter implements BukkitEventSubscriber {
  private final IntavePlugin plugin;

  private final Map<UUID, Long> lastKicked = new ConcurrentHashMap<>();
  private final Map<InetAddress, Long> lastKickedIp = new ConcurrentHashMap<>();

  private long delay;
  private boolean refresh;
  private String rawMessage;

  public ReconDelayLimiter(IntavePlugin plugin) {
    this.plugin = plugin;

    setup();
  }

  public void setup() {
    YamlConfiguration config = plugin.configurationService().configuration();

    delay = config.getInt("rejoin.delay") * 50L;
    refresh = config.getBoolean("rejoin.refresh");
    rawMessage = config.getString("rejoin.message");

    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void on(AsyncPlayerPreLoginEvent e) {
    long ipDelayLeft = AccessHelper.now() - lastKicked.getOrDefault(e.getUniqueId(), 0L);
    long accDelayLeft = AccessHelper.now() - lastKickedIp.getOrDefault(e.getAddress(), 0L);

    if (ipDelayLeft < delay || accDelayLeft < delay) {
      String message = rawMessage;
      message = Placeholders.replacePlaceholders(message, Placeholders.PLUGIN_CONTEXT);
      message = ChatColor.translateAlternateColorCodes('&', message);
      e.setKickMessage(message);
      e.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST);
      if(refresh) {
        ban(e.getAddress(), e.getUniqueId(), "rejoin");
      }
    }
  }

  public void ban(InetAddress address, UUID uuid, String check) {
    if (!address.getHostAddress().contains("127.0.0.1")) {
      lastKickedIp.put(address, AccessHelper.now());
    }
    lastKicked.put(uuid, AccessHelper.now());
  }
}
