package de.jpx3.intave.event;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.MathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class ConnectionHealthTelemetry implements PacketEventSubscriber {
  private final IntavePlugin plugin;
  private final static long TIMEOUT_DURATION = 1000 * 30;

  public ConnectionHealthTelemetry(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);

    this.plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
      for (Player player : Bukkit.getOnlinePlayers()) {
        User user = UserRepository.userOf(player);
        long dur = AccessHelper.now() - lastKeepAliveResponse(user);
        if (TIMEOUT_DURATION < dur) {
          IntaveLogger.logger().pushPrintln("[Intave] " + player.getName() + " was not responding to keep-alive packets for at least 30 seconds");
          user.synchronizedDisconnect("Timed out");
        }
      }
    }, 0, (TIMEOUT_DURATION / 50) / 2);
  }

  private long lastKeepAliveResponse(User user) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Long, Long> remainingPingPackets = synchronizeData.remainingPingPacketTimestamps();
    long last = AccessHelper.now();
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
    user.meta().connection().remainingPingPacketTimestamps().put(id, AccessHelper.now());
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

    Map<Long, Long> remainingPingPackets = synchronizeData.remainingPingPacketTimestamps();

    Long id;
    if (packet.getLongs().size() > 0) {
      id = packet.getLongs().read(0);
    } else {
      id = Long.valueOf(packet.getIntegers().read(0));
    }

    if (!remainingPingPackets.containsKey(id)) {
      IntaveLogger.logger().error(player.getName() + " sent KAI " + id + ", but expected one of " + remainingPingPackets);
      user.synchronizedDisconnect("Unknown keep-alive identifier");
      return;
    }

    List<Long> differenceBalance = synchronizeData.latencyDifferenceBalance();
    Long timeSent = remainingPingPackets.remove(id);
    long difference = MathHelper.minmax(0, AccessHelper.now() - timeSent, 1000);
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
