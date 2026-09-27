package de.jpx3.intave.klass.create;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.library.asm.MethodVisitor;
import de.jpx3.intave.library.asm.Type;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.packet.reader.AnimationReader;
import de.jpx3.intave.packet.reader.PacketReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import static de.jpx3.intave.library.asm.Opcodes.ALOAD;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class IRXClassFactoryTest {
  public static final String CALLER_METHOD_DESCRIPTION = "(Lde/jpx3/intave/module/linker/packet/PacketEventSubscriber;Lcom/comphenix/protocol/events/PacketEvent;)V";
  private static MockedStatic<IntaveLogger> loggerMock;

  @BeforeAll
  static void setup() {
    IRXClassAssembler.TEST_MODE = true;
    loggerMock = mockStatic(IntaveLogger.class);
    loggerMock.when(IntaveLogger::logger).thenReturn(mock(IntaveLogger.class));
  }

  @AfterAll
  static void teardown() {
    IRXClassAssembler.TEST_MODE = false;
    loggerMock.close();
  }

  @Test
  void generatedCallerCastsAndForwardsArguments() throws Exception {
    AtomicReference<Object> received = new AtomicReference<>();
    Target.received = received;

    Class<Caller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
      Caller.class,
      "srcfile",
      "invoke",
      "(Ljava/lang/Object;Ljava/lang/Object;)V",
      Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(String.class)),
      Type.getInternalName(Target.class),
      "accept",
      "(Ljava/lang/String;)V",
      false,
      false,
      index -> index);

    callerClass.getDeclaredConstructor().newInstance().invoke(new Target(), "forwarded");

    assertEquals("forwarded", received.get());
  }

  @Test
  void generatedCallerCanInjectParametersFromTheEventSlot() throws Exception {
    AtomicReference<Object> received = new AtomicReference<>();
    Target.received = received;
    Function<String, BiConsumer<String, MethodVisitor>> instructions = type -> {
      if (type.equals(PacketEvent.class.getName())) {
        return (className, visitor) -> visitor.visitVarInsn(ALOAD, 2);
      }
      return null;
    };

    Class<EventCaller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
      EventCaller.class,
      "srcfile",
      "invoke",
      "(Ljava/lang/Object;Lcom/comphenix/protocol/events/PacketEvent;)V",
      Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(PacketEvent.class)),
      Type.getInternalName(Target.class),
      "acceptEvent",
      Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(PacketEvent.class)),
      false,
      false,
      IntUnaryOperator.identity(),
      instructions);

    PacketEvent event = null;
    callerClass.getDeclaredConstructor().newInstance().invoke(new Target(), event);

    assertEquals(event, received.get());
  }

  @Test
  void additionalParameterInstructionsAreAppliedToEveryCalledParameter() throws Exception {
    AtomicReference<Object> received = new AtomicReference<>();
    Target.received = received;
    List<String> requestedTypes = new ArrayList<>();
    Function<String, BiConsumer<String, MethodVisitor>> instructions = type -> {
      requestedTypes.add(type);
      if (type.equals(PacketEvent.class.getName())) {
        return (className, visitor) -> visitor.visitVarInsn(ALOAD, 2);
      }
      if (type.equals(String.class.getName())) {
        return (className, visitor) -> visitor.visitLdcInsn("injected");
      }
      return null;
    };

    Class<EventCaller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
      EventCaller.class,
      "srcfile",
      "invoke",
      "(Ljava/lang/Object;Lcom/comphenix/protocol/events/PacketEvent;)V",
      Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(PacketEvent.class)),
      Type.getInternalName(Target.class),
      "acceptEventAndValue",
      Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(PacketEvent.class), Type.getType(String.class)),
      false,
      false,
      index -> index,
      instructions);

    callerClass.getDeclaredConstructor().newInstance().invoke(new Target(), null);

    assertArrayEquals(new Object[]{null, "injected"}, (Object[]) received.get());
    assertEquals(List.of(PacketEvent.class.getName(), String.class.getName()), requestedTypes);
  }

  @Test
  void packetReaderInjectionReleasesTheReaderAfterInvocation() throws Exception {
    PacketEvent event = mock(PacketEvent.class);
    PacketReader reader = mock(PacketReader.class);
    when(event.getPacket()).thenReturn(null);
    Target.received = new AtomicReference<>();

    @SuppressWarnings("unchecked") Map<String, BiConsumer<String, MethodVisitor>> instructions = (Map<String, BiConsumer<String, MethodVisitor>>) getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class)) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      Class<PacketCaller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
        PacketCaller.class,
        "srcfile",
        "invoke", CALLER_METHOD_DESCRIPTION,
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(PacketEvent.class)),
        Type.getInternalName(Target.class),
        "acceptReader",
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(PacketReader.class)),
        false,
        false,
        index -> index,
        instructions::get);

      callerClass.getDeclaredConstructor().newInstance().invoke(new Target(), event);
    }

    assertEquals(reader, Target.received.get());
    verify(reader).releaseSafe();
  }

  @Test
  void packetReaderInjectionSupportsDeclaredSubtypes() throws Exception {
    PacketEvent event = mock(PacketEvent.class);
    AnimationReader reader = mock(AnimationReader.class);
    when(event.getPacket()).thenReturn(null);
    Target.received = new AtomicReference<>();

    @SuppressWarnings("unchecked") Map<String, BiConsumer<String, MethodVisitor>> instructions = (Map<String, BiConsumer<String, MethodVisitor>>) getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class)) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      Class<PacketCaller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
        PacketCaller.class,
        "srcfile",
        "invoke", CALLER_METHOD_DESCRIPTION,
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(PacketEvent.class)),
        Type.getInternalName(Target.class),
        "acceptReaderSubtype",
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(AnimationReader.class)),
        false,
        false,
        index -> index,
        instructions::get);

      callerClass.getDeclaredConstructor().newInstance().invoke(new Target(), event);
    }

    assertEquals(reader, Target.received.get());
    verify(reader, times(1)).releaseSafe();
  }

  @Test
  void packetReaderIsReleasedWhenCalledMethodThrows() throws Exception {
    PacketEvent event = mock(PacketEvent.class);
    PacketReader reader = mock(PacketReader.class);
    when(event.getPacket()).thenReturn(null);

    @SuppressWarnings("unchecked") Map<String, BiConsumer<String, MethodVisitor>> instructions =
      (Map<String, BiConsumer<String, MethodVisitor>>) getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class)) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      Class<PacketCaller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
        PacketCaller.class,
        "srcfile",
        "invoke", CALLER_METHOD_DESCRIPTION,
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(PacketEvent.class)),
        Type.getInternalName(Target.class),
        "acceptReaderAndThrow",
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(PacketReader.class)),
        false,
        false,
        index -> index,
        instructions::get);

      PacketCaller generatedCaller = callerClass.getDeclaredConstructor().newInstance();
      assertThrows(IllegalStateException.class, () -> generatedCaller.invoke(new Target(), event));
    } finally {
      verify(reader, times(1)).releaseSafe();
    }
  }

  @Test
  void manyParameters() throws Exception {
    PacketEvent event = mock(PacketEvent.class);
    Player player = mock(Player.class);
    PacketType type = mock(PacketType.class);
    User user = mock(User.class);
    PacketReader reader = mock(PacketReader.class);
    when(event.getPlayer()).thenReturn(player);
    when(event.getPacket()).thenReturn(null);
    when(event.getPacketType()).thenReturn(type);
    Target.received = new AtomicReference<>();

    @SuppressWarnings("unchecked") Map<String, BiConsumer<String, MethodVisitor>> instructions = (Map<String, BiConsumer<String, MethodVisitor>>) getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class);
         MockedStatic<UserRepository> users = mockStatic(UserRepository.class)
    ) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      users.when(() -> UserRepository.userOf(player)).thenReturn(user);

      Class<PacketCaller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
        PacketCaller.class,
        "srcfile",
        "invoke", CALLER_METHOD_DESCRIPTION,
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(PacketEvent.class)),
        Type.getInternalName(Target.class),
        "acceptMany",
        Type.getMethodDescriptor(Type.VOID_TYPE,
          Type.getType(Player.class),
          Type.getType(User.class),
          Type.getType(PacketReader.class),
          Type.getType(PacketEvent.class),
          Type.getType(PacketType.class)),
        false,
        false,
        index -> index,
        instructions::get);

      callerClass.getDeclaredConstructor().newInstance().invoke(new Target(), event);
    }

    Object[] received = (Object[]) Target.received.get();
    assertArrayEquals(new Object[]{player, user, reader, event, type}, received);
    verify(reader).releaseSafe();
  }

  @Test
  void manyParametersBlockAfterReaderFailure() throws Exception {
    PacketEvent event = mock(PacketEvent.class);
    Player player = mock(Player.class);
    PacketType type = mock(PacketType.class);
    User user = mock(User.class);
    when(event.getPlayer()).thenReturn(player);
    when(event.getPacket()).thenReturn(null);
    when(event.getPacketType()).thenReturn(type);
    Target.received = new AtomicReference<>();

    @SuppressWarnings("unchecked") Map<String, BiConsumer<String, MethodVisitor>> instructions = (Map<String, BiConsumer<String, MethodVisitor>>) getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class);
         MockedStatic<UserRepository> users = mockStatic(UserRepository.class)
    ) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenThrow(new IllegalStateException("missing reader"));
      users.when(() -> UserRepository.userOf(player)).thenReturn(user);

      Class<PacketCaller> callerClass = IRXClassFactory.assembleCallerClass(getClass().getClassLoader(),
        PacketCaller.class,
        "srcfile",
        "invoke", CALLER_METHOD_DESCRIPTION,
        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Target.class), Type.getType(PacketEvent.class)),
        Type.getInternalName(Target.class),
        "acceptShuffled",
        Type.getMethodDescriptor(Type.VOID_TYPE,
          Type.getType(PacketType.class),
          Type.getType(PacketReader.class),
          Type.getType(User.class),
          Type.getType(PacketEvent.class),
          Type.getType(Player.class)),
        false,
        false,
        index -> index,
        instructions::get);

      PacketCaller caller = callerClass.getDeclaredConstructor().newInstance();
      caller.invoke(new Target(), event);
      caller.invoke(new Target(), event);
      //noinspection resource,DataFlowIssue
      readers.verify(() -> PacketReaders.readerOf(null), times(1));
    }

    assertNull(Target.received.get());
  }

  private static Object getAdditionalInstructions() throws Exception {
    Field field = Class.forName("de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker").getDeclaredField(
      "additionalParameterInstructions");
    field.setAccessible(true);
    return field.get(null);
  }

  public interface Caller {
    void invoke(Object target, Object value);
  }

  public interface EventCaller {
    void invoke(Object target, PacketEvent event);
  }

  public interface PacketCaller {
    void invoke(PacketEventSubscriber target, PacketEvent event);
  }

  @SuppressWarnings("unused")
  public static final class Target implements PacketEventSubscriber {
    private static AtomicReference<Object> received;

    public void accept(String value) {
      received.set(value);
    }

    public void acceptEvent(PacketEvent event) {
      received.set(event);
    }

    public void acceptEventAndValue(PacketEvent event, String value) {
      received.set(new Object[]{event, value});
    }

    public void acceptReader(PacketReader reader) {
      received.set(reader);
    }

    public void acceptReaderSubtype(AnimationReader reader) {
      received.set(reader);
    }

    public void acceptReaderAndThrow(PacketReader reader) {
      throw new IllegalStateException("target failure");
    }

    public void acceptMany(Player player, User user, PacketReader reader, PacketEvent event, PacketType type) {
      received.set(new Object[]{player, user, reader, event, type});
    }

    public void acceptShuffled(PacketType type, PacketReader reader, User user, PacketEvent event, Player player) {
      received.set(new Object[]{type, reader, user, event, player});
    }
  }
}
