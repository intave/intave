package de.jpx3.intave.command.stages;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Forward;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.actionbar.ActionBarDisplayer;
import de.jpx3.intave.module.actionbar.DisplayType;
import de.jpx3.intave.module.nayoro.Classifier;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.nayoro.OperationalMode;
import de.jpx3.intave.module.violation.ViolationVerboseMode;
import de.jpx3.intave.player.ProfileLookup;
import de.jpx3.intave.security.LicenseAccess;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.permission.BukkitPermissionCheck;
import de.jpx3.intave.user.storage.StorageViolationEvent;
import de.jpx3.intave.user.storage.StorageViolationEvents;
import de.jpx3.intave.user.storage.ViolationStorage;
import de.jpx3.intave.version.DurationTranslator;
import de.jpx3.intave.version.IntaveVersion;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BaseStage extends CommandStage {
  private static BaseStage singletonInstance;

  private BaseStage() {
    super(null, "/intave");
  }

  @SubCommand(
    selectors = "verbose",
    usage = "<player...>",
    description = "Toggle verbose messages",
    permission = "intave.command.verbose"
  )
  public void verboseCommand(User user, @Optional Player[] selectedPlayers) {
    Player player = user.player();
    boolean receivesVerbose = user.receives(MessageChannel.VERBOSE);

    ViolationVerboseMode mode = Modules.violationProcessor().verboseMode();
    String modeName = mode.name().toLowerCase();

    if (user.receives(MessageChannel.VERBOSE)) {
      if (selectedPlayers != null && !user.hasChannelConstraint(MessageChannel.VERBOSE)) {
        List<UUID> uniqueIds = Arrays.stream(selectedPlayers).map(Entity::getUniqueId).distinct().collect(Collectors.toList());
        user.setChannelConstraint(MessageChannel.VERBOSE, player1 -> uniqueIds.contains(player1.getUniqueId()));
        String names = ChatColor.RED + describePlayerList(Arrays.stream(selectedPlayers).map(Entity::getName).map(s -> ChatColor.RED + s).collect(Collectors.toList()));
        player.sendMessage(IntavePlugin.prefix() + "You have specified " + modeName + " verbose output to " + names);
        return;
      }
    } /*else if (selectedPlayers == null && !IntavePlugin.singletonInstance().sibyl().isAuthenticated(player)) {
      player.sendMessage(IntavePlugin.prefix() + "/intave verbose <player...>");
      return;
    }*/

    user.toggleReceive(MessageChannel.VERBOSE);
    user.removeChannelConstraint(MessageChannel.VERBOSE);

    if (receivesVerbose) {
      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.RED + "no longer " + IntavePlugin.defaultColor() + "receiving " + modeName + " verbose output");
    } else {
      if (selectedPlayers == null) {
        String target = ChatColor.RED + "everyone";
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now " + IntavePlugin.defaultColor() + "receiving " + modeName + " verbose output for " + target);
      } else {
        List<UUID> uniqueIds = Arrays.stream(selectedPlayers).map(Entity::getUniqueId).distinct().collect(Collectors.toList());
        user.setChannelConstraint(MessageChannel.VERBOSE, player1 -> uniqueIds.contains(player1.getUniqueId()));
        String names = ChatColor.RED + describePlayerList(Arrays.stream(selectedPlayers).map(Entity::getName).map(s -> ChatColor.RED + s).collect(Collectors.toList()));
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now" + IntavePlugin.defaultColor() + " receiving " + modeName + " verbose output for " + names);
      }
    }
  }

  private static String describePlayerList(List<String> elements) {
    int size = elements.size();
    String defaultColor = IntavePlugin.defaultColor();
    if (size == 0) {
      return defaultColor + "nobody";
    } else if (size == 1) {
      return elements.get(0);
    } else {
      return defaultColor + String.join(defaultColor + ", ", elements.subList(0, size - 1)) + defaultColor + " and " + elements.get(size - 1);
    }
  }

  // REMOVE ON LIVE SERVER
  /*
  @SubCommand(
    selectors = "record",
    usage = "",
    description = "Record timings"
  )
  @Native
  public void recordCommand(User user, @Optional Player target) {
    if (IntaveControl.DISABLE_LICENSE_CHECK && !IntaveControl.GOMME_MODE) {
      User targetUser = target != null ? UserRepository.userOf(target) : user;
      Nayoro nayoro = Modules.nayoro();
      if (!nayoro.recordingActiveFor(targetUser)) {
        nayoro.enableRecordingFor(targetUser);
        user.player().sendMessage(ChatColor.GREEN + "Recording enabled for " + ChatColor.RED + targetUser.player().getName());
      } else {
        nayoro.disableRecordingFor(targetUser);
        user.player().sendMessage(ChatColor.GREEN + "Recording disabled for " + ChatColor.RED + targetUser.player().getName());
      }
    } else {
      user.player().sendMessage(ChatColor.RED + "This command is not available.");
    }
  }

  @SubCommand(
    selectors = "playback",
    usage = "",
    description = "Playback recorded timings"
  )
  @Native
  public void playbackCommand(User user, @Optional Player target) {
    if (IntaveControl.DISABLE_LICENSE_CHECK && !IntaveControl.GOMME_MODE) {
      User targetUser = target != null ? UserRepository.userOf(target) : user;
      user.player().sendMessage(ChatColor.GREEN + "Playback for " + ChatColor.RED + targetUser.player().getName() + ChatColor.GREEN + " started");
      Nayoro nayoro = Modules.nayoro();
      nayoro.instantPlayback(targetUser);
    } else {
      user.player().sendMessage(ChatColor.RED + "This command is not available.");
    }
  }
  */

  @SubCommand(
    selectors = "record",
    usage = "",
    description = "Record players"
  )
  @Native
  public void recordCommand(User user, @Optional Player target, @Optional Classifier classifier) {
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      user.player().sendMessage(ChatColor.RED + "This command is not available");
      return;
    }
    if (classifier == null) {
      user.player().sendMessage(ChatColor.RED + "Please specify a classifier");
      return;
    }

    User targetUser = target != null ? UserRepository.userOf(target) : user;
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(targetUser)) {
      nayoro.enableRecordingFor(targetUser, classifier, OperationalMode.LOCAL_STORAGE);
      user.player().sendMessage(ChatColor.GREEN + "Recording enabled for " + ChatColor.RED + targetUser.player().getName());
    } else {
      nayoro.disableRecordingFor(targetUser);
      user.player().sendMessage(ChatColor.GREEN + "Recording disabled for " + ChatColor.RED + targetUser.player().getName());
    }
  }

  @SubCommand(
    selectors = {"cps", "clicks"},
    permission = "intave.command.cps",
    usage = "[<player...>]",
    description = "Display click visualizer"
  )
  public void cpsCommand(User user, @Optional Player selectedPlayer) {
    Player player = user.player();

    if (selectedPlayer == null) {
      selectedPlayer = player;
    }

    ActionBarDisplayer actionBar = Modules.actionBar();

    if (actionBar.inSubscription(user)) {
//      boolean isSameActionTarget = Objects.equals(user.actionTarget(), selectedPlayer.getUniqueId());
//      if (isSameActionTarget) {
//      }
      actionBar.unsubscribe(user);
      player.sendMessage(IntavePlugin.prefix() + "Unsubscribed from " + ChatColor.RED + selectedPlayer.getName() + IntavePlugin.defaultColor() + "'s clicks");
      return;
    }

    actionBar.subscribe(user, UserRepository.userOf(selectedPlayer), DisplayType.CLICKS);
    player.sendMessage(IntavePlugin.prefix() + "Subscribed to " + ChatColor.RED + selectedPlayer.getName() + IntavePlugin.defaultColor() + "'s clicks");
  }

  @SubCommand(
    selectors = {"alert", "alerts"},
    hideInHelp = true,
    description = ""
  )
  public void redirectToVerbose(CommandSender sender) {
    if (!BukkitPermissionCheck.permissionCheck(sender, "intave.command.verbose")) {
      showAllCommands(sender);
    } else {
      sender.sendMessage(IntavePlugin.prefix() + "Did you mean verbose or notify?");
    }
  }

  @SubCommand(
    selectors = {"notify", "notifications"},
    usage = "[<player...>]",
    description = "Toggle notifications",
    permission = "intave.command.notify"
  )
  public void notifyCommand(User user, @Optional Player[] selectedPlayers) {
    Player player = user.player();
    boolean receivesNotify = user.receives(MessageChannel.NOTIFY);

    if (user.receives(MessageChannel.NOTIFY)) {
      if (selectedPlayers != null && !user.hasChannelConstraint(MessageChannel.NOTIFY)) {
        List<UUID> uniqueIds = Arrays.stream(selectedPlayers).map(Entity::getUniqueId).distinct().collect(Collectors.toList());
        user.setChannelConstraint(MessageChannel.NOTIFY, player1 -> uniqueIds.contains(player1.getUniqueId()));
        String names = ChatColor.RED + describePlayerList(Arrays.stream(selectedPlayers).map(Entity::getName).map(s -> ChatColor.RED + s).collect(Collectors.toList()));
        player.sendMessage(IntavePlugin.prefix() + "You have specified notifications to " + names);
        return;
      }
    }

    user.toggleReceive(MessageChannel.NOTIFY);
    user.removeChannelConstraint(MessageChannel.NOTIFY);

    if (receivesNotify) {
      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.RED + "no longer " + IntavePlugin.defaultColor() + "receiving notifications");
    } else {
      if (selectedPlayers == null) {
        String target = ChatColor.RED + "everyone";
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now " + IntavePlugin.defaultColor() + "receiving notifications for " + target);
      } else {
        List<UUID> uniqueIds = Arrays.stream(selectedPlayers).map(Entity::getUniqueId).distinct().collect(Collectors.toList());
        user.setChannelConstraint(MessageChannel.NOTIFY, player1 -> uniqueIds.contains(player1.getUniqueId()));
        String names = ChatColor.RED + describePlayerList(Arrays.stream(selectedPlayers).map(Entity::getName).map(s -> ChatColor.RED + s).collect(Collectors.toList()));
        player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now" + IntavePlugin.defaultColor() + " receiving notifications for " + names);
      }
    }
  }

