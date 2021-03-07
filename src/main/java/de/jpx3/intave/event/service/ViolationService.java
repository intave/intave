package de.jpx3.intave.event.service;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveCommandExecutionEvent;
import de.jpx3.intave.access.IntaveViolationEvent;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.placeholder.TextContext;
import de.jpx3.intave.tools.placeholder.ViolationContext;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMessageChannel;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

public final class ViolationService {
  private final IntavePlugin plugin;

  // 100 ~ kick

  public ViolationService(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Deprecated
  public boolean processViolation(Player detectedPlayer, double vl, String checkName, String message) {
    return processViolation(detectedPlayer, vl, checkName, message, "", "thresholds");
  }

  public boolean processViolation(Player detectedPlayer, double vl, String checkName, String message, String details) {
    return processViolation(detectedPlayer, vl, checkName, message, details, "thresholds");
  }

  public synchronized boolean processViolation(Player detectedPlayer, double vl, String checkName, String message, String details, String thresholdsKey) {
    checkName = checkName.toLowerCase(Locale.ROOT);

    User detectedUser = UserRepository.userOf(detectedPlayer);
    if(detectedUser.justJoined() || !detectedUser.hasOnlinePlayer()) {
      return true;
    }

    IntaveCheck check = plugin.checkService().searchCheck(checkName);

    if(!check.enabled()) {
      return false;
    }

    double oldVl = violationMapOf(detectedPlayer).computeIfAbsent(checkName, s -> new HashMap<>()).computeIfAbsent(thresholdsKey, s -> 0d);
    double newVl = MathHelper.minmax(0, oldVl + vl, 1000);
    double preventionActivation = resolvePreventionActivationThreshold(checkName, detectedPlayer);

    String finalCheckName = checkName;
    IntaveViolationEvent violationEvent = plugin.customEventService().invokeEvent(
      IntaveViolationEvent.class,
      event -> event.copy(detectedPlayer, finalCheckName, message, details, oldVl, newVl)
    );

    if(violationEvent.isCancelled()) {
      IntaveViolationEvent.Reaction response = violationEvent.reaction();
      return response == IntaveViolationEvent.Reaction.INTERRUPT && preventionActivation <= newVl;
    }

    performVerbose(detectedUser, checkName, oldVl, newVl, message, details);
    violationMapOf(detectedPlayer).get(checkName).put(thresholdsKey, newVl);

    List<String> resolvedCommands = null;
    Map<Integer, List<String>> thresholds = check.checkConfiguration.settings().thresholdsBy(thresholdsKey);

    for (int i = (int) oldVl + 1; i <= newVl; i++) {
      List<String> commands = thresholds.get(i);
      if(commands != null) {
        for (String command : commands) {
          String executedCommand = MessageFormatter.resolveCommandReplacements(detectedPlayer, command);
          IntaveCommandExecutionEvent commandTriggerEvent = plugin.customEventService().invokeEvent(
            IntaveCommandExecutionEvent.class,
            event -> event.copy(detectedPlayer, executedCommand, false)
          );
          if(!commandTriggerEvent.isCancelled()) {
            if(resolvedCommands == null) {
              resolvedCommands = new ArrayList<>();
            }
            resolvedCommands.add(commandTriggerEvent.command());
          }
        }
      }
    }

    if(resolvedCommands != null) {
      for (String resolvedCommand : resolvedCommands) {
        String finalCheckName1 = checkName;
        Synchronizer.synchronize(() -> {
          if(resolvedCommand.startsWith("ban") || resolvedCommand.startsWith("kick")) {
            plugin.eventService().reconDelayLimiter().ban(detectedPlayer.getAddress().getAddress(), detectedPlayer.getUniqueId(), finalCheckName1);
          }
          IntaveLogger.logger().globalPrintLn("[Intave] Executing \"" + ChatColor.stripColor(resolvedCommand) + "\"");
          plugin.logger().commandExecution(resolvedCommand);
          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCommand);
        });
      }
    }

    return preventionActivation <= newVl;
  }

  public void performVerbose(User detectedUser, String checkName, double oldVl, double newVl, String message, String details) {
    Player detectedPlayer = detectedUser.player();
    ViolationContext compactViolationContext = new ViolationContext(checkName, message, "", oldVl, newVl);
    ViolationContext fullViolationContext = new ViolationContext(checkName, message, details, oldVl, newVl);
    String trustFactorStringOutput = detectedUser.trustFactor().name().toLowerCase().replace("_", "");
    String vlAdded = MathHelper.formatDouble((newVl - oldVl), 2);
    String newVLDisplay = MathHelper.formatDouble(newVl, 2);
    plugin.logger().violation(detectedPlayer.getName() + "/" +trustFactorStringOutput + " " + message + " (" + details +") (+"+ vlAdded + " -> " + newVLDisplay +" on "+checkName+")");
    broadcastVerbose(detectedPlayer, fullViolationContext, compactViolationContext);
  }

  public final static UserMessageChannel NOTIFY_MESSAGE_CHANNEL = UserMessageChannel.NOTIFY;

  public void broadcastNotify(String fullMessage) {
    String notifyMessage = MessageFormatter.resolveNotifyReplacements(new TextContext(fullMessage));

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      User user = UserRepository.userOf(onlinePlayer);
      if(user.receives(NOTIFY_MESSAGE_CHANNEL)) {
        if(user.hasChannelConstraint(NOTIFY_MESSAGE_CHANNEL)) {
          if(user.channelPlayerConstraint(NOTIFY_MESSAGE_CHANNEL).appliesTo(onlinePlayer)) {
            onlinePlayer.sendMessage(notifyMessage);
          }
        } else {
          onlinePlayer.sendMessage(notifyMessage);
        }
      }
    }
  }

  private final static UserMessageChannel VERBOSE_MESSAGE_CHANNEL = UserMessageChannel.VERBOSE;

  public void broadcastVerbose(Player player, ViolationContext full, ViolationContext compact) {
    String fullMessage = MessageFormatter.resolveVerboseMessage(player, full);

    Synchronizer.synchronize(() -> {
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        User user = UserRepository.userOf(onlinePlayer);
        if(user.receives(VERBOSE_MESSAGE_CHANNEL)) {
          if(user.hasChannelConstraint(VERBOSE_MESSAGE_CHANNEL)) {
            if(user.channelPlayerConstraint(VERBOSE_MESSAGE_CHANNEL).appliesTo(player)) {
              onlinePlayer.sendMessage(fullMessage);
            }
          } else {
            onlinePlayer.sendMessage(fullMessage);
          }
        }
      }
    });
  }

  private Map<String, Map<String, Double>> violationMapOf(Player player) {
    return UserRepository.userOf(player).meta().violationLevelData().violationLevel;
  }

  private double resolvePreventionActivationThreshold(String checkName, Player player) {
    return plugin.trustFactorService().trustFactorSetting(checkName + ".prevention-activation", player);
  }
}