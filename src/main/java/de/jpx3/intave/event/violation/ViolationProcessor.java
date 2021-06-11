package de.jpx3.intave.event.violation;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.check.event.IntaveCommandExecutionEvent;
import de.jpx3.intave.access.check.event.IntaveViolationEvent;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.connect.proxy.protocol.packets.IntavePacketOutKicked;
import de.jpx3.intave.detect.CheckStatistics;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.placeholder.TextContext;
import de.jpx3.intave.tools.placeholder.ViolationPlaceholderContext;
import de.jpx3.intave.tools.placeholder.ViolationPlaceholderContext.DetailScope;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Predicate;

import static de.jpx3.intave.event.violation.Violation.ViolationFlags;

public final class ViolationProcessor {
  private final IntavePlugin plugin;

  public ViolationProcessor(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public ViolationContext processViolation(Violation violation) {
    ViolationContext violationContext = ViolationContext.newOf(violation);

    Optional<Player> playerSearch = violation.findPlayer();
    if (!playerSearch.isPresent()) {
      return violationContext.counterThreatBecause("Player is not reachable").complete();
    }

    Player player = playerSearch.get();
    User user = UserRepository.userOf(player);

    if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      return violationContext.ignoreThreatBecause("Player has bypass trust factor").complete();
    }

    IntaveCheck check = violation.check();
    if (!check.enabled()) {
      return violationContext.ignoreThreatBecause("Check is disabled").complete();
    }

    if (user.justJoined() || !user.hasOnlinePlayer()) {
      return violationContext.counterThreatBecause("Player is not reachable").complete();
    }

    fillInVLContext(violationContext);

    processViolationEvent(violationContext);
    processViolationOverflow(violationContext);
    processViolationStatistics(violationContext);
    processViolationVerbose(violationContext);
    processViolationLevelIncrease(violationContext);

    lookupThresholdCommands(violationContext);
    processThresholdsEvents(violationContext);
    executeCommands(violationContext);

    if (!violationContext.completed() && violationContext.violationLevelPassedPreventionActivation()) {
      violationContext.counterThreatBecause("Activation prevention reached");
    }

    return violationContext.complete();
  }

  private void fillInVLContext(
    ViolationContext violationContext
  ) {
    if (violationContext.completed()) {
      return;
    }
    Violation violation = violationContext.violation();
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
    String checkName = violation.check().name().toLowerCase(Locale.ROOT);
    String thresholdsKey = violation.threshold();
    double violationLevelAdded = violation.addedViolationPoints();
    double violationLevelBeforeViolation = violationMapOf(player).computeIfAbsent(checkName, s -> new HashMap<>()).computeIfAbsent(thresholdsKey, s -> 0d);
    double violationLevelAfterViolation = MathHelper.minmax(0, violationLevelBeforeViolation + violationLevelAdded, 1000);
    double preventionActivation = resolvePreventionActivationThreshold(checkName, player);

    violationContext.setViolationLevelAfter(reducePrecision(violationLevelAfterViolation));
    violationContext.setViolationLevelBefore(reducePrecision(violationLevelBeforeViolation));
    violationContext.setPreventionActivation(reducePrecision(preventionActivation));
  }

  private final static double REDUCE_APPLIER = 1000d;

  private double reducePrecision(double input) {
    return Math.round(input * REDUCE_APPLIER) / REDUCE_APPLIER;
  }

  private void processViolationEvent(
    ViolationContext violationContext
  ) {
    if (violationContext.completed()) {
      return;
    }
    Violation violation = violationContext.violation();
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);

    String checkName = violation.check().name().toLowerCase(Locale.ROOT);
    String message = violation.message();
    String details = violation.details();

    double oldVl = violationContext.violationLevelBefore();
    double newVl = violationContext.violationLevelAfter();

    IntaveViolationEvent violationEvent = plugin.customEventService().invokeEvent(
      IntaveViolationEvent.class,
      event -> event.copy(player, checkName, message, details, oldVl, newVl)
    );

