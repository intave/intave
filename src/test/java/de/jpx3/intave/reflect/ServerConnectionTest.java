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

package de.jpx3.intave.reflect;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

final class ServerConnectionTest {
  private final List<EmbeddedChannel> allocatedChannels = new ArrayList<>();
  private Field bukkitServerField;
  private Object previousBukkitServer;

  @BeforeEach
  void saveBukkitServer() throws ReflectiveOperationException {
    this.bukkitServerField = Bukkit.class.getDeclaredField("server");
    this.bukkitServerField.setAccessible(true);
    this.previousBukkitServer = this.bukkitServerField.get(null);
    this.bukkitServerField.set(null, null);
  }

  @AfterEach
  void restoreBukkitServerAndReleaseChannels() throws ReflectiveOperationException {
    this.bukkitServerField.set(null, this.previousBukkitServer);
    for (EmbeddedChannel channel : this.allocatedChannels) {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void failsClearlyBeforeBukkitInitialization() {
    IllegalStateException failure = assertThrows(IllegalStateException.class, ServerConnection::find);
    assertEquals("Bukkit has not initialized its server", failure.getMessage());
  }

  @Test
  void reportsCraftServerTypeWhenMinecraftServerIsMissing() throws Exception {
    Server craftServer = installCraftServer(null);
    IllegalStateException failure = assertThrows(IllegalStateException.class, ServerConnection::find);
    assertEquals("Could not find MinecraftServer from " + craftServer.getClass().getName(), failure.getMessage());
  }

  @Test
  void reportsMinecraftServerTypeWhenConnectionIsMissing() throws Exception {
    installCraftServer(new MinecraftServer());
    IllegalStateException failure = assertThrows(IllegalStateException.class, ServerConnection::find);
    assertEquals("Could not find ServerConnection from " + MinecraftServer.class.getName(), failure.getMessage());
  }

  @Test
  void findsLegacyConnectionThroughPrivateField() throws Exception {
    ChannelFuture listening = channelFuture();
    installCraftServer(new MinecraftServer() {
      private final Legacy.ServerConnection connection = new Legacy.ServerConnection(listening);
    });
    assertEquals(channels(listening), ServerConnection.find().listeningChannels());
  }

  @Test
  void findsModernConnectionThroughPrivateGetterAndInheritedInterfaceName() throws Exception {
    ChannelFuture listening = channelFuture();
    installCraftServer(new MinecraftServer() {
      private ModernConnection connection() {
        return new ModernConnection(listening);
      }
    });
    assertEquals(channels(listening), ServerConnection.find().listeningChannels());
  }

  @Test
  void findsPrivateMembersInServerAndConnectionSuperclasses() throws Exception {
    ChannelFuture listening = channelFuture();
    class BaseServer extends MinecraftServer {
      private final DerivedLegacyConnection connection = new DerivedLegacyConnection(listening);
    }
    installCraftServer(new BaseServer() { });
    assertEquals(channels(listening), ServerConnection.find().listeningChannels());
  }

  @Test
  void findsPrivateGetterInServerSuperclass() throws Exception {
    ChannelFuture listening = channelFuture();
    class BaseServer extends MinecraftServer {
      private Legacy.ServerConnection connection() {
        return new Legacy.ServerConnection(listening);
      }
    }
    installCraftServer(new BaseServer() { });
    assertEquals(channels(listening), ServerConnection.find().listeningChannels());
  }

  @Test
  void prefersGettersOverFieldsEvenWhenGetterIsInherited() throws Exception {
    ChannelFuture getterChannel = channelFuture();
    ChannelFuture fieldChannel = channelFuture();
    class BaseServer extends MinecraftServer {
      private Legacy.ServerConnection connection() {
        return new Legacy.ServerConnection(getterChannel);
      }
    }
    installCraftServer(new BaseServer() {
      private final Legacy.ServerConnection connection = new Legacy.ServerConnection(fieldChannel);
    });
    assertEquals(channels(getterChannel), ServerConnection.find().listeningChannels());
  }

  @Test
  void fallsBackToFieldWhenGetterReturnsNull() throws Exception {
    ChannelFuture listening = channelFuture();
    installCraftServer(new MinecraftServer() {
      private final Legacy.ServerConnection connection = new Legacy.ServerConnection(listening);

      private Legacy.ServerConnection connection() {
        return null;
      }
    });
    assertEquals(channels(listening), ServerConnection.find().listeningChannels());
  }

  @Test
  void fallsBackToFieldWhenGetterThrows() throws Exception {
    ChannelFuture listening = channelFuture();
    installCraftServer(new MinecraftServer() {
      private final Legacy.ServerConnection connection = new Legacy.ServerConnection(listening);

      private Legacy.ServerConnection connection() {
        throw new IllegalStateException("server is still starting");
      }
    });
    assertEquals(channels(listening), ServerConnection.find().listeningChannels());
  }

  @Test
  void continuesToSuperclassAfterNullGetterAndField() throws Exception {
    ChannelFuture listening = channelFuture();
    class BaseServer extends MinecraftServer {
      private final Legacy.ServerConnection connection = new Legacy.ServerConnection(listening);
    }
    installCraftServer(new BaseServer() {
      private final Legacy.ServerConnection connection = null;

      private Legacy.ServerConnection unavailable() {
        return null;
      }
    });
    assertEquals(channels(listening), ServerConnection.find().listeningChannels());
  }

  @Test
  void ignoresStaticMembersAndGettersRequiringArguments() throws Exception {
    installCraftServer(new DecoyServer());
    assertThrows(IllegalStateException.class, ServerConnection::find);
  }

  @Test
  void doesNotSelectMembersByNameOrRuntimeValueAlone() throws Exception {
    installCraftServer(new MinecraftServer() {
      private final Object serverConnection = new Legacy.ServerConnection();

      private Object getServerConnection() {
        throw new AssertionError("Unrelated return types must not be invoked");
      }
    });
    assertThrows(IllegalStateException.class, ServerConnection::find);
  }

  @Test
  void doesNotCacheConnectionAcrossFindCalls() throws Exception {
    ChannelFuture first = channelFuture();
    ChannelFuture second = channelFuture();
    class MutableServer extends MinecraftServer {
      private Legacy.ServerConnection connection = new Legacy.ServerConnection(first);
    }
    MutableServer server = new MutableServer();
    installCraftServer(server);
    ServerConnection oldConnection = ServerConnection.find();
    server.connection = new Legacy.ServerConnection(second);
    assertEquals(channels(second), ServerConnection.find().listeningChannels());
    assertEquals(channels(first), oldConnection.listeningChannels());
  }

  @Test
  void emptyHandleHasNoListeningChannels() throws Exception {
    assertTrue(wrap(new Object()).listeningChannels().isEmpty());
  }

  @Test
  void readsAllPrivateTypedListsAcrossHierarchy() throws Exception {
    ChannelFuture first = channelFuture();
    ChannelFuture second = channelFuture();
    class BaseConnection {
      private final List<ChannelFuture> inherited = Collections.singletonList(first);
    }
    Object handle = new BaseConnection() {
      private final List<ChannelFuture> own = Collections.singletonList(second);
    };
    List<Channel> actual = wrap(handle).listeningChannels();
    assertEquals(2, actual.size());
    assertTrue(actual.containsAll(channels(first, second)));
  }

  @Test
  void preservesOrderWithinListeningList() throws Exception {
    ChannelFuture first = channelFuture();
    ChannelFuture second = channelFuture();
    ChannelFuture third = channelFuture();
    assertEquals(channels(first, second, third), wrap(new Legacy.ServerConnection(first, second, third)).listeningChannels());
  }

  @Test
  void acceptsConcreteListFieldTypes() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final ArrayList<ChannelFuture> futures = new ArrayList<>(Collections.singletonList(listening));
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void ignoresStaticListsAndNonListFields() throws Exception {
    ChannelFuture listening = channelFuture();
    class DecoyConnection {
      private static final List<ChannelFuture> staticFutures = new FailOnIterationList<>();
      private final Object hiddenList = Collections.singletonList(listening);
      private final ChannelFuture[] array = {listening};
      private final ChannelFuture future = listening;
    }
    assertTrue(wrap(new DecoyConnection()).listeningChannels().isEmpty());
  }

  @Test
  void handlesNullAndEmptyTypedLists() throws Exception {
    Object handle = new Object() {
      private final List<ChannelFuture> absent = null;
      private final List<ChannelFuture> empty = Collections.emptyList();
    };
    assertTrue(wrap(handle).listeningChannels().isEmpty());
  }

  @Test
  void typedListsTakePrecedenceOverRuntimeFallbackLists() throws Exception {
    ChannelFuture listening = channelFuture();
    ChannelFuture decoy = channelFuture();
    Object handle = new Object() {
      private final List<Object> unrelated = Collections.singletonList(decoy);
      private final List<ChannelFuture> listeners = Collections.singletonList(listening);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void emptyTypedListStillSuppressesRuntimeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    Object handle = new Object() {
      private final List<Object> unrelated = Collections.singletonList(decoy);
      private final List<ChannelFuture> listeners = Collections.emptyList();
    };
    assertTrue(wrap(handle).listeningChannels().isEmpty());
  }

  @Test
  void nullTypedListStillSuppressesRuntimeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    Object handle = new Object() {
      private final List<Object> unrelated = Collections.singletonList(decoy);
      private final List<ChannelFuture> listeners = null;
    };
    assertTrue(wrap(handle).listeningChannels().isEmpty());
  }

  @Test
  @SuppressWarnings("rawtypes")
  void fallsBackToRawWildcardAndObjectLists() throws Exception {
    ChannelFuture first = channelFuture();
    ChannelFuture second = channelFuture();
    ChannelFuture third = channelFuture();
    Object handle = new Object() {
      private final List raw = Arrays.asList("not a future", null, first);
      private final List<?> wildcard = Arrays.asList(null, second, "not a future");
      private final List<Object> objects = Arrays.asList("not a future", third, null);
      private final List<?> empty = Collections.emptyList();
      private final List<?> absent = null;
    };
    List<Channel> actual = wrap(handle).listeningChannels();
    assertEquals(3, actual.size());
    assertTrue(actual.containsAll(channels(first, second, third)));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void skipsNullAndForeignElementsEvenInDeclaredFutureLists() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<ChannelFuture> listeners = (List) Arrays.asList(null, "foreign", listening, 12);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void returnsIndependentSnapshotsAndObservesLaterListReplacement() throws Exception {
    ChannelFuture first = channelFuture();
    ChannelFuture second = channelFuture();
    Legacy.ServerConnection handle = new Legacy.ServerConnection(first);
    ServerConnection connection = wrap(handle);
    List<Channel> snapshot = connection.listeningChannels();
    handle.listeners = new ArrayList<>(Collections.singletonList(second));
    List<Channel> next = connection.listeningChannels();
    assertEquals(channels(first), snapshot);
    assertEquals(channels(second), next);
    assertNotSame(snapshot, next);
    next.clear();
    assertEquals(channels(second), connection.listeningChannels());
    handle.listeners.clear();
    assertTrue(connection.listeningChannels().isEmpty());
    assertEquals(channels(first), snapshot);
  }

  @Test
  void doesNotRequireSuccessfulOrCompletedFutures() throws Exception {
    EmbeddedChannel channel = newChannel();
    ChannelPromise pending = channel.newPromise();
    ChannelFuture failed = channel.newFailedFuture(new IllegalStateException("bind failed"));
    assertEquals(Arrays.asList(channel, channel), wrap(new Legacy.ServerConnection(pending, failed)).listeningChannels());
    assertFalse(pending.isDone());
  }

  @Test
  void locksTypedListWhileIterating() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<ChannelFuture> listeners = new LockCheckingList<>(Collections.singletonList(listening));
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void locksFallbackListDuringDetectionAndExtraction() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<Object> listeners = new LockCheckingList<>(Arrays.asList(null, "decoy", listening));
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void toleratesUninspectableFallbackLists() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<?> broken = new FailOnIterationList<>();
      private final List<?> listeners = Collections.singletonList(listening);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void doesNotHideFailureReadingKnownListeningList() throws Exception {
    Object handle = new Object() {
      private final List<ChannelFuture> listeners = new FailOnIterationList<>();
    };
    assertThrows(UnsupportedOperationException.class, () -> wrap(handle).listeningChannels());
  }

  @Test
  void concurrentUpdatesNeverExposeHalfWrittenListeningLists() throws Exception {
    ChannelFuture first = channelFuture();
    ChannelFuture second = channelFuture();
    List<ChannelFuture> listeners = Collections.synchronizedList(new ArrayList<>());
    listeners.addAll(Arrays.asList(first, second));
    Object handle = new Object() {
      private final List<ChannelFuture> futures = listeners;
    };
    ServerConnection connection = wrap(handle);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<?> writer = executor.submit(() -> {
        await(start);
        for (int i = 0; i < 5_000; i++) {
          synchronized (listeners) {
            listeners.clear();
            listeners.add(first);
            Thread.yield();
            listeners.add(second);
          }
        }
      });
      start.countDown();
      for (int i = 0; i < 5_000; i++) {
        assertEquals(channels(first, second), connection.listeningChannels());
      }
      writer.get(10, TimeUnit.SECONDS);
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void recognizesWildcardFutureDeclarationsBeforeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    Object handle = new Object() {
      private final List<? extends ChannelFuture> listeners = Collections.emptyList();
      private final List<?> unrelated = Collections.singletonList(decoy);
    };
    assertTrue(wrap(handle).listeningChannels().isEmpty());
  }

  @Test
  void recognizesFutureSubtypeDeclarationsBeforeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    Object handle = new Object() {
      private final List<ChannelPromise> listeners = Collections.emptyList();
      private final List<?> unrelated = Collections.singletonList(decoy);
    };
    assertTrue(wrap(handle).listeningChannels().isEmpty());
  }

  @Test
  void nestedFutureGenericDoesNotSuppressRealFallbackList() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<List<ChannelFuture>> nested = Collections.singletonList(Collections.singletonList(listening));
      private final List<?> listeners = Collections.singletonList(listening);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void futureArrayGenericDoesNotSuppressRealFallbackList() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<ChannelFuture[]> arrays = Collections.singletonList(new ChannelFuture[]{listening});
      private final List<?> listeners = Collections.singletonList(listening);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void recognizesFutureBoundedTypeVariablesBeforeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    class GenericConnection<T extends ChannelFuture> {
      private final List<T> listeners = Collections.emptyList();
      private final List<?> unrelated = Collections.singletonList(decoy);
    }
    assertTrue(wrap(new GenericConnection<ChannelFuture>()).listeningChannels().isEmpty());
  }

  @Test
  void recursiveUnrelatedTypeBoundsDoNotPreventFallback() throws Exception {
    ChannelFuture listening = channelFuture();
    class GenericConnection<T extends Comparable<T>> {
      private final List<T> unrelated = Collections.emptyList();
      private final List<?> listeners = Collections.singletonList(listening);
    }
    assertEquals(channels(listening), wrap(new GenericConnection<String>()).listeningChannels());
  }

  @Test
  void recognizesParameterizedFutureSubtypesBeforeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    Object handle = new Object() {
      private final List<GenericFuture<String>> listeners = Collections.emptyList();
      private final List<?> unrelated = Collections.singletonList(decoy);
    };
    assertTrue(wrap(handle).listeningChannels().isEmpty());
  }

