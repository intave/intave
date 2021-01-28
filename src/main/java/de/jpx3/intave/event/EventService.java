package de.jpx3.intave.event;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.event.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.dispatch.*;
import de.jpx3.intave.event.service.ConnectionHealthResolver;
import de.jpx3.intave.event.service.MovementEmulationEngine;
import de.jpx3.intave.event.service.TransactionFeedbackService;
import de.jpx3.intave.event.service.entity.ClientSideEntityService;
import de.jpx3.intave.permission.PermissionCheck;
import de.jpx3.intave.reflect.caller.CallerResolver;
import de.jpx3.intave.reflect.caller.PluginInvocation;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.DurationTranslator;
import de.jpx3.intave.update.VersionInformation;
import de.jpx3.intave.user.UserRepositoryEventListener;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class EventService implements BukkitEventSubscriber {
  private final IntavePlugin plugin;
  private TransactionFeedbackService transactionFeedbackService;
  private MovementEmulationEngine emulationEngine;

  public EventService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    this.transactionFeedbackService = new TransactionFeedbackService(plugin);
    this.emulationEngine = new MovementEmulationEngine(plugin);
    new UserRepositoryEventListener(plugin);
    new AttackDispatcher(plugin);
    new BlockActionDispatcher(plugin);
    new MovementDispatcher(plugin);
    new PotionEffectEvaluator(plugin);
    new PlayerAbilityEvaluator(plugin);
    new PlayerInventoryEvaluator(plugin);
    new ClientSideEntityService(plugin);
    new ConnectionHealthResolver(plugin);

    plugin.eventLinker().registerEventsIn(this);
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    boolean hasNotificationPermission = PermissionCheck.permissionCheck(player, "intave.command");

    if (!hasNotificationPermission) {
      return;
    }

    String currentVersion = IntavePlugin.version();
    VersionInformation versionInformation = plugin.versionList().versionInformation(currentVersion);

    if(versionInformation == null) {
      sendPrefixedMessage(ChatColor.YELLOW + "This server is running an experimental version of Intave (" + currentVersion + ")", player);
      sendPrefixedMessage(ChatColor.YELLOW + "It is possible that bugs occur", player);
    } else {
      if(versionInformation.typeClassifier() == VersionInformation.VersionTypeClassifier.OUTDATED) {
        long duration = AccessHelper.now() - versionInformation.release();
        String durationAsString = DurationTranslator.translateDuration(duration);

        sendPrefixedMessage(ChatColor.RED + "This server is running an outdated version of Intave ("+durationAsString+" old)", player);
        sendPrefixedMessage(ChatColor.RED + "I hope you know why updating your *security* software might be important.", player);
      }
    }
  }

  @BukkitEventSubscription
  public void on(PlayerTeleportEvent event) {
    if(IntaveControl.DEBUG_TELEPORT_CAUSE_AND_CAUSER) {
      PluginInvocation pluginInvocation = CallerResolver.callerPluginInfo();
      String pluginClass = pluginInvocation == null ? "no other plugin" : pluginInvocation.className();
      event.getPlayer().sendMessage("Teleport " + event.getCause() + " " + event.getTo() + " by " + pluginClass);
    }
  }

  public void sendPrefixedMessage(String message, CommandSender target) {
    target.sendMessage(IntavePlugin.prefix() + message);
  }

  public MovementEmulationEngine emulationEngine() {
    return emulationEngine;
  }

  public TransactionFeedbackService transactionFeedbackService() {
    return transactionFeedbackService;
  }
}