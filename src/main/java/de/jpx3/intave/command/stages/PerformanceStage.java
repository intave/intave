package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.user.User;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static de.jpx3.intave.math.MathHelper.formatDouble;

public final class PerformanceStage extends CommandStage {
  private static PerformanceStage singletonInstance;
  private final IntavePlugin plugin;

  private PerformanceStage() {
    super(BaseStage.singletonInstance(), "performance");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "events",
    usage = "",
    description = "Output timing data",
    permission = "intave.command.diagnostics"
  )
  @Native
  public void eventTimingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

    Player player = user.player();
//    if (plugin.sibyl().authentication().isAuthenticated(player)) {
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
//    }
  }

  @SubCommand(
    selectors = "packets",
    usage = "",
    description = "Output timing data",
    permission = "intave.command.diagnostics"
  )
  @Native
  public void packetTimingsCommand(User user, @Optional String[] specifier) {
    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

    Player player = user.player();
//    if (plugin.sibyl().authentication().isAuthenticated(player)) {
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
//    }
  }

  public static PerformanceStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new PerformanceStage();
    }
    return singletonInstance;
  }
}
