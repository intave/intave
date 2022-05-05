package de.jpx3.intave.check.movement.timer;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.check.CheckStatistics;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.executor.Synchronizer;
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

import static de.jpx3.intave.module.linker.packet.PacketId.Server.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;

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
    metaOf(player).timerBalance -= 20.0;
  }

  @PacketSubscription(
    packetsOut = {
      POSITION
    }
  )
  public void sentPosition(PacketEvent event) {
    User user = userOf(event.getPlayer());
    double leniency = user.meta().violationLevel().isInActiveTeleportBundle ? 2 : 12.5;
    BalanceMeta timerData = metaOf(user);
    timerData.timerBalance -= leniency;
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
    timerData.timerBalance -= delta / 5.0;
    timerData.timerBalance += 10;
    int allowedLagInMilliseconds = trustFactorSetting("buffer-size", player);
    if (highToleranceMode || meta.abilities().probablyFlying()) {
      allowedLagInMilliseconds *= 1.5;
    }
    if (System.currentTimeMillis() - timerData.lastRespawn < 6000) {
      allowedLagInMilliseconds = Math.max(allowedLagInMilliseconds, 8000);
    }
    if (System.currentTimeMillis() - timerData.lastLagSpike < 12000 && !highToleranceMode) {
      allowedLagInMilliseconds = Math.max(allowedLagInMilliseconds / 2, 500);
    }
    double lowerPacketBalanceLimit = (allowedLagInMilliseconds / 1000d) * -(20 * 10);
    timerData.timerBalance = MathHelper.minmax(lowerPacketBalanceLimit, timerData.timerBalance, 200);
    if (delta > 500) {
      timerData.lastLagSpike = System.currentTimeMillis();
      Synchronizer.synchronize(() -> {
        Modules.feedback().synchronize(player, (player1, target) -> {
          // Lag spike - requesting feedback to reset balance
          timerData.timerBalance = Math.max(0, timerData.timerBalance);
        });
      });
    }
    if (timerData.timerBalance < -50 && System.currentTimeMillis() - timerData.lastLagSpike > 500) {
      int adder = timerData.timerBalance < -400 ? 9 : 3;
      timerData.timerBalance += adder;
    }
    statisticApply(user, CheckStatistics::increaseTotal);
    int overflowLimit = highToleranceMode ? 150 : 20;
    if (timerData.timerBalance > overflowLimit && !user.meta().movement().isInVehicle()) {
      String balanceAsString = MathHelper.formatDouble(timerData.timerBalance / 10, 2);
      statisticApply(user, CheckStatistics::increaseFails);
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("moved too frequently").withDetails(balanceAsString + " ticks ahead").withVL(0.5)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        MovementMetadata movementData = user.meta().movement();
        movementData.invalidMovement = true;
        Vector setback = new Vector(movementData.physicsMotionX, movementData.physicsMotionY, movementData.physicsMotionZ);
        Modules.mitigate().movement().emulationSetBack(player, setback, 12, false);
      }
      timerData.lastTimerFlag = System.currentTimeMillis();
      timerData.timerBalance -= highToleranceMode || timerData.timerBalance > overflowLimit ? 2.5 : 0.5;
    } else {
      statisticApply(user, CheckStatistics::increasePasses);
      if (timerData.timerBalance > 0) {
        timerData.timerBalance -= highToleranceMode ? 0.075 : 0.025;
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
    Entity entity = event.getEntity();
    if (entity instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      Player player = (Player) entity;
      int attackCancelThreshold = trustFactorSetting("act", player);
      int attackCancelLength = trustFactorSetting("acl", player);
      cancelOnPacketOverflow(player, event, attackCancelThreshold, attackCancelLength);
    }
  }

  private final static long DEFAULT_DELAY = 5;
  private final static long DEFAULT_THRESHOLD = 2000;

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable) {
    cancelOnPacketOverflow(player, cancellable, DEFAULT_THRESHOLD, DEFAULT_DELAY);
  }

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable, long threshold, long delay) {
    User user = userOf(player);
    BalanceMeta timerData = metaOf(user);
    long lastTimerFlag = timerData.lastTimerFlag;
    long msSinceFlag = System.currentTimeMillis() - lastTimerFlag;
    ViolationMetadata violationLevelData = user.meta().violationLevel();
    Map<String, Map<String, Double>> violationLevel = violationLevelData.violationLevel;
    String name = name().toLowerCase();
    if (!violationLevel.containsKey(name)) {
      return;
    }
    Map<String, Double> stringDoubleMap = violationLevel.get(name);
    if (stringDoubleMap.get("thresholds") > threshold && msSinceFlag < delay) {
      cancellable.setCancelled(true);
      player.updateInventory();
    }
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
    public long lastFlyingPacket;
    public long lastTimerFlag;
    public long lastLagSpike;
    public long lastRespawn;
    public boolean receivedMovingPacket;
    public boolean flagTick;
  }
}