package de.jpx3.intave.event.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.diagnostics.timings.Timing;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.user.UserRepository;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LocalPacketAdapter extends IntavePacketAdapter implements Comparable<LocalPacketAdapter> {
  private final String methodName;
  private final ListenerPriority priority;
  private final PacketEventSubscriber subscriber;
  private final PacketSubscriptionMethodExecutor executor;
  private final Map<PacketType, Timing> localTimings = new HashMap<>();

  public LocalPacketAdapter(
    IntavePlugin plugin,
    PacketEventSubscriber subscriber,
    ListenerPriority priority, PacketType[] packetTypes,
    String methodName, PacketSubscriptionMethodExecutor executor
  ) {
    super(plugin, priority.toProtocolLibPriority(), packetTypes);
    this.subscriber = subscriber;
    this.methodName = methodName;
    this.priority = priority;
    this.executor = executor;
  }

  @Override
  public void onPacketReceiving(PacketEvent event) {
    if(!validateEvent(event)) {
      return;
    }

    Timing timing = localTimings.computeIfAbsent(event.getPacketType(), Timings::packetTimingOf);

    try {
      Timings.EXE_NETTY.start();
      timing.start();
      executor.invoke(subscriber, event);
    } catch (RuntimeException exception) {
      processException(event.getPacketType(), exception);
    } finally {
      timing.stop();
      Timings.EXE_NETTY.stop();
    }
  }

  @Override
  public void onPacketSending(PacketEvent event) {
    if(!validateEvent(event)) {
      return;
    }
    try {
      executor.invoke(subscriber, event);
    } catch (RuntimeException exception) {
      exception.getStackTrace();
      processException(event.getPacketType(), exception);
    }
  }

  private boolean validateEvent(PacketEvent event) {
    return event.getPlayer() != null && UserRepository.hasUser(event.getPlayer());
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
    IntaveLogger.logger().globalPrintLn("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a "+packetType+" packet ("+subscriber.getClass().getSimpleName()+"."+methodName+")");
    exception.printStackTrace();
  }

  private final static char[] vocals = "AEIOU".toCharArray();

  private String resolveIndefArticle(String exceptionName) {
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
