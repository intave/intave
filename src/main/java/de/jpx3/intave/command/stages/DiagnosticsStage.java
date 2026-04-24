package de.jpx3.intave.command.stages;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Optional;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.diagnostic.PacketSynchronizations;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.nayoro.event.AttackEvent;
import de.jpx3.intave.module.nayoro.event.BlockPlaceEvent;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.module.testing.ChestLootProvider;
import de.jpx3.intave.module.tracker.player.PacketLogging;
import de.jpx3.intave.player.DamageModify;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.ResourceRegistry;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import de.jpx3.intave.user.storage.PlaytimeStorage;
import de.jpx3.intave.util.PlainHashes;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Turtle;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;

public final class DiagnosticsStage extends CommandStage {
  private static final DateTimeFormatter MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH.mm.ss.SSS");
  private static final DateTimeFormatter FILE_MESSAGE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy-HH-mm-ss");
  private static DiagnosticsStage singletonInstance;
  private final IntavePlugin plugin;

  private final Map<UUID, PacketListenerAbstract> adapterMap = GarbageCollector.watch(new HashMap<>());

  //  private final Set<UUID> damageDebug = GarbageCollector.watch(new HashSet<>());

  private DiagnosticsStage() {
    super(BaseStage.singletonInstance(), "diagnostics");
    plugin = IntavePlugin.singletonInstance();
  }

  private static String threadDumpFileName() {
    return "intave-threaddump-" + LocalDateTime.now().format(FILE_MESSAGE_DATE_FORMATTER).toLowerCase(Locale.ROOT) + ".txt";
  }

