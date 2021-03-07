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
import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.detect.checks.combat.heuristics.detection.*;
import de.jpx3.intave.detect.checks.combat.heuristics.mining.MiningStrategyExecutor;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaAttackData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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
    //noinspection deprecation
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
    if(IntaveControl.USE_AUTOCLICKER_CHECK) {
      appendCheckPart(new AirClickLimitHeuristic(this));
    }
    appendCheckPart(new RotationLHeuristics(this));
    appendCheckPart(new AttackReduceIgnoreHeuristic(this));
    appendCheckPart(new PacketInventoryHeuristic(this));
    appendCheckPart(new BlockingHeuristic(this));
//    appendCheckPart(new LinearRegressionHeuristic(this));
  }

  public void saveAnomaly(Player player, Anomaly anomaly) {
    metaOf(player).anomalies.add(anomaly);
    performMiningStrategy(player);
    Synchronizer.synchronize(() -> debug(player, anomaly));
  }

  @Native
  private void debug(Player player, Anomaly anomaly) {
    HeuristicMeta heuristicMeta = metaOf(player);
    List<Anomaly> anomalies = heuristicMeta.anomalies;
    anomalies.removeIf(Anomaly::expired);
    anomalies = new ArrayList<>(anomalies);

    List<Confidence> allConfidences = resolveConfidencesOf(anomalies);
    Confidence overallConfidence = computeOverallConfidence(allConfidences);

    String pattern = anomaly.key();
    String description = anomaly.description();
    String message = ChatColor.RED + "[IH] " + player.getName() + " on p[" + pattern + "]" + overallConfidence.output() + " " + description;

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibylIntegrationService().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    for (Player authenticatedPlayer : Bukkit.getOnlinePlayers()) {
      if (plugin.sibylIntegrationService().isAuthenticated(authenticatedPlayer)) {
        authenticatedPlayer.sendMessage(message);
      }
    }
  }

  private void evaluateAll() {
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      evaluate(onlinePlayer, false);
    }
  }

  public void evaluate(Player player, boolean enforceDecision) {
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    if (!IntaveControl.DISABLE_LICENSE_CHECK && onlinePlayers.size() <= 6) {
      return;
    }

    HeuristicMeta heuristicMeta = metaOf(player);
    List<Anomaly> anomalies = heuristicMeta.anomalies;
    anomalies.removeIf(Anomaly::expired);
    anomalies = new ArrayList<>(anomalies);

    // filter non active (delay)
    anomalies.removeIf(anomaly -> !anomaly.active());

    List<Confidence> allConfidences = resolveConfidencesOf(anomalies);
    Confidence overallConfidence = computeOverallConfidence(allConfidences);
    if (overallConfidence.level() >= Confidence.LIKELY.level()) {
      Anomaly.Type type = findDominantType(anomalies);
      String identifier;
      if (IntaveControl.DEBUG_HEURISTICS) {
        identifier = anomaliesForId(anomalies).stream().map(anomaly -> "p[" + anomaly.key() + "]").collect(Collectors.joining(","));
      } else {
        identifier = resolveIdentifier(anomalies);
      }
      String details = type.details() + ": " + overallConfidence.name().toLowerCase().replace("_", " ") + " / " + identifier;
      plugin.violationProcessor().processViolation(player, 25, this.name(), "is fighting suspiciously", details, "confidence-thresholds." + overallConfidence.output());
    }
  }

  private List<Confidence> resolveConfidencesOf(List<Anomaly> anomalies) {
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

  private Anomaly.Type findDominantType(List<Anomaly> anomalies) {
    return anomalies.stream()
      .collect(Collectors.groupingBy(Anomaly::type, Collectors.counting()))
      .entrySet()
      .stream()
      .max(Comparator.comparingLong(Map.Entry::getValue))
      .orElseThrow(IllegalMonitorStateException::new)
      .getKey();
  }

  private void performMiningStrategy(Player player) {
    User user = userOf(player);
    UserMetaAttackData attackData = user.meta().attackData();
    HeuristicMeta heuristicMeta = metaOf(player);
    List<Anomaly> anomalies = heuristicMeta.anomalies;
    List<Confidence> allConfidences = resolveConfidencesOf(anomalies);
    Confidence overallConfidence = computeOverallConfidence(allConfidences);
    Set<MiningStrategy> activeMiningStrategies = attackData.activeMiningStrategies.keySet();
    if (overallConfidence.level() >= Confidence.PROBABLE.level()) {
      // perform mining strategies
      MiningStrategy suitableMiningStrategy = findSuitableMiningStrategy(activeMiningStrategies, anomalies, overallConfidence);
      if (suitableMiningStrategy != null) {
        performMiningStrategy(user, suitableMiningStrategy);
      }
    }
  }

  private MiningStrategy findSuitableMiningStrategy(
    Collection<MiningStrategy> activeStrategies,
    List<Anomaly> anomalies,
    Confidence overallConfidence
  ) {
    List<MiningStrategy> availableMiningStrategies = Arrays.stream(MiningStrategy.values()).collect(Collectors.toList());
    availableMiningStrategies.removeAll(activeStrategies);
    List<MiningStrategy> sorted = availableMiningStrategies
      .stream()
      .filter(miningStrategy -> overallConfidence.level() >= miningStrategy.detectionConfidence().level())
      .sorted().collect(Collectors.toList());
    boolean requiresHeavyCombat = anomalies.stream().anyMatch(Anomaly::requiresHeavyCombat);
    sorted.removeIf(miningStrategy -> miningStrategy == MiningStrategy.EMULATION_HEAVY && !requiresHeavyCombat);
    return sorted.isEmpty() ? null : sorted.get(sorted.size() - 1);
  }

  private void performMiningStrategy(User user, MiningStrategy miningStrategy) {
    miningStrategy.apply(user);
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

  @BukkitEventSubscription
  public void receiveAttack(EntityDamageByEntityEvent event) {
    Entity damager = event.getDamager();
    if (!(damager instanceof Player)) {
      return;
    }
    Player player = (Player) damager;
    User user = userOf(player);
    UserMetaAttackData attackData = user.meta().attackData();
    if (!attackData.activeMiningStrategies.isEmpty()) {
      removeExpiredStrategies(attackData.activeMiningStrategies);
      submitAttackToExecutors(event, attackData.activeMiningStrategies.values());
    }
  }

  private void submitAttackToExecutors(
    EntityDamageByEntityEvent event,
    Iterable<MiningStrategyExecutor> activeMiningStrategies
  ) {
    for (MiningStrategyExecutor miningStrategy : activeMiningStrategies) {
      miningStrategy.receiveAttackOfPlayer(event);
    }
  }

  private void removeExpiredStrategies(
    Map<MiningStrategy, MiningStrategyExecutor> activeMiningStrategies
  ) {
    Collection<MiningStrategyExecutor> executors = activeMiningStrategies.values();
    executors.stream()
      .filter(MiningStrategyExecutor::expired)
      .forEach(MiningStrategyExecutor::stopStrategy);
    executors.removeIf(MiningStrategyExecutor::expired);
  }

  @BukkitEventSubscription
  public void receiveQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    evaluate(player, true);
  }

  // encryption

  @Native
  private String resolveIdentifier(List<Anomaly> anomalies) {
    return encryptAnomalies(anomaliesForId(anomalies));
  }

  private List<Anomaly> anomaliesForId(List<Anomaly> anomalies) {
    // Remove anomalies without effect
    anomalies.removeIf(anomaly -> anomaly.confidence() == Confidence.NONE);

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

  @Native
  private String encryptAnomalies(List<Anomaly> anomalies) {
    List<String> usableAnomalies = new ArrayList<>();
    for (Anomaly anomaly : anomalies) {
      String key = anomaly.key();
      if (!usableAnomalies.contains(key)) {
        usableAnomalies.add(key);
      }
    }
    StringBuilder nonPaddedBuilder = new StringBuilder();
    for (String pattern : usableAnomalies) {
      int size = usableAnomalies.size();
      int subCheck = Integer.parseInt(pattern.substring(pattern.length() - 1));
      int mainCheck = Integer.parseInt(pattern.substring(0, pattern.length() - 1));
      int checkCombined = mainCheck << 3 | subCheck;
      checkCombined ^= 452938422 ^ 987509231 ^ size;
      for (int i = 0; i < size * 2; i++) {
        checkCombined ^= size * 28037423 * i;
        checkCombined ^= 928344123 * size;
        checkCombined ^= i * 4203874;
      }
      byte[] encode = Base64.getEncoder().encode(new byte[]{(byte) checkCombined});
      String result = new String(encode).replace("=", "");
      result = result.length() > 10 ? result.substring(0, 10) : result;
      nonPaddedBuilder.append(result);
    }
    String pattern = nonPaddedBuilder.toString();
    String string;
    boolean exceededLength = pattern.length() >= 4;
    int endingGarbageCharacters = exceededLength ? -1 : 6 - pattern.length();
    endingGarbageCharacters ^= pattern.charAt(0);
    String first = new String(Base64.getEncoder().encode(new byte[]{(byte) endingGarbageCharacters}));
    first = first.replace("=", "");
    if (pattern.length() >= 4) {
      string = first + pattern;
    } else {
      StringBuilder patternStringBuilder = new StringBuilder();
      patternStringBuilder.append(first);
      patternStringBuilder.append(pattern);
      while (patternStringBuilder.length() < 6) {
        int garbageCharacter = Math.max(1, new SecureRandom().nextInt(64));
        String garbage = new String(Base64.getEncoder().encode(new byte[]{(byte) garbageCharacter}));
        garbage = garbage.replace("=", "");
        patternStringBuilder.append(garbage);
      }
      string = patternStringBuilder.toString();
    }
    char characterA = (char) Base64.getEncoder().encode(new byte[]{(byte) new SecureRandom().nextInt(0b111111)})[0];
    char characterB = (char) Base64.getEncoder().encode(new byte[]{(byte) new SecureRandom().nextInt(0b111111)})[0];
    byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < bytes.length; i++) {
      int key = characterA ^ characterB % (i + 1 ^ characterB * 5);
      bytes[i] ^= key;
    }
    String encode = new String(Base64.getEncoder().encode(bytes));
    encode = encode.replace("=", "");
    return String.valueOf(characterA) + characterB + encode;
  }

  public static class HeuristicMeta extends UserCustomCheckMeta {
    public List<Anomaly> anomalies = Lists.newCopyOnWriteArrayList();
    public int overallAttacks = 0;
    public long firstAttack = Long.MAX_VALUE;
  }
}