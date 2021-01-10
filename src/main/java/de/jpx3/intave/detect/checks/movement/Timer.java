package de.jpx3.intave.detect.checks.movement;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveMetaCheck;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserCustomCheckMeta;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class Timer extends IntaveMetaCheck<Timer.TimerData> {
  private final IntavePlugin plugin;

  public Timer(IntavePlugin plugin) {
    super("Timer", "timer", TimerData.class);
    this.plugin = plugin;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "POSITION")
    }
  )
  public void sentPosition(PacketEvent event) {
    metaOf(event.getPlayer()).timerBalance -= 10.5;
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void checkPacketFrequency(PacketEvent event) {
    Player player = event.getPlayer();
    if(player == null) {
      return;
    }

    User user = userOf(player);
    TimerData timerData = metaOf(user);

    long time = AccessHelper.now();
    long delta = time - timerData.lastFlyingPacket;
    timerData.lastFlyingPacket = AccessHelper.now();
    timerData.timerBalance -= delta / 5.0;

    if(!user.meta().clientData().flyingPacketStream() && event.getPacketType() == PacketType.Play.Client.FLYING) {
      // account missing flying packets
      timerData.timerBalance += 200;
    } else {
      timerData.timerBalance += 10;
    }

    int allowedLagInSeconds = 8;
    int packetLimit = allowedLagInSeconds * -(20 * 10);

    timerData.timerBalance = MathHelper.minmax(packetLimit, timerData.timerBalance, 200);

    if(delta > 500) {
      timerData.lastLagSpike = AccessHelper.now();
      player.sendMessage(String.valueOf(timerData.timerBalance));
    }

    // fast recover
    if(timerData.timerBalance < -50 && AccessHelper.now() - timerData.lastLagSpike > 500) {
      int adder = timerData.timerBalance < -400 ? 9 : 3;
      timerData.timerBalance += adder;
    }

    if(timerData.timerBalance > 10) {
      String balanceAsString = MathHelper.formatDouble(timerData.timerBalance / 10, 2);
      if (plugin.retributionService().markPlayer(player, 2, "Timer", "sent moves too frequently (" + balanceAsString + " packets gained)")) {
//        plugin.eventService().emulationEngine().emulationSetBack(player, new Vector(0,0,0), 6);
        UserMetaMovementData movementData = user.meta().movementData();
        plugin.eventService().emulationEngine().emulationSetBack(player, new Vector(movementData.physicsLastMotionX, movementData.physicsLastMotionY, movementData.physicsLastMotionZ), 6);
        if(timerData.timerBalance > 50) {
          event.setCancelled(true);
        }
//        timerData.flagTick = true;
      }
      timerData.timerBalance -= 12.5;
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
    public long lastLagSpike;
    public boolean flagTick;
  }
}