  public static DiagnosticsStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new DiagnosticsStage();
    }
    return singletonInstance;
  }

  @SubCommand(selectors = "environment", usage = "", description = "Dumps environment infos to a players chat", permission = "intave.command.diagnostics.performance")
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
    String packetEventsVersion = packetEventsVersion();
    sender.sendMessage(ChatColor.GRAY + "Spigot is " + ChatColor.WHITE + serverVersion);
    sender.sendMessage(ChatColor.GRAY + "PacketEvents is " + ChatColor.WHITE + packetEventsVersion);
    sender.sendMessage(ChatColor.GRAY + "Intave is " + ChatColor.WHITE + intaveVersion);

    TextComponent message = new TextComponent("[Copy report message to chat]");
    message.setColor(net.md_5.bungee.api.ChatColor.GRAY);
    message.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "Environment: `" + playerVersion + "`,`" + serverVersion + "`,`" + packetEventsVersion + "`,`" + intaveVersion + "`"));

    if (player != null) {
      // Send the message to the player
      player.spigot().sendMessage(message);
    }
  }

  @SubCommand(
    selectors = "nerfers",
    usage = "",
    description = "Output active nerfers",
    permission = "intave.command.diagnostics.performance"
  )
  public void nerfers(User user) {
    if (IntaveControl.DISABLE_LICENSE_CHECK || IntaveControl.AUTHENTICATION_DEBUG_MODE) {
      List<PunishmentMetadata.AttackNerfer> attackNerfers = user.meta().punishment().activeNerfers();
      if (attackNerfers.isEmpty()) {
        user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "No active nerfers");
      } else {
        user.player().sendMessage(IntavePlugin.prefix() + "Active nerfers: " + attackNerfers.stream().map(nerfer -> nerfer.strategy().typeName()).collect(Collectors.joining(", ")));
      }
    } else {
      user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Currently unavailable");
    }
  }

  private enum Mode {
    PHYSICS_EVAL("pxeval")

    ;

    private String name;

    private Mode(String name) {
      this.name = name;
    }

    public String typeName() {
      return this.name;
    }
  }

  @SubCommand(
    selectors = "trustmap",
    usage = "",
    permission = "sibyl"
  )
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
    selectors = "nerf",
    usage = "",
    description = "Output active nerfers",
    permission = "intave.command.diagnostics.performance"
  )
  public void nerf(User user, String type) {
    if (IntaveControl.DISABLE_LICENSE_CHECK || IntaveControl.AUTHENTICATION_DEBUG_MODE) {
      try {
        AttackNerfStrategy strategy = AttackNerfStrategy.byName(type);
        if (strategy == null) {
          user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Invalid nerf type");
          return;
        }
        user.nerfPermanently(strategy, "command");
        user.player().sendMessage(IntavePlugin.prefix() + "Nerf " + strategy.typeName() + " applied");
      } catch (Exception exception) {
        user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Invalid nerf type");
      }
    } else {
      user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Currently unavailable");
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
    player.sendMessage(IntavePlugin.prefix() + connection.tracedEntities().stream().map(entity -> entity.entityName() + "/" + entity.entityId()).collect(Collectors.toList()));
  }

  @SubCommand(
    selectors = "turtle",
    usage = "",
    description = "Spawn a turtle",
    permission = "intave.command.diagnostics.performance"
  )
  public void turtleCommand(User user) {
    Player player = user.player();
    if (MinecraftVersions.VER1_20.atOrAbove()) {
      Bukkit.getScheduler().runTask(IntavePlugin.singletonInstance(), () -> {
        Turtle turtle = player.getWorld().spawn(player.getLocation(), Turtle.class);
        turtle.setPassenger(player);
      });
      player.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Turtle spawned");
    } else {
      player.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Nah");
    }
  }

  @SubCommand(selectors = "ntrace", usage = "", description = "Sample click/attack trace", permission = "intave.command.diagnostics.performance")
  public void ntraceCommand(User user) {
    Player player = user.player();
    Nayoro nayoro = Modules.nayoro();
    nayoro.pushSink(user, new EventSink() {
      @Override
      public void visit(de.jpx3.intave.module.nayoro.event.ClickEvent event) {
        player.sendMessage("ClickEvent");
      }

      @Override
      public void visit(AttackEvent event) {
        player.sendMessage("AttackEvent");
      }

      @Override
      public void visit(BlockPlaceEvent event) {
        Synchronizer.synchronize(() -> {
          player.sendMessage("BlockPlaceEvent{");
          player.sendMessage("  " + event.placedBlock());
          player.sendMessage("  " + event.againstBlock());
          player.sendMessage("  " + event.direction());
          player.sendMessage("  " + event.rotation());
          player.sendMessage("  " + event.eyePosition());
          player.sendMessage("  " + event.endOfRaytrace());
          //        player.sendMessage("  " + event.facingX());
          //        player.sendMessage("  " + event.facingY());
          //        player.sendMessage("  " + event.facingZ());
          player.sendMessage("  " + event.hand());
          player.sendMessage("  " + event.typeName());
          player.sendMessage("  " + event.amountInHand());
          player.sendMessage("}");
        });
      }

      @Override
      public String name() {
        return "ntrace/anonymous";
      }
    });
    player.sendMessage(ChatColor.RED + "Added ntracing");
  }

  @SubCommand(
    selectors = "storagetrace",
    usage = "",
    description = "",
    permission = "intave.command.diagnostics.performance"
  )
  public void storageTrace(User user) {
    Player player = user.player();
    PlaytimeStorage storage = user.storageOf(PlaytimeStorage.class);
    if (storage.readTag() != 0) {
      player.sendMessage("Removing storage-tag");
      storage.removeDebugTag();
      return;
    }
    player.sendMessage("You are now in storage trace mode");
    storage.setDebugTag();
    Synchronizer.synchronize(() -> {
      player.sendMessage("Your storage-tag is " + storage.readTag());
    });
  }

  @SubCommand(
    selectors = "playtime",
    usage = "[<target>]",
    description = "",
    permission = "sibyl"
  )
  public void playtimeOf(User user, @Optional Player target) {
    Player player = user.player();
    Player targetPlayer = target == null ? player : target;
    User targetUser = UserRepository.userOf(targetPlayer);
    PlaytimeStorage storage = targetUser.storageOf(PlaytimeStorage.class);
    long minutesPlayed = storage.minutesPlayed();
    long minutesAfk = storage.minutesAfk();
    Synchronizer.synchronize(() -> {
      player.sendMessage("The player " + targetPlayer.getName() + " has played for " + minutesPlayed + " minutes and was afk for " + minutesAfk + " minutes");
    });
  }

  @SubCommand(
    selectors = "etxtrace",
    usage = "",
    description = "Sample click/attack trace",
    permission = "intave.command.diagnostics.performance"
  )
  public void etxTraceCommand(User user) {
    Player player = user.player();
    player.sendMessage(ChatColor.DARK_PURPLE + "Toggled tracing entity trackings");
    user.meta().connection().debugEntityTracing = !user.meta().connection().debugEntityTracing;
  }

  @SubCommand(
    selectors = {"platrace"},
    usage = "",
    description = "Sample attack protocollib trace",
    permission = "intave.command.diagnostics.performance"
  )
  public void attackTraceCommand(User user) {
    user.player().sendMessage(IntavePlugin.prefix() + "PacketEvents owns packet listener ordering; legacy listener trace is no longer available.");
  }

  @SubCommand(selectors = "damage", usage = "", description = "Put your attack damage in chat", permission = "intave.command.diagnostics.performance")
  public void damageCommand(User user) {
    Player player = user.player();
    Bukkit.getPluginManager().registerEvents(new Listener() {
      @org.bukkit.event.EventHandler
      public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager().equals(player)) {
          double baseDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
          double predictedDamage = DamageModify.attackDamageOf(player) + DamageModify.sharpnessDamageOf(player.getInventory().getItemInMainHand());
          boolean probablyCritical = Math.abs(baseDamage * 1.5 - predictedDamage) < 0.01;
          player.sendMessage("Dealt " + event.getFinalDamage() + " damage" + (probablyCritical ? " (critical)" : ""));
        }
      }
    }, plugin);
  }

  @SubCommand(selectors = "timings", usage = "", description = "Output timing data", permission = "intave.command.diagnostics.performance")
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
      String message = String.format("%s: %s::%sms (%s&f ms/c)", timing.coloredName(), timing.recordedCalls(), MathHelper.formatDouble(timing.totalDurationMillis(), 4), (suspicious ? (dumping ? ChatColor.RED : ChatColor.YELLOW) : ChatColor.GREEN) + "" + MathHelper.formatDouble(timing.averageCallDurationInMillis(), 8));
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
    selectors = "fireball",
    usage = "",
    description = "Fireball catapult",
    permission = "intave.command.diagnostics.performance"
  )
  public void fireballCommand(User user) {
    Player player = user.player();

//    val gamer = Gamer.getGamer(player);

//    if (gamer == null || gamer.isSpectator()) {
//      return;
//    }

//    val damagerPlayer = game.getMetadata().<Player>get(damager, "owner");
//    if (!game.getSetting(GameSetting.FIREBALL_FRIENDLY_FIRE, true)) {
//      val team = game.getTeamManager().getTeam(gamer);
//
//      if (team != null) {
//        val damagerGamer = Gamer.getGamer(damagerPlayer);
//
//        if (damagerGamer != null && !damagerGamer.equals(gamer) && team.contains(damagerGamer)) {
//          return;
//        }
//      }
//    }

    player.damage(0);

    double multiply = 1.5;
    Vector vector = getPosition(player, player.getLocation(), player.getLocation().clone().add(0, 0.5, 0), 1.2);

    vector.setX(vector.getX() * multiply);
//    if (game.getSetting(GameSetting.MULTIPLY_Y_VELOCITY, false)) {
      vector.setY(vector.getY() * multiply);
//    }
    vector.setZ(vector.getZ() * multiply);

    player.setVelocity(vector);
  }

  private static Vector getPosition(Entity entity, Location location1, Location location2, double defaultY) {
    double distance = location1.distance(location2);

    double x = location1.getX() - location2.getX();
    double y = (defaultY / distance);
    double z = location1.getZ() - location2.getZ();

    double distanceForce = Math.min(1.2, 0.9 / distance);

    double finalX = x * distanceForce;
    double finalY = entity.isOnGround() ? (distance <= 1.5 ? defaultY : (y * 1.2)) : defaultY;
    double finalZ = z * distanceForce;

    return new Vector(finalX, finalY, finalZ);
  }

  @SubCommand(
    selectors = "resistance",
    usage = "",
    description = "Give everyone on the server resistance",
    permission = "intave.command.diagnostics.performance"
  )
  public void resistanceCommand(User user) {
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Currently unavailable");
      return;
    }
    Bukkit.getOnlinePlayers().forEach(player -> {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 999999, 100));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 999999, 100));
      }
    );
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
      int attempts = 100;
      BoundingBox playerBox = BoundingBox.fromPosition(
        user, user.meta().movement(), player.getLocation()
      );
      double moveX;
      do {
        moveX = ThreadLocalRandom.current().nextGaussian();
      } while (Collision.present(player, playerBox.move(moveX, 0, 0)) && attempts-- > 0);
      if (attempts <= 0) moveX = 0;
      attempts = 100;
      double moveY;
      do {
        moveY = ThreadLocalRandom.current().nextGaussian();
      } while (Collision.present(player, playerBox.move(0, moveY, 0)) && attempts-- > 0);
      double moveZ;
      do {
        moveZ = ThreadLocalRandom.current().nextGaussian();
      } while (Collision.present(player, playerBox.move(0, 0, moveZ)) && attempts-- > 0);
      if (attempts <= 0) moveZ = 0;
      if (attempts <= 0) moveY = 0;
      player.teleport(player.getLocation().clone().add(moveX, moveY, moveZ));

      if (user.receives(MessageChannel.DEBUG_TELEPORT)) {
        player.sendMessage(IntavePlugin.prefix() + "Teleport to " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ() + " " + " as " + ChatColor.RED + " it was command-requested");
      }
    }, 20, 3);
  }

  @SubCommand(
    selectors = "flyingswitch",
    usage = "",
    description = "Spam teleport yourself",
    permission = "intave.command.diagnostics.performance"
  )
  public void flyingSwitch(User user) {
    Player player = user.player();
    player.sendMessage(ChatColor.RED + "Logout to stop");

    int[] id = {0};
    id[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
      if (!player.isOnline()) {
        Bukkit.getScheduler().cancelTask(id[0]);
        return;
      }
      boolean canFly = player.getAllowFlight();
      Synchronizer.synchronizeDelayed(() -> player.setAllowFlight(!canFly), 40);
      player.sendMessage(IntavePlugin.prefix() + "Flying will be " + ChatColor.RED + (!canFly ? "enabled" : "disabled") + ChatColor.GRAY + " in 2 seconds");
    }, 20, 20 * 10);
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
    strider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 2, false, false));
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
    PacketListenerAbstract adapter = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
      final long timeout = System.currentTimeMillis() + 60000;

      @Override
      public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.WINDOW_CONFIRMATION
          && event.getPacketType() != PacketType.Play.Server.PING) {
          return;
        }
        Player receiver = event.getPlayer();
        if (receiver == null || !receiver.getUniqueId().equals(userId)) {
          return;
        }
        if (System.currentTimeMillis() > timeout) {
          PacketEvents.getAPI().getEventManager().unregisterListener(this);
          adapterMap.remove(userId);

          Player blayer = Bukkit.getPlayer(userId);
          if (blayer.isOnline()) {
            blayer.sendMessage(IntavePlugin.prefix() + ChatColor.GREEN + "You can now get feedback again.");
          }
          return;
        }
        event.setCancelled(true);
      }
    };
    adapterMap.put(userId, adapter);
    PacketEvents.getAPI().getEventManager().registerListener(adapter);
  }

  @SubCommand(
    selectors = "resync",
    usage = "",
    permission = "intave.command.diagnostics.statistics",
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

  public <K extends Comparable<? super K>, V extends Comparable<? super V>> Map<K, V> sortHashMapByValues(Map<K, V> passedMap) {
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
      sender.sendMessage(IntavePlugin.prefix() + " " + identifier.substring(0, 2) + " of " + resourceHash(resource))
    );
  }

  private String resourceHash(Resource resource) {
    try {
      return PlainHashes.sha256(resource.read());
    } catch (IOException exception) {
      return "unavailable";
    }
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
      printStream.println(" PacketEvents: " + packetEventsVersion());
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

  @SubCommand(selectors = {"packetlog", "pl"}, usage = "[<target>]", permission = "intave.command.diagnostics.statistics", description = "Create and save packet logs")
  public void startPacketLog(CommandSender sender, Player target) {
    Synchronizer.synchronize(() -> {
      Modules.find(PacketLogging.class).togglePacketLogging(sender, target);
    });
  }

  @SubCommand(selectors = "statistics", usage = "", permission = "intave.command.diagnostics.statistics", description = "Output check statistics")
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

  @SubCommand(
    selectors = "lootchest",
    usage = "",
    description = "Open a loot chest",
    permission = "intave.command.diagnostics.performance"
  )
  public void onLootChest(User user) {
    ChestLootProvider provider = Modules.find(ChestLootProvider.class);
    if (!user.player().isOp()) {
      user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "You need to be op to use this command");
      return;
    }
    if (!IntaveControl.DISABLE_LICENSE_CHECK) {
      user.player().sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Not available in production");
      return;
    }
    provider.openLootChestCommand(user.player());
  }

  private static String packetEventsVersion() {
    Plugin packetEvents = Bukkit.getPluginManager().getPlugin("packetevents");
    return packetEvents == null ? "missing" : packetEvents.getDescription().getVersion();
  }
}
