package de.jpx3.intave.event.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.packet.PacketRegistry;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.lib.asm.Type;
import de.jpx3.intave.reflect.irx.IRXFactory;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.IntFunction;

public final class PacketSubscriptionLinker {
  private final IntavePlugin plugin;
  private final List<WeakReference<ForwardingPacketAdapter>> packetAdapters = Lists.newArrayList();
  private final static boolean NO_CHAT_HOOKUP = false;

  public PacketSubscriptionLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void linkSubscriptionsIn(PacketEventSubscriber subscriber) {
    for (Method method : subscriber.getClass().getMethods()) {
      if (methodRequestsSubscription(method)) {
        linkSubscription(subscriber, method);
      }
    }
  }

  public void removeSubscriptionsOf(PacketEventSubscriber subscriber) {
    for (WeakReference<ForwardingPacketAdapter> packetAdapterWR : packetAdapters) {
      ForwardingPacketAdapter packetAdapter = packetAdapterWR.get();
      if (packetAdapter != null && packetAdapter.subscriber() == subscriber) {
        ProtocolLibrary.getProtocolManager().removePacketListener(packetAdapter);
      }
    }
    packetAdapters.removeIf(x -> x.get() == null);
  }

  private boolean methodRequestsSubscription(Method method) {
    return annotatedAsSubscription(method) && validParameters(method) && validModifiers(method);
  }

  private boolean annotatedAsSubscription(Method method) {
    return method.getAnnotation(PacketSubscription.class) != null;
  }

  private boolean validParameters(Method method) {
    return method.getParameterCount() == 1 && method.getParameterTypes()[0] == PacketEvent.class;
  }

  private boolean validModifiers(Method method) {
    int modifiers = method.getModifiers();
    return !Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers);
  }

  private void linkSubscription(PacketEventSubscriber subscriber, Method method) {
    PacketSubscription metadata = method.getAnnotation(PacketSubscription.class);
    PacketSubscriptionMethodExecutor executor = assembleSubscriptionMethodCaller(subscriber, method, metadata.identifier());
    linkAsAdapter(plugin, subscriber, metadata.priority(), translatePacketTypes(metadata.packets()), method.getName(), executor);
  }

  private PacketType[] translatePacketTypes(PacketDescriptor[] packetDescriptors) {
    PacketType[] packetTypes = Arrays.stream(packetDescriptors).map(this::translatePacketType).toArray(PacketType[]::new);
    return distinct(excludeProblematic(packetTypes), PacketType[]::new);
  }

  private <T> T[] distinct(T[] input, IntFunction<T[]> generator) {
    return Arrays.stream(input).filter(Objects::nonNull).distinct().toArray(generator);
  }

  private PacketType[] excludeProblematic(PacketType[] input) {
    for (int i = 0; i < input.length; i++) {
      PacketType packetType = input[i];
      if (excluded(packetType)) {
        input[i] = null;
      }
    }
    return input;
  }

  private boolean excluded(PacketType packetType) {
    if(NO_CHAT_HOOKUP) {
      return packetType == PacketType.Play.Client.TAB_COMPLETE ||
        packetType == PacketType.Play.Server.TAB_COMPLETE ||
        packetType == PacketType.Play.Client.CHAT;
    }

//    return !(packetType == PacketType.Play.Client.POSITION ||
//      packetType == PacketType.Play.Client.POSITION_LOOK);

    return false;
  }

  private PacketType translatePacketType(PacketDescriptor packetDescriptor) {
    ConnectionSide connectionSide = packetDescriptor.sender().toSide();
    Set<PacketType> availableTypes = selectPacketTypesFor(connectionSide);
    return searchByName(availableTypes, packetDescriptor.packetName());
  }

  private Set<PacketType> selectPacketTypesFor(ConnectionSide connectionSide) {
    Set<PacketType> availableTypes = new HashSet<>();
    if(connectionSide.isForServer()) {
      availableTypes.addAll(PacketRegistry.getServerPacketTypes());
    }
    if(connectionSide.isForClient()) {
      availableTypes.addAll(PacketRegistry.getClientPacketTypes());
    }
    return availableTypes;
  }

  private PacketType searchByName(Collection<PacketType> packetPool, String name) {
    return packetPool.stream()
      .filter(packetType -> matches(packetType, name))
      .findFirst().orElse(null);
  }

  private boolean matches(PacketType packetType, String name) {
    return packetType.name().equalsIgnoreCase(name);
  }

  private PacketSubscriptionMethodExecutor assembleSubscriptionMethodCaller(
    PacketEventSubscriber target,
    Method calledMethod,
    String identifier
  ) {
    String packetSubscriberSuperClassPath = canonicalRepresentation(className(PacketEventSubscriber.class));
    String packetSubscriberClassPath = canonicalRepresentation(className(target.getClass()));
    String packetEventClassPath = canonicalRepresentation(className(PacketEvent.class));
    Class<PacketSubscriptionMethodExecutor> executorClass = IRXFactory.assembleCallerClass(
      PacketSubscriptionLinker.class.getClassLoader(),
      PacketSubscriptionMethodExecutor.class,
      "Generic method pointer for packet subscriptions (" + identifier + ")",
      "invoke",
      "(L"+packetSubscriberSuperClassPath+";L"+packetEventClassPath+";)V",
      "(L"+packetSubscriberClassPath+";L"+packetEventClassPath+";)V",
      packetSubscriberClassPath,
      calledMethod.getName(),
      Type.getMethodDescriptor(calledMethod),
      false, false
    );
    return instanceOf(executorClass);
  }

  private <T> T instanceOf(Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new Error(e);
    }
  }

  private String className(Class<?> clazz) {
    return clazz.getCanonicalName();
  }

  private String canonicalRepresentation(String input) {
    return input.replaceAll("\\.", "/");
  }

  private void linkAsAdapter(
    IntavePlugin plugin, PacketEventSubscriber subscriber,
    ListenerPriority priority, PacketType[] translatePacketTypes,
    String methodName, PacketSubscriptionMethodExecutor executor
  ) {
    if(translatePacketTypes.length == 0) {
      return;
    }
    ForwardingPacketAdapter adapter = new ForwardingPacketAdapter(plugin, subscriber, priority, translatePacketTypes, methodName, executor);
    packetAdapters.removeIf(x -> x.get() == null);
    packetAdapters.add(new WeakReference<>(adapter));
    linkAdapter(adapter);
  }

  private void linkAdapter(PacketAdapter adapter) {
    ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
  }
}
