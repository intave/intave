package de.jpx3.intave.diagnostic.message;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.sibyl.SibylIntegrationService;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugBroadcast {
  private static final ConfigurationResolver configurationResolver = new HardcodeConfigurationResolver();
  private static final Map<UUID, OutputConfiguration> outputConfigurations = new ConcurrentHashMap<>();

  public static OutputConfiguration configurationOf(UUID owner) {
    return outputConfigurations.computeIfAbsent(owner, configurationResolver::of);
  }

  public static boolean anyoneListeningTo(MessageCategory category, Player constraint) {
    Collection<Player> players = MessageChannelSubscriptions.sibylReceivers();
    if (players.isEmpty()) {
      return false;
    }
    for (Player player : players) {
      UUID id = player.getUniqueId();
      if (configurationOf(id).canOutput(category, constraint)) {
        return true;
      }
    }
    return false;
  }

  public static void broadcast(Player target, MessageCategory category, MessageSeverity severity, String fullMessage, String shortMessage) {
    Collection<Player> receivers = MessageChannelSubscriptions.sibylReceivers();
    if (receivers.isEmpty()) {
      return;
    }
    if (!Bukkit.isPrimaryThread()) {
      Synchronizer.synchronize(() -> broadcast(target, category, severity, fullMessage, shortMessage));
      return;
    }
    SibylIntegrationService sibyl = IntavePlugin.singletonInstance().sibyl();
    for (Player receiver : receivers) {
      if (sibyl.isAuthenticated(receiver)) {
        // Use new sibyl if encryption available otherwise use fallback method
        if (sibyl.encryptionActiveFor(receiver)) {
          sibyl.publishDebug(receiver, category.ordinal(), fullMessage, shortMessage);
        } else {
          OutputConfiguration configuration = configurationOf(receiver.getUniqueId());
          if (configuration.canOutput(category, target) && !severity.isLowerThan(configuration.minimumSeverity())) {
            String color = configuration.colorOf(category).toString();
            String prefix = configuration.prefixSelector().formatPrefix(severity, category.name());
            String theMessage = configuration.detailOf(category).select(fullMessage, shortMessage);
            String completeMessage = ChatColor.RED + "(insecure) " + color + prefix + " " + theMessage;
            receiver.sendMessage(completeMessage);
          }
        }
      }
    }
  }
}
