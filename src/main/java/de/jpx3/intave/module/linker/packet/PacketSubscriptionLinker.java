package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.CancellableEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.OneForAll;
import de.jpx3.intave.module.linker.OneForOne;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import static de.jpx3.intave.IntaveControl.IGNORE_CHUNK_PACKETS;

public final class PacketSubscriptionLinker extends Module {
  private static boolean IGNORE_CHAT_PACKETS = false;
  private static boolean IGNORE_SCOREBOARD_TEAM_PACKETS = false;

  private final IntavePlugin plugin;
  private final List<FilteringPacketAdapter> packetListeners = new ArrayList<>();

  public PacketSubscriptionLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void enable() {
    IGNORE_CHAT_PACKETS = IGNORE_SCOREBOARD_TEAM_PACKETS =
      plugin.getConfig().getBoolean("compatibility.ignore-scoreboard-packets", false);
  }

  @Override
  public void disable() {
    for (FilteringPacketAdapter packetListener : packetListeners) {
      unlinkAdapter(packetListener);
    }
    packetListeners.clear();
  }

  public void linkSubscriptionsIn(PacketEventSubscriber subscriber) {
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProvider = instanceProviderFor(subscriber);
    for (Method method : instanceProvider.type().getMethods()) {
      if (methodRequestsSubscription(method)) {
        linkSubscription(instanceProvider, method);
      }
    }
  }

  public void removeSubscriptionsOf(PacketEventSubscriber subscriber) {
    Class<? extends PacketEventSubscriber> subscriberClass = subscriber.getClass();
    List<FilteringPacketAdapter> removed = new ArrayList<>();
    for (FilteringPacketAdapter packetListener : packetListeners) {
      PacketEventSubscriber linkedSubscriber = packetListener.subscriber();
      if (linkedSubscriber != null && linkedSubscriber.getClass().equals(subscriberClass)) {
        removed.add(packetListener);
      }
    }
    for (FilteringPacketAdapter packetListener : removed) {
      unlinkAdapter(packetListener);
      packetListeners.remove(packetListener);
    }
  }

  public void refreshLinkages() {
    for (FilteringPacketAdapter packetListener : packetListeners) {
      unlinkAdapter(packetListener);
      linkAdapter(packetListener);
    }
  }

  private void linkAdapter(FilteringPacketAdapter adapter) {
    PacketEvents.getAPI().getEventManager().registerListener(adapter);
  }

  private void unlinkAdapter(FilteringPacketAdapter adapter) {
    PacketEvents.getAPI().getEventManager().unregisterListener(adapter);
  }

  private boolean methodRequestsSubscription(Method method) {
    return annotatedAsSubscription(method) && validParameters(method) && validModifiers(method);
  }

  private boolean annotatedAsSubscription(Method method) {
    return method.getAnnotation(PacketSubscription.class) != null;
  }

  private boolean validParameters(Method method) {
    Class<?>[] parameterTypes = method.getParameterTypes();
    if (parameterTypes.length == 1 && ProtocolPacketEvent.class.isAssignableFrom(parameterTypes[0])) {
      return true;
    }
    for (Class<?> parameterType : parameterTypes) {
      if (!validParameter(parameterType)) {
        return false;
      }
    }
    return true;
  }

  private boolean validParameter(Class<?> type) {
    return ProtocolPacketEvent.class.isAssignableFrom(type)
      || CancellableEvent.class.isAssignableFrom(type)
      || User.class.isAssignableFrom(type)
      || Player.class.isAssignableFrom(type)
      || PacketTypeCommon.class.isAssignableFrom(type)
      || (PacketWrapper.class.isAssignableFrom(type) && type != PacketWrapper.class);
  }

