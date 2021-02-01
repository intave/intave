package de.jpx3.intave.event.service;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.AsyncIntaveCommandTriggerEvent;
import de.jpx3.intave.access.AsyncIntaveViolationEvent;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.tools.MathHelper;
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

    int protocolVersion = detectedUser.meta().clientData().protocolVersion();

    String finalCheckName = checkName;
    AsyncIntaveViolationEvent violationEvent = plugin.customEventService().invokeEvent(
      AsyncIntaveViolationEvent.class,
      event -> event.renew(detectedPlayer, protocolVersion, finalCheckName, message, details, oldVl, newVl)
    );

    violationEvent.suggestResponse(AsyncIntaveViolationEvent.SuggestiveResponse.INTERRUPT_AND_REPORT);

    if(violationEvent.isCancelled()) {
      AsyncIntaveViolationEvent.SuggestiveResponse response = violationEvent.suggestedResponse();
      return response == AsyncIntaveViolationEvent.SuggestiveResponse.ONLY_INTERRUPT && preventionActivation <= newVl;
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
          AsyncIntaveCommandTriggerEvent commandTriggerEvent = plugin.customEventService().invokeEvent(
            AsyncIntaveCommandTriggerEvent.class,
            event -> event.renew(detectedPlayer, executedCommand, false)
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
        Synchronizer.synchronize(() -> {
          System.out.println("[Intave] Executing \"" + ChatColor.stripColor(resolvedCommand) + "\"");
          plugin.logger().commandExecution(resolvedCommand);
          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCommand);
        });
      }
    }

    return preventionActivation <= newVl;
  }

  private void performVerbose(User detectedUser, String checkName, double oldVl, double newVl, String message, String details) {
    Player detectedPlayer = detectedUser.player();
    ViolationContext compactViolationContext = new ViolationContext(checkName, message, "", oldVl, newVl);
    ViolationContext fullViolationContext = new ViolationContext(checkName, message, details, oldVl, newVl);
    String trustFactorStringOutput = detectedUser.trustFactor().name().toLowerCase().replace("_", "");
    plugin.logger().violation(detectedPlayer.getName() + "/" +trustFactorStringOutput + " " + message + " (" + details +") (+"+(newVl - oldVl) + " -> " + newVl+" on "+checkName+")");
    broadcastVerbose(detectedPlayer, fullViolationContext, compactViolationContext);
  }

  private void broadcastVerbose(Player player, ViolationContext full, ViolationContext compact) {
    String fullMessage = MessageFormatter.resolveVerboseMessage(player, full);
    String compactMessage = MessageFormatter.resolveVerboseMessage(player, compact);

    Synchronizer.synchronize(() -> {
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        User user = UserRepository.userOf(onlinePlayer);
        if(user.receives(UserMessageChannel.VERBOSE)) {
          if(user.hasChannelConstraint(UserMessageChannel.VERBOSE)) {
            if(user.channelPlayerConstraint(UserMessageChannel.VERBOSE).appliesTo(player)) {
              onlinePlayer.sendMessage(fullMessage);
            }
          } else {
            if(onlinePlayer.equals(player)) {
              onlinePlayer.sendMessage(fullMessage);
            } else {
              onlinePlayer.sendMessage(compactMessage);
            }
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