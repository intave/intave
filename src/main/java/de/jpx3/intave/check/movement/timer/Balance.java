package de.jpx3.intave.check.movement.timer;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;

@Deprecated
public final class Balance extends MetaCheckPart<Timer, Balance.BalanceMeta> {
  private final CheckViolationLevelDecrementer decrementer;
  private final boolean highToleranceMode;

  public Balance(Timer parentCheck) {
    super(parentCheck, BalanceMeta.class);
    this.decrementer = parentCheck.decrementer();
    this.highToleranceMode = parentCheck().highToleranceMode();
  }

  @PacketSubscription(
    packetsOut = {
      RESPAWN
    }
  )
  public void respawnTolerance(PacketEvent event) {
    Player player = event.getPlayer();
    metaOf(player).lastRespawn = System.currentTimeMillis();
    metaOf(player).timerBalance -= TimeUnit.MILLISECONDS.toNanos(50);
  }

  @DispatchTarget
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    if (player == null) {
      return;
    }
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    BalanceMeta timerData = metaOf(user);

    long time = System.nanoTime();
    long delta = time - timerData.lastFlyingPacket;
    timerData.lastFlyingPacket = System.nanoTime();
    timerData.timerBalance += TimeUnit.MILLISECONDS.toNanos(50) - delta;
    int allowedLagInMilliseconds = trustFactorSetting("buffer-size", player);
    if (highToleranceMode || meta.abilities().probablyFlying()) {
      // disable any limits for high tolerance mode and flying
      allowedLagInMilliseconds = Integer.MAX_VALUE;
    }
    if (System.currentTimeMillis() - timerData.lastRespawn < 6000) {
      allowedLagInMilliseconds = Math.max(allowedLagInMilliseconds, 8000);
    }
    timerData.timerBalance = MathHelper.minmax(TimeUnit.MILLISECONDS.toNanos(-allowedLagInMilliseconds), timerData.timerBalance, TimeUnit.MILLISECONDS.toNanos(1000));
    if (timerData.nextConfirmedBalance != -1) {
      timerData.confirmedBalance = timerData.nextConfirmedBalance;
      timerData.nextConfirmedBalance = -1;
    }
    // transactions!
    statisticApply(user, CheckStatistics::increaseTotal);

    // 12 is theoretical limit, 25 works well
    long overflowLimit = TimeUnit.MILLISECONDS.toNanos(100);
    MovementMetadata movementData = user.meta().movement();

//    player.setLevel((int) TimeUnit.NANOSECONDS.toMicros(timerData.timerBalance) + 1000000);

    if (timerData.timerBalance > overflowLimit && !user.meta().movement().isInVehicle()) {
      if (System.currentTimeMillis() - timerData.lastTimerFlag > 60 * 1000) {
        timerData.lastTimerFlag = System.currentTimeMillis();
        timerData.timerBalance -= TimeUnit.MILLISECONDS.toNanos(50);
        return;
      }
      double displayValue = TimeUnit.NANOSECONDS.toMillis(timerData.timerBalance) / 50d;
      if (displayValue < 0.01) {
        displayValue = 0.01;
      }
      String balanceAsString = formatDouble(displayValue, 2);
      statisticApply(user, CheckStatistics::increaseFails);
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("moved too frequently").withDetails(balanceAsString + " ticks ahead")
        .withVL(System.currentTimeMillis() - timerData.lastTimerFlag < 1000 || violationLevelOf(user) > 16 ? 0.5 : 1)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        movementData.invalidMovement = true;
        Vector setback = new Vector(movementData.baseMotionX, movementData.baseMotionY, movementData.baseMotionZ);
        Modules.mitigate().movement().emulationSetBack(player, setback, 3, 2, false);
      }
      timerData.lastTimerFlag = System.currentTimeMillis();
      timerData.timerBalance -= TimeUnit.MILLISECONDS.toNanos(violationContext.shouldCounterThreat() ? 5 : 10);
    } else {
      statisticApply(user, CheckStatistics::increasePasses);
      if (timerData.timerBalance > 0) {
        // 1% timer = 50ms * 0.01 = 0.5ms
        // 0.5ms allowed per tick
        timerData.timerBalance -= 750_000L;
      }
      if (System.currentTimeMillis() - timerData.lastTimerFlag > 10000) {
        decrementer.decrement(user, 0.01);
      }
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

  private static final long DEFAULT_DELAY = 500;
  private static final long DEFAULT_THRESHOLD = 5;

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable) {
    cancelOnPacketOverflow(player, cancellable, DEFAULT_THRESHOLD, DEFAULT_DELAY);
  }

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable, long threshold, long delay) {
    User user = userOf(player);
    BalanceMeta timerData = metaOf(user);
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

  public static class BalanceMeta extends CheckCustomMetadata {
    public long timerBalance = Long.MIN_VALUE / 2; // give initial breathing room
    public long lastFlyingPacket;
    public long lastTimerFlag;
    public long lastRespawn;
    public long nextConfirmedBalance = -1;
    public long confirmedBalance = Integer.MAX_VALUE;
  }
}