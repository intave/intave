package de.jpx3.intave.event.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.UserRepository;

import java.util.Arrays;

public final class LocalPacketAdapter implements Comparable<LocalPacketAdapter> {
  private final static boolean TEMP_PLAYER_CHECK;
  static {
    TEMP_PLAYER_CHECK = Arrays.stream(PacketEvent.class.getMethods())
      .anyMatch(method -> method.getName().equalsIgnoreCase("isPlayerTemporary"));
  }

  private int exceptions;
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
//    super(plugin, com.comphenix.protocol.events.ListenerPriority.LOWEST, packetTypes);\
    this.subscriber = subscriber;
    this.methodName = methodName;
    this.priority = priority;
    this.executor = executor;
  }

//  @Override
  public void onPacketReceiving(PacketEvent event) {
    if(!validateEvent(event)) {
      return;
    }
    if(exceptions > 50) {
      return;
    }
    try {
//      Timings.packetProcessing.start();
      if (TEMP_PLAYER_CHECK) {
        // perform temporary check
        if(event.isPlayerTemporary()) {
//          Timings.packetProcessing.stop();
          return;
        }
      }
      executor.invoke(subscriber, event);
//      Timings.packetProcessing.stop();
    } catch (UnsupportedOperationException exception) {
      System.out.println("[Intave] We recommend updating ProtocolLib");
      processException(event.getPacketType(), exception);
    } catch (RuntimeException exception) {
      processException(event.getPacketType(), exception);

      if(exceptions++ >= 50) {
        System.out.println(subscriber.getClass().getSimpleName()+"."+methodName + " has been deactivated due to too many exceptions");
      }
    }
  }

//  @Override
  public void onPacketSending(PacketEvent event) {
    if(!validateEvent(event)) {
      return;
    }
    if (TEMP_PLAYER_CHECK) {
      // perform temporary check
      // this method does not exist in all version of protocollib, so we need to check for it before
      if(event.isPlayerTemporary()) {
        return;
      }
    }
    if(exceptions > 50) {
      return;
    }
    try {
      executor.invoke(subscriber, event);
    }/* catch (UnsupportedOperationException exception) {
      System.out.println("[Intave] We recommend updating ProtocolLib");
      processException(exception);
    } */catch (RuntimeException exception) {
      exception.fillInStackTrace();
      processException(event.getPacketType(), exception);
      if(exceptions++ >= 50) {
        System.out.println(subscriber.getClass().getSimpleName()+"."+methodName + " has been deactivated due to too many exceptions");
      }
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
    System.out.println("[Intave] " + resolveIndefArticle(simpleName) + " " + simpleName + " occurred while processing a "+packetType+" packet ("+subscriber.getClass().getSimpleName()+"."+methodName+")");
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
