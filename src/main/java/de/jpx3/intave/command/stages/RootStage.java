package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.block.shape.ArrayBlockShape;
import de.jpx3.intave.block.shape.CubeShape;
import de.jpx3.intave.block.state.BlockState;
import de.jpx3.intave.block.state.BlockStateAccess;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.check.combat.heuristics.MiningStrategy;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.diagnostic.*;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.CharacterIterator;
import java.text.DecimalFormat;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static de.jpx3.intave.diagnostic.ShapeAccessFlowStudy.*;
import static de.jpx3.intave.math.MathHelper.formatDouble;

public final class RootStage extends CommandStage {
  private static RootStage singletonInstance;
  private final IntavePlugin plugin;

  private RootStage() {
    super(BaseStage.singletonInstance(), "root");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "timings",
    usage = "",
    description = "Output timing data",
    permission = "sibyl"
  )
  @Native
  public void timingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

    Player player = user.player();
    if (plugin.sibylIntegrationService().authentication().isAuthenticated(player)) {
      player.sendMessage(ChatColor.RED + "Loading timings...");
      List<Timing> timings = new ArrayList<>(Timings.timingPool());
      timings.sort(Timing::compareTo);

      timings.forEach(timing -> {
        if (timing.isPacketEventTiming() || timing.isBukkitEventTiming()) {
          return;
        }
        boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
        boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
        String message;
        ChatColor outputColor = suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN;
        message = String.format(
          "%s: %s::%s%s (%s&f %s/c)",
          timing.coloredName(),
          timing.recordedCalls(),
          formatDouble(timing.totalDurationMillis() / 1000d, 2),
          "s",
          outputColor + "" + largeNumberFormat((long) timing.averageCallDurationInNanos()),
          "ns"
        );
        if (!fullSpecifier.isEmpty() && !fullSpecifier.equals("ns") && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
          message = IntavePlugin.defaultColor() + ChatColor.stripColor(message);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
      });
    }
  }

  public static String largeNumberFormat(double value) {
    DecimalFormat df = new DecimalFormat("###,###,###");
    return df.format(value);
  }

  @SubCommand(
    selectors = "eventtimings",
    usage = "",
    description = "Output timing data",
    permission = "sibyl"
  )
  @Native
  public void eventTimingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

    Player player = user.player();
    if (plugin.sibylIntegrationService().authentication().isAuthenticated(player)) {
      player.sendMessage(ChatColor.RED + "Loading timings...");

      List<Timing> timings = new ArrayList<>(Timings.timingPool());
      timings.sort(Timing::compareTo);

      timings.forEach(timing -> {
        if (!timing.isBukkitEventTiming()) return;
        boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
        boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
        String message = String.format(
          "%s: %s::%sms (%s ms/c)",
          timing.coloredName(),
          timing.recordedCalls(),
          formatDouble(timing.totalDurationMillis(), 4),
          (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" +
            formatDouble(timing.averageCallDurationInMillis(), 8)
            + ChatColor.WHITE
        );
        if (!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
          message = IntavePlugin.defaultColor() + ChatColor.stripColor(message);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
      });
    }
  }

  @SubCommand(
    selectors = "record",
    usage = "",
    description = "Record timings",
    permission = "sibyl"
  )
  @Native
  public void recordCommand(User user, @Optional Player target) {
    User targetUser = target != null ? UserRepository.userOf(target) : user;
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(targetUser)) {
      nayoro.enableRecordingFor(targetUser);
      user.player().sendMessage(ChatColor.GREEN + "Recording enabled for " + ChatColor.RED + targetUser.player().getName());
    } else {
      nayoro.disableRecordingFor(targetUser);
      user.player().sendMessage(ChatColor.GREEN + "Recording disabled for " + ChatColor.RED + targetUser.player().getName());
    }
  }

