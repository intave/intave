package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.detect.Check;
import de.jpx3.intave.detect.CheckStatistics;
import de.jpx3.intave.diagnostics.timings.Timing;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.User;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class DiagnosticsStage extends CommandStage {
  private static DiagnosticsStage singletonInstance;
  private final IntavePlugin plugin;

  protected DiagnosticsStage() {
    super(BaseStage.singletonInstance(), "diagnostics");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "timings",
    usage = "",
    description = "Output timing data",
    permission = "intave.command.diagnostics.performance"
  )
  @Native
  public void timingsCommand(User user, @Optional String[] specifier) {
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Currently unavailable");
      return;
    }

    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

    Player player = user.player();
    player.sendMessage(ChatColor.RED + "Loading timings...");
    List<Timing> timings = new ArrayList<>(Timings.timingPool());
    timings.sort(Timing::compareTo);

    timings.forEach(timing -> {
      if (timing.isPacketEventTiming() || timing.isBukkitEventTiming()) {
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
      if (!fullSpecifier.isEmpty() && !timing.name().toLowerCase(Locale.ROOT).contains(fullSpecifier)) {
        message = IntavePlugin.defaultColor() + ChatColor.stripColor(message);
      }
      player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    });
  }

  @SubCommand(
    selectors = "performance",
    usage = "",
    description = "Output performance data",
    permission = "intave.command.diagnostics.performance"
  )
  public void timingsCommand(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Currently unavailable");
//    sender.sendMessage(IntavePlugin.prefix() + "Service status");
//    List<Timing> timings = new ArrayList<>(Timings.timingPool());
//    timings.sort(Timing::compareTo);
//
//    timings.forEach(timing -> {
//      boolean suspicious = timing.getAverageCallDurationInMillis() > 0.5d;
//      boolean dumping = timing.getAverageCallDurationInMillis() > 1.5d;
//      String type;
//      if (suspicious) {
//        type = ChatColor.GOLD + "SUSPICIOUS";
//      } else if (dumping) {
//        type = ChatColor.RED + "CRITICAL";
//      } else {
//        type = ChatColor.GREEN + "HEALTHY";
//      }
//      String message = type + " " + ChatColor.GRAY + timing.getTimingName();
//      sender.sendMessage(message);
//    });
  }

  @SubCommand(
    selectors = "statistics",
    usage = "",
    permission = "intave.command.diagnostics.statistics",
    description = "Output check statistics"
  )
  public void checkStatisticsCommand(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Loading check statistics...");
    List<Check> checks = new ArrayList<>(plugin.checkService().checks());
    checks.sort(Comparator.comparing(check -> check.baseStatistics().totalFails()));
    for (Check check : checks) {
      CheckStatistics statistics = check.baseStatistics();
      long processed = statistics.totalProcessed();
      long violations = statistics.totalViolations();
      if (processed == 0 || !check.enabled()) {
        continue;
      }
      String violatedRate = MathHelper.formatDouble((((double) violations / (double) processed)) * 100d, 5);
      String checkFormat = ChatColor.RED + check.name();
      String message = checkFormat + IntavePlugin.defaultColor() + ": " + violations + " detections in " + processed + " processes (" + violatedRate + "%)";
      sender.sendMessage(message);
    }
  }

  public static DiagnosticsStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new DiagnosticsStage();
    }
    return singletonInstance;
  }
}