  private boolean validModifiers(Method method) {
    int modifiers = method.getModifiers();
    return !Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers);
  }

  private void linkSubscription(SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProvider, Method method) {
    PacketSubscription metadata = method.getAnnotation(PacketSubscription.class);
    PacketSubscriptionMethodExecutor executor = assembleSubscriptionMethodCaller(method);
    String methodName = method.getName();
    ListenerPriority priority = metadata.priority();
    PacketTypeCommon[] packetTypes = translatePacketTypes(metadata.packetsIn(), metadata.packetsOut(), metadata.debug());
    boolean ignoreCancelled = metadata.ignoreCancelled();
    if (packetTypes.length == 0) {
      return;
    }
    FilteringPacketAdapter adapter = new FilteringPacketAdapter(instanceProvider, priority, packetTypes, methodName, executor, ignoreCancelled);
    packetListeners.add(adapter);
    linkAdapter(adapter);
  }

  private SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProviderFor(PacketEventSubscriber subscriber) {
    if (subscriber instanceof PlayerPacketEventSubscriber) {
      PlayerPacketEventSubscriber playerListener = (PlayerPacketEventSubscriber) subscriber;
      return new OneForOne<>(playerListener::packetSubscriberFor);
    }
    return new OneForAll<>(subscriber);
  }

  private PacketTypeCommon[] translatePacketTypes(
    PacketId.Client[] clientPackets,
    PacketId.Server[] serverPackets,
    boolean debug
  ) {
    return distinct(
      excludeProblematic(translate(clientPackets, serverPackets, debug), debug),
      PacketTypeCommon[]::new
    );
  }

  private PacketTypeCommon[] translate(PacketId.Client[] clientPackets, PacketId.Server[] serverPackets, boolean debug) {
    PacketTypeCommon[] clientPacketTypes = clientTranslate(clientPackets, debug);
    PacketTypeCommon[] serverPacketTypes = serverTranslate(serverPackets, debug);
    return merge(clientPacketTypes, serverPacketTypes);
  }

  private PacketTypeCommon[] clientTranslate(PacketId.Client[] clientPackets, boolean debug) {
    if (clientPackets.length == 1 && clientPackets[0].lookupName().equals("*")) {
      return PacketType.Play.Client.values();
    }
    List<PacketTypeCommon> list = new ArrayList<>();
    for (PacketId.Client clientPacket : clientPackets) {
      PacketTypeCommon[] packetTypes = clientPacket.packetTypes();
      if (debug) {
        IntaveLogger.logger().info("Translated " + clientPacket.lookupName() + " to " + Arrays.toString(packetTypes));
      }
      list.addAll(Arrays.asList(packetTypes));
    }
    return list.toArray(new PacketTypeCommon[0]);
  }

  private PacketTypeCommon[] serverTranslate(PacketId.Server[] serverPackets, boolean debug) {
    if (serverPackets.length == 1 && "*".equals(serverPackets[0].lookupName())) {
      return PacketType.Play.Server.values();
    }
    List<PacketTypeCommon> list = new ArrayList<>();
    for (PacketId.Server serverPacket : serverPackets) {
      PacketTypeCommon[] packetTypes = serverPacket.packetTypes();
      if (debug) {
        IntaveLogger.logger().info("Translated " + serverPacket.lookupName() + " to " + Arrays.toString(packetTypes));
      }
      list.addAll(Arrays.asList(packetTypes));
    }
    return list.toArray(new PacketTypeCommon[0]);
  }

  private <T> T[] distinct(T[] input, IntFunction<T[]> generator) {
    return Arrays.stream(input).filter(Objects::nonNull).distinct().toArray(generator);
  }

  private final Set<String> exclusionNoted = new HashSet<>();

  private PacketTypeCommon[] excludeProblematic(PacketTypeCommon[] input, boolean debug) {
    for (int i = 0; i < input.length; i++) {
      PacketTypeCommon packetType = input[i];
      if (excluded(packetType)) {
        String typeName = packetType.getName();
        if (!exclusionNoted.contains(typeName)) {
          IntaveLogger.logger().info("Ignoring " + typeName + " packets");
        }
        exclusionNoted.add(typeName);
        input[i] = null;
      }
    }
    return input;
  }

  private boolean excluded(PacketTypeCommon packetType) {
    boolean tabChatPacket = packetType == PacketType.Play.Client.TAB_COMPLETE
      || packetType == PacketType.Play.Server.TAB_COMPLETE
      || packetType == PacketType.Play.Client.CHAT_MESSAGE
      || packetType == PacketType.Play.Client.CHAT_COMMAND
      || packetType == PacketType.Play.Client.CHAT_COMMAND_UNSIGNED;
    if (IGNORE_CHAT_PACKETS && tabChatPacket) {
      return true;
    }
    if (IGNORE_CHUNK_PACKETS && (packetType == PacketType.Play.Server.CHUNK_DATA
      || packetType == PacketType.Play.Server.MAP_CHUNK_BULK)) {
      return true;
    }
    return IGNORE_SCOREBOARD_TEAM_PACKETS && packetType == PacketType.Play.Server.TEAMS;
  }

  private static final ThreadLocal<Map<Integer, Object[]>> argumentCache = ThreadLocal.withInitial(HashMap::new);
  private static final ThreadLocal<Map<Integer, Boolean>> argumentLocks = ThreadLocal.withInitial(HashMap::new);

  private PacketSubscriptionMethodExecutor assembleSubscriptionMethodCaller(Method calledMethod) {
    calledMethod.setAccessible(true);
    if (calledMethod.getParameterCount() == 1 && ProtocolPacketEvent.class.isAssignableFrom(calledMethod.getParameterTypes()[0])) {
      return (subscriber, event) -> {
        try {
          calledMethod.invoke(subscriber, event);
        } catch (Exception exception) {
          throw new RuntimeException("Failed to invoke packet subscription method " + calledMethod + " in " + subscriber.getClass().getCanonicalName(), exception);
        }
      };
    }

    Class<?>[] parameterTypes = calledMethod.getParameterTypes();
    int length = parameterTypes.length;

    return (subscriber, event) -> {
      Map<Integer, Boolean> locks = argumentLocks.get();
      boolean isLocked = Boolean.TRUE.equals(locks.get(length));
      if (!isLocked) {
        locks.put(length, true);
      }

      Object[] arguments = isLocked ? new Object[length] : argumentCache.get().computeIfAbsent(length, x -> new Object[length]);
      try {
        for (int i = 0; i < parameterTypes.length; i++) {
          Class<?> parameterType = parameterTypes[i];
          if (parameterType.isInstance(event)) {
            arguments[i] = event;
          } else if (CancellableEvent.class.isAssignableFrom(parameterType)) {
            arguments[i] = event;
          } else if (Player.class.isAssignableFrom(parameterType)) {
            arguments[i] = event.getPlayer();
          } else if (User.class.isAssignableFrom(parameterType)) {
            arguments[i] = UserRepository.userOf((Player) event.getPlayer());
          } else if (PacketTypeCommon.class.isAssignableFrom(parameterType)) {
            arguments[i] = event.getPacketType();
          } else if (PacketWrapper.class.isAssignableFrom(parameterType)) {
            arguments[i] = wrapperFor(parameterType, event);
          }
        }

        calledMethod.invoke(subscriber, arguments);
      } catch (Exception exception) {
        throw new RuntimeException("Failed to invoke packet subscription method " + calledMethod + " in " + subscriber.getClass().getCanonicalName(), exception);
      } finally {
        if (!isLocked) {
          locks.put(length, false);
          Arrays.fill(arguments, null);
        }
      }
    };
  }

  private PacketWrapper<?> wrapperFor(Class<?> wrapperType, ProtocolPacketEvent event) {
    try {
      Constructor<?> constructor = event instanceof PacketReceiveEvent
        ? wrapperType.getConstructor(PacketReceiveEvent.class)
        : wrapperType.getConstructor(PacketSendEvent.class);
      return (PacketWrapper<?>) constructor.newInstance(event);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create PacketEvents wrapper " + wrapperType.getName() + " for " + event.getPacketType().getName(), exception);
    }
  }

  private static <T> T[] merge(T[] array1, T[] array2) {
    if (array1 == null) {
      return clone(array2);
    } else if (array2 == null) {
      return clone(array1);
    }
    @SuppressWarnings("unchecked")
    T[] joinedArray = (T[]) Array.newInstance(array1.getClass().getComponentType(), array1.length + array2.length);
    System.arraycopy(array1, 0, joinedArray, 0, array1.length);
    try {
      System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
      return joinedArray;
    } catch (ArrayStoreException exception) {
      Class<?> type1 = array1.getClass().getComponentType();
      Class<?> type2 = array2.getClass().getComponentType();
      if (!type1.isAssignableFrom(type2)) {
        throw new IllegalArgumentException("Cannot store " + type2.getName() + " in an array of " + type1.getName());
      }
      throw exception;
    }
  }

  private static <T> T[] clone(T[] array) {
    return array == null ? null : array.clone();
  }
}
