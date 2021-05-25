package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.detect.CheckStatistics;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import de.jpx3.intave.detect.checks.combat.heuristics.MiningStrategy;
import de.jpx3.intave.diagnostics.KeyPressStudy;
import de.jpx3.intave.diagnostics.timings.Timing;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy.*;

public final class IntaveRootStage extends CommandStage {
  private static IntaveRootStage singletonInstance;
  private final IntavePlugin plugin;

  private IntaveRootStage() {
    super(IntaveCommandStage.singletonInstance(), "root", 1);
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
        if(timing.isPacketEventTiming() || timing.isBukkitEventTiming()) {
          return;
        }
        boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
        boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
        String message = String.format(
          "%s: %s::%sms (%s&f ms/c)",
          timing.coloredName(),
          timing.recordedCalls(),
          MathHelper.formatDouble(timing.totalDurationMillis(), 4),
          (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" +
            MathHelper.formatDouble(timing.averageCallDurationInMillis(), 8)
        );
        if(!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
          message = ChatColor.GRAY + ChatColor.stripColor(message);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
      });
    }
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
        if(!timing.isBukkitEventTiming()) return;
        boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
        boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
        String message = String.format(
          "%s: %s::%sms (%s&f ms/c)",
          timing.coloredName(),
          timing.recordedCalls(),
          MathHelper.formatDouble(timing.totalDurationMillis(), 4),
          (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" +
            MathHelper.formatDouble(timing.averageCallDurationInMillis(), 8)
        );
        if(!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
          message = ChatColor.GRAY + ChatColor.stripColor(message);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
      });
    }
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
        if(!timing.isPacketEventTiming()) return;
        boolean suspicious = timing.averageCallDurationInMillis() > 0.5d;
        boolean dumping = timing.averageCallDurationInMillis() > 1.5d;
        String message = String.format(
          "%s: %s::%sms (%s&f ms/c)",
          timing.coloredName(),
          timing.recordedCalls(),
          MathHelper.formatDouble(timing.totalDurationMillis(), 4),
          (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" +
            MathHelper.formatDouble(timing.averageCallDurationInMillis(), 8)
        );
        if(!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
          message = ChatColor.GRAY + ChatColor.stripColor(message);
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
    for (IntaveCheck check : plugin.checkService().checks()) {
      CheckStatistics statistics = check.baseStatistics();
      double processed = statistics.totalProcessed();
      double violations = statistics.totalViolations();
      double failed = statistics.totalFails();
      long passed = statistics.totalPasses();

      if (processed == 0) {
        continue;
      }

      String failedRate = MathHelper.formatDouble(failed / processed * 100, 5);
      String violatedRate = MathHelper.formatDouble(violations / processed * 100, 5);

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

    long biasCalls = Timings.CHECK_PHYSICS_PROC_BIA.recordedCalls();
    long failedCalls = Timings.CHECK_PHYSICS_PROC_ITR.recordedCalls();
    long successfulCalls = biasCalls - failedCalls;

    double percentage = ((double) successfulCalls / (double) biasCalls) * 100;

    player.sendMessage(biasCalls + " biased with " + failedCalls + " fails");
    player.sendMessage(MathHelper.formatDouble(percentage, 2) + "% movements bias simulated");
    double estimatedTimeIfAllBiasCallsWereIterative = biasCalls * Timings.CHECK_PHYSICS_PROC_ITR.getAverageCallDurationInNanos();
    double savedTime = (estimatedTimeIfAllBiasCallsWereIterative - Timings.CHECK_PHYSICS_PROC_BIA.getTotalDurationNanos()) / 1000000d;

    player.sendMessage("Saved " + (savedTime > 0 ? ChatColor.GREEN : ChatColor.RED) + MathHelper.formatDouble(savedTime, 2) + ChatColor.WHITE + "ms");
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
      player.sendMessage("Key " + keys + " " + MathHelper.formatDouble(percentage * 100, 4) + "%");
    });
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
    player.sendMessage(ChatColor.GRAY + "" + requests + " requests required " + lookups + " lookups ("+colorScheme+"), " + ChatColor.AQUA + ((lookups) - dynamic) + ChatColor.GRAY + " by server");
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
    OCBlockShapeAccess bba = user.blockShapeAccess();
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
    Heuristics heuristicsCheck = plugin.checkService().searchCheck(Heuristics.class);

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

  @SubCommand(
    selectors = "settrust",
    usage = "<trustfactor> [<target>]",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void setTrustFactor(User user, TrustFactor trustFactor, @Optional Player target) {
    if(target == null) {
      target = user.player();
    }
    UserRepository.userOf(target).setTrustFactor(trustFactor);
    user.player().sendMessage(ChatColor.GRAY + "Applied "+trustFactor.chatColor() + trustFactor.name() + ChatColor.GRAY+" trustfactor to " +ChatColor.RED + target.getName());
  }

  @SubCommand(
    selectors = "trust",
    usage = "[<target>]",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void lookupTrust(User user, @Optional Player target) {
    if(target == null) {
      target = user.player();
    }
    TrustFactor trustFactor = UserRepository.userOf(target).trustFactor();
    user.player().sendMessage(ChatColor.RED + target.getName() + ChatColor.GRAY + " has a "+trustFactor.chatColor() + trustFactor.name() + ChatColor.GRAY+" trustfactor");
  }

  @SubCommand(
    selectors = "asyncmessage",
    usage = "",
    description = "",
    permission = "sibyl"
  )
  @Native
  public void asyncMessageInNetty(User user) {
    user.meta().connectionData().sendAsyncMessage = true;
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
    user.blockShapeAccess().override(player.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), Material.OBSIDIAN, 0);
    player.sendMessage(ChatColor.GREEN + "Block summoned");
  }

  public static IntaveRootStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new IntaveRootStage();
    }
    return singletonInstance;
  }
}
