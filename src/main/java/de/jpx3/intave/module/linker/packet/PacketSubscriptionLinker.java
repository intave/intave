/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.linker.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.packet.PacketRegistry;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.klass.create.IRXClassFactory;
import de.jpx3.intave.library.asm.Label;
import de.jpx3.intave.library.asm.MethodVisitor;
import de.jpx3.intave.library.asm.Type;
import de.jpx3.intave.library.asm.tree.AbstractInsnNode;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.OneForAll;
import de.jpx3.intave.module.linker.OneForOne;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import static de.jpx3.intave.IntaveControl.IGNORE_CHUNK_PACKETS;

import static de.jpx3.intave.library.asm.Opcodes.*;

public final class PacketSubscriptionLinker extends Module {
  private static boolean IGNORE_CHAT_PACKETS = false;
  private static boolean IGNORE_SCOREBOARD_TEAM_PACKETS = false;
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
    boolean protocolLib4 = ProtocolLibrary.getPlugin().getDescription().getVersion().startsWith("4");
    IGNORE_CHAT_PACKETS = IGNORE_SCOREBOARD_TEAM_PACKETS = plugin.getConfig().getBoolean("compatibility.ignore-scoreboard-packets", !protocolLib4);
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
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProvider = instanceProviderFor(subscriber);
    for (Method method : instanceProvider.type().getMethods()) {
      if (methodRequestsSubscription(method)) {
        linkSubscription(instanceProvider, method);
      }
    }
  }

  public void removeSubscriptionsOf(PacketEventSubscriber subscriber) {
    Class<? extends PacketEventSubscriber> subscriberClass = subscriber.getClass();
    for (SCOWAList<FilteringPacketAdapter> value : internalPacketListenerMappings.values()) {
      value.removeIf(localPacketAdapter -> localPacketAdapter.subscriber() != null && localPacketAdapter.subscriber().getClass().equals(subscriberClass));
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
	  if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == PacketEvent.class) {
		  return true;
	  }
	  if (Arrays.stream(method.getParameterTypes()).allMatch(type -> validParameterTypes.stream().anyMatch(aClass -> aClass.isAssignableFrom(type) /*|| type.isAssignableFrom(aClass)*/))) {
		  return true;
	  }
	  return false;
  }

  private boolean validModifiers(Method method) {
    int modifiers = method.getModifiers();
    return !Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers);
  }

  private void linkSubscription(SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProvider, Method method) {
    PacketSubscription metadata = method.getAnnotation(PacketSubscription.class);
    String methodName = method.getName();
    ListenerPriority priority = metadata.priority();
    boolean ignoreCancelled = metadata.ignoreCancelled();

    switch (metadata.engine()) {
      case INTERNAL:
        PacketSubscriptionMethodExecutor executor = assemblePESubscriptionMethodCaller(instanceProvider.type(), method, metadata.engine());
        PacketType[] packetTypes = translateProtocolLibPacketTypes(metadata.packetsIn(), metadata.packetsOut(), metadata.debug());
        performCustomLinkage(instanceProvider, priority, packetTypes, ignoreCancelled, methodName, executor);
        break;
      case PROTOCOLLIB:
        executor = assemblePESubscriptionMethodCaller(instanceProvider.type(), method, metadata.engine());
        packetTypes = translateProtocolLibPacketTypes(metadata.packetsIn(), metadata.packetsOut(), metadata.debug());
        if (metadata.prioritySlot() == PrioritySlot.INTERNAL) {
          performInternalProtocolLibLinkage(instanceProvider, priority, packetTypes, ignoreCancelled, methodName, executor);
        } else {
          performExternalProtocolLibLinkage(instanceProvider, priority, packetTypes, ignoreCancelled, methodName, executor);
        }
        break;
    }
  }

  private SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProviderFor(PacketEventSubscriber subscriber) {
    if (subscriber instanceof PlayerPacketEventSubscriber) {
      PlayerPacketEventSubscriber playerListener = (PlayerPacketEventSubscriber) subscriber;
      return new OneForOne<>(playerListener::packetSubscriberFor);
    } else {
      return new OneForAll<>(subscriber);
    }
  }

  private PacketType[] translateProtocolLibPacketTypes(
    PacketId.Client[] clientPackets,
    PacketId.Server[] serverPackets,
    boolean debug
  ) {
    return distinct(
      excludeProblematic(translate(clientPackets, serverPackets, debug), debug),
      PacketType[]::new
    );
  }

  private PacketType[] translate(PacketId.Client[] clientPackets, PacketId.Server[] serverPackets, boolean debug) {
    PacketType[] serverPacketTypes = clientTranslate(clientPackets, debug);
    PacketType[] clientPacketTypes = serverTranslate(serverPackets, debug);
    return merge(serverPacketTypes, clientPacketTypes);
  }

  private PacketType[] clientTranslate(PacketId.Client[] clientPackets, boolean debug) {
    if (clientPackets.length == 1 && clientPackets[0].lookupName().equals("*")) {
      return PacketRegistry.getClientPacketTypes().toArray(new PacketType[0]);
    }
    List<PacketType> list = new ArrayList<>();
    for (PacketId.Client clientPacket : clientPackets) {
      PacketType[] packetTypes = translateClientPacketType(clientPacket, debug);
      list.addAll(Arrays.asList(packetTypes));
    }
    return list.toArray(new PacketType[0]);
  }

  private PacketType[] serverTranslate(PacketId.Server[] serverPackets, boolean debug) {
    if (serverPackets.length == 1 && "*".equals(serverPackets[0].lookupName())) {
      return PacketRegistry.getClientPacketTypes().toArray(new PacketType[0]);
    }
    List<PacketType> list = new ArrayList<>();
    for (PacketId.Server serverPacket : serverPackets) {
      PacketType[] packetTypes = translateServerPacketType(serverPacket, debug);
      list.addAll(Arrays.asList(packetTypes));
    }
    return list.toArray(new PacketType[0]);
  }

  private <T> T[] distinct(T[] input, IntFunction<T[]> generator) {
    return Arrays.stream(input).filter(Objects::nonNull).distinct().toArray(generator);
  }

  private final Set<String> exclusionNoted = new HashSet<>();

  private PacketType[] excludeProblematic(PacketType[] input, boolean debug) {
    for (int i = 0; i < input.length; i++) {
      PacketType packetType = input[i];
      if (excluded(packetType)) {
        String typeName = packetType.name();
        if (!exclusionNoted.contains(typeName)) {
          IntaveLogger.logger().info("Ignoring " + typeName + " packets");
        }
        exclusionNoted.add(typeName);
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
    if (IGNORE_CHAT_PACKETS && tabChatPacket) {
      return true;
    }
    if (IGNORE_CHUNK_PACKETS && (packetType == PacketType.Play.Server.MAP_CHUNK ||
      packetType == PacketType.Play.Server.MAP_CHUNK_BULK)) {
      return true;
    }
    if (IGNORE_SCOREBOARD_TEAM_PACKETS && (packetType == PacketType.Play.Server.SCOREBOARD_TEAM)) {
      return true;
    }

//    if (
//      packetType == PacketType.Play.Client.WINDOW_CLICK ||
////        packetType == PacketType.Play.Client.CUSTOM_PAYLOAD ||
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

  private PacketType[] translateClientPacketType(PacketId.Client clientPacket, boolean debug) {
    PacketType[] results = searchByName(selectPacketTypesFor(ConnectionSide.CLIENT_SIDE), clientPacket.lookupName());
    if (debug) {
      IntaveLogger.logger().info("Translated " + clientPacket.lookupName() + " to " + Arrays.toString(results));
    }
    return results;
  }

  private PacketType[] translateServerPacketType(PacketId.Server serverPacket, boolean debug) {
    PacketType[] results = searchByName(selectPacketTypesFor(ConnectionSide.SERVER_SIDE), serverPacket.lookupName());
    if (debug) {
      IntaveLogger.logger().info("Translated " + serverPacket.lookupName() + " to " + Arrays.toString(results));
    }
    return results;
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

  
  private static final Map<String, BiConsumer<String, MethodVisitor>> additionalParameterInstructions = new HashMap<>();

  private static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
    try {
      return clazz.getMethod(methodName, parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Failed to find method " + methodName + " in class " + clazz.getCanonicalName(), e);
    }
  }
  
  private static void visitMethodInsn(MethodVisitor mv, Method method) {
    mv.visitMethodInsn(
      Modifier.isStatic(method.getModifiers()) ? INVOKESTATIC : INVOKEVIRTUAL,
      Type.getInternalName(method.getDeclaringClass()),
      method.getName(),
      Type.getMethodDescriptor(method),
      false
    );
  }
  
  static {
    // locals: 0 - this, 1 - subscriber, 2 - PacketEvent, 3 - PacketReader (if applicable)
    additionalParameterInstructions.put(Player.class.getName(), (className, mv) -> {
      mv.visitVarInsn(ALOAD, 2);
      visitMethodInsn(mv, getMethod(PacketEvent.class, "getPlayer"));
    } );
    additionalParameterInstructions.put(User.class.getName(), (className, mv) -> {
      mv.visitVarInsn(ALOAD, 2);
      visitMethodInsn(mv, getMethod(PacketEvent.class, "getPlayer"));
      visitMethodInsn(mv, getMethod(UserRepository.class, "userOf", Player.class));
    } );
    additionalParameterInstructions.put(Cancellable.class.getName(), (className, mv) -> {
      mv.visitVarInsn(ALOAD, 2);
    } );
    additionalParameterInstructions.put(PacketContainer.class.getName(), (className, mv) -> {
      mv.visitVarInsn(ALOAD, 2);
      visitMethodInsn(mv, getMethod(PacketEvent.class, "getPacket"));
    } );
    additionalParameterInstructions.put(PacketReader.class.getName(), (className, mv) -> {
      // IRXClassAssembler injects the field "block" into the generated class
      // based on "PacketReader" presence in the target method's parameters
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, className, "block", "Z");
      mv.visitInsn(ICONST_0);
      Label startLabel = new Label();
      mv.visitJumpInsn(IF_ICMPEQ, startLabel);
      mv.visitInsn(RETURN);
      mv.visitLabel(startLabel);
      
      mv.visitVarInsn(ALOAD, 2);
      visitMethodInsn(mv, getMethod(PacketEvent.class, "getPacket"));
      
      Label tryStart = new Label();
      mv.visitLabel(tryStart);
      visitMethodInsn(mv, getMethod(PacketReaders.class, "readerOf", PacketContainer.class));
      Label continueLabel = new Label();
      mv.visitJumpInsn(GOTO, continueLabel);
      Label tryEnd = new Label();
      mv.visitLabel(tryEnd);
      {
        // catch
        Label catchBlock = new Label();
        mv.visitTryCatchBlock(tryStart, tryEnd, catchBlock, Type.getInternalName(Exception.class));
        mv.visitLabel(catchBlock);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_1);
        mv.visitFieldInsn(PUTFIELD, className, "block", "Z");
        // IntaveLogger.logger().info(subscriber.getClass().getCanonicalName() + " skipped packet type due to ProtocolLib missing packet " + event.getPacketType().name());
        visitMethodInsn(mv, getMethod(IntaveLogger.class, "logger"));
        mv.visitTypeInsn(NEW, Type.getInternalName(StringBuilder.class));
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(StringBuilder.class), "<init>", "()V", false);
        mv.visitVarInsn(ALOAD, 1);
        visitMethodInsn(mv, getMethod(Object.class, "getClass"));
        visitMethodInsn(mv, getMethod(Class.class, "getCanonicalName"));
        visitMethodInsn(mv, getMethod(StringBuilder.class, "append", String.class));
        mv.visitLdcInsn(" skipped packet type due to ProtocolLib missing packet ");
        visitMethodInsn(mv, getMethod(StringBuilder.class, "append", String.class));
        mv.visitVarInsn(ALOAD, 2);
        visitMethodInsn(mv, getMethod(PacketEvent.class, "getPacketType"));
        visitMethodInsn(mv, getMethod(PacketType.class, "name"));
        visitMethodInsn(mv, getMethod(StringBuilder.class, "append", String.class));
        visitMethodInsn(mv, getMethod(StringBuilder.class, "toString"));
        visitMethodInsn(mv, getMethod(IntaveLogger.class, "info", String.class));
        mv.visitInsn(RETURN);
        //end catch
      }
      mv.visitLabel(continueLabel);
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 3);
    } );
    additionalParameterInstructions.put(PacketEvent.class.getName(), (className, mv) -> {
      mv.visitVarInsn(ALOAD, 2);
    } );
    additionalParameterInstructions.put(PacketType.class.getName(), (className, mv) -> {
      mv.visitVarInsn(ALOAD, 2);
      visitMethodInsn(mv, getMethod(PacketEvent.class, "getPacketType"));
    } );
  }

  private PacketSubscriptionMethodExecutor assemblePESubscriptionMethodCaller(
    Class<? extends PacketEventSubscriber> targetClass,
    Method calledMethod,
    Engine engine
  ) {
    if (calledMethod.getParameterCount() == 1 && calledMethod.getParameterTypes()[0] == PacketEvent.class) {
      String packetSubscriberSuperClassPath = Type.getInternalName(PacketEventSubscriber.class);
      String packetSubscriberClassPath = Type.getInternalName(targetClass);
      String packetEventClassPath = Type.getInternalName(PacketEvent.class);
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
      String packetSubscriberSuperClassPath = Type.getInternalName(PacketEventSubscriber.class);
      String packetSubscriberClassPath = Type.getInternalName(targetClass);
      String packetEventClassPath = Type.getInternalName(PacketEvent.class);
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
        IntUnaryOperator.identity(),
        additionalParameterInstructions::get
      );
      return instanceOf(executorClass);
    }
  }

  private <T> T instanceOf(Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new Error(exception);
    }
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
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber,
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

  private void performInternalProtocolLibLinkage(
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber,
    ListenerPriority priority, PacketType[] translatePacketTypes,
    boolean ignoreCancelled,
    String methodName, PacketSubscriptionMethodExecutor executor
  ) {
    for (PacketType translatePacketType : translatePacketTypes) {
      FilteringPacketAdapter adapter = new FilteringPacketAdapter(plugin, subscriber, priority, new PacketType[]{translatePacketType}, methodName, executor, ignoreCancelled);
      internalPacketListenerMappings.computeIfAbsent(translatePacketType, x -> new SCOWAList<>()).add(adapter);
    }
  }

  private void performExternalProtocolLibLinkage(
    SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> subscriber,
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
