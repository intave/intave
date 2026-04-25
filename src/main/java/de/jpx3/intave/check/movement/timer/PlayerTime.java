package de.jpx3.intave.check.movement.timer;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackOptions;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.LOGIN;

public class PlayerTime extends MetaCheckPart<Timer, PlayerTime.PlayerTimeMeta> {
  private static final long DEFAULT_DELAY = 500;
  private static final long DEFAULT_THRESHOLD = 10;
  private final Map<UUID, Long> playerJoinTimeCache = GarbageCollector.watch(new HashMap<>());
  private final CheckViolationLevelDecrementer decrementer;

  public PlayerTime(Timer parentCheck) {
    super(parentCheck, PlayerTimeMeta.class);
    decrementer = parentCheck.decrementer();

    Bukkit.getScheduler().runTaskTimer(IntavePlugin.singletonInstance(), () -> {
      UserRepository.applyOnAll(user -> {
        ConnectionMetadata connectionData = user.meta().connection();
        PlayerTimeMeta checkMeta = metaOf(user);
        //noinspection deprecation
        Modules.feedback().synchronize(user.player(), System.nanoTime(), (unused, time) -> {
          connectionData.queueToNextTransaction(() -> {
            checkMeta.time = Math.max(checkMeta.time, checkMeta.limitToBeApplied);
            checkMeta.queuedLimit = time;
          });
        });
      });
    }, 0, 1);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      LOGIN
    }
  )
  public void receiveLogin(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    if (player == null) {
      return;
    }
    User user = userOf(player);
    PlayerTimeMeta checkMeta = metaOf(user);
    playerJoinTimeCache.put(player.getUniqueId(), System.nanoTime());
    PacketSender.sendServerPacketWithoutEvent(player, event.getPacket());
    user.tickFeedback(() -> checkMeta.gameJoinReceived = true);
    event.setCancelled(true);
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    if (!playerJoinTimeCache.containsKey(player.getUniqueId())) {
      User user = userOf(player);
      PlayerTimeMeta checkMeta = metaOf(user);
      playerJoinTimeCache.put(player.getUniqueId(), System.nanoTime());
      user.tickFeedback(() -> checkMeta.gameJoinReceived = true);
    }
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    playerJoinTimeCache.remove(quit.getPlayer().getUniqueId());
  }

  @DispatchTarget
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    if (player == null) {
      return;
    }
    User user = userOf(player);
    PlayerTimeMeta checkMeta = metaOf(user);
    MetadataBundle bundle = user.meta();
    MovementMetadata movementData = bundle.movement();
    AbilityMetadata abilityData = user.meta().abilities();

    // Time was not loaded yet
    if (checkMeta.time == -1) {
      // The default fallback time should never be used but let's provide it if something goes horribly wrong
      checkMeta.time = playerJoinTimeCache.getOrDefault(player.getUniqueId(), System.nanoTime());
    }
    // Exclude players in certain states such as creative, spectator or teleport
    // We also have to check if the player received the initial join packet due to proxies doing weird things
    if (!checkMeta.gameJoinReceived || movementData.lastTeleport == 0
      || abilityData.inGameModeIncludePending(AbilityTracker.GameMode.CREATIVE) || abilityData.ignoringMovementPackets() || user.meta().movement().isInVehicle()) {
      return;
    }

    checkMeta.time += 50_000_000; // allow constant 0.05ms clock error = give 1s every 2000s (33min)
    checkMeta.limitToBeApplied = checkMeta.queuedLimit;
    long diff = checkMeta.time - System.nanoTime();
    statisticApply(user, CheckStatistics::increaseTotal);
    double experimentalLimit = (-user.latency()) * 1_000_000f;

    boolean bad = diff > experimentalLimit + 25_000_000;

