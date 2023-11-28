package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.connect.cloud.Cloud;
import de.jpx3.intave.connect.cloud.protocol.Shard;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.Classifier;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.nayoro.OperationalMode;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class CloudStage extends CommandStage {
  private static CloudStage singletonInstance;

  private CloudStage() {
    super(BaseStage.singletonInstance(), "cloud");
  }

  @SubCommand(
    selectors = "status",
    usage = "",
    description = "Show version info"
  )
  public void statusCommand(CommandSender commandSender) {
    Cloud cloud = IntavePlugin.singletonInstance().cloud();
    boolean enabled = cloud.isEnabled();

    if (!enabled) {
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Cloud connection is not enabled");
      return;
    }

    Map<Shard, Boolean> shardConnected = cloud.shardConnections();
    Map<Shard, Long> receivedBytes = cloud.receivedBytesPerShard();
    Map<Shard, Long> sentBytes = cloud.sentBytesPerShard();


    // connected to at least one
    boolean connectedToAtLeastOne = shardConnected.values().stream().anyMatch(b -> b);
    commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.GRAY + "Cloud is " + (connectedToAtLeastOne ? ChatColor.GREEN + "connected" : ChatColor.RED + "disconnected"));

    for (Map.Entry<Shard, Boolean> entry : shardConnected.entrySet()) {
      Shard shard = entry.getKey();
      boolean connected = entry.getValue();
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.GRAY + "Shard " + ChatColor.GREEN + shard.name() + ChatColor.GRAY + " is " + (connected ? ChatColor.GREEN + "CONNECTED" : ChatColor.RED + "DISCONNECTED") + ChatColor.GRAY + " (" + ChatColor.GREEN + formatBytes(receivedBytes.get(shard)) + ChatColor.GRAY + " received, " + ChatColor.GREEN + formatBytes(sentBytes.get(shard)) + ChatColor.GRAY + " sent)");
    }
  }

  @SubCommand(
    selectors = "record",
    usage = "[<target>] [<classifier>] <client> <scenario/cheat>",
    permission = "intave.command",
    description = "Record players"
  )
  @Native
  public void recordCommand(User user, @Optional Player target, @Optional Classifier classifier, @Optional String client, @Optional String scenario) {
    Nayoro nayoro = Modules.nayoro();
    Player player = user.player();
    if (IntaveControl.GOMME_MODE || !IntaveControl.DISABLE_LICENSE_CHECK && !IntavePlugin.singletonInstance().sibyl().isAuthenticated(player)) {
      player.sendMessage(ChatColor.RED + "This command is not available");
      return;
    }
    if (target == null) {
      target = user.player();
    }
    User targetUser = UserRepository.userOf(target);
    if (nayoro.recordingActiveFor(targetUser)) {
      nayoro.disableRecordingFor(targetUser);
      player.sendMessage(ChatColor.RED + "Recording disabled for " + target.getName());
    } else {
      if (scenario == null) {
        user.player().sendMessage(ChatColor.RED + "Please specify a scenario, usage: /iac cloud [<target>] [<classifier>] <client> <scenario/cheat>");
        return;
      }
      if (classifier == null || classifier == Classifier.UNKNOWN) {
        user.player().sendMessage(ChatColor.RED + "Please specify a valid classifier (CHEAT or LEGIT), usage: /iac cloud [<target>] [<classifier>] <client> <scenario/cheat>");
        return;
      }
      if (client == null) {
        user.player().sendMessage(ChatColor.RED + "Please specify a client, usage: /iac cloud [<target>] [<classifier>] <client> <scenario/cheat>");
        return;
      }
      Cloud cloud = IntavePlugin.singletonInstance().cloud();
      cloud.requestSampleTransmission(target, classifier, scenario, client + "@" + targetUser.meta().protocol().versionString(), classifier1 -> {
        nayoro.enableRecordingFor(targetUser, classifier, OperationalMode.CLOUD_STORAGE);
        player.sendMessage(ChatColor.GREEN + "Recording with label \"" + classifier + "\"/"+scenario+" granted by cloud.");
      });
    }
  }

  private String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + "B";
    } else if (bytes < 1024 * 1024) {
      return bytes / 1024 + "KB";
    } else if (bytes < 1024 * 1024 * 1024) {
      return bytes / (1024 * 1024) + "MB";
    } else {
      return bytes / (1024 * 1024 * 1024) + "GB";
    }
  }

  public static CloudStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new CloudStage();
    }
    return singletonInstance;
  }
}
