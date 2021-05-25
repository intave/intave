package de.jpx3.intave.event.packet.tinyprotocol;

import com.comphenix.protocol.PacketType;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.LocalPacketAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InjectionService implements EventProcessor {
  private final Map<PacketType, Collection<LocalPacketAdapter>> packetListeners = new ConcurrentHashMap<>();

  private final IntavePlugin plugin;
  private final TinyProtocol pipelineInjector;

  public InjectionService(IntavePlugin plugin) {
    this.plugin = plugin;
    this.pipelineInjector = new EventTinyProtocol(plugin, this);
//    if (MinecraftVersions.VER1_12_0.atOrAbove()) {
//      return;
//    }
    injectAll();
    this.plugin.eventLinker().registerEventsIn(this);
  }

  protected Collection<LocalPacketAdapter> subscriptionsOf(PacketType type) {
    return packetListeners.get(type);
  }

  public void setupSubscriptions(PacketType type, Collection<LocalPacketAdapter> listeners) {
    packetListeners.put(type, listeners);
  }

  public void reset() {
    packetListeners.clear();
  }

  @BukkitEventSubscription
  public void onJoin(PlayerJoinEvent join) {
    inject(join.getPlayer());
  }

  @BukkitEventSubscription
  public void onQuit(PlayerQuitEvent quit) {
    uninject(quit.getPlayer());
  }

  public void injectAll() {
    Bukkit.getOnlinePlayers().forEach(this::inject);
  }

  public void inject(Player player) {
    if(!pipelineInjector.hasInjected(player)) {
      pipelineInjector.injectPlayer(player);
    }
  }

  public void uninjectAll() {
    Bukkit.getOnlinePlayers().forEach(this::uninject);
  }

  public void uninject(Player player) {
//    if(pipelineInjector.hasInjected(player)) {
//      pipelineInjector.uninjectPlayer(player);
//    }
  }
}
