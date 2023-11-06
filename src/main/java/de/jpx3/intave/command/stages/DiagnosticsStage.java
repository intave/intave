package de.jpx3.intave.command.stages;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.ResourceRegistry;
import de.jpx3.intave.security.HashAccess;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;

public final class DiagnosticsStage extends CommandStage {
  private static DiagnosticsStage singletonInstance;
  private final IntavePlugin plugin;

  private DiagnosticsStage() {
    super(BaseStage.singletonInstance(), "diagnostics");
    plugin = IntavePlugin.singletonInstance();
  }

  @SubCommand(
    selectors = "environment",
    usage = "",
    description = "Dumps environment infos to a players chat",
    permission = "intave.command.diagnostics.performance"
  )
  public void environment(CommandSender sender) {
    Player player = null;
    String playerVersion = "";
    if (sender instanceof Player) {
      player = ((Player) sender);
      User user = UserRepository.userOf(player);
      ProtocolMetadata protocol = user.meta().protocol();
      playerVersion = protocol.versionString() + "@" + protocol.protocolVersion();
      sender.sendMessage(ChatColor.GRAY + "Player is " + ChatColor.WHITE + playerVersion);
    } else {
      sender.sendMessage(ChatColor.GRAY + "Run this command in-game to display client version");
    }
    String intaveVersion = IntavePlugin.version();
    String serverVersion = Bukkit.getName() + "@" + Bukkit.getVersion();
    String protocolLibVersion = ProtocolLibrary.getPlugin().getDescription().getVersion();
    sender.sendMessage(ChatColor.GRAY + "Spigot is " + ChatColor.WHITE + serverVersion);
    sender.sendMessage(ChatColor.GRAY + "ProtocolLib is " + ChatColor.WHITE + protocolLibVersion);
    sender.sendMessage(ChatColor.GRAY + "Intave is " + ChatColor.WHITE + intaveVersion);

    TextComponent message = new TextComponent("[Copy report message to chat]");
    message.setColor(net.md_5.bungee.api.ChatColor.GRAY);
    message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "Environment: `" +playerVersion + "`,`" + serverVersion + "`,`" + protocolLibVersion + "`,`" + intaveVersion + "`"));

    if (player != null) {
      // Send the message to the player
      player.spigot().sendMessage(message);
    }
  }

  @SubCommand(
    selectors = "entities",
    usage = "",
    description = "Output entity data",
    permission = "intave.command.diagnostics.performance"
  )
  public void entityCommand(User user) {
    Player player = user.player();

    ConnectionMetadata connection = user.meta().connection();
    int totalEntities = connection.entities().size();
//    int tickedEntities = connection.tickedEntities().size();
    int tracedEntities = connection.tracedEntities().size();
    player.sendMessage(IntavePlugin.prefix() + "Monitoring " + ChatColor.RED + totalEntities + IntavePlugin.defaultColor() + " entities, tracing " + ChatColor.RED + tracedEntities + IntavePlugin.defaultColor() + " entities");
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
    selectors = "teleportspam",
    usage = "",
    description = "Spam teleport yourself",
    permission = "intave.command.diagnostics.performance"
  )
  public void teleportSpam(User user) {
    Player player = user.player();
    player.sendMessage(ChatColor.RED + "Logout to stop");

    int[] id = {0};
    id[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
      if (!player.isOnline()) {
        Bukkit.getScheduler().cancelTask(id[0]);
        return;
      }
      player.teleport(player.getLocation().add(0, 0, 0));
    }, 20, 3);
  }

  @SubCommand(
    selectors = "walkspeed",
    usage = "",
    description = "Set your walkspeed",
    permission = "intave.command.diagnostics.performance"
  )
  public void walkSpeed(User user, @Optional Double speed, @Optional WalkSpeedMethod method) {
    if (speed == null) {
      speed = 0.1d;
    }
    if (method == null) {
      method = WalkSpeedMethod.ATTRIBUTE;
    }
    Player player = user.player();

    if (method == WalkSpeedMethod.ATTRIBUTE) {
      player.getAttribute(GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
    } else {
      player.setWalkSpeed(speed.floatValue());
    }
  }

  public static enum WalkSpeedMethod {
    DIRECT,
    ATTRIBUTE
  }

  @SubCommand(
    selectors = "vehicleboost",
    usage = "",
    description = "Boost your vehicle",
    permission = "intave.command.diagnostics.performance"
  )
  public void boostVehicle(User user) {
    Player player = user.player();
    LivingEntity strider = (LivingEntity) player.getVehicle();

    int duration = 100;
    if (strider.hasPotionEffect(PotionEffectType.SPEED)) {
      duration += strider.getPotionEffect(PotionEffectType.SPEED).getDuration();
    }
    strider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 2,
      false, false));
  }

  @SubCommand(
    selectors = "simnofeedback",
    usage = "",
    description = "Temporarily ignore feedback packets",
    permission = "intave.command.diagnostics.performance"
  )
  public void simulateNoFeedback(User user) {
    Player player = user.player();
    UUID userId = player.getUniqueId();

    player.sendMessage(ChatColor.RED + "You will need to wait one minute to get feedback again.");
    PacketAdapter adapter = new PacketAdapter(
      IntavePlugin.singletonInstance(),
      PacketType.Play.Server.TRANSACTION,
      PacketType.Play.Server.PING
    ) {
      final long timeout = System.currentTimeMillis() + 60000;

      @Override
      public void onPacketSending(PacketEvent event) {
        if (System.currentTimeMillis() > timeout) {
          ProtocolLibrary.getProtocolManager().removePacketListener(this);
          adapterMap.remove(userId);

          Player blayer = Bukkit.getPlayer(userId);
          if (blayer.isOnline()) {
            blayer.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "You can now get feedback again.");
          }
          return;
        }
        event.setCancelled(true);
      }

      @Override
      public void onPacketReceiving(PacketEvent event) {
      }
    };
    adapterMap.put(userId, adapter);
    ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
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
    File logsFolder = IntaveControl.GOMME_MODE ? new File("logs") : new File(plugin.dataFolder(), "packetlogs");
    File packetLogFile = new File(logsFolder, packetLogFileName(target.getName()));

    UUID userId = target.getUniqueId();
    if (packetLoggers.containsKey(sender.getName())) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "Packetlogging stopped");
      sender.sendMessage(IntavePlugin.prefix() + "Type /intave diagnostics packetlogupload to upload the log");
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
        ListenerPriority.MONITOR,
        PacketType.values(),
        ListenerOptions.SKIP_PLUGIN_VERIFIER
      ) {
        @Override
        public void onPacketSending(PacketEvent event) {
          if (event.getPlayer().getUniqueId().equals(userId)) {
            synchronized (printStream) {
              printStream.println((System.currentTimeMillis() % 1000) + " --> " + event.getPacketType().name() + (event.isCancelled() ? " (cancelled)" : "") + " " + packetContent(event.getPacket()));
            }
          }
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
          if (event.getPlayer().getUniqueId().equals(userId)) {
            synchronized (printStream) {
              printStream.println((System.currentTimeMillis() % 1000) + " <-- " + event.getPacketType().name() + (event.isCancelled() ? " (cancelled)" : "") + " " + packetContent(event.getPacket()));
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
    if (packet == null) return "null";
//    if (packet.getType().name().contains("CHAT") || packet.getType().name().contains("TAB_COMPLETE")) {
//      return "REDACTED";
//    }
    String contents = packet.getModifier()
      .getValues().stream()
      .map(DiagnosticsStage::stringFromType)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.joining(", "));
    return "{" + contents + "}";
  }

  private static String stringFromType(Object object) {
    if (object == null) {
      return "null";
    } else if (object instanceof Number) {
      return object.toString();
    } else if (object instanceof String) {
      return "\"" + object + "\"";
    } else if (object instanceof Boolean) {
      return object.toString();
    } else if (object instanceof byte[]) {
      byte[] bytes = (byte[]) object;
      if (bytes.length == 0) {
        return "[]";
      } else {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int limit = Math.min(bytes.length, 40);
        for (int i = 0; i < limit; i++) {
          builder.append(bytes[i]);
          if (i != limit - 1) {
            builder.append(", ");
          }
        }
        if (bytes.length > 40) {
          builder.append("...");
        }
        builder.append("]");
        return builder.toString();
      }
    } else if (object instanceof int[]) {
      int[] ints = (int[]) object;
      if (ints.length == 0) {
        return "[]";
      } else {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int limit = Math.min(ints.length, 40);
        for (int i = 0; i < limit; i++) {
          builder.append(ints[i]);
          if (i != limit - 1) {
            builder.append(", ");
          }
        }
        if (ints.length > 40) {
          builder.append("...");
        }
        builder.append("]");
        return builder.toString();
      }
    } else if (object instanceof Object[]) {
      Object[] objects = (Object[]) object;
      if (objects.length == 0) {
        return "[]";
      } else {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int limit = Math.min(objects.length, 40);
        for (int i = 0; i < limit; i++) {
          builder.append(stringFromType(objects[i]));
          if (i != limit - 1) {
            builder.append(", ");
          }
        }
        if (objects.length > 40) {
          builder.append("...");
        }
        builder.append("]");
        return builder.toString();
      }
    } else if (object instanceof Collection) {
      Collection<?> collection = (Collection<?>) object;
      if (collection.isEmpty()) {
        return "[]";
      } else {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int limit = Math.min(collection.size(), 40);
        int i = 0;
        for (Object o : collection) {
          builder.append(stringFromType(o));
          if (i != limit - 1) {
            builder.append(", ");
          }
          i++;
        }
        if (collection.size() > 40) {
          builder.append("...");
        }
        builder.append("]");
        return builder.toString();
      }
    } else if (object instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) object;
      if (map.isEmpty()) {
        return "{}";
      } else {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        int limit = Math.min(map.size(), 40);
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          builder.append(stringFromType(entry.getKey()));
          builder.append("=");
          builder.append(stringFromType(entry.getValue()));
          if (i != limit - 1) {
            builder.append(", ");
          }
          i++;
        }
        if (map.size() > 40) {
          builder.append("...");
        }
        builder.append("}");
        return builder.toString();
      }
    } else if (object.toString().contains("DataWatcher@")) {
//      WrappedDataWatcher watcher = new WrappedDataWatcher(object);
//      return "DataWatcher{" + watcher.getWatchableObjects().stream().map(watchableObject -> {
//        String value = stringFromType(watchableObject.getValue());
//        return watchableObject.getIndex() + "=" + value;
//      }).collect(Collectors.joining(", ")) + "}";
      return "DataWatcher{...}";
    } else if (object.toString().contains("WatchableObject@")) {
//      WrappedDataWatcher.WrappedDataWatcherObject watcherObject = new WrappedDataWatcher.WrappedDataWatcherObject(object);
//      return "WatchableObject{" + watcherObject.getIndex() + "=" + stringFromType(watcherObject.getHandle()) + "}";
      return "WatchableObject{...}";
    } else {
      return object.toString();
    }
  }

  private static final DateTimeFormatter FILE_MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH-mm-ss");

  private static String threadDumpFileName() {
    return "intave-threaddump-" + LocalDateTime.now().format(FILE_MESSAGE_DATE_FORMATTER).toLowerCase(Locale.ROOT) + ".txt";
  }

  private static String packetLogFileName(String playername) {
    return "intave-packetlog-" + playername + "-" + LocalDateTime.now().format(FILE_MESSAGE_DATE_FORMATTER).toLowerCase(Locale.ROOT) + ".txt";
  }

  @SubCommand(
    selectors = "packetlogupload",
    usage = "",
    permission = "intave.command.diagnostics.statistics",
    description = "Upload packet logs"
  )
  public void uploadPacketLog(CommandSender sender) {
    sender.sendMessage(IntavePlugin.prefix() + "Uploading packet logs...");
    File logsFolder = new File(plugin.dataFolder(), "packetlogs");
    File[] files = logsFolder.listFiles();
    if (files == null) {
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "No packet logs found");
      return;
    }
    // get newest file
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
    File packetLogFile = files[files.length - 1];
    BackgroundExecutors.executeWhenever(() -> {
      upload(packetLogFile, sender);
    });
  }

  private void upload(File file, CommandSender sender) {
    try {
      // upload to anonfile
      URL url = new URL("https://api.anonfiles.com/upload");

      String boundary = Long.toHexString(System.currentTimeMillis());
      URLConnection connection = url.openConnection();
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      try (
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))
      ) {
        writer.println("--" + boundary);
        writer.println("Content-Disposition: form-data; name=file; filename=\"" + file.getName() + "\"");
        writer.println("Content-Type: text/plain; charset=UTF-8");
        writer.println();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
          for (String line; (line = reader.readLine()) != null; ) {
            writer.println(line);
          }
        }
        writer.println("--" + boundary + "--");
      }

      connection.connect();

      HttpsURLConnection httpsURLConnection = (HttpsURLConnection) connection;
      int responseCode = httpsURLConnection.getResponseCode();

      if (responseCode != 200) {
        sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Failed to upload");
        return;
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()))) {
        String str = reader.lines().collect(Collectors.joining("\n"));
        try {
          JsonObject jsonObject = new JsonParser().parse(str).getAsJsonObject();
          String url1 = jsonObject.getAsJsonObject("data").getAsJsonObject("file").getAsJsonObject("url").get("short").getAsString();
          sender.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "Uploaded to " + url1);
        } catch (Exception exception) {
          exception.printStackTrace();
          sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Failed to upload");
          System.out.println(str);
        }
//        System.out.println(str);
      }

    } catch (IOException exception) {
      exception.printStackTrace();
      sender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Failed to upload");
    }
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