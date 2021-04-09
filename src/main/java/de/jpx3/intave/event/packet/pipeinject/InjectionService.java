package de.jpx3.intave.event.packet.pipeinject;

import com.comphenix.protocol.PacketType;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.LocalPacketAdapter;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.tools.annotate.Native;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class InjectionService implements EventProcessor {
  private final static String injectorClassName;
  static {
    String handler = "de.jpx3.intave.event.packet.pipeinject.v8PipelineHandler";
    String injector = "de.jpx3.intave.event.packet.pipeinject.v8PipelineInjector";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), handler);
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), injector);
    injectorClassName = injector;
  }

  private final Map<PacketType, Collection<LocalPacketAdapter>> packetListeners = new HashMap<>();

  private final IntavePlugin plugin;
  private final PipelineInjector pipelineInjector;

  public InjectionService(IntavePlugin plugin) {
    this.plugin = plugin;
//    this.plugin.eventLinker().registerEventsIn(this);
    this.pipelineInjector = loadInjector();
//    injectAll();
  }

  @Native
  private <T> T loadInjector() {
    try {
      //noinspection unchecked
      return (T) Class.forName(injectorClassName).getConstructor(InjectionService.class).newInstance(this);
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException exception) {
      throw new IntaveInternalException(exception);
    }
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
    pipelineInjector.inject(player);
  }

  public void uninjectAll() {
    Bukkit.getOnlinePlayers().forEach(this::uninject);
  }

  public void uninject(Player player) {
    pipelineInjector.uninject(player);
  }
}
