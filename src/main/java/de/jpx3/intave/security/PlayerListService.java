package de.jpx3.intave.security;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.HighOrderService;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

@HighOrderService
public final class PlayerListService implements BukkitEventSubscriber {
  private final IntavePlugin plugin;
  private final Resource blacklistResource = Resources.localServiceCacheResource("blacklist", "abx-blacklist", TimeUnit.DAYS.toMillis(7));
  private final Resource graylistResource = Resources.localServiceCacheResource("graylist", "xgl-graylist", TimeUnit.DAYS.toMillis(7));
  private final Resource graylistKnowledgeResource = Resources.fileCache("gaf-graylist-log");
  private final Resource bluelistKnowledgeResource = Resources.fileCache("rax-bluelist-log");
  private final List<String> bluelistKnowledge = new ArrayList<>();
  private final List<String> graylistKnowledge = new ArrayList<>();
  private final Set<InetAddress> blocked = new HashSet<>();
  private String kickMessage;
  private boolean messageInChat;
  private HashList blackList, grayList;

  public PlayerListService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    try {
      loadFilterList();
      linkEvents();
      applyFilterToOnline();
      kickMessage = plugin.settings().getString("blacklist.kick-message", "&cYou are on an anti-cheat blacklist and can't join this server");
      kickMessage = ChatColor.translateAlternateColorCodes('&', kickMessage);
      messageInChat = plugin.settings().getBoolean("blacklist.message-in-chat", false);
      ShutdownTasks.add(this::saveGraylistKnowledgeToResource);
      ShutdownTasks.add(this::saveBluelistKnowledgeToResource);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }

  public String grayKnowledgeData() {
    return graylistKnowledgeResource.readAsString();
  }

  public String blueKnowledgeData() {
    return bluelistKnowledgeResource.readAsString();
  }

  public void saveGraylistKnowledgeToResource() {
    List<String> strings;
    if (graylistKnowledgeResource.available()) {
      strings = graylistKnowledgeResource.readLines();
    } else {
      strings = new ArrayList<>();
    }
    for (String id : graylistKnowledge) {
      if (!strings.contains(id)) {
        strings.add(id);
      }
    }
    graylistKnowledge.clear();
    graylistKnowledgeResource.write(strings);
  }

  public void saveBluelistKnowledgeToResource() {
    List<String> strings;
    if (bluelistKnowledgeResource.available()) {
      strings = bluelistKnowledgeResource.readLines();
    } else {
      strings = new ArrayList<>();
    }
    for (String id : bluelistKnowledge) {
      if (!strings.contains(id)) {
        strings.add(id);
      }
    }
    bluelistKnowledge.clear();
    bluelistKnowledgeResource.write(strings);
  }

  private void loadFilterList() {
    blackList = HashList.from(blacklistResource);
    grayList = HashList.from(graylistResource);
  }

  private void linkEvents() {
    plugin.eventLinker().registerEventsIn(this);
  }

  private void applyFilterToOnline() {
    Bukkit.getOnlinePlayers().stream().filter(this::blacklisted).forEach(this::synchronizedDisconnect);
  }

  private void synchronizedDisconnect(Player player) {
    if (!enabled()) {
      return;
    }
    Synchronizer.synchronize(() -> disconnect(player));
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    if (blacklisted(player) && enabled()) {
      disconnect(player);
    } else if (graylisted(player)) {
      graylistEvent(player);
      saveGraylistKnowledgeToResource();
    }
    if ((ProtocolMetadata.MARKED_FOR_PLAYER_REPORT & 128) != 0 || IntaveControl.DEBUG_BLUELIST) {
      bluelistEvent(player);
      saveBluelistKnowledgeToResource();
    }
  }

  private void graylistEvent(Player player) {
    if (graylistKnowledge.contains(player.getUniqueId().toString())) {
      return;
    }
    // let's not risk it
//    if (DEBUG_GRAYLIST) {
//      player.sendMessage(ChatColor.RED + "You are graylisted.");
//    }
    graylistKnowledge.add(player.getUniqueId().toString());
  }

  private void bluelistEvent(Player player) {
    if (bluelistKnowledge.contains(player.getUniqueId().toString())) {
      return;
    }
    // let's not risk it
//    if (DEBUG_GRAYLIST) {
//      player.sendMessage(ChatColor.RED + "You are graylisted.");
//    }
    bluelistKnowledge.add(player.getUniqueId().toString());
  }


  private boolean blacklisted(Player player) {
    String name = player.getName();
    UUID id = player.getUniqueId();
    return blackList != null && (blackList.containsName(name) || blackList.containsId(id)) || blocked.contains(player.getAddress().getAddress());
  }

  private boolean graylisted(Player player) {
    String name = player.getName();
    UUID id = player.getUniqueId();
    return grayList != null && (grayList.containsName(name) || grayList.containsId(id));
  }

//  private static final String KICK_MESSAGE = ChatColor.RED + "You are on an anti-cheat blacklist and can't join this server";

  private void disconnect(Player player) {
    if (!enabled()) {
      return;
    }
    blocked.add(player.getAddress().getAddress());
    Synchronizer.synchronize(() -> {
      if (messageInChat) {
        player.sendMessage(kickMessage);
        Synchronizer.synchronizeDelayed(() ->
          player.kickPlayer(""), 5
        );
      } else {
        player.kickPlayer(kickMessage);
      }
    });
  }

  public boolean enabled() {
    return plugin.settings().getBoolean("blacklist.apply");
  }
}