    if (violationEvent.isCancelled()) {
      IntaveViolationEvent.Reaction response = violationEvent.reaction();
      boolean counterThreat = response == IntaveViolationEvent.Reaction.INTERRUPT && violationContext.violationLevelPassedPreventionActivation();
      if (counterThreat) {
        violationContext.counterThreatBecause("Intave access requested it");
      } else {
        violationContext.ignoreThreatBecause("Intave access requested it");
      }
      violationContext.complete();
    }
  }

  private void processViolationOverflow(
    ViolationContext violationContext
  ) {
    if (violationContext.completed()) {
      return;
    }
    User user = UserRepository.userOf(violationContext.violation().findPlayer().orElseThrow(IllegalStateException::new));
    UserMetaViolationLevelData violationLevelData = user.meta().violationLevelData();

    if (AccessHelper.now() - violationLevelData.detectionCounterReset > 10000) {
      violationLevelData.detectionCounter = 0;
      violationLevelData.detectionCounterReset = AccessHelper.now();
    }

    if (violationLevelData.detectionCounter++ > 500) {
      user.synchronizedDisconnect("You are sending too many packets :[");
    }
  }

  private void processViolationStatistics(
    ViolationContext violationContext
  ) {
    if (violationContext.completed()) {
      return;
    }
    boolean ignoreVioStat = violationContext.violation().flagSet(ViolationFlags.DONT_PROCESS_VIOSTAT);
    if (ignoreVioStat) {
      return;
    }
    User user = UserRepository.userOf(violationContext.violation().findPlayer().orElseThrow(IllegalStateException::new));
    IntaveCheck check = violationContext.violation().check();
    check.statisticApply(user, CheckStatistics::increaseViolations);
  }

  private final static String LOGGER_MESSAGE_LAYOUT = "%s/%s %s %s(+%s -> %s on %s)";

  @Native
  private void processViolationVerbose(ViolationContext violationContext) {
    boolean enterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
    if (violationContext.completed()) {
      return;
    }
    Violation violation = violationContext.violation();
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
    User user = UserRepository.userOf(player);
    String checkName = violation.check().name().toLowerCase(Locale.ROOT);

    // default verbose
    broadcastVerbose(player, violationContext);

    // console output
    String trustFactor = user.trustFactor().name().toLowerCase().replace("_", "");
    String vlAdded = MathHelper.formatDouble((violationContext.violationLevelAfter() - violationContext.violationLevelBefore()), 2);
    String vlAfterViolation = MathHelper.formatDouble(violationContext.violationLevelAfter(), 2);
    String message = violation.message().trim();
    String details = violation.details().isEmpty() ? "" : "(" + violation.details().trim() + ")" + " ";
    if (!enterprise) {
      details = "";
    }
    String consoleMessage = String.format(
      LOGGER_MESSAGE_LAYOUT, player.getName(), trustFactor,
      message, details, vlAdded, vlAfterViolation, checkName
    );
    plugin.logger().violation(consoleMessage);
  }

  private void processViolationLevelIncrease(ViolationContext violationContext) {
    if (violationContext.completed()) {
      return;
    }
    // hehehe fix fix
    try {
      Violation violation = violationContext.violation();
      Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
      String checkName = violation.check().name().toLowerCase(Locale.ROOT);
      String threshold = violation.threshold();
      double violationLevelAfter = violationContext.violationLevelAfter();
      violationMapOf(player).get(checkName).put(threshold, violationLevelAfter);
    } catch (Exception ignored) {}
  }

  private void lookupThresholdCommands(ViolationContext violationContext) {
    if (violationContext.completed()) {
      return;
    }
    Violation violation = violationContext.violation();
    IntaveCheck check = violation.check();
    String threshold = violation.threshold();

    double oldVl = violationContext.violationLevelBefore();
    double newVl = violationContext.violationLevelAfter();

    Map<Integer, List<String>> thresholds = check.configuration().settings().thresholdsBy(threshold);
    for (int i = (int) oldVl + 1; i <= newVl; i++) {
      List<String> commands = thresholds.get(i);
      if (commands != null) {
        commands.forEach(violationContext::addCommand);
        violationContext.setMeetsThresholds(true);
      }
    }
  }

  @Native
  private void processThresholdsEvents(
    ViolationContext violationContext
  ) {
    if (violationContext.completed() || violationContext.commands().isEmpty()) {
      return;
    }
    Violation violation = violationContext.violation();
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);

    String checkName = violation.check().name().toLowerCase(Locale.ROOT);
    String message = violation.message();
    String details = violation.details();

    double afterVL = violationContext.violationLevelAfter();

    List<String> newCommands = new ArrayList<>();
    for (String command : violationContext.commands()) {
      ViolationPlaceholderContext placeholderContext = violationContext.placeholderContextOf(DetailScope.FULL /* automaticallly striped when not enterprise */);
      String executedCommand = MessageFormatter.resolveCommandReplacements(player, command, placeholderContext);
      IntaveCommandExecutionEvent commandTriggerEvent = plugin.customEventService().invokeEvent(
        IntaveCommandExecutionEvent.class,
        event -> event.copy(player, executedCommand, checkName, message, details, afterVL, false)
      );
      if (!commandTriggerEvent.isCancelled()) {
        newCommands.add(commandTriggerEvent.command());
      }
    }
    violationContext.setCommands(newCommands);
  }

  private void executeCommands(ViolationContext violationContext) {
    if (violationContext.completed() || violationContext.commands().isEmpty()) {
      return;
    }
    for (String command : violationContext.commands()) {
      executeCommand(violationContext, command);
    }
  }

  private void executeCommand(ViolationContext violationContext, String command) {
    Violation violation = violationContext.violation();
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
    String checkName = violation.check().name().toLowerCase(Locale.ROOT);

    Synchronizer.synchronize(() -> {
      boolean playerRemoved = command.startsWith("ban") || command.startsWith("kick");
      if (playerRemoved) {
        plugin.eventService().reconDelayLimiter().ban(player.getAddress().getAddress(), player.getUniqueId(), checkName);
        plugin.proxyMessenger().sendPacket(player, new IntavePacketOutKicked(
          player.getUniqueId(),
          checkName,
          violation.message(),
          violationContext.violationLevelAfter()
        ));
      }
      plugin.logger().commandExecution(command);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    });
  }

  private final static UserMessageChannel NOTIFY_MESSAGE_CHANNEL = UserMessageChannel.NOTIFY;

  public void broadcastNotify(String fullMessage) {
    String notifyMessage = MessageFormatter.resolveNotifyReplacements(new TextContext(fullMessage));
    for (Player allPlayers : Bukkit.getOnlinePlayers()) {
      User user = UserRepository.userOf(allPlayers);
      if (user.receives(NOTIFY_MESSAGE_CHANNEL)) {
        synchronizedMessage(allPlayers, notifyMessage);
      }
    }
  }

  private final static UserMessageChannel VERBOSE_MESSAGE_CHANNEL = UserMessageChannel.VERBOSE;

  public void broadcastVerbose(Player player, ViolationContext violationContext) {
    String fullMessage = MessageFormatter.resolveVerboseMessage(
      player, violationContext.placeholderContextOf(DetailScope.FULL)
    );
    sendConstraintMessageOnChannel(player, VERBOSE_MESSAGE_CHANNEL, fullMessage);
  }

  private void sendConstraintMessageOnChannel(
    Player target,
    UserMessageChannel channel,
    String message
  ) {
    for (Player allPlayers : Bukkit.getOnlinePlayers()) {
      User allUsers = UserRepository.userOf(allPlayers);
      if (!allUsers.receives(channel)) {
        continue;
      }
      Predicate<Player> constraint = allUsers.channelPlayerConstraint(channel);
      if (constraint == null || constraint.test(target)) {
        synchronizedMessage(allPlayers, message);
      }
    }
  }

  private void synchronizedMessage(Player player, String message) {
    if (Bukkit.isPrimaryThread()) {
      player.sendMessage(message);
    } else {
      Synchronizer.synchronize(() -> player.sendMessage(message));
    }
  }

  private Map<String, Map<String, Double>> violationMapOf(Player player) {
    return UserRepository.userOf(player).meta().violationLevelData().violationLevel;
  }

  private double resolvePreventionActivationThreshold(String checkName, Player player) {
    return plugin.trustFactorService().trustFactorSetting(checkName + ".prevention-activation", player);
  }
}