  @Test
  void wildcardNestedListDoesNotSuppressRealFallbackList() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<? extends List<ChannelFuture>> nested = Collections.emptyList();
      private final List<?> listeners = Collections.singletonList(listening);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void genericFutureArraysDoNotSuppressRealFallbackList() throws Exception {
    ChannelFuture listening = channelFuture();
    class GenericConnection<T extends ChannelFuture> {
      private final List<T[]> arrays = Collections.emptyList();
      private final List<?> listeners = Collections.singletonList(listening);
    }
    assertEquals(channels(listening), wrap(new GenericConnection<ChannelFuture>()).listeningChannels());
  }

  @Test
  void lowerBoundDoesNotGuaranteeFutureElements() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<? super ChannelFuture> objects = Collections.singletonList("not a future");
      private final List<?> listeners = Collections.singletonList(listening);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void lowerBoundListCanStillBeDiscoveredByItsContents() throws Exception {
    ChannelFuture listening = channelFuture();
    Object handle = new Object() {
      private final List<? super ChannelFuture> listeners = Arrays.asList("decoy", listening);
    };
    assertEquals(channels(listening), wrap(handle).listeningChannels());
  }

  @Test
  void recognizesFutureInIntersectionBoundsBeforeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    class GenericConnection<T extends Runnable & ChannelFuture> {
      private final List<T> listeners = Collections.emptyList();
      private final List<?> unrelated = Collections.singletonList(decoy);
    }
    assertTrue(wrap(new GenericConnection<>()).listeningChannels().isEmpty());
  }

  @Test
  void recognizesTransitiveTypeVariableBoundsBeforeFallback() throws Exception {
    ChannelFuture decoy = channelFuture();
    class GenericConnection<T extends ChannelFuture, U extends T> {
      private final List<U> listeners = Collections.emptyList();
      private final List<?> unrelated = Collections.singletonList(decoy);
    }
    assertTrue(wrap(new GenericConnection<ChannelFuture, ChannelFuture>()).listeningChannels().isEmpty());
  }

  private Server installCraftServer(MinecraftServer minecraftServer) throws IllegalAccessException {
    Server craftServer = (Server) Proxy.newProxyInstance(
      getClass().getClassLoader(), new Class<?>[]{Server.class, MinecraftServerAccess.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("getMinecraftServer")) {
          return minecraftServer;
        }
        throw new AssertionError("Unexpected Bukkit call: " + method);
      });
    this.bukkitServerField.set(null, craftServer);
    return craftServer;
  }

  // Construct only the reflection adapter; no server sockets or Minecraft runtime are needed.
  private static ServerConnection wrap(Object handle) throws ReflectiveOperationException {
    Constructor<ServerConnection> constructor = ServerConnection.class.getDeclaredConstructor(Object.class);
    constructor.setAccessible(true);
    return constructor.newInstance(handle);
  }

  private EmbeddedChannel newChannel() {
    EmbeddedChannel channel = new EmbeddedChannel();
    this.allocatedChannels.add(channel);
    return channel;
  }

  private ChannelFuture channelFuture() {
    return newChannel().newSucceededFuture();
  }

  private static List<Channel> channels(ChannelFuture... futures) {
    List<Channel> channels = new ArrayList<>();
    for (ChannelFuture future : futures) {
      channels.add(future.channel());
    }
    return channels;
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for concurrent reader");
      }
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new AssertionError(failure);
    }
  }

  public interface MinecraftServerAccess {
    MinecraftServer getMinecraftServer();
  }

  public static class MinecraftServer {
  }

  private interface GenericFuture<T> extends ChannelFuture {
  }

  private interface ServerConnectionListener {
  }

  private interface ExtendedListener extends ServerConnectionListener {
  }

  private static final class ModernConnection implements ExtendedListener {
    private final List<ChannelFuture> listeners;

    private ModernConnection(ChannelFuture... futures) {
      this.listeners = Arrays.asList(futures);
    }
  }

  private static final class Legacy {
    private static class ServerConnection {
      private List<ChannelFuture> listeners;

      private ServerConnection(ChannelFuture... futures) {
        this.listeners = new ArrayList<>(Arrays.asList(futures));
      }
    }
  }

  private static final class DerivedLegacyConnection extends Legacy.ServerConnection {
    private DerivedLegacyConnection(ChannelFuture... futures) {
      super(futures);
    }
  }

  private static final class DecoyServer extends MinecraftServer {
    private static final Legacy.ServerConnection connection = new Legacy.ServerConnection();

    private static Legacy.ServerConnection staticConnection() {
      throw new AssertionError("Static getter must not be invoked");
    }

    private Legacy.ServerConnection parameterizedConnection(int ignored) {
      throw new AssertionError("Getter with arguments must not be invoked");
    }
  }

  private static final class LockCheckingList<T> extends AbstractList<T> {
    private final List<T> elements;

    private LockCheckingList(List<T> elements) {
      this.elements = elements;
    }

    @Override
    public Iterator<T> iterator() {
      assertTrue(Thread.holdsLock(this), "List iteration must hold the list monitor");
      return super.iterator();
    }

    @Override
    public T get(int index) {
      assertTrue(Thread.holdsLock(this), "List reads must hold the list monitor");
      return this.elements.get(index);
    }

    @Override
    public int size() {
      assertTrue(Thread.holdsLock(this), "List reads must hold the list monitor");
      return this.elements.size();
    }
  }

  private static final class FailOnIterationList<T> extends AbstractList<T> {
    @Override
    public Iterator<T> iterator() {
      throw new UnsupportedOperationException("Cannot iterate this list");
    }

    @Override
    public T get(int index) {
      throw new AssertionError("Unexpected element read");
    }

    @Override
    public int size() {
      throw new AssertionError("Unexpected size read");
    }
  }
}
