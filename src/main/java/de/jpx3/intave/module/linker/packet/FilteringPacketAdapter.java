package de.jpx3.intave.module.linker.packet;

import com.comphenix.protocol.PacketType;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostic.timings.Timing;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FilteringPacketAdapter extends WeakReferencePacketAdapter implements Comparable<FilteringPacketAdapter> {
  private static final boolean TEMP_PLAYER_CHECK;

  static {
    TEMP_PLAYER_CHECK = Arrays.stream(ProtocolPacketEvent.class.getMethods())
      .anyMatch(method -> method.getName().equalsIgnoreCase("isPlayerTemporary"));
  }

  private final String methodName;
  private final ListenerPriority priority;
  private final SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber;
  private final PacketSubscriptionMethodExecutor executor;
  private final Map<PacketType, Timing> localTimings = new ConcurrentHashMap<>();
  private final boolean ignoreCancelled;

  public FilteringPacketAdapter(
    IntavePlugin plugin,
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber,
    ListenerPriority priority, PacketType[] packetTypes,
    String methodName, PacketSubscriptionMethodExecutor executor,
    boolean ignoreCancelled
  ) {
    super(plugin, priority.toProtocolLibPriority(), packetTypes, ALLOW_ASYNC_SENDING);
    this.subscriber = subscriber;
    this.methodName = methodName;
    this.priority = priority;
    this.executor = executor;
    this.ignoreCancelled = ignoreCancelled;
  }

  @Override
  public void onPacketReceiving(ProtocolPacketEvent event) {
    if (!validateEvent(event)) {
      return;
    }
    Timing timing = localTimings.computeIfAbsent(event.getPacketType(), Timings::packetTimingOf);
    try {
      Timings.EXE_NETTY.start();
      timing.start();
      User user = UserRepository.userOf(event.getPlayer());
      if (user.shouldIgnoreNextInboundPacket()) {
        return;
      }
      try {
        subscriber.apply(user, usr -> executor.invoke(usr, event));
      } catch (Throwable t) {
        t.printStackTrace();
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
  public void onPacketSending(ProtocolPacketEvent event) {
    if (!validateEvent(event)) {
      return;
    }
    try {
      User user = UserRepository.userOf(event.getPlayer());
      if (user.shouldIgnoreNextOutboundPacket()) {
        return;
      }
      subscriber.apply(user, usr -> executor.invoke(usr, event));
    } catch (UnsupportedFallbackOperationException ignored) {
      // ignored
    } catch (RuntimeException exception) {
      exception.getStackTrace();
      processException(event.getPacketType(), exception);
    } catch (Error error) {
      processError(event.getPacketType(), error);
    }
  }

  private boolean validateEvent(ProtocolPacketEvent event) {
    if (TEMP_PLAYER_CHECK) {
      // perform temporary check
      if (event.isPlayerTemporary()) {
        return false;
      }
    }
    return event.getPlayer() != null && (ignoreCancelled || !event.isCancelled())
        && (UserRepository.hasUser(event.getPlayer()) || event.getPacketType() == PacketType.Play.Server.LOGIN);
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

  private void processException(PacketType packetType, RuntimeException exception) {
    String simpleName = exception.getClass().getSimpleName();
    IntaveLogger.logger().printLine("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a " + packetType.name() + " packet (" + subscriber.getClass().getSimpleName() + "." + methodName + ")");
    exception.printStackTrace();
  }

  private void processError(PacketType packetType, Error error) {
    String simpleName = error.getClass().getSimpleName();
    IntaveLogger.logger().printLine("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a " + packetType.name() + " packet (" + subscriber.getClass().getSimpleName() + "." + methodName + ")");
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
