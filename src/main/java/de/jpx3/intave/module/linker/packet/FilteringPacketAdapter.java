package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FilteringPacketAdapter extends PacketListenerAbstract implements Comparable<FilteringPacketAdapter> {
  private final String methodName;
  private final ListenerPriority priority;
  private final SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber;
  private final PacketSubscriptionMethodExecutor executor;
  private final Set<PacketTypeCommon> packetTypes;
  private final Map<PacketTypeCommon, Timing> localTimings = new ConcurrentHashMap<>();
  private final boolean ignoreCancelled;

  public FilteringPacketAdapter(
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber,
    ListenerPriority priority,
    PacketTypeCommon[] packetTypes,
    String methodName,
    PacketSubscriptionMethodExecutor executor,
    boolean ignoreCancelled
  ) {
    super(priority.toPacketEventsPriority());
    this.subscriber = subscriber;
    this.methodName = methodName;
    this.priority = priority;
    this.executor = executor;
    this.packetTypes = new HashSet<>(Arrays.asList(packetTypes));
    this.ignoreCancelled = ignoreCancelled;
  }

  @Override
  public void onPacketReceive(PacketReceiveEvent event) {
    if (!listensFor(event) || !validateEvent(event)) {
      return;
    }
    Timing timing = localTimings.computeIfAbsent(event.getPacketType(), Timings::packetTimingOf);
    try {
      Timings.EXE_NETTY.start();
      timing.start();
      User user = UserRepository.userOf((Player) event.getPlayer());
      if (user.shouldIgnoreNextInboundPacket()) {
        user.receiveNextInboundPacketAgain();
        return;
      }
      try {
        subscriber.apply(user, usr -> executor.invoke(usr, event));
      } catch (Throwable throwable) {
        throwable.printStackTrace();
      }
    } catch (UnsupportedFallbackOperationException ignored) {
      // ignored
    } catch (RuntimeException exception) {
      processException(event.getPacketType(), exception);
    } catch (Error error) {
      processError(event.getPacketType(), error);
    } finally {
      timing.stop();
      Timings.EXE_NETTY.stop();
    }
  }

  @Override
  public void onPacketSend(PacketSendEvent event) {
    if (!listensFor(event) || !validateEvent(event)) {
      return;
    }
    try {
      User user = UserRepository.userOf((Player) event.getPlayer());
      if (user.shouldIgnoreNextOutboundPacket()) {
        user.receiveNextOutboundPacketAgain();
        return;
      }
      subscriber.apply(user, usr -> executor.invoke(usr, event));
    } catch (UnsupportedFallbackOperationException ignored) {
      // ignored
    } catch (RuntimeException exception) {
      processException(event.getPacketType(), exception);
    } catch (Error error) {
      processError(event.getPacketType(), error);
    }
  }

  private boolean listensFor(ProtocolPacketEvent event) {
    return packetTypes.contains(event.getPacketType());
  }

  private boolean validateEvent(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    return player != null
      && (ignoreCancelled || !event.isCancelled())
      && (UserRepository.hasUser(player) || event.getPacketType() == PacketType.Play.Server.JOIN_GAME);
  }

  public PacketEventSubscriber subscriber() {
    return subscriber.fallback();
  }

  public ListenerPriority priority() {
    return priority;
  }

  @Override
  public int compareTo(FilteringPacketAdapter other) {
    return Integer.compare(priority().slot(), other.priority().slot());
  }

  private void processException(PacketTypeCommon packetType, RuntimeException exception) {
    String simpleName = exception.getClass().getSimpleName();
    IntaveLogger.logger().printLine("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a " + packetType.getName() + " packet (" + subscriber.getClass().getSimpleName() + "." + methodName + ")");
    exception.printStackTrace();
  }

  private void processError(PacketTypeCommon packetType, Error error) {
    String simpleName = error.getClass().getSimpleName();
    IntaveLogger.logger().printLine("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a " + packetType.getName() + " packet (" + subscriber.getClass().getSimpleName() + "." + methodName + ")");
    error.printStackTrace();
  }

  private static final char[] vocals = "AEIOU".toCharArray();

  private String resolveIndefArticle(String exceptionName) {
    if (exceptionName.isEmpty()) {
      return "";
    }
    char c = exceptionName.toUpperCase(Locale.ROOT).toCharArray()[0];
    boolean isVocal = false;
    for (char vocal : vocals) {
      if (vocal == c) {
        isVocal = true;
        break;
      }
    }
    return isVocal ? "An" : "A";
  }
}
