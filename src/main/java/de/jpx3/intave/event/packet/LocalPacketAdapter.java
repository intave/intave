package de.jpx3.intave.event.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.user.UserRepository;

public final class LocalPacketAdapter extends IntavePacketAdapter implements Comparable<LocalPacketAdapter> {
  private final String methodName;
  private final ListenerPriority priority;
  private final PacketEventSubscriber subscriber;
  private final PacketSubscriptionMethodExecutor executor;

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
//    boolean cancelled = event.isCancelled();
    try {
//      Timings.packetProcessing.start();
      executor.invoke(subscriber, event);
//      Timings.packetProcessing.stop();
    } catch (RuntimeException exception) {
      processException(event.getPacketType(), exception);
    }
//    if(!cancelled && event.isCancelled()) {
//      IntaveLogger.logger().globalPrintLn(subscriber.getClass().getSimpleName()+"."+methodName + " cancelled packet " + event.getPacketType());
//    }
  }

  @Override
  public void onPacketSending(PacketEvent event) {
    if(!validateEvent(event)) {
      return;
    }
    try {
      executor.invoke(subscriber, event);
    }/* catch (UnsupportedOperationException exception) {
      IntaveLogger.logger().globalPrintLn("[Intave] We recommend updating ProtocolLib");
      processException(exception);
    } */catch (RuntimeException exception) {
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
    char c = exceptionName.toCharArray()[0];
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
