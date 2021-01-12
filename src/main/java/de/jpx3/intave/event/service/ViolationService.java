package de.jpx3.intave.event.service;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.AsyncIntaveViolationEvent;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.placeholder.ViolationContext;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMessageChannel;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

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

    String thresholdsConfigKey = "checks." + checkName + "." + thresholdsKey;
    IntaveCheck check = plugin.checkService().searchCheck(checkName);

    double oldVl = violationMapOf(detectedPlayer).computeIfAbsent(checkName, s -> 0d);
    double newVl = MathHelper.minmax(0, oldVl + vl, 1000);

    int protocolVersion = detectedUser.meta().clientData().protocolVersion();

    String finalCheckName = checkName;
    AsyncIntaveViolationEvent asyncIntaveViolationEvent = plugin.customEventService().invokeEvent(AsyncIntaveViolationEvent.class, event ->
      event.renew(detectedPlayer, protocolVersion, finalCheckName, message, details, oldVl, newVl));

    double preventionActivation = resolvePreventionActivationThreshold(checkName, detectedPlayer);

    ViolationContext compactViolationContext = new ViolationContext(checkName, message, "", oldVl, newVl);
    ViolationContext fullViolationContext = new ViolationContext(checkName, message, details, oldVl, newVl);

    broadcastVerbose(detectedPlayer, fullViolationContext, compactViolationContext);

    violationMapOf(detectedPlayer).put(checkName, newVl);
    return preventionActivation <= newVl;
  }

  private void broadcastVerbose(Player player, ViolationContext full, ViolationContext compact) {
    String fullMessage = MessageFormatter.resolveVerboseMessage(player, full);
    String compactMessage = MessageFormatter.resolveVerboseMessage(player, compact);

    Synchronizer.synchronize(() -> {
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        User user = UserRepository.userOf(onlinePlayer);
        if(user.receives(UserMessageChannel.VERBOSE)) {
          if(user.hasChannelConstraint(UserMessageChannel.VERBOSE)) {
            onlinePlayer.sendMessage(fullMessage);
          } else {
            onlinePlayer.sendMessage(compactMessage);
          }
        }
      }
    });
  }

  private Map<String, Double> violationMapOf(Player player) {
    return UserRepository.userOf(player).meta().violationLevelData().violationLevel;
  }

  private double resolvePreventionActivationThreshold(String checkName, Player player) {
    return plugin.trustFactorService().trustFactorSetting(checkName + ".prevention-activation", player);
  }
}