  @SubCommand(
    selectors = "playback",
    usage = "",
    description = "Playback recorded timings",
    permission = "sibyl"
  )
  @Native
  public void playbackCommand(User user, @Optional Player target) {
    User targetUser = target != null ? UserRepository.userOf(target) : user;
    Nayoro nayoro = Modules.nayoro();
    nayoro.instantPlayback(targetUser);
  }

  @SubCommand(
    selectors = "packettimings",
    usage = "",
    description = "Output timing data",
    permission = "sibyl"
  )
  @Native
  public void packetTimingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

    Player player = user.player();
    if (plugin.sibylIntegrationService().authentication().isAuthenticated(player)) {
      player.sendMessage(ChatColor.RED + "Loading timings...");

      List<Timing> timings = new ArrayList<>(Timings.timingPool());
      timings.sort(Timing::compareTo);

      timings.forEach(timing -> {
        if (!timing.isPacketEventTiming()) return;
        boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
        boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
        String message = String.format(
          "%s: %s::%sms (%s&f ms/c)",
          timing.coloredName(),
          timing.recordedCalls(),
          formatDouble(timing.totalDurationMillis(), 4),
          (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" +
            formatDouble(timing.averageCallDurationInMillis(), 8)
        );
        if (!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
          message = IntavePlugin.defaultColor() + ChatColor.stripColor(message);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
      });
    }
  }

  @SubCommand(
    selectors = "statistics",
    usage = "",
    description = "Output check statistics",
    permission = "sibyl"
  )
  @Native
  public void checkStatisticsCommand(User user) {
    Player player = user.player();
    player.sendMessage(ChatColor.RED + "Loading statistics...");
    for (Check check : plugin.checks().checks()) {
      CheckStatistics statistics = check.baseStatistics();
      double processed = statistics.totalProcessed();
      double violations = statistics.totalViolations();
      double failed = statistics.totalFails();
      long passed = statistics.totalPasses();

      if (processed == 0) {
        continue;
      }

      String failedRate = formatDouble(failed / processed * 100, 5);
      String violatedRate = formatDouble(violations / processed * 100, 5);

      String message = String.format("Check/%s: %s::%s%% / vio %s%%", check.name(), passed, failedRate, violatedRate);
      player.sendMessage(ChatColor.WHITE + message);
    }
  }

  @SubCommand(
    selectors = "biasec",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void outputBiasSuccess(User user) {
    Player player = user.player();

    long biasPredCalls = Timings.CHECK_PHYSICS_PROC_PRED_BIA.recordedCalls();
    long biasLKCalls = Timings.CHECK_PHYSICS_PROC_LK_BIA.recordedCalls();
    long biasTotalCalls = biasPredCalls + biasLKCalls;
    long iterativeCall = Timings.CHECK_PHYSICS_PROC_ITR.recordedCalls();
    long successfulCalls = biasTotalCalls - iterativeCall;

    double percentage = ((double) successfulCalls / ((double) biasTotalCalls)) * 100;

    long successfulBias = biasPredCalls - (biasLKCalls + iterativeCall);
    long successfulLK = biasLKCalls - iterativeCall;

    player.sendMessage(successfulBias + "/" + biasPredCalls + " pred biased, " + successfulLK + "/" + biasLKCalls + " lk biased with " + iterativeCall + " iterative");
    player.sendMessage(formatDouble(percentage, 2) + "% movements bias simulated");
    double estimatedTimeIfAllBiasCallsWereIterative = biasTotalCalls * Timings.CHECK_PHYSICS_PROC_ITR.averageCallDurationInNanos();
    double savedTime = (estimatedTimeIfAllBiasCallsWereIterative - (Timings.CHECK_PHYSICS_PROC_PRED_BIA.totalDurationNanos() + Timings.CHECK_PHYSICS_PROC_LK_BIA.totalDurationNanos())) / 1000000d;

    player.sendMessage("Saved " + (savedTime > 0 ? ChatColor.GREEN : ChatColor.RED) + formatDouble(savedTime, 2) + ChatColor.WHITE + "ms");
  }

  @SubCommand(
    selectors = "keys",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void outputKeyStatistic(User user) {
    Player player = user.player();
    player.sendMessage(ChatColor.RED + "Loading key study..");
    Map<String, Double> studyResult = KeyPressStudy.resultShare();
    Map<String, Double> sortedStudy = sortHashMapByValues(studyResult);

    sortedStudy.forEach((keys, percentage) -> {
      if (keys.trim().isEmpty()) {
        keys = "N";
      }
      player.sendMessage("Key " + keys + " " + formatDouble(percentage * 100, 4) + "%");
    });
  }

  @SubCommand(
    selectors = "resync",
    usage = "",
    permission = "sibyl",
    description = ""
  )
  public void checkPacketResync(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Loading data..");
    Map<String, Long> packets = PacketSynchronizations.output();
    if (packets.isEmpty()) {
      sender.sendMessage(ChatColor.GREEN + "No hard re-syncs on record");
    } else {
      packets = sortHashMapByValues(packets);
      packets.forEach((name, hardsResyncs) -> {
        sender.sendMessage(ChatColor.RED + name.toLowerCase(Locale.ROOT) + IntavePlugin.defaultColor() + " packets hit a total of " + ChatColor.RED + hardsResyncs + IntavePlugin.defaultColor() + " hard re-syncs");
      });
    }
  }

  @SubCommand(
    selectors = "latencies",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void outputAttackLatencies(User user) {
    Player player = user.player();
    player.sendMessage("The average attack latency is " + formatDouble(LatencyStudy.attackLatency(), 2) + " ticks");
  }

  @SubCommand(
    selectors = "iter",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void outputIterative(User user) {
    Player player = user.player();
    player.sendMessage("");
    player.sendMessage("Iterative Study");
    player.sendMessage("Average runs: " + formatDouble(IterativeStudy.average(), 2));
    IterativeStudy.ITERATORS.forEach((s, iterator) -> {
      player.sendMessage(s + " -> " + iterator.totalRuns() + " with " + (iterator.successRate() * 100d) + "% sucess");
    });
    player.sendMessage("");
  }


  @SubCommand(
    selectors = "bbaf",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void outputBBAF(User user) {
    Player player = user.player();
    player.sendMessage(ChatColor.RED + "Loading bounding box access flow study..");

    String colorScheme = ChatColor.GREEN + "" + green + " " + ChatColor.YELLOW + yellow + " " + ChatColor.RED + red + "" + ChatColor.GRAY;
    player.sendMessage(ChatColor.GRAY + "" + requests + " requests required " + lookups + " lookups (" + colorScheme + "), " + ChatColor.AQUA + ((lookups) - dynamic) + ChatColor.GRAY + " by server");
  }

  @SubCommand(
    selectors = "replacements",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void outputReplacements(User user) {
    Player player = user.player();
    BlockStateAccess bba = user.blockStates();
    player.sendMessage(ChatColor.RED + "You have " + bba.locatedReplacements().size() + "/" + bba.indexedReplacements().size() + " replacements");
  }

  @SubCommand(
    selectors = "mine",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void makeMiningProcedure(User user, MiningStrategy strategy, @Optional Player possibleOtherTarget) {
    Player player = user.player();
    Player target = possibleOtherTarget == null ? player : possibleOtherTarget;
    User targetUser = UserRepository.userOf(target);
    player.sendMessage(ChatColor.GREEN + "MiningStrategy applied to " + target.getName());
    strategy.apply(targetUser);
  }

  @SubCommand(
    selectors = "badboys",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void showConfidences(User user) {
    Player player = user.player();
    Map<UUID, Confidence> confidenceMap = new HashMap<>();
    Heuristics heuristicsCheck = plugin.checks().searchCheck(Heuristics.class);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      List<Anomaly> anomalies = heuristicsCheck.catchAnomaliesOf(UserRepository.userOf(onlinePlayer), false);
      Confidence confidence = heuristicsCheck.computeOverallConfidence(heuristicsCheck.resolveConfidencesOf(anomalies));
      confidenceMap.put(onlinePlayer.getUniqueId(), confidence);
    }

    sortHashMapByValues(confidenceMap);
    AtomicBoolean active = new AtomicBoolean();
    confidenceMap.forEach((uuid, confidence) -> {
      if (!confidence.atLeast(Confidence.PROBABLE)) {
        return;
      }
      active.set(true);
      Player otherPlayer = Bukkit.getPlayer(uuid);
      List<Anomaly> anomalies = heuristicsCheck.catchAnomaliesOf(UserRepository.userOf(otherPlayer), false);
      String patterns = anomalies.stream().map(anomaly -> "p[" + anomaly.key() + "]").distinct().collect(Collectors.joining(","));
      player.sendMessage(ChatColor.RED + confidence.name() + " " + ChatColor.GRAY + otherPlayer.getName() + " | " + patterns);
    });

    if (!active.get()) {
      player.sendMessage(ChatColor.GREEN + "No badboys detected");
    }
  }

  @SubCommand(
    selectors = "settrust",
    usage = "<trustfactor> [<target>]",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void setTrustFactor(User user, TrustFactor trustFactor, @Optional Player target) {
    if (target == null) {
      target = user.player();
    }
    UserRepository.userOf(target).setTrustFactor(trustFactor);
    user.player().sendMessage(ChatColor.GRAY + "Applied " + trustFactor.chatColor() + trustFactor.name() + ChatColor.GRAY + " trustfactor to " + ChatColor.RED + target.getName());
  }

  @SubCommand(
    selectors = "trust",
    usage = "[<target>]",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void lookupTrust(User user, @Optional Player target) {
    if (target == null) {
      target = user.player();
    }
    TrustFactor trustFactor = UserRepository.userOf(target).trustFactor();
    user.player().sendMessage(ChatColor.RED + target.getName() + ChatColor.GRAY + " has a " + trustFactor.chatColor() + trustFactor.name() + ChatColor.GRAY + " trustfactor");
  }

  @SubCommand(
    selectors = "traping",
    usage = "[<target>]",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void transactionPing(User user, @Optional Player target) {
    if (target == null) {
      target = user.player();
    }
    user.player().sendMessage(ChatColor.RED + target.getName() + ChatColor.GRAY + " has a transaction-ping of " + ChatColor.RED + UserRepository.userOf(target).meta().connection().transactionPingAverage() + ChatColor.GRAY + "ms");
  }

  @SubCommand(
    selectors = "trustmap",
    usage = "",
    permission = "sibyl"
  )
  @Native
  public void trustfactorMap(User user) {
    Map<TrustFactor, AtomicLong> trustfactorDistribution = new HashMap<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (UserRepository.hasUser(player)) {
        TrustFactor trustFactor = UserRepository.userOf(player).trustFactor();
        trustfactorDistribution
          .computeIfAbsent(trustFactor, x -> new AtomicLong())
          .incrementAndGet();
      }
    }
    Player player = user.player();
    player.sendMessage(ChatColor.GRAY + "Trustfactor distribution:");
    for (TrustFactor value : TrustFactor.values()) {
      long count = trustfactorDistribution.getOrDefault(value, new AtomicLong()).get();
      player.sendMessage((count > 0 ? ChatColor.RED + "" + count : ChatColor.GRAY + "0") + ChatColor.GRAY + "x " + value.chatColor() + value.name());
    }
  }

  @SubCommand(
    selectors = "asyncmessage",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void asyncMessageInNetty(User user) {
    user.meta().connection().sendAsyncMessage = true;
  }

  @SubCommand(
    selectors = "invisibleBlock",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void invisibleBlock(User user) {
    Player player = user.player();
    Location location = player.getLocation();
    user.blockStates().override(player.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), Material.OBSIDIAN, 0);
    player.sendMessage(ChatColor.GREEN + "Block summoned");
  }

  private final static Map<Class<?>, String> CLASS_NAME = new HashMap<>();

  static {
    CLASS_NAME.put(BoundingBox.class, "BoundingBoxes");
    CLASS_NAME.put(BlockState.class, "BlockStates");
    CLASS_NAME.put(CubeShape.class, "CubeShape");
    CLASS_NAME.put(ArrayBlockShape.class, "ArrayBlockShape");
  }

  @SubCommand(
    selectors = "memtrace",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void memtrace(User user) {
    Player player = user.player();
    if (!IntaveControl.ENABLE_MEMTRACE) {
      player.sendMessage(ChatColor.RED + "Please enable PERFORMANCE_RECORD to perform a type 1 memory trace");
      return;
    }


    Map<Class<?>, AtomicLong> traces = MemoryTraced.tracedClasses();
    Map<Class<?>, Long> memoryUsage = MemoryTraced.memoryUsage();
    traces.forEach((aClass, atomicInteger) -> {
      player.sendMessage(atomicInteger + " " + CLASS_NAME.get(aClass) + " (" + humanReadableByteCount(memoryUsage.get(aClass)) + ")");
    });
  }

  @SubCommand(
    selectors = "memtrace2",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void memtrace2(User user) {
    Player player = user.player();

    if (!MemoryWatchdog.supported()) {
      player.sendMessage(ChatColor.RED + "An Agent is required to perform a type 2 memory trace");
      return;
    }

    player.sendMessage(ChatColor.RED + "Computing memory trace..");
    Map<String, Long> trace = new HashMap<>();
    MemoryWatchdog.memoryTraceOf(IntavePlugin.singletonInstance(), trace, new HashSet<>());
    trace = sortHashMapByValues(trace);
    trace.forEach((s, aLong) -> {
      if (aLong > 200) {
        player.sendMessage(humanReadableByteCount(aLong) + " by " + (s.contains("intave") ? ChatColor.GRAY : ChatColor.DARK_GRAY) + s);
      }
    });
    player.sendMessage(ChatColor.RED + "Computing memory usage..");
    player.sendMessage(ChatColor.YELLOW + "Intave plugin obj memtrace: " + humanReadableByteCount(MemoryWatchdog.memoryUsageOf(IntavePlugin.singletonInstance(), new HashSet<>())));
    MemoryWatchdog.memoryUsage(stringLongMapx -> {
      Map<String, Long> stringLongMap = sortHashMapByValues(stringLongMapx);
      for (Map.Entry<String, Long> stringLongEntry : stringLongMap.entrySet()) {
        player.sendMessage(stringLongEntry.getKey() + " requires " + humanReadableByteCount(stringLongEntry.getValue()));
      }
    });
  }

  // stolen from https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
  private static String humanReadableByteCount(long bytes) {
    if (-1000 < bytes && bytes < 1000) {
      return bytes + "B";
    }
    CharacterIterator ci = new StringCharacterIterator("kMGTPE");
    while (bytes <= -999_950 || bytes >= 999_950) {
      bytes /= 1000;
      ci.next();
    }
    return String.format("%.1f%cB", bytes / 1000.0, ci.current());
  }

  public <K extends Comparable<? super K>, V extends Comparable<? super V>> Map<K, V> sortHashMapByValues(
    Map<K, V> passedMap
  ) {
    List<K> mapKeys = new ArrayList<>(passedMap.keySet());
    List<V> mapValues = new ArrayList<>(passedMap.values());
    Collections.sort(mapValues);
    Collections.reverse(mapValues);
    Collections.sort(mapKeys);
    Map<K, V> sortedMap = new LinkedHashMap<>();
    for (V val : mapValues) {
      Iterator<K> keyIt = mapKeys.iterator();
      while (keyIt.hasNext()) {
        K key = keyIt.next();
        if (passedMap.get(key).equals(val)) {
          keyIt.remove();
          sortedMap.put(key, val);
          break;
        }
      }
    }
    return sortedMap;
  }

  public static RootStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new RootStage();
    }
    return singletonInstance;
  }
}