//    player.sendMessage((bad ? ChatColor.RED : ChatColor.GRAY) + " " + (diff / (50 * 1_000_000f)) + " / " + (experimentalLimit / (50 * 1_000_000f)));

    int limit = 40_000_000;
    if ((diff > limit) && !user.meta().movement().isInVehicle()) {
      double displayValue = diff / (50 * 1_000_000f);
      if (displayValue < 0.01) {
        displayValue = 0.01;
      }
      String balanceAsString = formatDouble(displayValue, 2);
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("moved too frequently")
        .withDetails(balanceAsString + " ticks ahead")
        .withVL(Math.min(Math.max(displayValue * 3f, 1), 5))
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        checkMeta.lastTimerFlag = System.currentTimeMillis();
        movementData.invalidMovement = true;
        statisticApply(user, CheckStatistics::increaseFails);
        Vector setback = new Vector(0, 0, 0);
        Modules.mitigate().movement().emulationSetBack(player, setback, 3, 2, false);
        if (violationContext.violationLevelAfter() > 50) {
          user.nerfPermanently(AttackNerfStrategy.DMG_HIGH, "timer");
        }
      }
      checkMeta.time -= 10_000_000;
      return;
    } else if (System.currentTimeMillis() - checkMeta.lastTimerFlag > 10000) {
      decrementer.decrement(user, 0.005);
    }
    int blinkLimit = parentCheck().blinkLimit();
    if (!checkMeta.inTeleport && !user.justJoined() && !user.meta().abilities().probablyFlying() && blinkLimit > 0 && diff < -Math.abs(blinkLimit * 50_000_000L)/*-2_500_000_000L*/) {
      checkMeta.inTeleport = true;
      user.tickFeedback(() -> {
        checkMeta.inTeleport = false;
      }, FeedbackOptions.SELF_SYNCHRONIZATION);
      double displayValue = -diff / (50 * 1_000_000f);
      String balanceAsString = formatDouble(displayValue, 2);
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("is halting game ticks")
        .withDetails(balanceAsString + " ticks behind")
        .withVL(0)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      Vector setback = new Vector(0, 0, 0);
      Modules.mitigate().movement().emulationSetBack(player, setback, 1, 2, false);
    }
    statisticApply(user, CheckStatistics::increasePasses);
  }

  @BukkitEventSubscription
  public void receiveItemConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    cancelOnPacketOverflow(player, event);
  }

  @BukkitEventSubscription
  public void receiveBowShoot(EntityShootBowEvent event) {
    Entity entity = event.getEntity();
    if (entity instanceof Player) {
      cancelOnPacketOverflow((Player) entity, event);
    }
  }

  @BukkitEventSubscription
  public void receiveHealthUpdate(EntityRegainHealthEvent event) {
    Entity entity = event.getEntity();
    if (entity instanceof Player) {
      cancelOnPacketOverflow((Player) entity, event);
    }
  }

  @BukkitEventSubscription
  public void receiveAttackUpdate(EntityDamageByEntityEvent event) {
    Entity entity = event.getDamager();
    if (entity instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      Player player = (Player) entity;
      int attackCancelThreshold = trustFactorSetting("act", player);
      int attackCancelLength = trustFactorSetting("acl", player);
      cancelOnPacketOverflow(player, event, attackCancelThreshold, attackCancelLength);

      if ((System.currentTimeMillis() - metaOf(userOf(player)).lastTimerFlag) < 600) {
        event.setCancelled(true);
      }
    }
  }

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable) {
    cancelOnPacketOverflow(player, cancellable, DEFAULT_THRESHOLD, DEFAULT_DELAY);
  }

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable, long threshold, long delay) {
    User user = userOf(player);
    PlayerTimeMeta timerData = metaOf(user);
    long lastTimerFlag = timerData.lastTimerFlag;
    long msSinceFlag = System.currentTimeMillis() - lastTimerFlag;
    if (violationLevelOf(user) > threshold && msSinceFlag < delay) {
      cancellable.setCancelled(true);
      player.updateInventory();
    }
  }

  private double violationLevelOf(User user) {
    ViolationMetadata violationLevelData = user.meta().violationLevel();
    Map<String, Map<String, Double>> violationLevel = violationLevelData.violationLevel;
    String name = name().toLowerCase();
    if (!violationLevel.containsKey(name)) {
      return 0;
    }
    Map<String, Double> stringDoubleMap = violationLevel.get(name);
    return stringDoubleMap.get("thresholds");
  }

  public static class PlayerTimeMeta extends CheckCustomMetadata {
    public long time = -1;
    public long queuedLimit = System.nanoTime();
    public long limitToBeApplied = System.nanoTime();
    public boolean gameJoinReceived;
    public long lastTimerFlag;
    public boolean inTeleport = false;
  }
}
