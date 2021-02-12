package de.jpx3.intave.detect.checks.movement;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.CheckViolationLevelDecrementer;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class Timer extends IntaveMetaCheck<Timer.TimerData> {
  private final IntavePlugin plugin;
  private final CheckViolationLevelDecrementer decrementer;

  public Timer(IntavePlugin plugin) {
    super("Timer", "timer", TimerData.class);
    this.plugin = plugin;
    this.decrementer = new CheckViolationLevelDecrementer(this, 0.2);
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "POSITION")
    }
  )
  public void sentPosition(PacketEvent event) {
    User user = userOf(event.getPlayer());
    if(user.meta().clientData().flyingPacketStream()) {
      metaOf(user).timerBalance -= 10.0;
    }
  }

  public void receiveMovement(PacketEvent event, boolean teleportConf) {
    Player player = event.getPlayer();
    if(player == null) {
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
    int packetLimit = allowedLagInSeconds * -(20 * 10);

    timerData.timerBalance = MathHelper.minmax(packetLimit, timerData.timerBalance, 200);

    if(delta > 500) {
      timerData.lastLagSpike = AccessHelper.now();
      Synchronizer.synchronize(() -> {
        plugin.eventService().transactionFeedbackService().requestPong(player, null, (player1, target) -> {
          // Lag spike - requesting feedback to reset balance
          timerData.timerBalance = Math.max(0, timerData.timerBalance);
        });
      });
    }

    // fast recover
    if(timerData.timerBalance < -50 && AccessHelper.now() - timerData.lastLagSpike > 500) {
      int adder = timerData.timerBalance < -400 ? 9 : 3;
      timerData.timerBalance += adder;
    }

//    player.sendMessage(String.valueOf(timerData.timerBalance));

    if(timerData.timerBalance > 10) {
      String balanceAsString = MathHelper.formatDouble(timerData.timerBalance / 10, 2);
      if (plugin.violationProcessor().processViolation(player, 0.5, "Timer", "moved too frequently", balanceAsString + " packets ahead")) {
//        plugin.eventService().emulationEngine().emulationSetBack(player, new Vector(0,0,0), 6);
        UserMetaMovementData movementData = user.meta().movementData();
        plugin.eventService().emulationEngine().emulationSetBack(player, new Vector(movementData.physicsLastMotionX, movementData.physicsLastMotionY, movementData.physicsLastMotionZ), 6);
        if(timerData.timerBalance > 50) {
          event.setCancelled(true);
        }
        // packet removed
        timerData.timerBalance -= 5.0;
      }
      timerData.lastTimerFlag = AccessHelper.now();
      // leniency
      timerData.timerBalance -= 5.5;
    } else {
      if(timerData.timerBalance > 0) {
        timerData.timerBalance -= 0.025;
      }
      if(AccessHelper.now() - timerData.lastTimerFlag > 10000) {
        decrementer.decrement(user, 0.01);
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void checkSetback(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    TimerData timerData = metaOf(user);

    if(timerData.flagTick) {
      UserMetaMovementData movementData = user.meta().movementData();
      plugin.eventService().emulationEngine().emulationSetBack(player, new Vector(movementData.physicsLastMotionX,movementData.physicsLastMotionY, movementData.physicsLastMotionZ), 6);
      event.setCancelled(true);
    }
  }

  public static class TimerData extends UserCustomCheckMeta {
    public double timerBalance;
    public long lastFlyingPacket;
    public long lastTimerFlag;
    public long lastLagSpike;
    public boolean flagTick;
  }
}
