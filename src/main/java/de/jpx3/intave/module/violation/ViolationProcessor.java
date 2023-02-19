package de.jpx3.intave.module.violation;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.access.check.event.IntaveCommandExecutionEvent;
import de.jpx3.intave.access.check.event.IntaveViolationEvent;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.analytics.GlobalStatisticsRecorder;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.connect.proxy.protocol.packets.IntavePacketOutKicked;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.metric.ServerHealth;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.violation.placeholder.PlaceholderContext;
import de.jpx3.intave.module.violation.placeholder.TextContext;
import de.jpx3.intave.module.violation.placeholder.ViolationPlaceholderContext;
import de.jpx3.intave.module.violation.placeholder.ViolationPlaceholderContext.DetailScope;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ViolationMetadata;
import de.jpx3.intave.user.storage.ViolationStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Predicate;

import static de.jpx3.intave.access.check.MitigationStrategy.SILENT;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DONT_PROCESS_VIOSTAT;

public final class ViolationProcessor extends Module {
  public ViolationContext processViolation(Violation violation) {
    ViolationContext violationContext = ViolationContext.of(violation);
    Optional<Player> playerSearch = violation.findPlayer();
    if (!playerSearch.isPresent()) {
      return violationContext.counterThreatBecause("Player is not present").complete();
    }
    Player player = playerSearch.get();
    User user = UserRepository.userOf(player);
    if (user.trustFactor().atLeast(TrustFactor.BYPASS)) {
      return violationContext.ignoreThreatBecause("Player has the bypass trust-factor").complete();
    }
    Check check = violation.check();
    if (!check.enabled()) {
      return violationContext.ignoreThreatBecause("Check is disabled").complete();
    }
    if (user.justJoined() || !user.hasPlayer()) {
      if (violation.check().mitigationStrategy() == SILENT) {
        return violationContext.ignoreThreatBecause("Player just joined or is not reachable (silent mode)");
      } else {
        return violationContext.counterThreatBecause("Player just joined or is not reachable").complete();
      }
    }
    fillInVLContext(violationContext);
    processViolationEvent(violationContext);
    processViolationSpam(violationContext);
    processViolationStatistics(violationContext);
    processViolationVerbose(violationContext);
    forwardViolationToAnalytics(violationContext);
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

  private static final double REDUCE_APPLIER = 1000d;

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
    IntaveViolationEvent violationEvent = Modules.eventInvoker().invokeEvent(
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

  private void processViolationSpam(
    ViolationContext violationContext
  ) {
    if (violationContext.completed()) {
      return;
    }
    Violation violation = violationContext.violation();
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
    User user = UserRepository.userOf(player);
    ViolationMetadata violationLevelData = user.meta().violationLevel();
    if (System.currentTimeMillis() - violationLevelData.detectionCounterReset > 10000) {
      violationLevelData.detectionCounter = 0;
      violationLevelData.detectionCounterReset = System.currentTimeMillis();
    }
    if (violationLevelData.detectionCounter++ > 300) {
      user.kick("You are sending too many packets :[");
    }
  }

  private void processViolationStatistics(
    ViolationContext violationContext
  ) {
    if (violationContext.completed()) {
      return;
    }
    Violation violation = violationContext.violation();
    if (violation.flagSet(DONT_PROCESS_VIOSTAT)) {
      return;
    }
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
    User user = UserRepository.userOf(player);
    Check check = violation.check();
    check.statisticApply(user, CheckStatistics::increaseViolations);
  }

  private static final String LOGGER_MESSAGE_LAYOUT = "%s/%s %s %s(+%s -> %s on %s)";

  private void processViolationVerbose(ViolationContext violationContext) {
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
    String vlAdded = formatDouble((violationContext.violationLevelAfter() - violationContext.violationLevelBefore()), 2);
    String vlAfterViolation = formatDouble(violationContext.violationLevelAfter(), 2);
    String message = violation.message().trim();
    String details = violation.details().isEmpty() ? "" : "(" + violation.details().trim() + ")" + " ";
    String consoleMessage = String.format(
      LOGGER_MESSAGE_LAYOUT, player.getName(), trustFactor,
      message, details, vlAdded, vlAfterViolation, checkName
    );
    if (IntaveControl.GOMME_MODE) {
      consoleMessage += " | TPS: " + ServerHealth.stringFormattedTick() + " Ping: " + user.latency() + "ms";
    }
    plugin.logger().violation(consoleMessage);
  }

  private void forwardViolationToAnalytics(ViolationContext violationContext) {
    if (violationContext.completed()) {
      return;
    }
    GlobalStatisticsRecorder recorder = plugin.analytics().recorderOf(GlobalStatisticsRecorder.class);
    Violation violation = violationContext.violation();
    recorder.recordViolation(violation.check().name());
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
    User user = UserRepository.userOf(player);
    ViolationStorage violationStorage = user.storageOf(ViolationStorage.class);
    violationStorage.noteViolation(violationContext);
  }

  private void processViolationLevelIncrease(ViolationContext violationContext) {
    if (violationContext.completed()) {
      return;
    }
    // bad fix
    try {
      Violation violation = violationContext.violation();
      Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
      String checkName = violation.check().name().toLowerCase(Locale.ROOT);
      String threshold = violation.threshold();
      double violationLevelAfter = violationContext.violationLevelAfter();
      violationMapOf(player).computeIfAbsent(checkName, s -> new HashMap<>()).put(threshold, violationLevelAfter);
    } catch (Exception ignored) {
    }
  }

  private void lookupThresholdCommands(ViolationContext violationContext) {
    if (violationContext.completed()) {
      return;
    }
    Violation violation = violationContext.violation();
    Check check = violation.check();
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
      ViolationPlaceholderContext placeholderContext = violationContext.placeholder(DetailScope.FULL /* automatically striped when not enterprise */);
      String executedCommand = MessageFormatter.resolveCommandReplacements(player, command, placeholderContext);
      IntaveCommandExecutionEvent commandTriggerEvent = Modules.eventInvoker().invokeEvent(
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
        Modules.mitigate().reconnectionLimiter().ban(player.getAddress().getAddress(), player.getUniqueId(), checkName);
        plugin.proxy().sendPacket(player, new IntavePacketOutKicked(
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

  private static final MessageChannel NOTIFY_MESSAGE_CHANNEL = MessageChannel.NOTIFY;

  public void broadcastNotify(String fullMessage) {
    Collection<Player> receivers = MessageChannelSubscriptions.receiverOf(NOTIFY_MESSAGE_CHANNEL);
    if (receivers.isEmpty()) {
      return;
    }
    String notifyMessage = MessageFormatter.resolveNotifyReplacements(new TextContext(fullMessage));
    for (Player receiver : receivers/*Bukkit.getOnlinePlayers()*/) {
      User user = UserRepository.userOf(receiver);
      if (user.receives(NOTIFY_MESSAGE_CHANNEL)) {
        synchronizedMessage(receiver, notifyMessage);
      }
    }
  }

  private static final MessageChannel VERBOSE_MESSAGE_CHANNEL = MessageChannel.VERBOSE;

  public void broadcastVerbose(Player target, ViolationContext violationContext) {
    Collection<Player> receivers = MessageChannelSubscriptions.receiverOf(VERBOSE_MESSAGE_CHANNEL);
    if (receivers.isEmpty()) {
      return;
    }
    PlaceholderContext violationPlaceholder = violationContext.violation().placeholder();
    PlaceholderContext violationContextPlaceholder = violationContext.placeholder(DetailScope.FULL);

    String message = MessageFormatter.resolveVerboseMessage(
      target, violationPlaceholder.merge(violationContextPlaceholder)
    );
    for (Player receiver : receivers) {
      User receiverUser = UserRepository.userOf(receiver);
      if (!receiverUser.receives(VERBOSE_MESSAGE_CHANNEL)) {
        continue;
      }
      Predicate<Player> constraint = receiverUser.channelPlayerConstraint(VERBOSE_MESSAGE_CHANNEL);
      if (constraint == null || constraint.test(target)) {
        synchronizedMessage(receiver, message);
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
    return UserRepository.userOf(player).meta().violationLevel().violationLevel;
  }

  private double resolvePreventionActivationThreshold(String checkName, Player player) {
    return plugin.trustFactorService().trustFactorSetting(checkName + ".prevention-activation", player);
  }
}