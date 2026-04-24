package de.jpx3.intave.check.combat;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Combinator;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.*;
import de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionFluctuation;
import de.jpx3.intave.check.combat.heuristics.detect.inventory.PacketInventoryHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.*;
import de.jpx3.intave.check.combat.heuristics.detect.testing.TestingHeuristic;
import de.jpx3.intave.check.combat.heuristics.mine.MiningStrategyContainer;
import de.jpx3.intave.check.combat.heuristics.mine.MiningStrategyExecutor;
import de.jpx3.intave.diagnostic.message.DebugBroadcast;
import de.jpx3.intave.diagnostic.message.MessageCategory;
import de.jpx3.intave.diagnostic.message.MessageSeverity;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.storage.HeuristicsStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static de.jpx3.intave.check.combat.heuristics.Confidence.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class Heuristics extends MetaCheck<Heuristics.HeuristicMeta> {
  private final IntavePlugin plugin;
  private final Combinator combinator;

  public Heuristics(IntavePlugin plugin) {
    super("Heuristics", "heuristics", HeuristicMeta.class);
    this.plugin = plugin;
    this.combinator = new Combinator(this);

    this.setupSubChecks();
    this.setupEvaluationScheduler(plugin);
  }

  private void setupEvaluationScheduler(IntavePlugin plugin) {
    //noinspection deprecation
    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::evaluateAll, 0, 20 * 20);
    TaskTracker.begun(taskId);
  }

  //  @Native
  public void setupSubChecks() {
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.OldAirClickLimitHeuristic");
//        appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.other.AttackReduceIgnoreHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationStandardDeviationHeuristic");
    appendCheckPart(new RotationStandardDeviationRelayHeuristic(this));
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.other.RotationSnapHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.LongTermClickAccuracyHeuristic");
    appendCheckPart(new LongTermClickAccuracyRelayHeuristic(this));
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.other.ReshapedJumpHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationAccuracyYawHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationAccuracyPitchHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.PerfectAttackHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationSensitivityHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationModuloResetHeuristic");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.PreAttackHeuristic");

    appendCheckPart(new SameRotationHeuristic(this));
    appendCheckPart(new AttackRequiredHeuristic(this));
    appendCheckPart(new PacketOrderHeuristic(this));
    appendCheckPart(new BaritoneRotationCheck(this));
    appendCheckPart(new ToolSwitchHeuristic(this));

    // for testing
    appendCheckPart(new RotationPrevisionFluctuation(this));
    appendCheckPart(new TestingHeuristic(this));

    // Lucky experimental heuristics
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionDetermination");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.SwingLimitHeuristics");
    appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.SwingDeviationHeuristics");
