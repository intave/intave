package de.jpx3.intave.module.linker.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.packet.PacketRegistry;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.klass.create.IRXClassFactory;
import de.jpx3.intave.library.asm.Type;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.tinyprotocol.InjectionService;
import de.jpx3.intave.packet.reader.PacketReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import static de.jpx3.intave.IntaveControl.IGNORE_CHUNK_PACKETS;

@DoNotFlowObfuscate
public final class PacketSubscriptionLinker extends Module {
  private static boolean IGNORE_CHAT_PACKETS = false;
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

  private final Set<Class<?>> validParameterTypes = new HashSet<>();
  {
    validParameterTypes.add(PacketEvent.class);
    validParameterTypes.add(Cancellable.class);
    validParameterTypes.add(User.class);
    validParameterTypes.add(Player.class);
    validParameterTypes.add(PacketContainer.class);
    validParameterTypes.add(PacketReader.class);
    validParameterTypes.add(PacketType.class);
  }

  private boolean validParameters(Method method) {
    return (method.getParameterCount() == 1 && method.getParameterTypes()[0] == PacketEvent.class) ||
      Arrays.stream(method.getParameterTypes()).allMatch(type -> {
        return validParameterTypes.stream().anyMatch(aClass -> aClass.isAssignableFrom(type) /*|| type.isAssignableFrom(aClass)*/);
      });
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
    if (metadata.engine() == Engine.ASYNC_INTERNAL) {
      performCustomLinkage(subscriber, priority, packetTypes, ignoreCancelled, methodName, executor);
    } else {
      if (metadata.prioritySlot() == PrioritySlot.INTERNAL) {
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
    return Arrays.stream(clientPackets).map(this::translateClientPacketType).flatMap(Arrays::stream).toArray(PacketType[]::new);
  }

  private PacketType[] serverTranslate(PacketId.Server[] serverPackets) {
    if (serverPackets.length == 1 && "*".equals(serverPackets[0].lookupName())) {
      return PacketRegistry.getClientPacketTypes().toArray(new PacketType[0]);
    }
    return Arrays.stream(serverPackets).map(this::translateServerPacketType).flatMap(Arrays::stream).toArray(PacketType[]::new);
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
    boolean tabChatPacket = packetType == PacketType.Play.Client.TAB_COMPLETE ||
      packetType == PacketType.Play.Server.TAB_COMPLETE ||
      packetType == PacketType.Play.Client.CHAT;
//    if (tabChatPacket) {
//      Thread.dumpStack();
//    }
    if (IGNORE_CHAT_PACKETS) {
      return tabChatPacket;
    }
    if (IGNORE_CHUNK_PACKETS) {
      return packetType == PacketType.Play.Server.MAP_CHUNK ||
        packetType == PacketType.Play.Server.MAP_CHUNK_BULK;
    }

//    if (
//      packetType == PacketType.Play.Client.WINDOW_CLICK ||
//        packetType == PacketType.Play.Client.CUSTOM_PAYLOAD ||
//        packetType == PacketType.Play.Client.CLOSE_WINDOW ||
//        packetType == PacketType.Play.Client.CLIENT_COMMAND ||
////        packetType == PacketType.Play.Server.WINDOW_DATA ||
//        packetType == PacketType.Play.Server.WINDOW_ITEMS ||
//        packetType == PacketType.Play.Server.OPEN_WINDOW ||
//        packetType == PacketType.Play.Server.CLOSE_WINDOW
//    ) {
//      return true;
//    }

    return false;
  }

  private PacketType[] translateClientPacketType(PacketId.Client clientPacket) {
    return searchByName(selectPacketTypesFor(ConnectionSide.CLIENT_SIDE), clientPacket.lookupName());
  }

  private PacketType[] translateServerPacketType(PacketId.Server serverPacket) {
    return searchByName(selectPacketTypesFor(ConnectionSide.SERVER_SIDE), serverPacket.lookupName());
  }

  private Collection<PacketType> selectPacketTypesFor(ConnectionSide connectionSide) {
    Set<PacketType> availableTypes = new HashSet<>();
    if (connectionSide.isForServer()) availableTypes.addAll(PacketRegistry.getServerPacketTypes());
    if (connectionSide.isForClient()) availableTypes.addAll(PacketRegistry.getClientPacketTypes());
    return availableTypes;
  }

  private PacketType[] searchByName(Collection<? extends PacketType> packetPool, String name) {
    Collection<PacketType> packetTypes = PacketType.fromName(name);
    PacketType[] types = packetTypes.stream().filter(packetPool::contains).toArray(PacketType[]::new);
    if (types.length == 0) {
      types = packetPool.stream().filter(packetType -> matches(packetType, name)).toArray(PacketType[]::new);
    }
    return types;
  }

  private boolean matches(PacketType packetType, String name) {
    return packetType.name() != null && packetType.name().equalsIgnoreCase(name);
  }

  private static final ThreadLocal<Map<Integer, Object[]>> argumentCache = ThreadLocal.withInitial(HashMap::new);
  private static final ThreadLocal<Map<Integer, Boolean>> argumentLocks = ThreadLocal.withInitial(HashMap::new);

  private PacketSubscriptionMethodExecutor assembleSubscriptionMethodCaller(
    PacketEventSubscriber target,
    Method calledMethod,
    String identifier
  ) {
    if (calledMethod.getParameterCount() == 1 && calledMethod.getParameterTypes()[0] == PacketEvent.class) {
      String packetSubscriberSuperClassPath = canonicalRepresentation(className(PacketEventSubscriber.class));
      String packetSubscriberClassPath = canonicalRepresentation(className(target.getClass()));
      String packetEventClassPath = canonicalRepresentation(className(PacketEvent.class));
      Class<PacketSubscriptionMethodExecutor> executorClass = IRXClassFactory.assembleCallerClass(
        PacketSubscriptionLinker.class.getClassLoader(),
        PacketSubscriptionMethodExecutor.class,
        "<irx>",
        "invoke",
        "(L" + packetSubscriberSuperClassPath + ";L" + packetEventClassPath + ";)V",
        "(L" + packetSubscriberClassPath + ";L" + packetEventClassPath + ";)V",
        packetSubscriberClassPath,
        calledMethod.getName(),
        Type.getMethodDescriptor(calledMethod),
        false, false,
        IntUnaryOperator.identity()
      );
      return instanceOf(executorClass);
    } else {
      Class<?>[] parameterTypes = calledMethod.getParameterTypes();
      int length = parameterTypes.length;

      int playerParameterIndex = findParameterPosition(parameterTypes, Player.class);
      int userParameterPosition = findParameterPosition(parameterTypes, User.class);
      int cancelableParameterPosition = findParameterPosition(parameterTypes, Cancellable.class);
      int packetContainerParameterPosition = findParameterPosition(parameterTypes, PacketContainer.class);
      int packetReaderParameterPosition = findParameterPosition(parameterTypes, PacketReader.class);
      int packetEventParameterPosition = findParameterPosition(parameterTypes, PacketEvent.class);
      int packetTypeParameterPosition = findParameterPosition(parameterTypes, PacketType.class);

      return (subscriber, event) -> {
        Player player = event.getPlayer();

        Map<Integer, Boolean> locks = argumentLocks.get();
        Boolean isLocked = locks.get(length);
        if (isLocked == null) {
          locks.put(length, true);
          isLocked = false;
        }

        Object[] arguments = isLocked ? new Object[length] : argumentCache.get().computeIfAbsent(length, x -> new Object[length]);

        if (playerParameterIndex != -1) {
          arguments[playerParameterIndex] = player;
        }
        if (userParameterPosition != -1) {
          arguments[userParameterPosition] = UserRepository.userOf(player);
        }
        if (cancelableParameterPosition != -1) {
          arguments[cancelableParameterPosition] = event;
        }
        if (packetContainerParameterPosition != -1) {
          arguments[packetContainerParameterPosition] = event.getPacket();
        }
        PacketReader packetReader = null;
        if (packetReaderParameterPosition != -1) {
          packetReader = PacketReaders.readerOf(event.getPacket());
          arguments[packetReaderParameterPosition] = packetReader;
        }
        if (packetEventParameterPosition != -1) {
          arguments[packetEventParameterPosition] = event;
        }
        if (packetTypeParameterPosition != -1) {
          arguments[packetTypeParameterPosition] = event.getPacketType();
        }

        try {
          calledMethod.invoke(subscriber, arguments);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }

        if (packetReader != null) {
          packetReader.releaseSafe();
        }

        if (!isLocked) {
          locks.put(length, false);
          Arrays.fill(arguments, null);
        }
      };
    }
  }

  private static int findParameterPosition(Class<?>[] parameterTypes, Class<?> parameterType) {
    for (int i = 0; i < parameterTypes.length; i++) {
      if (parameterTypes[i] == parameterType) {
        return i;
      }
      // or is a subclass of the parameter type
      if (parameterType.isAssignableFrom(parameterTypes[i])) {
        return i;
      }
    }
    return -1;
  }

  private <T> T instanceOf(Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new Error(exception);
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

  private static <T> T[] clone(T[] array) {
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
    for (PacketType translatePacketType : translatePacketTypes) {
      FilteringPacketAdapter adapter = new FilteringPacketAdapter(plugin, subscriber, priority, new PacketType[]{translatePacketType}, methodName, executor, ignoreCancelled);
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
