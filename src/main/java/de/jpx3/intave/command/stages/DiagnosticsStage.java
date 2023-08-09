package de.jpx3.intave.command.stages;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.diagnostic.PacketSynchronizations;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.ResourceRegistry;
import de.jpx3.intave.security.HashAccess;
import de.jpx3.intave.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public final class DiagnosticsStage extends CommandStage {
  private static DiagnosticsStage singletonInstance;
  private final IntavePlugin plugin;

  private DiagnosticsStage() {
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
    Player player = user.player();
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      player.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Currently unavailable");
      return;
    }

    String fullSpecifier = specifier != null ? Arrays.stream(specifier).map(s -> s + " ").collect(Collectors.joining()).trim().toLowerCase(Locale.ROOT) : "";

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
    selectors = "resync",
    usage = "",
    permission = "intave.command.diagnostics.performance",
    description = "Output packet re-synchronizations"
  )
  public void checkPacketResync(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Loading data..");
    Map<String, Long> packets = PacketSynchronizations.output();
    if (packets.isEmpty()) {
      sender.sendMessage(ChatColor.GREEN + "No hard re-syncs on record");
    } else {
      packets = sortHashMapByValues(packets);
      packets.forEach((name, hardsResyncs) -> sender.sendMessage(ChatColor.RED + name.toLowerCase(Locale.ROOT) + IntavePlugin.defaultColor() + " packets hit a total of " + ChatColor.RED + hardsResyncs + IntavePlugin.defaultColor() + " hard re-syncs"));
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
    selectors = "resources",
    usage = "",
    permission = "intave.command.diagnostics.performance",
    description = ""
  )
  public void resourceStatus(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Resources");
    ResourceRegistry.registeredResources().forEach((identifier, resource) ->
      sender.sendMessage(IntavePlugin.prefix() + " " + identifier.substring(0, 2) + " of " + HashAccess.readHashFromStream(resource.read()))
    );
  }

  @SubCommand(
    selectors = "invalidatecaches",
    usage = "",
    permission = "intave.command.diagnostics.performance",
    description = ""
  )
  public void cacheInvalidate(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Invalidating caches..");
    for (Resource value : ResourceRegistry.registeredResources().values()) {
      value.delete();
    }
    sender.sendMessage(IntavePlugin.prefix() + "Done, please restart Intave");
  }

  private static final DateTimeFormatter MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss.SSS");

  @SubCommand(
    selectors = "threaddump",
    usage = "",
    permission = "intave.command.diagnostics.statistics",
    description = "Create and save thread dumps"
  )
  public void createThreadDump(CommandSender sender) {
    File dumpsFolder = new File(plugin.dataFolder(), "dumps");
    File threadDumpFile = new File(dumpsFolder, threadDumpFileName());

    try {
      dumpsFolder.mkdir();
      threadDumpFile.createNewFile();
    } catch (IOException exception) {
      exception.printStackTrace();
      return;
    }

    try {
      FileOutputStream stream = new FileOutputStream(threadDumpFile);
      PrintStream printStream = new PrintStream(stream);
      printStream.println("Static environment");
      printStream.println(" Time: " + LocalDateTime.now().format(MESSAGE_DATE_FORMATTER));
      printStream.println(" Intave: " + IntavePlugin.version());
      printStream.println(" ProtocolLib: " + Bukkit.getPluginManager().getPlugin("ProtocolLib").getDescription().getVersion());
      if (Bukkit.getPluginManager().getPlugin("ViaVersion") != null) {
        printStream.println(" ViaVersion: " + Bukkit.getPluginManager().getPlugin("ViaVersion").getDescription().getVersion());
      } else {
        printStream.println(" ViaVersion not present");
      }
      printStream.println(" Server: "/* + Bukkit.getServerName() + "/"*/ + Bukkit.getVersion() + "/" + Bukkit.getBukkitVersion());
      printStream.println(" Minecraft: " + MinecraftVersion.getCurrentVersion().toString());
      printStream.println("Players");
      printStream.println(" Thread dump creator: " + sender.getName());
      printStream.println(" Players online: " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
      printStream.println(" ");
      Thread.getAllStackTraces().forEach((thread, stackTraceElements) -> {
        String threadName = thread.getName();
        if (threadName.contains("Netty") || threadName.contains("Intave") || threadName.contains("Server thread")) {
          printStream.println("Thread " + threadName);
          Exception exception = new Exception();
          exception.setStackTrace(stackTraceElements);
          exception.printStackTrace(printStream);
        }
      });
      printStream.flush();
      printStream.close();
    } catch (FileNotFoundException exception) {
      exception.printStackTrace();
    }
    sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "Threaddump created");
    sender.sendMessage(IntavePlugin.prefix() + "You can find it under " + threadDumpFile.getAbsolutePath());
  }

  private final Map<String, UUID> packetLoggers = GarbageCollector.watch(new HashMap<>());
  private final Map<UUID, PacketAdapter> adapterMap = GarbageCollector.watch(new HashMap<>());
  private final Map<UUID, PrintStream> packetLogStreams = GarbageCollector.watch(new HashMap<>());

  {
    ShutdownTasks.add(() -> {
      packetLogStreams.forEach((uuid, printStream) -> {
        printStream.flush();
        printStream.close();
      });
    });
  }

  @SubCommand(
    selectors = {"packetlog", "pl"},
    usage = "[<target>]",
    permission = "intave.command.diagnostics.statistics",
    description = "Create and save packet logs"
  )
  public void startPacketLog(CommandSender sender, Player target) {
    File logsFolder = new File(plugin.dataFolder(), "packetlogs");
    File packetLogFile = new File(logsFolder, packetLogFileName(target.getName()));

    UUID userId = target.getUniqueId();
    if (packetLoggers.containsKey(sender.getName())) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "Packetlogging stopped for " + packetLoggers.get(sender.getName()).toString());
      PacketAdapter remove1 = adapterMap.remove(userId);
      ProtocolLibrary.getProtocolManager().removePacketListener(remove1);
      packetLoggers.remove(sender.getName());
      PrintStream remove = packetLogStreams.remove(userId);
      if (remove != null) {
        remove.flush();
        remove.close();
      }
      return;
    }

    try {
      logsFolder.mkdir();
      packetLogFile.createNewFile();
    } catch (IOException exception) {
      exception.printStackTrace();
      return;
    }

    try {
      OutputStream stream = new FileOutputStream(packetLogFile);
      stream = new BufferedOutputStream(stream);
      PrintStream printStream = new PrintStream(stream);

      PacketAdapter adapter = new PacketAdapter(
        IntavePlugin.singletonInstance(),
        PacketType.values()
      ) {
        @Override
        public void onPacketSending(PacketEvent event) {
          if (event.getPlayer().getUniqueId().equals(userId)) {
            synchronized (printStream) {
              printStream.println((System.currentTimeMillis() % 1000) + " --> " + event.getPacketType().name() + " " + packetContent(event.getPacket()));
            }
          }
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
          if (event.getPlayer().getUniqueId().equals(userId)) {
            synchronized (printStream) {
              printStream.println((System.currentTimeMillis() % 1000) + " <-- " + event.getPacketType().name() + " " + packetContent(event.getPacket()));
            }
          }
        }
      };
      adapterMap.put(userId, adapter);
      ProtocolLibrary.getProtocolManager().addPacketListener(adapter);

      packetLoggers.put(sender.getName(), userId);
      packetLogStreams.put(userId, printStream);

    } catch (FileNotFoundException exception) {
      exception.printStackTrace();
    }
    sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "Packetlogging started for " + target.getName());
    sender.sendMessage(IntavePlugin.prefix() + "You can find it under " + packetLogFile.getAbsolutePath());
  }

  private static String packetContent(PacketContainer packet) {
    String contents = packet.getModifier()
      .getValues().stream()
      .map(o -> o == null ? "null" : o.toString())
      .filter(s -> !s.isEmpty())
      .collect(Collectors.joining(", "));
    return "{"+ contents + "}";
  }

  private static final DateTimeFormatter FILE_MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH-mm-ss");

  private static String threadDumpFileName() {
    return "intave-threaddump-" + LocalDateTime.now().format(FILE_MESSAGE_DATE_FORMATTER).toLowerCase(Locale.ROOT) + ".txt";
  }

  private static String packetLogFileName(String playername) {
    return "intave-packetlog-" + playername + "-" + LocalDateTime.now().format(FILE_MESSAGE_DATE_FORMATTER).toLowerCase(Locale.ROOT) + ".txt";
  }

  @SubCommand(
    selectors = "statistics",
    usage = "",
    permission = "intave.command.diagnostics.statistics",
    description = "Output check statistics"
  )
  public void checkStatisticsCommand(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Loading check statistics...");
    List<Check> checks = new ArrayList<>(plugin.checks().checks());
    checks.sort(Comparator.comparing(check -> check.baseStatistics().totalFails()));
    boolean output = false;
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
      output = true;
    }
    if (!output) {
      sender.sendMessage(IntavePlugin.prefix() + "No check statistics available");
    }
  }

  public static DiagnosticsStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new DiagnosticsStage();
    }
    return singletonInstance;
  }
}