//  @SubCommand(
//    selectors = "notify",
//    usage = "",
//    description = "Toggle notifications",
//    permission = "intave.command.notify"
//  )
//  public void notifyCommand(User user) {
//    Player player = user.player();
//
//    boolean receiveNotify = user.receives(MessageChannel.NOTIFY);
//    user.toggleReceive(MessageChannel.NOTIFY);
//
//    if (receiveNotify) {
//      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.RED + "no longer " + IntavePlugin.defaultColor() + "receiving notifications");
//    } else {
//      player.sendMessage(IntavePlugin.prefix() + "You are " + ChatColor.GREEN + "now " + IntavePlugin.defaultColor() + "receiving notifications");
//    }
//  }

  @SubCommand(
    selectors = {"history", "logs"},
    usage = "<player>",
    description = "Show violation history",
    permission = "intave.command.history"
  )
  public void historyCommand(CommandSender sender, String playerName) {
    Player player = Bukkit.getPlayer(playerName);
    if (isOnline(player)) {
      User targetUser = UserRepository.userOf(player);
      String name = player.getName();
      UUID id = player.getUniqueId();
      ViolationStorage violationStorage = targetUser.storageOf(ViolationStorage.class);
      outputHistory(sender, name, id, violationStorage);
    } else {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.YELLOW + "Loading history..");
      ProfileLookup.lookupIdFromName(playerName, uuid -> {
        if (uuid == null) {
          sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Player \"" + playerName + "\" not found");
        } else {
          Modules.storage().nullableManualStorageRequest(uuid, playerStorage -> {
            if (playerStorage == null) {
              sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + playerName + " hasn't played yet");
            } else {
              outputHistory(sender, playerName, uuid, playerStorage.storageOf(ViolationStorage.class));
            }
          });
        }
      });
    }
  }

  private void outputHistory(CommandSender sender, String name, UUID id, ViolationStorage violationStorage) {
    StorageViolationEvents violations = violationStorage.violations();
    sender.sendMessage(String.format("%sHistory of " + ChatColor.RED + "%s%s:", IntavePlugin.prefix(), name, IntavePlugin.defaultColor()));
    if (violations.isEmpty()) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "No violations found");
      return;
    }
    printHistory(sender, "Reach", violations.fromCheck("attackraytrace"));
    printHistory(sender, "KillAura", violations.fromCheck("heuristics"));
    printHistory(sender, "Fly/Speed", violations.fromCheck("physics"));
    printHistory(sender, "Timer", violations.fromCheck("timer"));
    printHistory(sender, "AutoClicker", violations.fromCheck("clickpatterns"));
    printHistory(sender, "AutoClicker (speed)", violations.fromCheck("clickspeedlimiter"));
    printHistory(sender, "FastBreak", violations.fromCheck("breakspeedlimiter"));
    printHistory(sender, "BadPackets", violations.fromCheck("protocolscanner"));
    printHistory(sender, "Scaffold", violations.fromCheck("placementanalysis"));
    printHistory(sender, "ChestStealer", violations.fromCheck("inventoryanalysis"));
  }

  private void printHistory(CommandSender sender, String cheat, StorageViolationEvents violations) {
    if (violations.isEmpty()) {
      return;
    }
    if (violations.size() == 1) {
      StorageViolationEvent firstViolation = violations.first();
      String baseMessage = MessageFormat.format("{0}- detected for using {1}{2}{0} {3}", IntavePlugin.defaultColor(), ChatColor.RED, cheat, durationToString(firstViolation.timePassedSince()));
      String defaultColor = IntavePlugin.defaultColor();
      TextComponent textComponent = new TextComponent(baseMessage);
      textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
        new TextComponent(defaultColor + "Check " + ChatColor.RED + correctlyFormattedCheckName(firstViolation.checkName())),
        new TextComponent(defaultColor + " reached " + ChatColor.RED + firstViolation.violationLevel() + defaultColor + "VL"),
        new TextComponent(defaultColor + " on " + ChatColor.RED + dateFormat(firstViolation.timestamp())),
      }));
      if (sender instanceof Player) {
        ((Player) sender).spigot().sendMessage(textComponent);
      } else {
        sender.sendMessage(baseMessage);
      }
      return;
    }

    String baseMessage = IntavePlugin.defaultColor() + "- detected multiple times for using " + ChatColor.RED + cheat + IntavePlugin.defaultColor() + ", last was " + durationToString(violations.newest().timePassedSince());
    String defaultColor = IntavePlugin.defaultColor();
    TextComponent newLine = new TextComponent(ComponentSerializer.parse("{text: \"\n\"}"));
    TextComponent[] textComponents = new TextComponent[violations.size()];
    int i = 0;
    for (StorageViolationEvent violation : violations) {
      TextComponent textComponent = new TextComponent(
        new TextComponent(defaultColor + "Check " + ChatColor.RED + correctlyFormattedCheckName(violation.checkName())),
        new TextComponent(defaultColor + " reached " + ChatColor.RED + violation.violationLevel() + defaultColor + "VL"),
        new TextComponent(defaultColor + " on " + ChatColor.RED + dateFormat(violation.timestamp()))
      );
      if (i != violations.size() - 1) {
        textComponent.addExtra(newLine);
      }
      textComponents[i++] = textComponent;
    }

    TextComponent textComponent = new TextComponent(baseMessage);
    textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, textComponents));
    if (sender instanceof Player) {
      ((Player) sender).spigot().sendMessage(textComponent);
    } else {
      sender.sendMessage(baseMessage);
    }
  }

  private String correctlyFormattedCheckName(String checkNameLowercase) {
    IntavePlugin plugin = IntavePlugin.singletonInstance();
    return plugin.checks().searchCheck(checkNameLowercase).name();
  }

  private final DateFormat dateFormat = new SimpleDateFormat("HH:mm dd/MM/yy");

  private String dateFormat(long input) {
    return dateFormat.format(new Date(input));
  }

  // converts milliseconds to a string like "a few days ago"
  private String durationToString(long duration) {
    long seconds = duration / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;
    long days = hours / 24;
    if (days > 0) {
      return days + " days ago";
    }
    if (hours > 0) {
      return hours + " hours ago";
    }
    if (minutes > 0) {
      return minutes + " minutes ago";
    }
    if (seconds > 0) {
      return seconds + " seconds ago";
    }
    return "a few seconds ago";
  }

  private StorageViolationEvents filterByCheck(String check, StorageViolationEvents allViolations) {
    return allViolations.filter(event -> event.checkName().equalsIgnoreCase(check));
  }

  private boolean isOnline(OfflinePlayer player) {
    return player != null && (player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != null);
  }

  @SubCommand(
    selectors = "version",
    usage = "",
    description = "Show version info"
  )
  public void versionCommand(CommandSender commandSender) {
    sendVersionMessage(commandSender);
  }

