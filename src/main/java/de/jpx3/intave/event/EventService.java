package de.jpx3.intave.event;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.clazz.trace.Caller;
import de.jpx3.intave.clazz.trace.PluginInvocation;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.entity.datawatcher.DataWatcherAccess;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackSender;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.tracker.entity.EntityCollisionDisabler;
import de.jpx3.intave.module.tracker.entity.LazyEntityCollisionService;
import de.jpx3.intave.player.ItemProperties;
import de.jpx3.intave.player.dmc.DamageController;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLifetimeService;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import de.jpx3.intave.version.DurationTranslator;
import de.jpx3.intave.version.IntaveVersion;
import de.jpx3.intave.violation.mitigate.CombatMitigator;
import de.jpx3.intave.violation.mitigate.MovementEmulator;
import de.jpx3.intave.violation.mitigate.ReconDelayLimiter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import static de.jpx3.intave.entity.datawatcher.DataWatcherAccess.WATCHER_BLOCKING_ID;
import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING;

@Deprecated
public final class EventService implements BukkitEventSubscriber {
  private final static boolean DISABLE_ENTITY_COLLISIONS = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);

  private final IntavePlugin plugin;
  private MovementEmulator emulationEngine;
  private CombatMitigator combatMitigator;
  private ReconDelayLimiter reconDelayLimiter;

  public EventService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Deprecated
  public void setup() {
    this.emulationEngine = new MovementEmulator(plugin);
    this.combatMitigator = new CombatMitigator(plugin);
    this.reconDelayLimiter = new ReconDelayLimiter(plugin);
    new UserLifetimeService(plugin);
    new LazyEntityCollisionService(plugin);
    new ConnectionHealthTelemetry(plugin);
    if (DISABLE_ENTITY_COLLISIONS) {
      new EntityCollisionDisabler(plugin);
    }

    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  @IdoNotBelongHere
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
        long duration = System.currentTimeMillis() - version.release();
        String durationAsString = DurationTranslator.translateDuration(duration);

        sendPrefixedMessage(ChatColor.RED + "This server is running an outdated version of Intave ("+durationAsString+" old)", player);
        if (!Bukkit.getPluginManager().isPluginEnabled("IntaveBootstrap")) {
          sendPrefixedMessage(ChatColor.RED + "Too lazy? Stay up-to-date automatically with IntaveBootstrap", player);
        }
        sendPrefixedMessage(ChatColor.RED + "We hope you understand why updating your *security* software might be important.", player);
      }
    }
  }

  @BukkitEventSubscription
  @IdoNotBelongHere
  public void on(PlayerTeleportEvent teleport) {
    if (IntaveControl.DEBUG_TELEPORT_CAUSE_AND_CAUSER) {
      PluginInvocation pluginInvocation = Caller.pluginInfo();
      String pluginClass = pluginInvocation == null ? "no other plugin" : pluginInvocation.className();
      teleport.getPlayer().sendMessage("Teleport " + teleport.getCause() + " " + teleport.getTo() + " by " + pluginClass);
    }
  }

  @BukkitEventSubscription
  @IdoNotBelongHere
  public void on(WorldUnloadEvent unloadEvent) {
    World world = unloadEvent.getWorld();
    GarbageCollector.clear(world);
    GarbageCollector.clear(world.getUID());
    GarbageCollector.clearIf(o -> o instanceof Location && ((Location) o).getWorld().equals(world));
  }

  @BukkitEventSubscription
  @IdoNotBelongHere
  public void on(PlayerQuitEvent quit) {
    Player player = quit.getPlayer();
    GarbageCollector.clear(player);
    GarbageCollector.clear(player.getUniqueId());
  }

  /*
   * fixes a bug where players drop their sword whilst blocking, tricking the server into letting them constantly block - even whilst attacking
   */
  @BukkitEventSubscription(ignoreCancelled = true)
  @IdoNotBelongHere
  public void on(PlayerDropItemEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (/*user.meta().inventory().handActive() && */ItemProperties.isSwordItem(player.getItemOnCursor()) && !ViaVersionAdapter.ignoreBlocking(user.player())) {
      Synchronizer.synchronize(() -> {
        DataWatcherAccess.setDataWatcherFlag(player, WATCHER_BLOCKING_ID, false);
//        player.sendMessage(ReflectiveDataWatcherAccess.getDataWatcherFlag(player, WATCHER_BLOCKING_ID) + "");
      });
    }
  }

  @BukkitEventSubscription
  public void on(EntityDamageByEntityEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      return;
    }
    Entity attacked = event.getEntity();
    if (!(attacked instanceof Player)) {
      return;
    }
    Player attackedPlayer = (Player) attacked;
    User user = UserRepository.userOf(attackedPlayer);
    double blockingDamageAbsorption = event.getDamage(BLOCKING);
    if (blockingDamageAbsorption < 0 && !user.meta().inventory().handActive()) {
      DamageController.withNewDamageApplier(event, BLOCKING, current -> -0d);
    }
  }

  @BukkitEventSubscription
  public void on(EntityShootBowEvent event) {
    if (!(event.getEntity() instanceof Player)) {
      return;
    }
    User user = UserRepository.userOf((Player) event.getEntity());
    InventoryMetadata inventory = user.meta().inventory();
    if (inventory.blockNextArrow && !inventory.handActive()) {
      event.setCancelled(true);
      inventory.blockNextArrow = false;
    }
  }

  @IdoNotBelongHere
  private void sendPrefixedMessage(String message, CommandSender target) {
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronize(() -> sendPrefixedMessage(message, target));
      return;
    }
    target.sendMessage(IntavePlugin.prefix() + message);
  }

  @Deprecated
  @IdoNotBelongHere
  public ReconDelayLimiter reconDelayLimiter() {
    return reconDelayLimiter;
  }

  @Deprecated
  @IdoNotBelongHere
  public MovementEmulator emulationEngine() {
    return emulationEngine;
  }

  @Deprecated
  @IdoNotBelongHere
  public FeedbackSender feedback() {
//    return feedbackService;
    return Modules.find(FeedbackSender.class);
  }

  @Deprecated
  @IdoNotBelongHere
  public CombatMitigator combatMitigator() {
    return combatMitigator;
  }
}