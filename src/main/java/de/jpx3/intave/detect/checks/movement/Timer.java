package de.jpx3.intave.detect.checks.movement;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.event.service.violation.Violation;
import de.jpx3.intave.event.service.violation.ViolationContext;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.util.Vector;

import java.util.Map;

public final class Timer extends IntaveMetaCheck<Timer.TimerData> {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;

  private final boolean highToleranceMode;

  public Timer(IntavePlugin plugin) {
    super("Timer", "timer", TimerData.class);
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, 0.2);
    highToleranceMode = configuration().settings().boolBy("high-tolerance", false);
    if (highToleranceMode) {
      IntaveLogger.logger().info("Enabled high ping tolerance");
    }
  }


  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "RESPAWN")
    }
  )
  public void respawnTolerance(PacketEvent event) {
    Player player = event.getPlayer();
    metaOf(player).lastRespawn = AccessHelper.now();
    metaOf(player).timerBalance -= 20.0;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "POSITION")
    }
  )
  public void sentPosition(PacketEvent event) {
    User user = userOf(event.getPlayer());
//    if(user.meta().clientData().flyingPacketStream()) {
//    }
    metaOf(user).timerBalance -= 12.5;
  }

  public void receiveMovement(PacketEvent event, boolean teleportConf) {
    Player player = event.getPlayer();
    if (player == null) {
      return;
    }

//    if(teleportConf) {
//      return;
//    }

    User user = userOf(player);
    TimerData timerData = metaOf(user);

    long time = AccessHelper.now();
    long delta = time - timerData.lastFlyingPacket;
    timerData.lastFlyingPacket = AccessHelper.now();
    timerData.timerBalance -= delta / 5.0;

//    if(!user.meta().clientData().flyingPacketStream() && event.getPacketType() == PacketType.Play.Client.POSITION) {
//      // account missing flying packets
//      timerData.timerBalance += 200;
//    } else {
    timerData.timerBalance += 10;
//    }

    int allowedLagInSeconds = trustFactorSetting("buffer-size", player);

    if(highToleranceMode) {
      allowedLagInSeconds *= 1.5;
    }

    if (AccessHelper.now() - timerData.lastRespawn < 6000) {
      allowedLagInSeconds = Math.max(allowedLagInSeconds, 8);
    }
    if(AccessHelper.now() - timerData.lastLagSpike < 12000 && !highToleranceMode) {
      allowedLagInSeconds = Math.max(allowedLagInSeconds / 2, 1);
    }

    int packetLimit = allowedLagInSeconds * -(20 * 10);

    timerData.timerBalance = MathHelper.minmax(packetLimit, timerData.timerBalance, 200);

    if (delta > 500) {
      timerData.lastLagSpike = AccessHelper.now();
      Synchronizer.synchronize(() -> {
        plugin.eventService().transactionFeedbackService().requestPong(player, null, (player1, target) -> {
          // Lag spike - requesting feedback to reset balance
          timerData.timerBalance = Math.max(0, timerData.timerBalance);
        });
      });
    }

    // fast recover
    if (timerData.timerBalance < -50 && AccessHelper.now() - timerData.lastLagSpike > 500) {
      int adder = timerData.timerBalance < -400 ? 9 : 3;
      timerData.timerBalance += adder;
    }

    statistics().increaseTotal();

    int overflowLimit = highToleranceMode ? 150 : 20;

    if (timerData.timerBalance > overflowLimit) {
      String balanceAsString = MathHelper.formatDouble(timerData.timerBalance / 10, 2);
      statistics().increaseFails();

      Violation violation = Violation.fromType(Timer.class)
        .withPlayer(player).withMessage("moved too frequently").withDetails(balanceAsString + " ticks ahead")
        .withDefaultThreshold().withVL(0.5)
        .build();
      ViolationContext violationContext = plugin.violationProcessor().processViolation(violation);

      if (violationContext.shouldCounterThreat()) {
        UserMetaMovementData movementData = user.meta().movementData();
        plugin.eventService().emulationEngine().emulationSetBack(player, new Vector(movementData.physicsMotionX, movementData.physicsMotionY, movementData.physicsMotionZ), 12);
        if (timerData.timerBalance > 50) {
          event.setCancelled(true);
        }
        // packet removed
//        timerData.timerBalance -= 5.0;
      }
      timerData.lastTimerFlag = AccessHelper.now();
      // leniency
      timerData.timerBalance -= highToleranceMode || timerData.timerBalance > overflowLimit ? 2.5 : 0.5;
    } else {
      statistics().increasePasses();
      if (timerData.timerBalance > 0) {
        timerData.timerBalance -= highToleranceMode ? 0.075 : 0.025;
      }
      if (AccessHelper.now() - timerData.lastTimerFlag > 10000) {
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

  private void cancelOnPacketOverflow(Player player, Cancellable cancellable) {
    User user = UserRepository.userOf(player);
    Timer.TimerData timerData = metaOf(user);
    long lastTimerFlag = timerData.lastTimerFlag;
    long msSinceFlag = AccessHelper.now() - lastTimerFlag;
    UserMetaViolationLevelData violationLevelData = user.meta().violationLevelData();
    Map<String, Map<String, Double>> violationLevel = violationLevelData.violationLevel;
    String name = name().toLowerCase();
    if (!violationLevel.containsKey(name)) {
      return;
    }
    Map<String, Double> stringDoubleMap = violationLevel.get(name);
    if (stringDoubleMap.get("thresholds") > 5 && msSinceFlag < 2000) {
      cancellable.setCancelled(true);
      player.updateInventory();
    }
  }

  public void checkSetback(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    TimerData timerData = metaOf(user);

    if (timerData.flagTick) {
      UserMetaMovementData movementData = user.meta().movementData();
      plugin.eventService().emulationEngine().emulationSetBack(player, new Vector(movementData.physicsMotionX, movementData.physicsMotionY, movementData.physicsMotionZ), 6);
      event.setCancelled(true);
    }
  }

  @Override
  public boolean enabled() {
    return true;
  }

  public static class TimerData extends UserCustomCheckMeta {
    public double timerBalance;
    public long lastFlyingPacket;
    public long lastTimerFlag;
    public long lastLagSpike;
    public long lastRespawn;
    public boolean flagTick;
  }
}
