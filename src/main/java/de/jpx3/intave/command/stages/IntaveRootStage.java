package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.diagnostics.timings.Timing;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.user.User;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class IntaveRootStage extends CommandStage {
  private static IntaveRootStage singletonInstance;
  private final IntavePlugin plugin;

  private IntaveRootStage() {
    super(IntaveCommandStage.singletonInstance(), "internals", 1);
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "timings",
    usage = "",
    permission = "sibyl",
    description = "Output timing data"
  )
  @Native
  public void internalCommand(User user) {
    Player player = user.player();
    if(plugin.sibylIntegrationService().authentication().isAuthenticated(player)) {
      player.sendMessage(ChatColor.RED + "Loading timings...");
      List<Timing> timings = new ArrayList<>(Timings.timingPool());
      timings.sort(Timing::compareTo);

      timings.forEach(timing -> {
        boolean suspicious = timing.getAverageCallDurationInMillis() > 0.5d;
        boolean dumping = timing.getAverageCallDurationInMillis() > 1.5d;
        String message = String.format("%s: %s::%sms (%s&f ms/c)",
          timing.getTimingName(),
          timing.getRecordedCalls(),
          MathHelper.formatDouble(timing.totalDurationMillis(), 4),
          (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW ) : ChatColor.GREEN) + "" +
            MathHelper.formatDouble(timing.getAverageCallDurationInMillis(), 8)
        );
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',message));
      });
    }
  }


  public static IntaveRootStage singletonInstance() {
    if(singletonInstance == null) {
      singletonInstance = new IntaveRootStage();
    }
    return singletonInstance;
  }
}
