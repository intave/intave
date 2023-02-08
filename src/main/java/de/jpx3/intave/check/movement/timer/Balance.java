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
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.access.player.trust.TrustFactor.ORANGE;
import static de.jpx3.intave.access.player.trust.TrustFactor.RED;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;

public final class Balance extends MetaCheckPart<Timer, Balance.BalanceMeta> {
  private final CheckViolationLevelDecrementer decrementer;
  private final boolean highToleranceMode;
  private final boolean antiStutter;

  public Balance(Timer parentCheck) {
    super(parentCheck, BalanceMeta.class);
    this.decrementer = parentCheck.decrementer();
    this.highToleranceMode = parentCheck().highToleranceMode();
    this.antiStutter = parentCheck().stutterPatch();
  }

  @PacketSubscription(
    packetsOut = {
      RESPAWN
    }
  )
  public void respawnTolerance(PacketEvent event) {
    Player player = event.getPlayer();
    metaOf(player).lastRespawn = System.currentTimeMillis();
    metaOf(player).timerBalance -= 50.0;
  }

  @PacketSubscription(
    packetsOut = {
      POSITION
    }
  )
  public void sentPosition(PacketEvent event) {
    User user = userOf(event.getPlayer());
//    double leniency = user.meta().violationLevel().isInActiveTeleportBundle ? 10 : 60;
    BalanceMeta timerData = metaOf(user);
    timerData.timerBalance -= 50;
    timerData.lastFlyingPacket = System.currentTimeMillis();
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
    long time = System.currentTimeMillis();
    long delta = time - timerData.lastFlyingPacket;
    timerData.lastFlyingPacket = System.currentTimeMillis();
    timerData.timerBalance += 50 - delta;
    int allowedLagInMilliseconds = trustFactorSetting("buffer-size", player);
    if (highToleranceMode || meta.abilities().probablyFlying()) {
      // disable any limits for high tolerance mode and flying
      allowedLagInMilliseconds = Integer.MAX_VALUE;
    }
    if (System.currentTimeMillis() - timerData.lastRespawn < 6000) {
      allowedLagInMilliseconds = Math.max(allowedLagInMilliseconds, 8000);
    }
//    if (System.currentTimeMillis() - timerData.lastLagSpike < 1000 && !highToleranceMode) {
//      allowedLagInMilliseconds = Math.max(allowedLagInMilliseconds / 2, 500);
//    }
    timerData.timerBalance = MathHelper.minmax(-allowedLagInMilliseconds, timerData.timerBalance, 1000);
    if (timerData.nextConfirmedBalance != -1) {
      timerData.confirmedBalance = timerData.nextConfirmedBalance;
      timerData.nextConfirmedBalance = -1;
    }
    // transactions!
//    if (timerData.timerBalance < -250 && System.currentTimeMillis() - timerData.lastLagSpike > 500) {
//      timerData.timerBalance += timerData.timerBalance < -400 ? 45 : 15;
//    }
    statisticApply(user, CheckStatistics::increaseTotal);
    boolean lowToleranceMode = parentCheck().lowToleranceMode() &&/*violationLevelOf(user) > 10 && */user.trustFactor().atOrBelow(ORANGE) /*&& System.currentTimeMillis() - timerData.lastTimerFlag < 2000*/;
    int overflowLimit = lowToleranceMode ? 40 : 120;

//    List<Double> safeTimerBalanceHistory = timerData.safeTimerBalanceHistory;
//    List<Double> timerBalanceHistory = timerData.timerBalanceHistory;

    MovementMetadata movementData = user.meta().movement();
//    boolean flyingPackets = user.meta().protocol().flyingPacketStream();
//    boolean moving = Hypot.fast(movementData.motionX(), movementData.motionZ()) + Math.abs(movementData.motionY()) >= 0.1 && movementData.pastFlyingPacketAccurate() > 8;
//    boolean checkAllowed = moving || flyingPackets;
//    if (checkAllowed) {
//      safeTimerBalanceHistory.add(Math.min(timerData.timerBalance, timerData.confirmedBalance));
//      timerBalanceHistory.add(timerData.timerBalance);
//    }
//    if (safeTimerBalanceHistory.size() > 20) {
//      safeTimerBalanceHistory.remove(0);
//    }
//    if (timerBalanceHistory.size() > 40) {
//      timerBalanceHistory.remove(0);
//    }
//    int safeMean = mean(safeTimerBalanceHistory);
//    int mean = mean(timerBalanceHistory);
//    double absoluteBalance = Math.abs(timerData.timerBalance);
//    double safeAbsoluteMean = Math.abs(safeMean);
//    double absoluteMean = Math.abs(mean);
//    double safeDiff = safeAbsoluteMean - absoluteBalance;
//    double diff = absoluteMean - absoluteBalance;
//    boolean safeVl = checkAllowed && safeDiff < -50;
//    double vl = checkAllowed ? ((diff < -20) ? ((diff < -50) ? 5 : 3) : -0.5) : -0.5;
//    boolean combatMicroLag = parentCheck().lowToleranceMode();
//    if (safeVl || vl < 0) {
//      timerData.balanceUnderflowVL += vl;
//    }
//    timerData.balanceUnderflowVL = MathHelper.minmax(-50, timerData.balanceUnderflowVL, 30);
//    boolean hasRedTrustfactor = !user.trustFactor().atLeast(TrustFactor.ORANGE);
//    if (timerData.balanceUnderflowVL > 15 && combatMicroLag && IntaveControl.GOMME_MODE && hasRedTrustfactor) {
//      connection.lastAttackQueueRequest = System.currentTimeMillis();
//    }
    boolean hasSuspiciousBalance = timerData.timerBalance < -150 && Math.abs(delta) > 100 && timerData.timerBalance + delta > -150;
    if (antiStutter && user.latency() < 150 && hasSuspiciousBalance && !user.justJoined() && user.meta().protocol().flyingPacketsAreSent() && user.trustFactor().atOrBelow(RED)) {
      user.nerfOnce(AttackNerfStrategy.DMG_HIGH, "76");
//      user.player().sendMessage(ChatColor.RED + "Stutter detected");
//      player.sendMessage(timerData.timerBalance + "/" + overflowLimit + " @" + user.latency() + "ms");
    }

//    player.sendMessage("vl: " + timerData.balanceUnderflowVL);
//    player.sendMessage("§c" + timerData.timerBalance + "§7 ~§c" + mean + "§7 -> §c" + formatDouble(timerData.balanceUnderflowVL, 2));
//    player.setLevel((int) timerData.timerBalance);
    if (timerData.timerBalance > overflowLimit && !user.meta().movement().isInVehicle()) {
      String balanceAsString = formatDouble(timerData.timerBalance / 50, 2);
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
      timerData.timerBalance -= !violationContext.shouldCounterThreat() ? 25 : 10;
    } else {
      statisticApply(user, CheckStatistics::increasePasses);
      if (timerData.timerBalance > 0) {
        timerData.timerBalance -= 2;
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

//      User user = userOf(player);
//      ConnectionMetadata connection = user.meta().connection();
//      if (System.currentTimeMillis() - connection.lastAttackQueueRequest < 250) {
//        event.setCancelled(true);
//      }
    }
  }

  private static final long DEFAULT_DELAY = 250;
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

//  @Deprecated
//  public void checkSetback(PacketEvent event) {
//    Player player = event.getPlayer();
//    User user = userOf(player);
//    MovementMetadata movementData = user.meta().movement();
//    TimerData timerData = metaOf(user);
//    if (timerData.flagTick) {
//      ComplexColliderSimulationResult result = this.simulationProcessor.simulateMovementWithoutKeyPress(user);
//      Vector bukkitVector = result.context().toBukkitVector();
//      Modules.mitigate().movementEmulator().emulationSetBack(player, bukkitVector, 6, false);
//      movementData.invalidMovement = true;
//    }
//  }

  public static class BalanceMeta extends CheckCustomMetadata {
    public double timerBalance;
    public List<Double> safeTimerBalanceHistory = new LinkedList<>();
    public List<Double> timerBalanceHistory = new LinkedList<>();
    public long lastFlyingPacket;
    public long lastTimerFlag;
    public long lastLagSpike;
    public long lastRespawn;
    public long nextConfirmedBalance;
    public long confirmedBalance;
    public double balanceUnderflowVL;
    public boolean currentUnderflow;
  }
}