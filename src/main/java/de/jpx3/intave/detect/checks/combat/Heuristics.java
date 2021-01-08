package de.jpx3.intave.detect.checks.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.user.UserCustomCheckMeta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::evaluateAll, 0, 400);
  }

  public void setupSubChecks() {
//    appendCheckPart(new ExampleHeuristic(this));
//    appendCheckPart(new ReshapedJumpHeuristic(this));
//    appendCheckPart(new RotationStandardDeviationHeuristic(this));
  }

  public void saveAnomaly(Player player, Anomaly anomaly) {
    metaOf(player).anomalies.add(anomaly);
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

    List<Confidence> confidences = new ArrayList<>();
    for (Anomaly anomaly : anomalies) {
      confidences.add(anomaly.confidence());
    }
    Confidence overallConfidence = computeOverallConfidence(confidences);

    if (overallConfidence.level() >= Confidence.PROBABLE.level()) {
      boolean hasPerformedMiningStrategyYet = !heuristicMeta.performedMiningStrategies.isEmpty();
      boolean mightBeAGoodIdeaToPerformMiningStrategy = overallConfidence.level() <= Confidence.VERY_LIKELY.level();

      if(!hasPerformedMiningStrategyYet && mightBeAGoodIdeaToPerformMiningStrategy) {

      }

      plugin.retributionService().markPlayer(player, -1, this.name(), "is fighting suspiciously (confidence: " + overallConfidence.output() + ")");
    }
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
    return MiningStrategy.RATING
            .keySet()
            .stream()
            .filter(
              miningStrategy -> availableMiningStrategies.contains(miningStrategy) &&
                                miningStrategy.detectionConfidence().level() > requiredConfidence.level()
            ).findFirst()
            .orElseThrow(IllegalStateException::new);
  }

  private void performMiningStrategy(Player player, MiningStrategy miningStrategy) {
    HeuristicMeta heuristicMeta = metaOf(player);
    if(heuristicMeta.performedMiningStrategies.contains(miningStrategy)) {
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