//  @SubCommand(
//    selectors = "ui",
//    usage = "",
//    permission = "intave.command",
//    description = "Open the Intave UI"
//  )
//  public void openUICommand(CommandSender commandSender) {
//
//  }

  @SubCommand(
    selectors = "proxy",
    usage = "",
    description = "Access proxy related features",
    permission = "intave.command.proxy"
  )
  @Forward(
    target = ProxyStage.class
  )
  public void proxyCommand(CommandSender sender) {
  }

  @SubCommand(
    selectors = "cloud",
    usage = "",
    description = "Access cloud related features",
    permission = "intave.command.cloud"
  )
  @Forward(
    target = CloudStage.class
  )
  public void cloudCommand(CommandSender sender) {
  }

  @SubCommand(
    selectors = "root",
    usage = "",
    description = "",
    permission = "sibyl",
    hideInHelp = true
  )
  @Forward(
    target = RootStage.class
  )
  public void rootCommand(User user) {
  }

  @SubCommand(
    selectors = "diagnostics",
    usage = "",
    description = "Runtime information and diagnostics tools",
    permission = "intave.command.diagnostics.*"
  )
  @Forward(
    target = DiagnosticsStage.class
  )
  public void diagnosticsCommand(CommandSender commandSender) {
  }

  @SubCommand(
    selectors = {"performance", "timings"},
    usage = "",
    description = "Performance data output"
  )
  @Forward(
    target = PerformanceStage.class
  )
  public void performanceTools(CommandSender commandSender) {

  }

  @SubCommand(
    selectors = "internals",
    usage = "",
    description = "Console-reserved commands",
    permission = "intave.command.internals.*"
  )
  @Forward(
    target = InternalsStage.class
  )
  public void internalCommand(User user) {
  }

  @Override
  protected void showAllCommands(CommandSender sender) {
    boolean hasIntavePermission = BukkitPermissionCheck.permissionCheck(sender, "intave.command");
    if (hasIntavePermission) {
      super.showAllCommands(sender);
    } else {
      sendVersionMessage(sender);
    }
  }

  @Native
  private void sendVersionMessage(CommandSender player) {
    boolean hasVersionViewPermission = BukkitPermissionCheck.permissionCheck(player, "intave.command");

    IntaveVersion versionInformation = IntavePlugin.singletonInstance().versions().versionInformation(IntavePlugin.version());
    String version;
    if (!hasVersionViewPermission) {
      version = "(version hidden)";
    } else if (versionInformation != null) {
      boolean outdated = versionInformation.outdated();
      version = IntavePlugin.version() + " (" + (outdated ? "outdated, " : "") + DurationTranslator.translateDuration(System.currentTimeMillis() - versionInformation.release()) + " old)";
    } else {
      version = IntavePlugin.version() + " (unknown version)";
    }

    boolean enterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;

    String prefix = IntavePlugin.prefix();
    player.sendMessage(new String[]{
      prefix + "Running Intave " + version,
      prefix + "Serving as automated cheat-removal and defense tool",
      prefix + "Visit " + ChatColor.UNDERLINE + "intave.de" + IntavePlugin.defaultColor() + " for more information",
    });

    if (IntaveControl.GOMME_MODE) {
      player.sendMessage(prefix + "Certified for GommeHDnet (trusted)");
    } else if (IntavePlugin.isInOfflineMode()) {
      player.sendMessage(prefix + "Certification failed, Intave servers down?");
    } else if (LicenseAccess.network().equals("~bypass")) {
      player.sendMessage(prefix + "Certification disabled, trust release");
    } else {
      player.sendMessage(prefix + "Certified for " + LicenseAccess.network() + (enterprise && !partner ? " (verified)" : (partner ? " (trusted)" : "")));
    }
  }

  public static BaseStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new BaseStage();
    }
    return singletonInstance;
  }
}
