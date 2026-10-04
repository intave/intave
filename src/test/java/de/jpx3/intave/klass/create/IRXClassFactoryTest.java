package de.jpx3.intave.klass.create;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.library.asm.MethodVisitor;
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
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static de.jpx3.intave.library.asm.Opcodes.ALOAD;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class IRXClassFactoryTest {
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
  void generatedClassCastsAndForwardsArguments() throws Exception {
    AtomicReference<Object> received = new AtomicReference<>();
    Target.received = received;

    Class<Caller> generated = assemble(methodOf(Caller.class, "invoke"), methodOf(Target.class, "accept"));

    generated.getDeclaredConstructor().newInstance().invoke(new Target(), "forwarded");

    assertEquals("forwarded", received.get());
  }

  @Test
  void generatedClassCanInjectParametersFromTheEventSlot() throws Exception {
    AtomicReference<Object> received = new AtomicReference<>();
    Target.received = received;
    Function<String, BiConsumer<String, MethodVisitor>> instructions = type -> {
      if (type.equals(PacketEvent.class.getName())) {
        return (className, visitor) -> visitor.visitVarInsn(ALOAD, 2);
      }
      return null;
    };

    Class<EventCaller> generated = assemble(
      methodOf(EventCaller.class, "invoke"),
      methodOf(Target.class, "acceptEvent"),
      instructions
    );

    PacketEvent event = null;
    generated.getDeclaredConstructor().newInstance().invoke(new Target(), event);

    assertEquals(event, received.get());
  }

  @Test
  void additionalParameterInstructionsAreAppliedToEveryTargetParameter() throws Exception {
    Target.received = new AtomicReference<>(new Object[0]);
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

    Class<EventCaller> generated = assemble(
      methodOf(EventCaller.class, "invoke"),
      methodOf(Target.class, "acceptEventAndValue"),
      instructions
    );

    generated.getDeclaredConstructor().newInstance().invoke(new Target(), null);

    assertArrayEquals(new Object[]{null, "injected"}, (Object[]) Target.received.get());
    assertEquals(List.of(String.class.getName()), requestedTypes);
  }

  @Test
  void packetReaderInjectionReleasesTheReaderAfterInvocation() throws Exception {
    PacketEvent event = mock(PacketEvent.class);
    PacketReader reader = mock(PacketReader.class);
    when(event.getPacket()).thenReturn(null);
    Target.received = new AtomicReference<>();

    Map<String, BiConsumer<String, MethodVisitor>> extraInstructions = getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class)) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      Class<PacketCaller> callerClass = assemble(
        methodOf(PacketCaller.class, "invoke"), methodOf(Target.class, "acceptReader"), extraInstructions::get
      );

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

    Map<String, BiConsumer<String, MethodVisitor>> instructions = getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class)) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      Class<PacketCaller> callerClass = assemble(
        methodOf(PacketCaller.class, "invoke"), methodOf(Target.class, "acceptReaderSubtype"), instructions::get
      );

      callerClass.getDeclaredConstructor().newInstance().invoke(new Target(), event);
    }

    assertEquals(reader, Target.received.get());
    verify(reader, times(1)).releaseSafe();
  }

  @Test
  void packetReaderIsReleasedWhenTargetMethodThrows() throws Exception {
    PacketEvent event = mock(PacketEvent.class);
    PacketReader reader = mock(PacketReader.class);
    when(event.getPacket()).thenReturn(null);

    Map<String, BiConsumer<String, MethodVisitor>> instructions = getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class)) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      Class<PacketCaller> callerClass = assemble(
        methodOf(PacketCaller.class, "invoke"), methodOf(Target.class, "acceptReaderAndThrow"), instructions::get
      );

      PacketCaller generatedInstance = callerClass.getDeclaredConstructor().newInstance();
      assertThrows(IllegalStateException.class, () -> generatedInstance.invoke(new Target(), event));
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
    Target.received = new AtomicReference<>(new Object[0]);

    Map<String, BiConsumer<String, MethodVisitor>> instructions = getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class);
         MockedStatic<UserRepository> users = mockStatic(UserRepository.class)
    ) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenReturn(reader);
      users.when(() -> UserRepository.userOf(player)).thenReturn(user);

      Class<PacketCaller> generatedInstance = assemble(
        methodOf(PacketCaller.class, "invoke"), methodOf(Target.class, "acceptMany"),
        instructions::get
      );

      generatedInstance.getDeclaredConstructor().newInstance().invoke(new Target(), event);
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
    Object[] initialObj = new Object[0];
    Target.received = new AtomicReference<>(initialObj);

    Map<String, BiConsumer<String, MethodVisitor>> instructions = getAdditionalInstructions();

    try (MockedStatic<PacketReaders> readers = mockStatic(PacketReaders.class);
         MockedStatic<UserRepository> users = mockStatic(UserRepository.class)
    ) {
      //noinspection resource,DataFlowIssue
      readers.when(() -> PacketReaders.readerOf(null)).thenThrow(new RuntimeException("test missing reader"));
      users.when(() -> UserRepository.userOf(player)).thenReturn(user);

      Class<PacketCaller> generatedInstance = assemble(
        methodOf(PacketCaller.class, "invoke"), methodOf(Target.class, "acceptShuffled"),
        instructions::get
      );

      PacketCaller caller = generatedInstance.getDeclaredConstructor().newInstance();
      caller.invoke(new Target(), event);
      caller.invoke(new Target(), event);
      //noinspection resource,DataFlowIssue
      readers.verify(() -> PacketReaders.readerOf(null), times(1));
    }

    assertSame(initialObj, Target.received.get());
    assertArrayEquals(initialObj, (Object[]) Target.received.get());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, BiConsumer<String, MethodVisitor>> getAdditionalInstructions() throws Exception {
    Field field = Class.forName("de.jpx3.intave.module.linker.packet.PacketSubscriptionLinker")
      .getDeclaredField("extraParamInstructions");
    field.setAccessible(true);
    return (Map<String, BiConsumer<String, MethodVisitor>>) field.get(null);
  }

  private static Method methodOf(Class<?> type, String name) {
    return Arrays.stream(type.getMethods())
      .filter(method -> method.getName().equals(name))
      .findFirst()
      .orElseThrow(() -> new NoSuchMethodError(type.getName() + "#" + name));
  }

  private static <T> Class<T> assemble(Method toImplement, Method targetMethod) {
    return assemble(toImplement, targetMethod, null);
  }

  private static <T> Class<T> assemble(
    Method toImplement,
    Method targetMethod,
    Function<String, BiConsumer<String, MethodVisitor>> extraInstructions
  ) {
    return IRXClassFactory.assembleCallerClass(
      IRXClassFactoryTest.class.getClassLoader(),
      toImplement,
      targetMethod,
      extraInstructions
    );
  }

  public interface Caller {
    void invoke(Target target, String value);
  }

  public interface EventCaller {
    void invoke(Target target, PacketEvent event);
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
