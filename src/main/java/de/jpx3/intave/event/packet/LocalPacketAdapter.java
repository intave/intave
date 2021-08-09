package de.jpx3.intave.event.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.UnsupportedFallbackOperationException;
import de.jpx3.intave.diagnostics.timings.Timing;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.user.UserRepository;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalPacketAdapter extends IntavePacketAdapter implements Comparable<LocalPacketAdapter> {
  private final String methodName;
  private final ListenerPriority priority;
  private final PacketEventSubscriber subscriber;
  private final PacketSubscriptionMethodExecutor executor;
  private final Map<PacketType, Timing> localTimings = new ConcurrentHashMap<>();
  private final boolean ignoreCancelled;

  public LocalPacketAdapter(
    IntavePlugin plugin,
    PacketEventSubscriber subscriber,
    ListenerPriority priority, PacketType[] packetTypes,
    String methodName, PacketSubscriptionMethodExecutor executor,
    boolean ignoreCancelled) {
    super(plugin, priority.toProtocolLibPriority(), packetTypes);
    this.subscriber = subscriber;
    this.methodName = methodName;
    this.priority = priority;
    this.executor = executor;
    this.ignoreCancelled = ignoreCancelled;
  }

  @Override
  public void onPacketReceiving(PacketEvent event) {
    if (!validateEvent(event)) {
      return;
    }
    Timing timing = localTimings.computeIfAbsent(event.getPacketType(), Timings::packetTimingOf);
    try {
      Timings.EXE_NETTY.start();
      timing.start();
      executor.invoke(subscriber, event);
    } catch (IntaveInternalException internalException) {
      if (!internalException.getMessage().contains("Unable to reference player")) {
        processException(event.getPacketType(), internalException);
      }
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
  public void onPacketSending(PacketEvent event) {
    if (!validateEvent(event)) {
      return;
    }
    try {
      executor.invoke(subscriber, event);
    } catch (UnsupportedFallbackOperationException ignored) {
      // ignored
    } catch (RuntimeException exception) {
      exception.getStackTrace();
      processException(event.getPacketType(), exception);
    } catch (Error error) {
      processError(event.getPacketType(), error);
    }
  }

  private boolean validateEvent(PacketEvent event) {
    return event.getPlayer() != null && (ignoreCancelled || !event.isCancelled()) && UserRepository.hasUser(event.getPlayer());
  }

  public PacketEventSubscriber subscriber() {
    return subscriber;
  }

  public ListenerPriority priority() {
    return priority;
  }

  @Override
  public int compareTo(LocalPacketAdapter other) {
    return Integer.compare(priority().slot(), other.priority().slot());
  }

  private void processException(PacketType packetType, RuntimeException exception) {
    String simpleName = exception.getClass().getSimpleName();
    IntaveLogger.logger().pushPrintln("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a "+packetType.name()+" packet ("+subscriber.getClass().getSimpleName()+"."+methodName+")");
    exception.printStackTrace();
  }

  private void processError(PacketType packetType, Error error) {
    String simpleName = error.getClass().getSimpleName();
    IntaveLogger.logger().pushPrintln("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a "+packetType.name()+" packet ("+subscriber.getClass().getSimpleName()+"."+methodName+")");
    error.printStackTrace();
  }

  private final static char[] vocals = "AEIOU".toCharArray();

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
