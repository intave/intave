package de.jpx3.intave.module.linker.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.packet.PacketRegistry;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.clazz.create.IRXClassFactory;
import de.jpx3.intave.lib.asm.Type;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.tinyprotocol.InjectionService;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

import static de.jpx3.intave.IntaveControl.DISABLE_CHUNK_PACKET_HOOK;

@DoNotFlowObfuscate
public final class PacketSubscriptionLinker extends Module {
  private final static boolean NO_CHAT_HOOKUP = false;
  private final IntavePlugin plugin;
  private final Map<PacketType, SCOWAList<FilteringPacketAdapter>> customEngineListenerMappings = new ConcurrentHashMap<>();
  private final Map<PacketType, SCOWAList<FilteringPacketAdapter>> internalPacketListenerMappings = new ConcurrentHashMap<>();
  private final List<WeakReferencePacketAdapter> internalPacketListener = new ArrayList<>();
  private final List<WeakReferencePacketAdapter> externalPacketListener = new ArrayList<>();
  private InjectionService customInjector;

  public PacketSubscriptionLinker(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void enable() {
    this.customInjector = new InjectionService(plugin);
  }

  @Override
  public void disable() {
    for (WeakReferencePacketAdapter packetListener : internalPacketListener) {
      unlinkAdapter(packetListener);
      packetListener.tryRemovePluginReference();
    }
    internalPacketListener.clear();
    for (WeakReferencePacketAdapter packetListener : externalPacketListener) {
      unlinkAdapter(packetListener);
      packetListener.tryRemovePluginReference();
    }
    externalPacketListener.clear();
    ProtocolLibrary.getProtocolManager().removePacketListeners(plugin);
    internalPacketListenerMappings.values().forEach(SCOWAList::clear);
    internalPacketListenerMappings.clear();
    customEngineListenerMappings.values().forEach(SCOWAList::clear);
    customEngineListenerMappings.clear();
    customInjector.reset();
    customInjector.uninjectAll();
  }

  public void linkSubscriptionsIn(PacketEventSubscriber subscriber) {
    for (Method method : subscriber.getClass().getMethods()) {
      if (methodRequestsSubscription(method)) {
        linkSubscription(subscriber, method);
      }
    }
  }

  public void removeSubscriptionsOf(PacketEventSubscriber subscriber) {
    for (SCOWAList<FilteringPacketAdapter> value : internalPacketListenerMappings.values()) {
      value.removeIf(localPacketAdapter -> localPacketAdapter.subscriber() == subscriber);
    }
  }

  public void refreshLinkages() {
    ProtocolLibrary.getProtocolManager().removePacketListeners(plugin);
    for (PacketType packetType : internalPacketListenerMappings.keySet()) {
      bakeSubscriptions(packetType, internalPacketListenerMappings.get(packetType));
    }
    for (WeakReferencePacketAdapter weakReferencePacketAdapter : externalPacketListener) {
      linkAdapter(weakReferencePacketAdapter);
    }
    customInjector.reset();
    customEngineListenerMappings.forEach(customInjector::setupSubscriptions);
  }

  private void bakeSubscriptions(PacketType type, SCOWAList<FilteringPacketAdapter> filteringPacketAdapters) {
    ForwardingPacketAdapter adapter = new ForwardingPacketAdapter(plugin, type, filteringPacketAdapters);
    internalPacketListener.add(adapter);
    linkAdapter(adapter);
  }

  private void linkAdapter(WeakReferencePacketAdapter adapter) {
    ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
  }

  private void unlinkAdapter(WeakReferencePacketAdapter adapter) {
    ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
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
    String methodName = method.getName();
    ListenerPriority priority = metadata.priority();
    PacketType[] packetTypes = translatePacketTypes(metadata.packetsIn(), metadata.packetsOut());
    boolean ignoreCancelled = metadata.ignoreCancelled();
    if(metadata.engine() == Engine.ASYNC_INTERNAL) {
      performCustomLinkage(subscriber, priority, packetTypes, ignoreCancelled, methodName, executor);
    } else {
      if(metadata.prioritySlot() == PrioritySlot.INTERNAL) {
        performInternalLinkage(subscriber, priority, packetTypes, ignoreCancelled, methodName, executor);
      } else {
        performExternalLinkage(subscriber, priority, packetTypes, ignoreCancelled, methodName, executor);
      }
    }
  }

  private PacketType[] translatePacketTypes(PacketId.Client[] clientPackets, PacketId.Server[] serverPackets) {
    return distinct(excludeProblematic(translate(clientPackets, serverPackets)), PacketType[]::new);
  }

  private PacketType[] translate(PacketId.Client[] clientPackets, PacketId.Server[] serverPackets) {
    PacketType[] serverPacketTypes = clientTranslate(clientPackets);
    PacketType[] clientPacketTypes = serverTranslate(serverPackets);
    return merge(serverPacketTypes, clientPacketTypes);
  }

  private PacketType[] clientTranslate(PacketId.Client[] clientPackets) {
    if (clientPackets.length == 1 && clientPackets[0].lookupName().equals("*")) {
      return PacketRegistry.getClientPacketTypes().toArray(new PacketType[0]);
    }
    return Arrays.stream(clientPackets).map(this::translateClientPacketType).toArray(PacketType[]::new);
  }

  private PacketType[] serverTranslate(PacketId.Server[] serverPackets) {
    if (serverPackets.length == 1 && serverPackets[0].lookupName().equals("*")) {
      return PacketRegistry.getClientPacketTypes().toArray(new PacketType[0]);
    }
    return Arrays.stream(serverPackets).map(this::translateServerPacketType).toArray(PacketType[]::new);
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
    if (NO_CHAT_HOOKUP) {
      return packetType == PacketType.Play.Client.TAB_COMPLETE ||
        packetType == PacketType.Play.Server.TAB_COMPLETE ||
        packetType == PacketType.Play.Client.CHAT;
    }
    if (DISABLE_CHUNK_PACKET_HOOK) {
      return packetType == PacketType.Play.Server.MAP_CHUNK ||
        packetType == PacketType.Play.Server.MAP_CHUNK_BULK;
    }
    return false;
  }

  private PacketType translateClientPacketType(PacketId.Client clientPacket) {
    return searchByName(selectPacketTypesFor(ConnectionSide.CLIENT_SIDE), clientPacket.lookupName());
  }

  private PacketType translateServerPacketType(PacketId.Server serverPacket) {
    return searchByName(selectPacketTypesFor(ConnectionSide.SERVER_SIDE), serverPacket.lookupName());
  }

  private Collection<PacketType> selectPacketTypesFor(ConnectionSide connectionSide) {
    Set<PacketType> availableTypes = new HashSet<>();
    if (connectionSide.isForServer()) availableTypes.addAll(PacketRegistry.getServerPacketTypes());
    if (connectionSide.isForClient()) availableTypes.addAll(PacketRegistry.getClientPacketTypes());
    return availableTypes;
  }

  private PacketType searchByName(Collection<PacketType> packetPool, String name) {
    return packetPool.stream().filter(packetType -> matches(packetType, name)).findFirst().orElse(null);
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
    Class<PacketSubscriptionMethodExecutor> executorClass = IRXClassFactory.assembleCallerClass(
      PacketSubscriptionLinker.class.getClassLoader(),
      PacketSubscriptionMethodExecutor.class,
      "<generated>",
      "invoke",
      "(L" + packetSubscriberSuperClassPath + ";L" + packetEventClassPath + ";)V",
      "(L" + packetSubscriberClassPath + ";L" + packetEventClassPath + ";)V",
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

  private static <T> T[] merge(T[] array1, T[] array2) {
    if (array1 == null) {
      return clone(array2);
    } else if (array2 == null) {
      return clone(array1);
    } else {
      //noinspection unchecked
      T[] joinedArray = (T[]) Array.newInstance(array1.getClass().getComponentType(), array1.length + array2.length);
      System.arraycopy(array1, 0, joinedArray, 0, array1.length);
      try {
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
      } catch (ArrayStoreException var6) {
        Class<?> type1 = array1.getClass().getComponentType();
        Class<?> type2 = array2.getClass().getComponentType();
        if (!type1.isAssignableFrom(type2)) {
          throw new IllegalArgumentException("Cannot store " + type2.getName() + " in an array of " + type1.getName());
        } else {
          throw var6;
        }
      }
    }
  }

  public static <T> T[] clone(T[] array) {
    return array == null ? null : array.clone();
  }

  private void performCustomLinkage(
    PacketEventSubscriber subscriber,
    ListenerPriority priority, PacketType[] translatePacketTypes,
    boolean ignoreCancelled,
    String methodName, PacketSubscriptionMethodExecutor executor
  ) {
    if (translatePacketTypes.length == 0) {
      return;
    }
    FilteringPacketAdapter adapter = new FilteringPacketAdapter(plugin, subscriber, priority, translatePacketTypes, methodName, executor, ignoreCancelled);
    for (PacketType translatePacketType : translatePacketTypes) {
      SCOWAList<FilteringPacketAdapter> adapters =
        customEngineListenerMappings.computeIfAbsent(translatePacketType, x -> new SCOWAList<>());
      adapters.add(adapter);
    }
  }

  private void performInternalLinkage(
    PacketEventSubscriber subscriber,
    ListenerPriority priority, PacketType[] translatePacketTypes,
    boolean ignoreCancelled,
    String methodName, PacketSubscriptionMethodExecutor executor
  ) {
    if (translatePacketTypes.length == 0) {
      return;
    }
    for (PacketType translatePacketType : translatePacketTypes) {
      FilteringPacketAdapter adapter = new FilteringPacketAdapter(plugin, subscriber, priority, new PacketType[] {translatePacketType}, methodName, executor, ignoreCancelled);
      internalPacketListenerMappings.computeIfAbsent(translatePacketType, x -> new SCOWAList<>()).add(adapter);
    }
  }

  private void performExternalLinkage(
    PacketEventSubscriber subscriber,
    ListenerPriority priority, PacketType[] translatePacketTypes,
    boolean ignoreCancelled,
    String methodName, PacketSubscriptionMethodExecutor executor
  ) {
    if (translatePacketTypes.length == 0) {
      return;
    }
    FilteringPacketAdapter adapter = new FilteringPacketAdapter(plugin, subscriber, priority, translatePacketTypes, methodName, executor, ignoreCancelled);
    linkAdapter(adapter);
    externalPacketListener.add(adapter);
  }
}
