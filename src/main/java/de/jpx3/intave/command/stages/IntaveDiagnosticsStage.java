package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.detect.CheckStatistics;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.tools.MathHelper;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IntaveDiagnosticsStage extends CommandStage {
  private static IntaveDiagnosticsStage singletonInstance;
  private final IntavePlugin plugin;

  protected IntaveDiagnosticsStage() {
    super(IntaveCommandStage.singletonInstance(), "diagnostics", 1);
    plugin = IntavePlugin.singletonInstance();
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
    List<IntaveCheck> checks = new ArrayList<>(plugin.checkService().checks());
    checks.sort(Comparator.comparing(check -> check.baseStatistics().totalFails()));
    for (IntaveCheck check : checks) {
      CheckStatistics statistics = check.baseStatistics();
      long processed = statistics.totalProcessed();
      long violations = statistics.totalViolations();
      if (processed == 0 || !check.enabled()) {
        continue;
      }
      String violatedRate = MathHelper.formatDouble((((double) violations / (double) processed)) * 100d, 5);
      String checkFormat = ChatColor.RED + check.name();
      String message = checkFormat + ChatColor.GRAY + ": " + violations + " detections in " + processed + " processes (" + violatedRate + "%)";
      sender.sendMessage(message);
    }
  }

  public static IntaveDiagnosticsStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new IntaveDiagnosticsStage();
    }
    return singletonInstance;
  }
}