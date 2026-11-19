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

package de.jpx3.intave.check;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ProtocolLibSubscriptionLinker;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.KeepAlivePacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.packet.wire.connection.keepalive.KeepAliveWirePacket;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public final class CheckLinkerTest {
  private final CheckLinker linker = new CheckLinker();
  private final PacketPerfectSubscriptionLinker packetLinker = new PacketPerfectSubscriptionLinker();
  private Map<Class<? extends Module>, Module> modules;
  private Map<Class<? extends Module>, Module> savedModules;
  private AtomicReference<Map<PacketType.Client, PacketSubscription[]>> receives;
  private AtomicReference<Map<PacketType.Server, PacketSubscription[]>> sends;
  private Map<PacketType.Client, PacketSubscription[]> savedReceives;
  private Map<PacketType.Server, PacketSubscription[]> savedSends;
  private Field versionField;
  private MinecraftVersion savedVersion;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws ReflectiveOperationException {
    Field poolField = Modules.class.getDeclaredField("pool");
    poolField.setAccessible(true);
    Object pool = poolField.get(null);
    Field modulesField = pool.getClass().getDeclaredField("moduleClassMappings");
    modulesField.setAccessible(true);
    modules = (Map<Class<? extends Module>, Module>) modulesField.get(pool);
    savedModules = new HashMap<>(modules);
    modules.put(ProtocolLibSubscriptionLinker.class, new ProtocolLibSubscriptionLinker(null));
    modules.put(PacketPerfectSubscriptionLinker.class, packetLinker);

    receives = subscriptions("CLIENT_SUBSCRIPTIONS");
    sends = subscriptions("SERVER_SUBSCRIPTIONS");
    savedReceives = receives.getAndSet(new EnumMap<>(PacketType.Client.class));
    savedSends = sends.getAndSet(new EnumMap<>(PacketType.Server.class));
    versionField = MinecraftVersion.class.getDeclaredField("currentVersion");
    versionField.setAccessible(true);
    savedVersion = (MinecraftVersion) versionField.get(null);
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
  }

  @AfterEach
  void tearDown() throws IllegalAccessException {
    packetLinker.disable();
    receives.set(savedReceives);
    sends.set(savedSends);
    modules.clear();
    modules.putAll(savedModules);
    versionField.set(null, savedVersion);
  }

  @Test
  void registersChecksAndInheritedPartsAndRemovesThemEvenWhenDisabled() throws ReflectiveOperationException {
    RuntimeCheck check = check(true);
    CounterPart part = new InheritedPart(check);
    check.parts.add(part);
    List<RuntimeCheck> checks = Collections.singletonList(check);

    linker.linkPacketEventSubscriptions(checks);
    linker.linkPacketEventSubscriptions(checks);
    dispatch(user());
    assertEquals(2, check.calls);
    assertEquals(2, part.calls);

    check.setEnabled(false);
    dispatch(user());
    assertEquals(2, check.calls);
    assertEquals(2, part.calls);
    linker.removePacketEventSubscriptions(checks);
    linker.removePacketEventSubscriptions(checks);
    assertTrue(receives.get().isEmpty());
    assertTrue(sends.get().isEmpty());

    check.setEnabled(true);
    linker.linkPacketEventSubscriptions(checks);
    linker.linkPacketEventSubscriptions(checks);
    dispatch(user());
    assertEquals(4, check.calls);
    assertEquals(4, part.calls);
  }

  @Test
  void keepsCheckAndPartEligibilityIndependent() throws ReflectiveOperationException {
    RuntimeCheck check = check(false);
    CounterPart followsParent = new CounterPart(check);
    CounterPart independent = new IndependentPart(check);
    check.parts.add(followsParent);
    check.parts.add(independent);
    List<RuntimeCheck> checks = Collections.singletonList(check);

    linker.linkPacketEventSubscriptions(checks);
    dispatch(user());
    assertEquals(0, check.calls);
    assertEquals(0, followsParent.calls);
    assertEquals(2, independent.calls);

    check.setEnabled(true);
    linker.linkPacketEventSubscriptions(checks);
    dispatch(user());
    assertEquals(2, check.calls);
    assertEquals(2, followsParent.calls);
    assertEquals(4, independent.calls);

    check.setEnabled(false);
    linker.removePacketEventSubscriptions(checks);
    dispatch(user());
    assertEquals(4, independent.calls);
    assertTrue(receives.get().isEmpty());
    assertTrue(sends.get().isEmpty());
  }

  @Test
  void registersAndRemovesTheResolvedPerPlayerCheckPart() throws ReflectiveOperationException {
    RuntimeCheck check = check(true);
    PlayerCheckPartDelegate<RuntimeCheck, PerPlayerPart> delegate = new PlayerCheckPartDelegate<>(check, PerPlayerPart.class);
    check.parts.add(delegate);
    List<RuntimeCheck> checks = Collections.singletonList(check);
    User first = user();
    User second = user();
    PerPlayerPart firstPart = (PerPlayerPart) delegate.packetSubscriberFor(first);
    PerPlayerPart secondPart = (PerPlayerPart) delegate.packetSubscriberFor(second);
    assertNotSame(firstPart, secondPart);

    linker.linkPacketEventSubscriptions(checks);
    dispatch(first);
    dispatch(second);
    dispatch(first);
    assertEquals(4, firstPart.calls);
    assertEquals(2, secondPart.calls);
    assertSame(first, firstPart.owningUser());
    assertSame(second, secondPart.owningUser());

    check.setEnabled(false);
    dispatch(first);
    assertEquals(4, firstPart.calls);
    linker.removePacketEventSubscriptions(checks);
    assertTrue(receives.get().isEmpty());
    assertTrue(sends.get().isEmpty());
    check.setEnabled(true);
    dispatch(first);
    assertEquals(4, firstPart.calls);

    linker.linkPacketEventSubscriptions(checks);
    linker.linkPacketEventSubscriptions(checks);
    dispatch(first);
    assertEquals(6, firstPart.calls);
    assertEquals(2, secondPart.calls);
  }

  private static RuntimeCheck check(boolean enabled) throws ReflectiveOperationException {
    // Test lifecycle behavior without booting the Bukkit plugin required by Check's constructor.
    RuntimeCheck check = (RuntimeCheck) ReflectionFactory.getReflectionFactory()
      .newConstructorForSerialization(RuntimeCheck.class, Object.class.getDeclaredConstructor()).newInstance();
    check.parts = new ArrayList<>();
    check.setEnabled(enabled);
    return check;
  }

  @SuppressWarnings("unchecked")
  private static <T> AtomicReference<Map<T, PacketSubscription[]>> subscriptions(String name) throws ReflectiveOperationException {
    Field field = PacketEventBus.class.getDeclaredField(name);
    field.setAccessible(true);
    return (AtomicReference<Map<T, PacketSubscription[]>>) field.get(null);
  }

  private static void dispatch(User user) {
    PacketEventBus.dispatch(new PacketReceiveEvent(user, PacketType.Client.KEEP_ALIVE_IN, new KeepAliveWirePacket()));
    PacketEventBus.dispatch(new PacketSendEvent(user, PacketType.Server.KEEP_ALIVE_OUT, new KeepAliveWirePacket()));
  }

  private static User user() {
    UUID id = UUID.randomUUID();
    Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
      (proxy, method, arguments) -> method.getName().equals("getUniqueId") ? id : null);
    return (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("hasPlayer")) return true;
        if (method.getName().equals("player")) return player;
        return null;
      });
  }

  public static final class RuntimeCheck extends Check {
    private List<CheckPart<?>> parts;
    private int calls;

    public RuntimeCheck() {
      super("test", "test");
    }

    @Override
    List<CheckPart<?>> checkParts() {
      return parts;
    }

    @OnReceive
    @OnSend
    public void onPacket(KeepAlivePacket packet) {
      calls++;
    }
  }

  public static class CounterPart extends CheckPart<RuntimeCheck> {
    private int calls;

    public CounterPart(RuntimeCheck check) {
      super(check);
    }

    @OnReceive
    @OnSend
    public void onPacket(KeepAlivePacket packet) {
      calls++;
    }
  }

  public static final class InheritedPart extends CounterPart {
    public InheritedPart(RuntimeCheck check) {
      super(check);
    }
  }

  public static final class IndependentPart extends CounterPart {
    public IndependentPart(RuntimeCheck check) {
      super(check);
    }

    @Override
    public boolean enabled() {
      return true;
    }
  }

  public static final class PerPlayerPart extends PlayerCheckPart<RuntimeCheck> {
    private int calls;

    public PerPlayerPart(User user, RuntimeCheck check) {
      super(user, check);
    }

    @OnReceive
    @OnSend
    public void onPacket(KeepAlivePacket packet) {
      calls++;
    }
  }
}
