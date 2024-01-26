package de.jpx3.intave.check.movement.timer;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackObserver;
import de.jpx3.intave.module.feedback.FeedbackOptions;
import de.jpx3.intave.module.feedback.FeedbackRequest;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.LOGIN;

public class PlayerTime extends MetaCheckPart<Timer, PlayerTime.PlayerTimeMeta> {
  private static final long DEFAULT_DELAY = 500;
  private static final long DEFAULT_THRESHOLD = 10;
  private final Map<UUID, Long> playerJoinTimeCache = new HashMap<>();
  private final CheckViolationLevelDecrementer decrementer;

  public PlayerTime(Timer parentCheck) {
    super(parentCheck, PlayerTimeMeta.class);
    decrementer = parentCheck.decrementer();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsOut = {
      LOGIN
    }
  )
  public void receiveLogin(PacketEvent event) {
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

  @DispatchTarget
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    if (player == null) {
      return;
    }
    User user = userOf(player);
    PlayerTimeMeta checkMeta = metaOf(user);
    // Time was not loaded yet
    if (checkMeta.time == -1) {
      // The default fallback time should never be used but let's provide it if something goes horribly wrong
      checkMeta.time = playerJoinTimeCache.getOrDefault(player.getUniqueId(), System.nanoTime());
    }
    MetadataBundle bundle = user.meta();
    ConnectionMetadata connectionData = bundle.connection();
    MovementMetadata movementData = bundle.movement();
    AbilityMetadata abilityData = user.meta().abilities();
    user.tracedTickFeedback(() -> {
      connectionData.queueToNextTransaction(() -> {
        checkMeta.time = Math.max(checkMeta.time, checkMeta.limitToBeApplied);
        checkMeta.queuedLimit = checkMeta.lastSentTransaction;
      });
    }, new FeedbackObserver() {
      @Override
      public void sent(FeedbackRequest<?> request) {
        checkMeta.lastSentTransaction = System.nanoTime();
      }

      @Override
      public void received(FeedbackRequest<?> request) {
        // ignore
      }

      @Override
      public void failed() {
        // ignore
      }
    }, FeedbackOptions.SELF_SYNCHRONIZATION);
    // Exclude players in certain states such as creative, spectator or teleport
    // We also have to check if the player received the initial join packet due to proxies doing weird things
    if (!checkMeta.gameJoinReceived || movementData.lastTeleport == 0
      || abilityData.inGameModeIncludePending(AbilityTracker.GameMode.CREATIVE) || abilityData.ignoringMovementPackets()) {
      return;
    }
    checkMeta.time += 49_950_000;
    checkMeta.limitToBeApplied = checkMeta.queuedLimit;
    long diff = checkMeta.time - System.nanoTime();
//    ChatColor color = diff > 10_000_000 ? ChatColor.RED : ChatColor.GRAY;
//    player.sendMessage(color + "" + ((double)diff / 1_000_000L) + "ms ");
    // We could most likely flag for > 1_000_000 but let's be safe
    if (diff > (10 * 1_000_000) && !user.meta().movement().isInVehicle()) {
      double displayValue = diff / (50 * 1_000_000f);
      if (displayValue < 0.01) {
        displayValue = 0.01;
      }
      String balanceAsString = formatDouble(displayValue, 2);
      statisticApply(user, CheckStatistics::increaseFails);
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("moved too frequently").withDetails(balanceAsString + " ticks ahead")
        .withVL(Math.min(displayValue, 2))
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        checkMeta.lastTimerFlag = System.currentTimeMillis();
        movementData.invalidMovement = true;
        Vector setback = new Vector(0, 0, 0);
        Modules.mitigate().movement().emulationSetBack(player, setback, 3, 2, false);
      }
      checkMeta.time -= 1_000_000;
    } else if (System.currentTimeMillis() - checkMeta.lastTimerFlag > 10000) {
      decrementer.decrement(user, 0.005);
    }
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
    public long lastSentTransaction = System.nanoTime();
    public long lastTimerFlag;
  }
}
