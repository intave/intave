package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.detect.checks.combat.heuristics.ReshapedJumpHeuristic;
import de.jpx3.intave.detect.checks.combat.heuristics.RotationStandardDeviationHeuristic;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.UserCustomCheckMeta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class Heuristics extends IntaveMetaCheck<Heuristics.HeuristicMeta> {
  private final IntavePlugin plugin;

  public Heuristics(IntavePlugin plugin) {
    super("Heuristics", "heuristics", HeuristicMeta.class);
    this.plugin = plugin;
    this.setupSubChecks();
    this.setupEvaluationScheduler(plugin);
  }

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

  private void setupEvaluationScheduler(IntavePlugin plugin) {
    Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::evaluateAll, 0, 400);
  }

  public void setupSubChecks() {
    appendCheckPart(new ReshapedJumpHeuristic(this));
    appendCheckPart(new RotationStandardDeviationHeuristic(this));
  }

  public void saveAnomaly(Player player, Anomaly anomaly) {
    metaOf(player).anomalies.add(anomaly);
//    player.sendMessage(ChatColor.RED + "[HEUR] [DEB] Save anomaly " + anomaly.confidence.name() + " at pattern " + anomaly.description);
  }

  public void evaluateAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      evaluate(onlinePlayer, false);
    }
  }

  public void evaluate(Player player, boolean enforceDecision) {
    HeuristicMeta heuristicMeta = metaOf(player);
    List<Anomaly> anomalies = heuristicMeta.anomalies;
    anomalies.removeIf(Anomaly::expired);

    List<MiningStrategy> recommendedMiningStrategies = new ArrayList<>();
    List<Confidence> confidences = new ArrayList<>();
    for (Anomaly anomaly : anomalies) {
      confidences.add(anomaly.confidence);
      recommendedMiningStrategies.add(anomaly.recommendedMiningStrategy);
    }
    Confidence overallConfidence = computeOverallConfidence(confidences);

    if (overallConfidence.level >= Confidence.PROBABLE.level()) {
      boolean hasPerformedMiningStrategyYet = !heuristicMeta.performedMiningStrategies.isEmpty();
      boolean mightBeAGoodIdeaToPerformMiningStrategy = overallConfidence.level <= Confidence.VERY_LIKELY.level();

      if (!hasPerformedMiningStrategyYet && mightBeAGoodIdeaToPerformMiningStrategy) {

      }

      double percentage = resolveConfidencePercentage(overallConfidence.level());
      String confidence = MathHelper.formatDouble(percentage, 2) + "%" + overallConfidence.output();

      plugin.retributionService().markPlayer(player, -1, this.name(), "is fighting suspiciously (confidence: " + confidence + ")");
    }
  }

  private double resolveConfidencePercentage(double confidenceOutput) {
    double percentage = confidenceOutput >= 800 ? 100.0 : confidenceOutput / 800.0 * 100.0;
    if (percentage < 50) {
      percentage += 50;
    }
    return percentage;
  }

  public MiningStrategy findSuitableMiningStrategy(Player player, Confidence overallConfidence) {
    HeuristicMeta heuristicMeta = metaOf(player);
    List<MiningStrategy> availableMiningStrategies = Arrays.stream(MiningStrategy.values()).collect(Collectors.toList());
    availableMiningStrategies.removeAll(heuristicMeta.performedMiningStrategies);

    Confidence confidenceGoal = Confidence.CERTAIN;
    int overallConfidenceInteger = overallConfidence.level;
    int requiredConfidenceInter = confidenceGoal.level - overallConfidenceInteger;
    Confidence requiredConfidence = Confidence.confidenceFrom(requiredConfidenceInter);

    return null;
  }


  public void performMiningStrategy(MiningStrategy miningStrategy) {

  }

  public Confidence computeOverallConfidence(List<Confidence> confidences) {
    return computeOverallConfidence(confidences.toArray(new Confidence[0]));
  }

  public Confidence computeOverallConfidence(Confidence... confidences) {
    return Confidence.confidenceFrom(Confidence.levelFrom(confidences));
  }

  public static class Anomaly {
    private final static long ANOMALY_EXPIRE_DURATION = TimeUnit.MINUTES.toMillis(5);

    private final long added;
    private final String description;
    private final Confidence confidence;
    private final MiningStrategy recommendedMiningStrategy;

    public Anomaly(
      String description,
      Confidence confidence,
      MiningStrategy recommendedMiningStrategy
    ) {
      this.added = AccessHelper.now();
      this.description = description;
      this.confidence = confidence;
      this.recommendedMiningStrategy = recommendedMiningStrategy;
    }

    public long timestamp() {
      return added;
    }

    public String description() {
      return description;
    }

    public Confidence confidence() {
      return confidence;
    }

    public MiningStrategy recommendedMiningStrategy() {
      return recommendedMiningStrategy;
    }

    public boolean expired() {
      return AccessHelper.now() - added > ANOMALY_EXPIRE_DURATION;
    }
  }

  public enum MiningStrategy {
    RAYTRX(player -> {}, 3, Confidence.CERTAIN, -1, false, false),
    IULIA(player -> {}, 1, Confidence.CERTAIN, -1, false, false),
    EMULATION_LIGHT(player -> {}, 1, Confidence.VERY_LIKELY, 10, false, true),
    EMULATION_MODERATE(player -> {}, 2, Confidence.VERY_LIKELY, 10, true, true),
    EMULATION_HEAVY(player -> {}, 3, Confidence.VERY_LIKELY, 10, true, true),
    SWAP_EMULATION(player -> {}, 4, Confidence.CERTAIN, 10, true, true),

    ;

    private final static Map<MiningStrategy, Integer> RATING;

    private final Consumer<Player> apply;
    private final int effectiveness;
    private final Confidence detectionConfidence;
    private final int duration;
    private final boolean observable;
    private final boolean uniqueResponse;

    MiningStrategy(
      Consumer<Player> apply,
      int effectiveness,
      Confidence detectionConfidence, int duration,
      boolean observable,
      boolean uniqueResponse
    ) {
      this.apply = apply;
      this.effectiveness = effectiveness;
      this.detectionConfidence = detectionConfidence;
      this.duration = duration;
      this.observable = observable;
      this.uniqueResponse = uniqueResponse;
    }

    public void apply(Player player) {
      apply.accept(player);
    }

    public int effectiveness() {
      return effectiveness;
    }

    public int duration() {
      return duration;
    }

    public boolean observable() {
      return observable;
    }

    private boolean uniqueResponse() {
      return uniqueResponse;
    }

    private Confidence detectionConfidence() {
      return detectionConfidence;
    }

    static {
      Map<MiningStrategy, Integer> ratings = Arrays.stream(MiningStrategy.values()).collect(Collectors.toMap(value -> value, MiningStrategy::computeStrategyRating, (a, b) -> b));
      RATING = ImmutableMap.copyOf(ratings);
    }

    public static int computeStrategyRating(MiningStrategy strategy) {
      int score = 0;
      score += strategy.effectiveness() * 10;
      score *= (double) strategy.detectionConfidence().level / Confidence.CERTAIN.level();
      score *= strategy.observable() ? 0.8 : 1;
      score *= strategy.uniqueResponse() ? 0.8 : 1;
      return score;
    }
  }

  public enum Confidence {
    CERTAIN("!!!", 1600),
    VERY_LIKELY("!!", 800),
    LIKELY("!", 400),
    PROBABLE("?!", 200),
    UNCERTAIN("?", 50),
    NONE("-", 0),

    ;

    final String output;
    final int level;

    Confidence(String output, int level) {
      this.output = output;
      this.level = level;
    }

    public int level() {
      return level;
    }

    public String output() {
      return output;
    }

    public static int levelFrom(Confidence... confidences) {
      return Arrays.stream(confidences).mapToInt(Confidence::level).sum();
    }

    public static Confidence confidenceFrom(int level) {
      Confidence highest = Confidence.NONE;
      for (Confidence value : Confidence.values()) {
        if (value.level > highest.level && value.level <= level) {
          highest = value;
        }
      }
      return highest;
    }
  }

  public static class HeuristicMeta extends UserCustomCheckMeta {
    public List<Anomaly> anomalies = Lists.newCopyOnWriteArrayList();
    public List<MiningStrategy> performedMiningStrategies = Lists.newCopyOnWriteArrayList();
    public int overallAttacks = 0;
    public long firstAttack = Long.MAX_VALUE;
  }
}
