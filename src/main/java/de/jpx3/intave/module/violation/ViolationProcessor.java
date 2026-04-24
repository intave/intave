package de.jpx3.intave.module.violation;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.check.event.IntaveCommandExecutionEvent;
import de.jpx3.intave.access.check.event.IntaveViolationEvent;
import de.jpx3.intave.access.player.trust.TrustFactor;
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
import de.jpx3.intave.user.storage.LongTermViolationStorage;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Predicate;

import static de.jpx3.intave.access.check.MitigationStrategy.SILENT;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DONT_PROCESS_VIOSTAT;

public final class ViolationProcessor extends Module {
  private ViolationVerboseMode verboseMode;

  @Override
  public void enable() {
    String verboseMode = plugin.settings().getString("logging.verbose-mode",
      plugin.settings().getString("logging.violation-output-mode", ViolationVerboseMode.MITIGATED.name()));

    try {
      this.verboseMode = ViolationVerboseMode.valueOf(verboseMode);
    } catch (Exception exception) {
      IntaveLogger.logger().warn("Invalid verbose mode '" + verboseMode + "' in settings. Using default value '" + this.verboseMode.name() + "'");
      this.verboseMode = ViolationVerboseMode.MITIGATED;
    }
  }

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
    if (user.justJoined()) {
      return;
    }
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

//    boolean doVerbose = false;

//    switch (verboseMode) {
//      case ALL:
//        doVerbose = true;
//        break;
//      case MITIGATED:
//        doVerbose = violationContext.violationLevelPassedPreventionActivation();
//        break;
//      case SELECTED:
//        doVerbose = violationContext.violationLevelPassedPreventionActivation() && SELECTED_CHECKS.contains(checkName);
//        break;
//    }
    if (verboseMode.doVerbose(violationContext) || violation.flagSet(DISPLAY_IN_ALL_VERBOSE_MODES)) {
      broadcastVerbose(player, violationContext);
    }
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
    consoleMessage += " | TPS: " + ServerHealth.stringFormattedTick() + " Ping: " + user.latency() + "ms";
    plugin.logger().violation(consoleMessage);
  }

  private void forwardViolationToAnalytics(ViolationContext violationContext) {
    if (violationContext.completed()) {
      return;
    }
//    GlobalStatisticsRecorder recorder = plugin.analytics().recorderOf(GlobalStatisticsRecorder.class);
    Violation violation = violationContext.violation();
//    recorder.recordViolation(violation.check().name());
    Player player = violation.findPlayer().orElseThrow(IllegalStateException::new);
    User user = UserRepository.userOf(player);
    LongTermViolationStorage violationStorage = user.storageOf(LongTermViolationStorage.class);
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

      String dispatchedCommand = command.contains("{log-id}") ? command.replace("{log-id}", localLogId(player)) : command;
      plugin.logger().commandExecution(dispatchedCommand);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), dispatchedCommand);
    });
  }

  private static String localLogId(Player player) {
    UUID uuid = player == null ? new UUID(0L, 0L) : player.getUniqueId();
    return "local-" + uuid;
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
        synchronizedMessage(receiver, notifyMessage, Collections.emptyMap());
      }
    }
  }

  private static final List<MessageChannel> VERBOSE_MESSAGE_CHANNELS =
    Arrays.asList(MessageChannel.VIOLATION_FINE/*, MessageChannel.VIOLATION_SIMPLE*/);

  public void broadcastVerbose(Player target, ViolationContext violationContext) {
    for (MessageChannel verboseMessageChannel : VERBOSE_MESSAGE_CHANNELS) {
      Collection<Player> receivers = MessageChannelSubscriptions.receiverOf(verboseMessageChannel);
      if (receivers.isEmpty()) {
        return;
      }
      PlaceholderContext violationPlaceholder = violationContext.violation().placeholder();
      PlaceholderContext violationContextPlaceholder = violationContext.placeholder(DetailScope.FULL);
      Map<String, String> granular = violationContext.violation().granular();
      String message = MessageFormatter.resolveVerboseMessage(
        target, violationPlaceholder.merge(violationContextPlaceholder), verboseMessageChannel == MessageChannel.VIOLATION_SIMPLE ? ViolationOutputMode.SIMPLE : ViolationOutputMode.FINE
      );
      for (Player receiver : receivers) {
        User receiverUser = UserRepository.userOf(receiver);
        if (!receiverUser.receives(verboseMessageChannel)) {
          continue;
        }
        Predicate<Player> constraint = receiverUser.channelPlayerConstraint(verboseMessageChannel);
        if (constraint == null || constraint.test(target)) {
          synchronizedMessage(receiver, message, granular);
        }
      }
    }
  }

  private void synchronizedMessage(Player player, String message, Map<String, String> granularInfos) {
    if (Bukkit.isPrimaryThread()) {
      // Send spigot message with hoverable text
      TextComponent textComponent = new TextComponent(message);
      if (!granularInfos.isEmpty()) {
        textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, formatGranularInfos(granularInfos)));
      }
      player.spigot().sendMessage(textComponent);
    } else {
      Synchronizer.synchronize(() -> synchronizedMessage(player, message, granularInfos));
    }
  }

  private BaseComponent[] formatGranularInfos(Map<String, String> granularInfos) {
    List<BaseComponent> components = new ArrayList<>();
    for (Map.Entry<String, String> entry : granularInfos.entrySet()) {
      TextComponent keyComponent = new TextComponent(entry.getKey().toUpperCase());
      keyComponent.setColor(ChatColor.RED);
      TextComponent valueComponent = new TextComponent(entry.getValue());
      valueComponent.setColor(ChatColor.GRAY);
      TextComponent spaceComponent = new TextComponent(": ");
      spaceComponent.setColor(ChatColor.GRAY);
      components.add(keyComponent);
      components.add(spaceComponent);
      components.add(valueComponent);
      components.add(new TextComponent("\n"));
    }
    // remove last newline
    if (!components.isEmpty()) {
      components.remove(components.size() - 1);
    }
    return components.toArray(new BaseComponent[0]);
  }

  private Map<String, Map<String, Double>> violationMapOf(Player player) {
    return UserRepository.userOf(player).meta().violationLevel().violationLevel;
  }

  private double resolvePreventionActivationThreshold(String checkName, Player player) {
    return plugin.trustFactorService().trustFactorSetting(checkName + ".prevention-activation", player);
  }

  public ViolationVerboseMode verboseMode() {
    return verboseMode;
  }
}
