package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.diagnostic.ConsoleOutput;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class ConnectionTracker extends Module {
  private static final long TIMEOUT_DURATION = 1000 * 30;
  private Task periodicTask;

  @Override
  public void enable() {
    periodicTask = Tasks.periodicNamed("ConnectionTracker.checkNettyDump", () -> UserRepository.applyOnAll(user -> {
      long dur = System.currentTimeMillis() - lastKeepAliveResponse(user);
      // Only a client that has gone quiet entirely counts as not responding. An answer
      // we could not match to an outgoing packet still leaves that packet pending
      // forever, which used to age past the timeout and kick a client that was in fact
      // replying the whole time.
      long sinceAnyResponse = System.currentTimeMillis() - user.meta().connection().lastKeepAliveResponseTime;
      if (dur > TIMEOUT_DURATION && sinceAnyResponse > TIMEOUT_DURATION && FaultKicks.IGNORING_KEEP_ALIVE) {
        IntaveLogger.logger().printLine("[Intave] " + user.player().getName() + " is not responding to keep-alive packets");
        user.kick("Not responding to keep-alive packets");
        if (IntaveControl.NETTY_DUMP_ON_TIMEOUT) {
          dumpNettyThreads();
        }
        // A kicked user is not removed from the repository straight away, so without
        // this the next cycle finds the same stale state and kicks (and dumps every
        // netty thread) again every 15 seconds.
        user.meta().connection().pingPackets().clear();
        user.meta().connection().lastKeepAliveResponseTime = System.currentTimeMillis();
      }
    }), 0, (TIMEOUT_DURATION / 50) / 2).startAsync();
  }

  @Override
  public void disable() {
    if (periodicTask != null) {
      periodicTask.cancel();
    }
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
        exception.printStackTrace(System.out);
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
    Long id = keepAliveIdOf(event.getPacket(), true);
    if (id == null) {
      return;
    }
    Map<Long, Long> pending = user.meta().connection().pingPackets();
    // Drop answers that can no longer be useful. Without this, one keep-alive we send
    // but never see answered stays in the map for the rest of the session: it is always
    // the oldest entry, so lastKeepAliveResponse() keeps reporting it and the liveness
    // check reads as "timed out" forever, and the map itself grows by four entries a
    // minute for as long as the player is online.
    if (pending.size() > 4) {
      long deadline = System.currentTimeMillis() - TIMEOUT_DURATION;
      pending.values().removeIf(sentAt -> sentAt < deadline);
    }
    pending.put(id, System.currentTimeMillis());
  }

  /**
   * Reads the identifier out of a keep-alive packet.
   * <p>
   * Both directions must agree on how this is read or nothing ever matches, so they
   * share one implementation instead of two copies that can drift apart. A read that
   * fails used to propagate out of the outgoing listener, which recorded nothing at all
   * -- and an empty record is precisely what makes every later answer look like an
   * unknown identifier. Report it once per direction rather than silently, since it
   * would otherwise present as random disconnects.
   */
  private Long keepAliveIdOf(PacketContainer packet, boolean outgoing) {
    try {
      if (packet.getLongs().size() > 0) {
        return packet.getLongs().read(0);
      }
      if (packet.getIntegers().size() > 0) {
        return (long) (int) packet.getIntegers().read(0);
      }
    } catch (Exception exception) {
      if (reportKeepAliveReadFailure(outgoing)) {
        IntaveLogger.logger().warn("Could not read the identifier of "
          + (outgoing ? "an outgoing" : "an incoming") + " keep-alive packet"
          + " (" + exception + ") - keep-alive matching is disabled for this session");
      }
      return null;
    }
    if (reportKeepAliveReadFailure(outgoing)) {
      IntaveLogger.logger().warn("A keep-alive packet carried no readable identifier"
        + " (" + (outgoing ? "outgoing" : "incoming") + ")"
        + " - keep-alive matching is disabled for this session");
    }
    return null;
  }

  private static boolean outgoingKeepAliveReadReported, incomingKeepAliveReadReported;

  private static synchronized boolean reportKeepAliveReadFailure(boolean outgoing) {
    if (outgoing) {
      if (outgoingKeepAliveReadReported) {
        return false;
      }
      return outgoingKeepAliveReadReported = true;
    }
    if (incomingKeepAliveReadReported) {
      return false;
    }
    return incomingKeepAliveReadReported = true;
  }

  // Keep-alives go out every 15 seconds, so reaching this many unmatched answers means
  // minutes of a client consistently answering ids the server never issued.
  private static final int KEEP_ALIVE_FAULT_LIMIT = 5;

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
    Long readId = keepAliveIdOf(packet, false);

    // Any answer at all proves the connection is alive, even one we cannot match or
    // cannot read.
    synchronizeData.lastKeepAliveResponseTime = System.currentTimeMillis();

    if (readId == null) {
      return;
    }
    long id = readId;

    if (id == 0) {
      event.setCancelled(true);
      return;
    }
    Long timeSent = remainingPingPackets.remove(id);
    if (timeSent == null) {
      event.setCancelled(true);
      // An unmatched answer does not prove the client made the id up -- it equally means
      // we never saw the server send it. That happens: outbound interception can miss a
      // packet (on region-threaded servers a send issued from the event loop itself
      // bypasses the queue the interception hooks), and a translating proxy layer can
      // rewrite keep-alives. Kicking on the first one turned any such miss into a
      // disconnect out of nowhere.
      //
      // Nothing is lost by being patient here: the id is unusable either way (the packet
      // is cancelled and no latency is derived from it), so a client cannot gain anything
      // by sending ids we never issued -- it can only look suspicious. So count it, and
      // only act once it is a pattern rather than an accident.
      //
      // An empty pending set is the clearest case of all: there is nothing outstanding to
      // have lied about, so it is evidence of a missed send and nothing else.
      if (remainingPingPackets.isEmpty()) {
        return;
      }
      if (!user.justJoined() && FaultKicks.IGNORING_KEEP_ALIVE
        && ++synchronizeData.keepAliveFaults > KEEP_ALIVE_FAULT_LIMIT) {
        if (ConsoleOutput.FAULT_KICKS) {
          IntaveLogger.logger().info(player.getName() + " sent keep-alive id " + id + ", but expected one of " + remainingPingPackets.keySet());
        }
        user.kick("Unknown keep-alive identifier");
      }
      return;
    }
    // A matched answer pays off an earlier unmatched one, so isolated misses can never
    // accumulate into a kick over a long session.
    if (synchronizeData.keepAliveFaults > 0) {
      synchronizeData.keepAliveFaults--;
    }
    List<Long> differenceBalance = synchronizeData.latencyDifferenceBalance();
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
      long sum = 0;
      long count = 0;
      for (Long value : differenceBalance) {
        long l = value;
        sum += l;
        count++;
      }
      user.meta().connection().latencyJitter =
        (int) (count > 0 ? (double) sum / count : 0d);
    }
    plugin.accessService()
      .playerAccessor()
      .netStatisticsAccessor()
      .pushPingJitterUpdate(player, synchronizeData.latency, (int) pingChange);
    synchronizeData.lastKeepAliveDifference = difference;
  }
}
