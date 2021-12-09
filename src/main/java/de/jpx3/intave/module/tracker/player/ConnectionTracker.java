package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class ConnectionTracker extends Module {
  private final static long TIMEOUT_DURATION = 1000 * 30;
  @Override
  public void enable() {
    int taskId = this.plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
      for (Player player : Bukkit.getOnlinePlayers()) {
        User user = UserRepository.userOf(player);
        long dur = System.currentTimeMillis() - lastKeepAliveResponse(user);
        if (TIMEOUT_DURATION < dur) {
          IntaveLogger.logger().printLine("[Intave] " + player.getName() + " was not responding to keep-alive packets for at least 30 seconds");
          user.synchronizedDisconnect("Timed out");
          if (IntaveControl.NETTY_DUMP_ON_TIMEOUT) {
            dumpNettyThreads();
          }
        }
      }
    }, 0, (TIMEOUT_DURATION / 50) / 2);
    TaskTracker.begun(taskId);
  }

  private void dumpNettyThreads() {
    Thread.getAllStackTraces().forEach((thread, stackTraceElements) -> {
      if (thread.getName().contains("Netty")) {
        boolean containsIntave = false;
        for (StackTraceElement stackTraceElement : stackTraceElements) {
          if (stackTraceElement.getClassName().contains("Intave")) {
            containsIntave = true;
            break;
          }
        }
//        if (containsIntave) {
          System.out.println("Thread:" + thread.getName());
          Exception exception = new Exception();
          exception.setStackTrace(stackTraceElements);
          exception.printStackTrace();
//        }
      }
    });
  }

  private long lastKeepAliveResponse(User user) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Long, Long> remainingPingPackets = synchronizeData.pingPackets();
    long last = System.currentTimeMillis();
    for (Long value : remainingPingPackets.values()) {
      last = Math.min(value, last);
    }
    return last;
  }

  @PacketSubscription(
    packetsOut = {
      PacketId.Server.KEEP_ALIVE
    }
  )
  public void processOutgoingPingPackets(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    long id;
    if (packet.getLongs().size() > 0) {
      id = packet.getLongs().read(0);
    } else {
      id = packet.getIntegers().read(0);
    }
    user.meta().connection().pingPackets().put(id, System.currentTimeMillis());
  }

  @PacketSubscription(
    packetsIn = {
      PacketId.Client.KEEP_ALIVE
    }
  )
  public void processIncomingPingPackets(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Long, Long> remainingPingPackets = synchronizeData.pingPackets();
    long id;
    if (packet.getLongs().size() > 0) {
      id = packet.getLongs().read(0);
    } else {
      id = Long.valueOf(packet.getIntegers().read(0));
    }
    if (id == 0) {
      event.setCancelled(true);
      return;
    }
    if (!remainingPingPackets.containsKey(id)) {
      IntaveLogger.logger().error(player.getName() + " sent keep-alive id " + id + ", but expected one of " + remainingPingPackets.keySet());
      user.synchronizedDisconnect("Unknown keep-alive identifier");
      return;
    }
    List<Long> differenceBalance = synchronizeData.latencyDifferenceBalance();
    Long timeSent = remainingPingPackets.remove(id);
    long difference = MathHelper.minmax(0, System.currentTimeMillis() - timeSent, 1000);
    synchronizeData.latency = (int) (((synchronizeData.latency * 3d) + difference) / 4d);
    long pingChange = Math.abs(difference - synchronizeData.lastKeepAliveDifference);
    int size = 8;
    boolean enoughPingDataAvailable = differenceBalance.size() >= size;
    if (enoughPingDataAvailable) {
      differenceBalance.remove(0);
    }
    differenceBalance.add(pingChange);
    if (enoughPingDataAvailable) {
      user.meta().connection().latencyJitter =
        (int) differenceBalance.stream().mapToLong(value -> value).average().orElse(0d);
    }
    plugin.accessService()
      .playerAccessor()
      .netStatisticsAccessor()
      .pushPingJitterUpdate(player, synchronizeData.latency, (int) pingChange);
    synchronizeData.lastKeepAliveDifference = difference;
  }
}
