package de.jpx3.intave.event;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.event.dispatch.*;
import de.jpx3.intave.event.feedback.FeedbackService;
import de.jpx3.intave.event.mitigate.CombatMitigator;
import de.jpx3.intave.event.mitigate.MovementEmulationEngine;
import de.jpx3.intave.event.mitigate.ReconDelayLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.tracker.entity.EntityNoCollisionService;
import de.jpx3.intave.module.tracker.entity.LazyEntityCollisionService;
import de.jpx3.intave.reflect.access.ReflectiveDataWatcherAccess;
import de.jpx3.intave.reflect.caller.CallerResolver;
import de.jpx3.intave.reflect.caller.PluginInvocation;
import de.jpx3.intave.tool.AccessHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLifetimeService;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import de.jpx3.intave.version.DurationTranslator;
import de.jpx3.intave.version.IntaveVersion;
import de.jpx3.intave.world.items.ItemProperties;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import static de.jpx3.intave.reflect.access.ReflectiveDataWatcherAccess.WATCHER_BLOCKING_ID;

public final class EventService implements BukkitEventSubscriber {
  private final static boolean DISABLE_ENTITY_COLLISIONS = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);

  private final IntavePlugin plugin;
  private FeedbackService feedbackService;
  private MovementEmulationEngine emulationEngine;
  private CombatMitigator combatMitigator;
  private ReconDelayLimiter reconDelayLimiter;

  public EventService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Deprecated
  public void setup() {
    this.feedbackService = new FeedbackService(plugin);
    this.emulationEngine = new MovementEmulationEngine(plugin);
    this.combatMitigator = new CombatMitigator(plugin);
    this.reconDelayLimiter = new ReconDelayLimiter(plugin);
    new UserLifetimeService(plugin);
    new AttackDispatcher(plugin);
    new AttributeTracker(plugin);
    new BlockActionDispatcher(plugin);
    new MovementDispatcher(plugin);
    new PotionEffectTracker(plugin);
    new PlayerAbilityTracker(plugin);
    new PlayerInventoryTracker(plugin);
    new LazyEntityCollisionService(plugin);
    new ConnectionHealthTelemetry(plugin);
    new PacketResynchronizer(plugin);
    if (DISABLE_ENTITY_COLLISIONS) {
      new EntityNoCollisionService(plugin);
    }

    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();

    boolean hasNotificationPermission = BukkitPermissionCheck.permissionCheck(player, "intave.command");
    if (!hasNotificationPermission) {
      return;
    }
    String currentVersion = IntavePlugin.version();
    IntaveVersion version = plugin.versions().versionInformation(currentVersion);
    if (version == null) {
      sendPrefixedMessage(ChatColor.YELLOW + "This server is running an unlisted version of Intave (" + currentVersion + ")", player);
      sendPrefixedMessage(ChatColor.YELLOW + "It is possible that bugs occur", player);
    } else {
      if (version.typeClassifier() == IntaveVersion.Status.OUTDATED) {
        long duration = AccessHelper.now() - version.release();
        String durationAsString = DurationTranslator.translateDuration(duration);

        sendPrefixedMessage(ChatColor.RED + "This server is running an outdated version of Intave ("+durationAsString+" old)", player);
        sendPrefixedMessage(ChatColor.RED + "Too lazy? Use IntaveBootstrap instead and stay up-to-date", player);
        sendPrefixedMessage(ChatColor.RED + "We hope you understand why updating your *security* software might be important.", player);
      }
    }
  }

  @BukkitEventSubscription
  public void on(PlayerTeleportEvent teleport) {
    if (IntaveControl.DEBUG_TELEPORT_CAUSE_AND_CAUSER) {
      PluginInvocation pluginInvocation = CallerResolver.callerPluginInfo();
      String pluginClass = pluginInvocation == null ? "no other plugin" : pluginInvocation.className();
      teleport.getPlayer().sendMessage("Teleport " + teleport.getCause() + " " + teleport.getTo() + " by " + pluginClass);
    }
  }

  @BukkitEventSubscription
  public void on(WorldUnloadEvent unloadEvent) {
    World world = unloadEvent.getWorld();
    GarbageCollector.clear(world);
    GarbageCollector.clearIf(o -> o instanceof Location && ((Location) o).getWorld().equals(world));
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    Player player = quit.getPlayer();
    GarbageCollector.clear(player);
    GarbageCollector.clear(player.getUniqueId());
  }

  /*
   * fixes a bug where players drop their sword whilst blocking, tricking the server into letting them constantly block - even whilst attacking
   */
  @BukkitEventSubscription(ignoreCancelled = true)
  public void on(PlayerDropItemEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (user.meta().inventory().handActive() && ItemProperties.isSwordItem(player.getItemOnCursor()) && !ViaVersionAdapter.ignoreBlocking(user.player())) {
      Synchronizer.synchronize(() -> ReflectiveDataWatcherAccess.setDataWatcherFlag(player, WATCHER_BLOCKING_ID, false));
    }
  }

  private void sendPrefixedMessage(String message, CommandSender target) {
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronize(() -> sendPrefixedMessage(message, target));
      return;
    }
    target.sendMessage(IntavePlugin.prefix() + message);
  }

  @Deprecated
  public ReconDelayLimiter reconDelayLimiter() {
    return reconDelayLimiter;
  }

  @Deprecated
  public MovementEmulationEngine emulationEngine() {
    return emulationEngine;
  }

  @Deprecated
  public FeedbackService feedback() {
    return feedbackService;
  }

  @Deprecated
  public CombatMitigator combatMitigator() {
    return combatMitigator;
  }
}