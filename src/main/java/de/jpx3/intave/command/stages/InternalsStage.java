package de.jpx3.intave.command.stages;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.CommandStage;
import de.jpx3.intave.command.Forward;
import de.jpx3.intave.command.SubCommand;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.ViolationStorage;
import de.jpx3.intave.world.WorldHeight;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class InternalsStage extends CommandStage {
  private static InternalsStage singletonInstance;
  private final IntavePlugin plugin;

  private final WrappedDataWatcher fallbackDatawatcher;

  private InternalsStage() {
    super(BaseStage.singletonInstance(), "internals");
    plugin = IntavePlugin.singletonInstance();

    fallbackDatawatcher = defaultWatcherOf(Bukkit.getWorlds().get(0), EntityType.ARMOR_STAND);
  }

  @SubCommand(
    selectors = "sendnotify",
    usage = "<message...>",
    permission = "intave.command.internals.sendnotify",
    description = "Send notifications"
  )
  public void internalCommand(CommandSender commandSender, String[] message) {
    String fullMessage = Arrays.stream(message).map(s -> s + " ").collect(Collectors.joining()).trim();
    Modules.violationProcessor().broadcastNotify(fullMessage);
  }

  @SubCommand(
    selectors = "bot",
    usage = "<player> <type>",
    description = "Bot related commands",
    permission = "intave.command.internals.bot"
  )
  @Forward(
    target = BotStage.class
  )
  public void botCommand(CommandSender commandSender) {
  }

  @SubCommand(
    selectors = "entitylag",
    usage = "<player>",
    permission = "intave.command.internals.entitylag",
    description = "Causes severe lag to a user"
  )
  public void lagPlayer(CommandSender commandSender, Player target) {
    int[] task = new int[]{0};
    task[0] = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
      if (!target.isOnline()) {
        Bukkit.getScheduler().cancelTask(task[0]);
        TaskTracker.stopped(task[0]);
        return;
      }
      Synchronizer.synchronize(() -> {
        for (int i = 0; i < 1000; i++) {
          sendPacket(target);
        }
        target.closeInventory();
      });
    }, 20 * 2, 20);
    TaskTracker.begun(task[0]);

    commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + target.getName() + " " + IntavePlugin.defaultColor() + "will now slowly begin to lag");
  }

  private void sendPacket(Player player) {
    PacketContainer newPacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);

    newPacket.getIntegers().
      write(0, ThreadLocalRandom.current().nextInt(100000000, 200000000)).
      write(1, (int) EntityType.ARMOR_STAND.getTypeId()).
      write(2, (int) (player.getLocation().getX() * 32)).
      write(3, -2 * 32).
      write(4, (int) (player.getLocation().getZ() * 32));

    newPacket.getDataWatcherModifier().
      write(0, fallbackDatawatcher);

    User user = UserRepository.userOf(player);
    user.ignoreNextOutboundPacket();
    PacketSender.sendServerPacket(player, newPacket);
    user.receiveNextOutboundPacketAgain();
  }

  private WrappedDataWatcher defaultWatcherOf(World world, EntityType type) {
    Entity entity = world.spawnEntity(new Location(world, 0, WorldHeight.UPPER_WORLD_LIMIT, 0), type);
    WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(entity).deepClone();
    entity.remove();
    return watcher;
  }

  @SubCommand(
    selectors = "storelog",
    usage = "<player> <check> <vl>",
    permission = "intave.command.internals.storelog",
    description = "Store the log of a player"
  )
  public void storeLog(CommandSender commandSender, Player target, String checkName, Double violationLevel) {
    User user = UserRepository.userOf(target);
    ViolationStorage violationStorage = user.storageOf(ViolationStorage.class);
    violationStorage.noteViolation(checkName, violationLevel.intValue());
    if (ViolationStorage.USE_AUTO_STORAGE) {
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Auto storage is enabled. This command will not work.");
      return;
    }
    commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED +"Added storage entry for "+ target.getName() + " " + IntavePlugin.defaultColor() + "reaching " + ChatColor.RED + violationLevel.intValue() + " VL " + IntavePlugin.defaultColor() + "on " + ChatColor.RED + checkName);
  }

  @SubCommand(
    selectors = {"collectivekick", "kickip"},
    usage = "<player> <message>",
    permission = "intave.command.internals.collectivekick",
    description = "Kicks all players with the same ip as the target player"
  )
  public void collectiveKick(CommandSender commandSender, Player target, String[] messageParts) {
    String message = Arrays.stream(messageParts).map(s -> s + " ").collect(Collectors.joining()).trim();
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      if (!onlinePlayer.equals(target) && onlinePlayer.getAddress().getAddress().equals(target.getAddress().getAddress())) {
        String parsedMessage = ChatColor.translateAlternateColorCodes('&', message);
        Synchronizer.synchronize(() -> onlinePlayer.kickPlayer(parsedMessage));
      }
    }
  }

  public static InternalsStage singletonInstance() {
    if (singletonInstance == null) {
      singletonInstance = new InternalsStage();
    }
    return singletonInstance;
  }
}