package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.detect.checks.combat.heuristics.AnomalyEnigma;
import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.detect.checks.combat.heuristics.detection.*;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.UserCustomCheckMeta;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class Heuristics extends IntaveMetaCheck<Heuristics.HeuristicMeta> {
  private final IntavePlugin plugin;

  public Heuristics(IntavePlugin plugin) {
    super("Heuristics", "heuristics", HeuristicMeta.class);
    this.plugin = plugin;
    this.setupSubChecks();
    this.setupEvaluationScheduler(plugin);
  }

  private void setupEvaluationScheduler(IntavePlugin plugin) {
    Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::evaluateAll, 0, 400);
  }

  @Native
  public void setupSubChecks() {
    appendCheckPart(new ReshapedJumpHeuristic(this));
    appendCheckPart(new RotationAccuracyYawHeuristic(this));
    appendCheckPart(new RotationAccuracyPitchHeuristic(this));
    appendCheckPart(new PerfectAttackHeuristic(this));
    appendCheckPart(new RotationSensitivityHeuristic(this));
    appendCheckPart(new RotationStandardDeviationHeuristic(this));
    appendCheckPart(new RotationModuloResetHeuristic(this));
    appendCheckPart(new PacketOrderSwingHeuristic(this));
    appendCheckPart(new PacketSprintToggleHeuristic(this));
    appendCheckPart(new AirClickLimitHeuristic(this));
    appendCheckPart(new RotationLHeuristics(this));
    appendCheckPart(new AttackReduceIgnoreHeuristic(this));
  }

  public void saveAnomaly(Player player, Anomaly anomaly) {
    metaOf(player).anomalies.add(anomaly);
    Synchronizer.synchronize(() -> debug(player, anomaly));
  }

  @Native
  private void debug(Player player, Anomaly anomaly) {
    HeuristicMeta heuristicMeta = metaOf(player);
    List<Anomaly> anomalies = heuristicMeta.anomalies;
    anomalies.removeIf(Anomaly::expired);
    anomalies = new ArrayList<>(anomalies);

    Map<String, Integer> types = new HashMap<>();
    List<Confidence> allConfidences = new ArrayList<>();

    // limit
    for (Anomaly existingAnomaly : anomalies) {
      String key = existingAnomaly.key();
      if (types.getOrDefault(key, 0) <= existingAnomaly.limit() || existingAnomaly.limit() == 0) {
        allConfidences.add(existingAnomaly.confidence());
      }
      types.put(key, types.getOrDefault(key, 0) + 1);
    }

    Confidence overallConfidence = computeOverallConfidence(allConfidences);

    String pattern = formatPattern(anomaly.key());
    String description = anomaly.description();
    String message = ChatColor.RED + "[HEUR] [DEB] " + player.getName() + " on p[" + pattern + "] (" + overallConfidence + "): " + description;

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibylIntegrationService().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
      if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
        authenticatedPlayer.sendMessage(message);
      }
    }
  }

  @Native
  private String formatPattern(String pattern) {
    return pattern.startsWith("0") ? pattern.substring(1) : pattern;
  }

  private void evaluateAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      evaluate(onlinePlayer, false);
    }
  }

  public void evaluate(Player player, boolean enforceDecision) {
    HeuristicMeta heuristicMeta = metaOf(player);
    List<Anomaly> anomalies = heuristicMeta.anomalies;
    anomalies.removeIf(Anomaly::expired);
    anomalies = new ArrayList<>(anomalies);

    // filter non active (delay)
    anomalies.removeIf(anomaly -> !anomaly.active());

    Map<String, Integer> types = new HashMap<>();
    List<Confidence> allConfidences = new ArrayList<>();

    // limit
    for (Anomaly anomaly : anomalies) {
      String key = anomaly.key();
      if (types.getOrDefault(key, 0) <= anomaly.limit() || anomaly.limit() == 0) {
        allConfidences.add(anomaly.confidence());
      }
      types.put(key, types.getOrDefault(key, 0) + 1);
    }

    Confidence overallConfidence = computeOverallConfidence(allConfidences);

    if (overallConfidence.level() >= Confidence.PROBABLE.level()) {
      boolean hasPerformedMiningStrategyYet = !heuristicMeta.performedMiningStrategies.isEmpty();
      boolean mightBeAGoodIdeaToPerformMiningStrategy = overallConfidence.level() <= Confidence.VERY_LIKELY.level();

      if (!hasPerformedMiningStrategyYet && mightBeAGoodIdeaToPerformMiningStrategy) {
        // perform mining strategies
      }
    }

    if (overallConfidence.level() >= Confidence.LIKELY.level()) {
      Anomaly.Type type = findDominantType(anomalies);
      String identifier = resolveIdentifier(anomalies);
      if (IntaveControl.DEBUG_HEURISTICS) {
        identifier = AnomalyEnigma.decryptPatterns(identifier);
      }
      String details = type.details() + overallConfidence.output() + ", " + identifier;
      plugin.retributionService().processViolation(player, 25, this.name(), "is fighting suspiciously", details, "confidence-thresholds." + overallConfidence.output());
    }
  }

  @Native
  private String resolveIdentifier(List<Anomaly> anomalies) {
    // Remove anomalies without effect
    anomalies.removeIf(anomaly -> anomaly.confidence() == Confidence.NONE);

    // Remove duplicated anomalies
    List<String> knownPatterns = Lists.newArrayList();
    Iterator<Anomaly> iterator = anomalies.iterator();
    while (iterator.hasNext()) {
      Anomaly anomaly = iterator.next();
      String pattern = anomaly.key();
      if (knownPatterns.contains(pattern)) {
        iterator.remove();
      } else {
        knownPatterns.add(pattern);
      }
    }

    // Format anomalies for their priority
    if (anomalies.size() > 3) {
      anomalies.sort(Comparator.comparingInt(o -> o.confidence().level()));
      Collections.reverse(anomalies);
      List<Anomaly> reducedAnomalies = Lists.newArrayList();
      for (int i = 0; i < 3; i++) {
        reducedAnomalies.add(anomalies.get(i));
      }
      anomalies = reducedAnomalies;
    }

    return AnomalyEnigma.encryptAnomalies(anomalies);
  }

  private Anomaly.Type findDominantType(List<Anomaly> anomalies) {
    return anomalies.stream().collect(Collectors.groupingBy(Anomaly::type, Collectors.counting())).entrySet().stream().sorted().map(Map.Entry::getKey).findFirst().orElse(null);
  }

  // this implementation is pure garbage, please get some experience with this check and refactor this method
  private MiningStrategy findSuitableMiningStrategy(Player player, Confidence overallConfidence) {
    HeuristicMeta heuristicMeta = metaOf(player);
    List<MiningStrategy> availableMiningStrategies = Arrays.stream(MiningStrategy.values()).collect(Collectors.toList());
    availableMiningStrategies.removeAll(heuristicMeta.performedMiningStrategies);

    Confidence confidenceGoal = Confidence.CERTAIN;
    int overallConfidenceInteger = overallConfidence.level();
    int requiredConfidenceInter = confidenceGoal.level() - overallConfidenceInteger;
    Confidence requiredConfidence = Confidence.confidenceFrom(requiredConfidenceInter);
    return MiningStrategy.RATING.keySet().stream().filter(miningStrategy -> availableMiningStrategies.contains(miningStrategy) && miningStrategy.detectionConfidence().level() > requiredConfidence.level()).findFirst().orElseThrow(IllegalStateException::new);
  }

  private void performMiningStrategy(Player player, MiningStrategy miningStrategy) {
    HeuristicMeta heuristicMeta = metaOf(player);
    if (heuristicMeta.performedMiningStrategies.contains(miningStrategy)) {
      return;
    }
    heuristicMeta.performedMiningStrategies.add(miningStrategy);
    miningStrategy.apply(player);
  }

  private Confidence computeOverallConfidence(List<Confidence> confidences) {
    return computeOverallConfidence(confidences.toArray(new Confidence[0]));
  }

  private Confidence computeOverallConfidence(Confidence... confidences) {
    return Confidence.confidenceFrom(Confidence.levelFrom(confidences));
  }

  // events

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    HeuristicMeta heuristicMeta = metaOf(player);
    PacketContainer packet = event.getPacket();
    if (packet.getEntityUseActions().read(0) == EnumWrappers.EntityUseAction.ATTACK) {
      if (heuristicMeta.overallAttacks++ == 0) {
        heuristicMeta.firstAttack = AccessHelper.now();
      }
    }
  }

  public static class HeuristicMeta extends UserCustomCheckMeta {
    public List<Anomaly> anomalies = Lists.newCopyOnWriteArrayList();
    public List<MiningStrategy> performedMiningStrategies = Lists.newCopyOnWriteArrayList();
    public int overallAttacks = 0;
    public long firstAttack = Long.MAX_VALUE;
  }
}
