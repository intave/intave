package de.jpx3.intave.check.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Combinator;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.OldAirClickLimitHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.SwingDeviationHeuristics;
import de.jpx3.intave.check.combat.heuristics.detect.clickpatterns.SwingLimitHeuristics;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.AttackRequiredHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.BaritoneRotationCheck;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.LongTermClickAccuracyHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.LongTermClickAccuracyRelayHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.PerfectAttackHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.PreAttackHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationAccuracyPitchHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationAccuracyYawHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationModuloResetHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationSensitivityHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationStandardDeviationHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.RotationStandardDeviationRelayHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.combatpatterns.SameRotationHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionDetermination;
import de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionFluctuation;
import de.jpx3.intave.check.combat.heuristics.detect.inventory.PacketInventoryHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.AttackInInvalidStateHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.BlockingHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.CivbreakHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.DoubleEntityActionHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.InvalidFlyingPacketHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.JumpVelocityHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.NoSwingHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.PacketOrderHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.PacketOrderSwingHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.PacketPlayerActionToggleHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.ReshapedJumpHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.RotationSnapHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.SprintOnAttackHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.other.ToolSwitchHeuristic;
import de.jpx3.intave.check.combat.heuristics.detect.testing.TestingHeuristic;
import de.jpx3.intave.check.combat.heuristics.mine.MiningStrategyContainer;
import de.jpx3.intave.check.combat.heuristics.mine.MiningStrategyExecutor;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.storage.HeuristicsStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static de.jpx3.intave.check.combat.heuristics.Confidence.ALMOST_CERTAIN;
import static de.jpx3.intave.check.combat.heuristics.Confidence.CERTAIN;
import static de.jpx3.intave.check.combat.heuristics.Confidence.MAYBE;
import static de.jpx3.intave.check.combat.heuristics.Confidence.NONE;
import static de.jpx3.intave.check.combat.heuristics.Confidence.confidenceFrom;
import static de.jpx3.intave.check.combat.heuristics.Confidence.levelFrom;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.FLYING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.VEHICLE_MOVE;

public final class Heuristics extends MetaCheck<Heuristics.HeuristicMeta> {
  private static Boolean legacyConfigLayCache = null;
  private final IntavePlugin plugin;
  private final Combinator combinator;

  public Heuristics(IntavePlugin plugin) {
    super("Heuristics", "heuristics", HeuristicMeta.class);
    this.plugin = plugin;
    this.combinator = new Combinator(this);

    this.setupSubChecks();
    this.setupEvaluationScheduler(plugin);
  }

  public static boolean legacyConfigurationLayout() {
    if (legacyConfigLayCache != null) {
      return legacyConfigLayCache;
    }
    YamlConfiguration settings = IntavePlugin.singletonInstance().settings();
    ConfigurationSection section = settings.getConfigurationSection("check.heuristics.cloud-thresholds.on-premise");
    if (section != null) {
      return legacyConfigLayCache = false;
    } else {
      return legacyConfigLayCache = true;
    }
  }

  public static void resetConfigurationCache() {
    legacyConfigLayCache = null;
  }

  private void setupEvaluationScheduler(IntavePlugin plugin) {
    //noinspection deprecation
    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::evaluateAll, 0, 20 * 20);
    TaskTracker.begun(taskId);
  }

  public void setupSubChecks() {
    appendCheckPart(new OldAirClickLimitHeuristic(this));
//        appendCheckPart("de.jpx3.intave.check.combat.heuristics.detect.other.AttackReduceIgnoreHeuristic");
    appendCheckPart(new RotationStandardDeviationHeuristic(this));
    appendCheckPart(new RotationStandardDeviationRelayHeuristic(this));
    appendCheckPart(new RotationSnapHeuristic(this));
    appendCheckPart(new LongTermClickAccuracyHeuristic(this));
    appendCheckPart(new LongTermClickAccuracyRelayHeuristic(this));
    appendCheckPart(new ReshapedJumpHeuristic(this));
    appendCheckPart(new RotationAccuracyYawHeuristic(this));
    appendCheckPart(new RotationAccuracyPitchHeuristic(this));
    appendCheckPart(new PerfectAttackHeuristic(this));
    appendCheckPart(new RotationSensitivityHeuristic(this));
    appendCheckPart(new RotationModuloResetHeuristic(this));
    appendCheckPart(new PreAttackHeuristic(this));

    appendCheckPart(new SameRotationHeuristic(this));
    appendCheckPart(new AttackRequiredHeuristic(this));
    appendCheckPart(new PacketOrderHeuristic(this));
    appendCheckPart(new BaritoneRotationCheck(this));
    appendCheckPart(new ToolSwitchHeuristic(this));

    // for testing
    appendCheckPart(new RotationPrevisionFluctuation(this));
    appendCheckPart(new TestingHeuristic(this));

    // Lucky experimental heuristics
    appendCheckPart(new RotationPrevisionDetermination(this));
    appendCheckPart(new SwingLimitHeuristics(this));
    appendCheckPart(new SwingDeviationHeuristics(this));
//    appendCheckPart(new RotationAngleHeuristic(this));

    appendCheckPart(new PacketOrderSwingHeuristic(this));
    appendCheckPart(new PacketPlayerActionToggleHeuristic(this));
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
  }

  private void evaluateAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      try {
        evaluate(onlinePlayer, false);
      } catch (UnsupportedFallbackOperationException ignored) {
      }
    }
  }

  public void evaluate(Player player, boolean enforceDecision) {
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    HeuristicsStorage storage = user.storageOf(HeuristicsStorage.class);

    // External confidence
    List<Anomaly> activeAnomalies = catchAnomaliesOf(user);
    List<Confidence> activeConfidence = resolveConfidencesOf(activeAnomalies);
    int overallActiveConfidenceLevel = levelFrom(activeConfidence.toArray(new Confidence[0]));
    Confidence overallActiveConfidence = computeOverallConfidence(activeConfidence);

    // Internal confidence
    List<Anomaly> allAnomalies = catchAnomaliesOf(user);
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
      List<Anomaly> anomaliesToOutput = restructureForOutput(activeAnomalies);
      String identifier = anomaliesToOutput.stream().map(Anomaly::key).collect(Collectors.joining(","));
      String threshold;
      if (legacyConfigurationLayout()) {
        threshold = "confidence-thresholds." + overallActiveConfidence.output();
      } else {
        threshold = "cloud-thresholds.on-premise";
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

  @NotNull
  public List<Anomaly> catchAnomaliesOf(User user) {
    if (!user.hasPlayer()) {
      return Collections.emptyList();
    }

    List<Anomaly> anomalies = metaOf(user).anomalies;
    anomalies.removeIf(Anomaly::expired);
    anomalies = new ArrayList<>(anomalies);
    Anomaly combined = combinator.combined(anomalies);
    if (combined != null) {
      anomalies.add(combined);
    }
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

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    HeuristicMeta heuristicMeta = metaOf(player);
    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
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
  public void receiveMovement(PacketEvent event) {
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
