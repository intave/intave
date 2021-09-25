package de.jpx3.intave.check.combat;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Combinator;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.check.combat.heuristics.detect.*;
import de.jpx3.intave.check.combat.heuristics.mine.MiningStrategyContainer;
import de.jpx3.intave.check.combat.heuristics.mine.MiningStrategyExecutor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::evaluateAll, 0, 400);
    TaskTracker.begun(taskId);
  }

  @Native
  public void setupSubChecks() {
    boolean enterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;

    if (enterprise) {
      appendCheckPart(new AirClickLimitHeuristic(this));
      appendCheckPart(new AttackReduceIgnoreHeuristic(this));
      appendCheckPart(new RotationStandardDeviationHeuristic(this));
      appendCheckPart(new RotationSnapHeuristic(this));
      appendCheckPart(new LongTermClickAccuracyHeuristic(this));
    }

    if (IntaveControl.GOMME_MODE || IntaveControl.DISABLE_LICENSE_CHECK) {
      appendCheckPart(new SameRotationHeuristic(this));
      appendCheckPart(new AttackRequiredHeuristic(this));
    }

    appendCheckPart(new PreAttackHeuristic(this));
//    appendCheckPart(new RotationAngleHeuristic(this));

    appendCheckPart(new ReshapedJumpHeuristic(this));
    appendCheckPart(new RotationAccuracyYawHeuristic(this));
    appendCheckPart(new RotationAccuracyPitchHeuristic(this));
    appendCheckPart(new PerfectAttackHeuristic(this));
    appendCheckPart(new RotationSensitivityHeuristic(this));
    appendCheckPart(new RotationModuloResetHeuristic(this));
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
  }

  public void saveAnomaly(Player player, Anomaly anomaly) {
    if (anomaly.confidence().level() > Confidence.NONE.level()) {
      HeuristicMeta meta = metaOf(player);
      boolean noLimit = anomaly.limit() == 0;
      int limit = anomaly.limit();
      int betterFound = (int) meta.anomalies
        .stream()
        .filter(anomaly1 -> anomaly1.key().equals(anomaly.key()) && anomaly1.confidence().atLeast(anomaly.confidence()))
        .count();
      if (noLimit || betterFound <= limit) {
        meta.anomalies.add(anomaly);
      }
    }
    Synchronizer.synchronize(() -> debug(player, anomaly));
  }

  @Native
  private void debug(Player player, Anomaly anomaly) {
    User user = userOf(player);
    List<Anomaly> anomalies = catchAnomaliesOf(user, false);
    List<Confidence> allConfidences = resolveConfidencesOf(anomalies);
    Confidence overallConfidence = computeOverallConfidence(allConfidences);

    String pattern = anomaly.key();
    String description = anomaly.description();

    String confidenceDetails = overallConfidence.output() + " (" + Confidence.levelFrom(allConfidences.toArray(new Confidence[0])) + "+" + anomaly.confidence().level() + ")";
    String message = ChatColor.RED + "[IH] " + player.getName() + " on p[" + pattern + "]" + confidenceDetails + " " + description;

    if (IntaveControl.DEBUG_HEURISTICS && !plugin.sibylIntegrationService().isAuthenticated(player)) {
      player.sendMessage(message);
    }

    if (IntaveControl.GOMME_MODE) {
      IntaveLogger.logger().printLine(message);
    }

    for (Player authenticatedPlayer : MessageChannelSubscriptions.sibylReceiver()/*Bukkit.getOnlinePlayers()*/) {
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

  @Native
  public void evaluate(Player player, boolean enforceDecision) {
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();

    // External confidence
    List<Anomaly> anomalies = catchAnomaliesOf(user, true);
    List<Confidence> allConfidences = resolveConfidencesOf(anomalies);
    Confidence overallConfidence = computeOverallConfidence(allConfidences);

    // Internal confidence
    List<Anomaly> anomaliesWithoutDelay = catchAnomaliesOf(user, false);
    List<Confidence> allConfidencesWithoutDelay = resolveConfidencesOf(anomaliesWithoutDelay);
    Confidence overallConfidenceWithoutDelay = computeOverallConfidence(allConfidencesWithoutDelay);

    if (attackData.activeMiningStrategy != null) {
      this.tryRemoveMiningStrategy(attackData.activeMiningStrategy);
    }

    boolean suitableConfidence = overallConfidenceWithoutDelay.level() >= Confidence.MAYBE.level() && overallConfidenceWithoutDelay.level() < Confidence.CERTAIN.level();
    if (IntaveControl.USE_MINING_STRATEGIES && suitableConfidence && !enforceDecision) {
      // perform mining strategies
      if (attackData.activeMiningStrategy == null) {
        MiningStrategy strategy = findSuitableMiningStrategy(
          anomaliesWithoutDelay,
          overallConfidenceWithoutDelay,
          attackData.lastMiningStrategy
        );
        if (strategy != null) {
          performMiningStrategy(user, strategy);
        }
      }
    }

    if (overallConfidence.level() >= Confidence.LIKELY.level()) {
      Anomaly.Type type = findDominantType(anomalies);
      String identifier;
      if (IntaveControl.DEBUG_HEURISTICS) {
        identifier = restructureForOutput(anomalies).stream().map(anomaly -> "p[" + anomaly.key() + "]").collect(Collectors.joining(","));
      } else {
        identifier = resolveIdentifier(anomalies);
      }
      String threshold = "confidence-thresholds." + overallConfidence.output();
      String message = "is fighting suspiciously";
      String details = type.details() + ": " + define(overallConfidence) + " / " + identifier;
      Violation violation = Violation.builderFor(Heuristics.class)
        .forPlayer(player).withMessage(message).withDetails(details)
        .withCustomThreshold(threshold).withVL(25)
        .build();
      Modules.violationProcessor().processViolation(violation);
    }
  }

  @Native
  private String define(Confidence confidence) {
    switch (confidence) {
      case CERTAIN:
        return "certain (!!)";
      case VERY_LIKELY:
        return "very likely (!)";
      case LIKELY:
        return "likely (?!)";
      case PROBABLE:
        return "probable (?)";
      case MAYBE:
        return "maybe (??)";
      default:
        return "none";
    }
  }

  @SuppressWarnings("UnusedAssignment")
  @Native
  public List<Anomaly> catchAnomaliesOf(User user, boolean delay) {
    Player player = user.player();
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    boolean isPartner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
    boolean isEnterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
    boolean trust = IntaveControl.DISABLE_LICENSE_CHECK || isPartner || isEnterprise || !(onlinePlayers.size() <= 5 || player.isOp());
    List<Anomaly> anomalies = metaOf(user).anomalies;
    anomalies.removeIf(Anomaly::expired);
    anomalies = new ArrayList<>(anomalies);
    if (delay) {
      // filter non active (delay)
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

  private Anomaly.Type findDominantType(List<Anomaly> anomalies) {
    return anomalies.stream()
      .collect(Collectors.groupingBy(Anomaly::type, Collectors.counting()))
      .entrySet()
      .stream()
      .max(Comparator.comparingLong(Map.Entry::getValue))
      .orElseThrow(IllegalStateException::new)
      .getKey();
  }

  @Nullable
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
      case VERY_LIKELY:
        strategy = MiningStrategy.EMULATION_LIGHT;
        break;
      case PROBABLE:
        strategy = random() && !triedEmulationLight ? MiningStrategy.EMULATION_LIGHT : MiningStrategy.EMULATION_MODERATE;
        break;
      case LIKELY:
        strategy = random() && !triedEmulationLight ? MiningStrategy.EMULATION_LIGHT : MiningStrategy.EMULATION_HEAVY;
        break;
    }
    return strategy;
  }

  private boolean random() {
    return ThreadLocalRandom.current().nextBoolean();
  }

  private void performMiningStrategy(User user, MiningStrategy miningStrategy) {
    miningStrategy.apply(user);
  }

  public Confidence computeOverallConfidence(List<Confidence> confidences) {
    return computeOverallConfidence(confidences.toArray(new Confidence[0]));
  }

  public Confidence computeOverallConfidence(Confidence... confidences) {
    return Confidence.confidenceFrom(Confidence.levelFrom(confidences));
  }

  // events

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

  @BukkitEventSubscription
  public void receiveQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    evaluate(player, true);
  }

  // encryption

  @Native
  private String resolveIdentifier(List<Anomaly> anomalies) {
    return encryptAnomalies(restructureForOutput(anomalies));
  }

  private List<Anomaly> restructureForOutput(List<Anomaly> anomalies) {
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

  public static class HeuristicMeta extends CheckCustomMetadata {
    public List<Anomaly> anomalies = Lists.newCopyOnWriteArrayList();
    public int overallAttacks = 0;
    public long firstAttack = Long.MAX_VALUE;
  }
}