//    appendCheckPart(new RotationAngleHeuristic(this));

    appendCheckPart(new PacketOrderSwingHeuristic(this));
    appendCheckPart(new PacketPlayerActionToggleHeuristic(this));
    appendCheckPart(new RotationUnlikelyAccuracyHeuristic(this));
    appendCheckPart(new PacketInventoryHeuristic(this));
    appendCheckPart(new BlockingHeuristic(this));
    appendCheckPart(new AttackInInvalidStateHeuristic(this));
    appendCheckPart(new NoSwingHeuristic(this));
    appendCheckPart(new DoubleEntityActionHeuristic(this));
    appendCheckPart(new SprintOnAttackHeuristic(this));
    appendCheckPart(new JumpVelocityHeuristic(this));
    appendCheckPart(new CivbreakHeuristic(this));
    appendCheckPart(new InvalidFlyingPacketHeuristic(this));
  }

  private void appendCheckPart(String name) {
    try {
      Class<?> clazz = Class.forName(name);
      if (CheckPart.class.isAssignableFrom(clazz)) {
        CheckPart<?> check = (CheckPart<?>) clazz.getConstructor(Heuristics.class).newInstance(this);
        appendCheckPart(check);
      }
    } catch (ClassNotFoundException ex) {
      System.out.println("Unable to load check part " + name);
      ex.printStackTrace();
    } catch (Exception | Error e) {
      e.printStackTrace();
    }
  }

  public void saveAnomaly(Player player, Anomaly anomaly) {
    if (anomaly.confidence().level() > NONE.level()) {
      HeuristicMeta meta = metaOf(player);
      int limit = anomaly.limit();
      int betterFound = (int) meta.anomalies.stream()
        .filter(anomaly1 -> anomaly1.key().equals(anomaly.key()) && anomaly1.confidence().atLeast(anomaly.confidence()))
        .count();
      if (limit == 0 || betterFound <= limit) {
        meta.anomalies.add(anomaly);
      }
    }
    Synchronizer.synchronize(() -> debug(player, anomaly));
  }

  //  @Native
  private void debug(Player player, Anomaly anomaly) {
    if (anomaly == null) {
      return;
    }
    User user = userOf(player);
    List<Anomaly> anomalies = catchAnomaliesOf(user, false);
    List<Confidence> allConfidences = resolveConfidencesOf(anomalies);
    Confidence overallConfidence = computeOverallConfidence(allConfidences);

    String pattern = anomaly.key();
    String description = anomaly.description();

    String confidenceDetails = overallConfidence.output() + " (" + levelFrom(allConfidences.toArray(new Confidence[0])) + "+" + anomaly.confidence().level() + ")";
    String defaultPrefix = ChatColor.RED + "[IH] ";
    if (IntavePlugin.singletonInstance().sibyl().encryptionActiveFor(player)) {
      defaultPrefix = "";
    }
    String message = defaultPrefix + player.getName() + " on p[" + pattern + "]" + confidenceDetails + " " + description;

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibyl().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    DebugBroadcast.broadcast(player, MessageCategory.HERAN, MessageSeverity.HIGH, message, message);
  }

  private void evaluateAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      try {
        evaluate(onlinePlayer, false);
      } catch (UnsupportedFallbackOperationException ignored) {
      }
    }
  }

  private static final long MAXIMUM_STORAGE_SAVE = 1000 * 60 * 30; // 30 minutes

  //  @Native
  public void evaluate(Player player, boolean enforceDecision) {
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    HeuristicsStorage storage = user.storageOf(HeuristicsStorage.class);

    if (!storage.isRead()) {
      storage.markRead();
      int confidenceLevel = storage.confidence();
      long timeOfSave = storage.timeOfSave();
      if (confidenceLevel > 0 && System.currentTimeMillis() - timeOfSave < MAXIMUM_STORAGE_SAVE) {
        String key = "11";
        List<Confidence> confidences = confidencesStackingTo(confidenceLevel);
        Anomaly.Type type = Anomaly.Type.KILLAURA;
        String description = "storage anomaly #";
        int i = 0;
        for (Confidence confidence : confidences) {
          Anomaly anomaly = Anomaly.anomalyOf(key, confidence, type, description + i, Anomaly.AnomalyOption.LIMIT_8);
          saveAnomaly(player, anomaly);
          i++;
        }
      }
      storage.eraseConfidence();
    }

    // External confidence
    List<Anomaly> activeAnomalies = catchAnomaliesOf(user, true);
    List<Confidence> activeConfidence = resolveConfidencesOf(activeAnomalies);
    int overallActiveConfidenceLevel = levelFrom(activeConfidence.toArray(new Confidence[0]));
    Confidence overallActiveConfidence = computeOverallConfidence(activeConfidence);

    // Internal confidence
    List<Anomaly> allAnomalies = catchAnomaliesOf(user, false);
    List<Confidence> allConfidencesWithoutDelay = resolveConfidencesOf(allAnomalies);
    Confidence overallAllConfidence = computeOverallConfidence(allConfidencesWithoutDelay);

    if (attackData.activeMiningStrategy != null) {
      this.tryRemoveMiningStrategy(attackData.activeMiningStrategy);
    }

    boolean suitableConfidenceForMining = overallAllConfidence.atLeast(MAYBE) && !overallAllConfidence.atLeast(CERTAIN);
    if (IntaveControl.USE_MINING_STRATEGIES && suitableConfidenceForMining && !enforceDecision) {
      // perform mining strategies
      if (attackData.activeMiningStrategy == null) {
        MiningStrategy strategy = findSuitableMiningStrategy(
          allAnomalies,
          overallAllConfidence,
          attackData.lastMiningStrategy
        );
        if (strategy != null) {
          performMiningStrategy(user, strategy);
        }
      }
    }

    /*
     * Only add confidence note when it is not already "critical",
     *  - this ensures we don't stack confidences
     */
    if (overallActiveConfidence.atLeast(ALMOST_CERTAIN)) {
      storage.eraseConfidence();
    } else {
      storage.confidenceNote(overallActiveConfidenceLevel);
    }

    if (overallActiveConfidence.atLeast(ALMOST_CERTAIN)) {
      String identifier;
      if (IntaveControl.DEBUG_HEURISTICS) {
        identifier = restructureForOutput(activeAnomalies).stream().map(anomaly -> "p[" + anomaly.key() + "]").collect(Collectors.joining(","));
      } else {
        identifier = resolveIdentifier(activeAnomalies);
      }
      String threshold;
      if (legacyConfigurationLayout()) {
        threshold = "confidence-thresholds." + overallActiveConfidence.output();
      } else {
        threshold = "analysis-thresholds.on-premise";
      }

      String message = "is fighting suspiciously";
      String details = "on-premise " + identifier;

      Violation violation = Violation.builderFor(Heuristics.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withCustomThreshold(threshold).withVL(25)
        .withPlaceholder("identifier", identifier)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
  }

  private static Boolean legacyConfigLayCache = null;

  public static boolean legacyConfigurationLayout() {
    if (legacyConfigLayCache != null) {
      return legacyConfigLayCache;
    }
    YamlConfiguration settings = IntavePlugin.singletonInstance().settings();
    ConfigurationSection section = settings.getConfigurationSection("check.heuristics.analysis-thresholds.on-premise");
    if (section != null) {
//      IntaveLogger.logger().info("Using new heuristics format");
      return legacyConfigLayCache = false;
    } else {
//      IntaveLogger.logger().info("Still using old heuristics config format");
      return legacyConfigLayCache = true;
    }
  }

  //  @Native
  @NotNull
  @SuppressWarnings("UnusedAssignment")
  public List<Anomaly> catchAnomaliesOf(User user, boolean delay) {
    if (user.hasPlayer()) {
      return Collections.emptyList();
    }
    Player player = user.player();
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    boolean isPartner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
    boolean isEnterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
    int amountOfPlugins = Bukkit.getPluginManager().getPlugins().length;
    boolean trust = IntaveControl.DISABLE_LICENSE_CHECK || !plugin.getServer().getOnlineMode() || isPartner || isEnterprise || !(onlinePlayers.size() <= 5 || player.isOp() || amountOfPlugins <= 5);

    List<Anomaly> anomalies = metaOf(user).anomalies;
    anomalies.removeIf(Anomaly::expired);
    anomalies = new ArrayList<>(anomalies);
    if (delay) {
      // filter non-active (delay)
      anomalies.removeIf(anomaly -> !anomaly.active());
    }
    if (!trust) {
      anomalies.removeIf(anomaly -> !anomaly.forceApply());
    }
    Anomaly combined = combinator.combined(anomalies);
    if (combined != null) {
      anomalies.add(combined);
    }
//    anomalies = new ArrayList<>(anomalies);
    return anomalies;
  }

  public List<Confidence> resolveConfidencesOf(List<Anomaly> anomalies) {
    anomalies.sort(Comparator.comparingDouble(value -> value.confidence().level()));
    Collections.reverse(anomalies);
    Map<String, Integer> types = new HashMap<>();
    List<Confidence> allConfidences = new ArrayList<>();
    for (Anomaly existingAnomaly : anomalies) {
      String key = existingAnomaly.key();
      if (types.getOrDefault(key, 0) <= existingAnomaly.limit() || existingAnomaly.limit() == 0) {
        allConfidences.add(existingAnomaly.confidence());
      }
      types.put(key, types.getOrDefault(key, 0) + 1);
    }
    return allConfidences;
  }

  private Anomaly.Type findDominantTypeIn(List<Anomaly> anomalies) {
    return anomalies.stream()
      .collect(Collectors.groupingBy(Anomaly::type, Collectors.counting()))
      .entrySet()
      .stream()
      .max(Comparator.comparingLong(Map.Entry::getValue))
      .orElseThrow(IllegalStateException::new)
      .getKey();
  }

  @Nullable
  @Deprecated
  private MiningStrategy findSuitableMiningStrategy(
    List<Anomaly> anomalies,
    Confidence overallConfidence,
    MiningStrategy lastMiningStrategy
  ) {
    boolean miningSuggested = anomalies.stream().anyMatch(Anomaly::miningSuggested);
    if (!miningSuggested) {
      return null;
    }
    MiningStrategy strategy = null;
    boolean triedEmulationLight = lastMiningStrategy == MiningStrategy.EMULATION_LIGHT;
    switch (overallConfidence) {
      case MAYBE:
      case ALMOST_CERTAIN:
        strategy = MiningStrategy.EMULATION_LIGHT;
        break;
      case PROBABLE:
        strategy = random() && !triedEmulationLight ? MiningStrategy.EMULATION_LIGHT : MiningStrategy.EMULATION_MODERATE;
        break;
      case VERY_LIKELY:
        strategy = random() && !triedEmulationLight ? MiningStrategy.EMULATION_LIGHT : MiningStrategy.EMULATION_HEAVY;
        break;
    }
    return strategy;
  }

  @Deprecated
  private boolean random() {
    return ThreadLocalRandom.current().nextBoolean();
  }

  @Deprecated
  private void performMiningStrategy(User user, MiningStrategy miningStrategy) {
    miningStrategy.apply(user);
  }

  public Confidence computeOverallConfidence(List<Confidence> confidences) {
    return computeOverallConfidence(confidences.toArray(new Confidence[0]));
  }

  public Confidence computeOverallConfidence(Confidence... confidences) {
    return confidenceFrom(levelFrom(confidences));
  }

  // events

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveUseEntity(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
    Player player = event.getPlayer();
    HeuristicMeta heuristicMeta = metaOf(player);
    if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      if (heuristicMeta.overallAttacks++ == 0) {
        heuristicMeta.firstAttack = System.currentTimeMillis();
      }
    }
  }

  @BukkitEventSubscription
  @Deprecated
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity damager = event.getDamager();
    if (!(damager instanceof Player)) {
      return;
    }
    Player player = (Player) damager;
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    if (attackData.activeMiningStrategy != null) {
      MiningStrategyExecutor executor = attackData.activeMiningStrategy.executor();
      executor.receiveAttackOfPlayer(event);
    }
  }

  @Deprecated
  private void tryRemoveMiningStrategy(
    MiningStrategyContainer miningStrategyContainer
  ) {
    MiningStrategyExecutor executor = miningStrategyContainer.executor();
    boolean expired = executor.expired();
    if (expired) {
      executor.unregisterStrategy();
    }
  }

  @PacketSubscription(
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE
    }
  )
  @Deprecated
  public void receiveMovement(ProtocolPacketEvent event) {
    if (event.isCancelled()) {
      return;
    }
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    MiningStrategyContainer activeMiningStrategy = attackData.activeMiningStrategy;
    if (activeMiningStrategy == null) {
      return;
    }
    MiningStrategyExecutor executor = activeMiningStrategy.executor();
    if (executor.threshold > 0) {
      executor.threshold -= 0.002;
    }
  }

  @BukkitEventSubscription
  public void receiveQuit(PlayerQuitEvent quit) {
    Player player = quit.getPlayer();
//    evaluate(player, true);
  }

  //  @Native
  private String resolveIdentifier(List<Anomaly> anomalies) {
    return restructureForOutput(anomalies).stream()
      .map(anomaly -> "p[" + anomaly.key() + "]")
      .collect(Collectors.joining(","));
  }

  private List<Anomaly> restructureForOutput(List<Anomaly> anomalies) {
    // Remove anomalies without effect
    anomalies.removeIf(anomaly -> anomaly.confidence() == NONE);

    // Remove duplicated anomalies
    List<String> knownPatterns = Lists.newArrayList();
    List<Anomaly> suitableAnomalies = Lists.newArrayList();

    for (Anomaly anomaly : anomalies) {
      String pattern = anomaly.key();
      if (!knownPatterns.contains(pattern)) {
        knownPatterns.add(pattern);
        suitableAnomalies.add(anomaly);
      }
    }
    anomalies = suitableAnomalies;

    // Format anomalies after their priority
    if (anomalies.size() > 2) {
      anomalies.sort(Comparator.comparingInt(o -> o.confidence().level()));
      Collections.reverse(anomalies);
      List<Anomaly> reducedAnomalies = Lists.newArrayList();
      for (int i = 0; i <= 1; i++) {
        reducedAnomalies.add(anomalies.get(i));
      }
      anomalies = reducedAnomalies;
    }
    return anomalies;
  }

  public static class HeuristicMeta extends CheckCustomMetadata {
    public List<Anomaly> anomalies = Lists.newCopyOnWriteArrayList();
    public int overallAttacks = 0;
    public long firstAttack = Long.MAX_VALUE;
  }
}
