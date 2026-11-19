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

package de.jpx3.intave.module.linker.packetperfect;

import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.packet.ListenerPriority;
import de.jpx3.intave.packet.ListenerStage;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.KeepAlivePacket;
import de.jpx3.intave.packet.contract.immutable.PlayerInputPacket;
import de.jpx3.intave.packet.contract.mutable.MutableKeepAlivePacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.connection.keepalive.KeepAliveWirePacket;
import de.jpx3.intave.packet.wire.connection.ping.PingWirePacket;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public final class PacketPerfectSubscriptionLinkerTest {
	private AtomicReference<Map<PacketType.Client, PacketSubscription[]>> subscriptions;
	private Map<PacketType.Client, PacketSubscription[]> savedSubscriptions;
	private AtomicReference<Map<PacketType.Server, PacketSubscription[]>> sendSubscriptions;
	private Map<PacketType.Server, PacketSubscription[]> savedSendSubscriptions;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void isolateSubscriptions() throws ReflectiveOperationException {
		Field field = PacketEventBus.class.getDeclaredField("CLIENT_SUBSCRIPTIONS");
		field.setAccessible(true);
		subscriptions = (AtomicReference<Map<PacketType.Client, PacketSubscription[]>>) field.get(null);
		savedSubscriptions = subscriptions.getAndSet(new EnumMap<>(PacketType.Client.class));
		Field sendField = PacketEventBus.class.getDeclaredField("SERVER_SUBSCRIPTIONS");
		sendField.setAccessible(true);
		sendSubscriptions = (AtomicReference<Map<PacketType.Server, PacketSubscription[]>>) sendField.get(null);
		savedSendSubscriptions = sendSubscriptions.getAndSet(new EnumMap<>(PacketType.Server.class));
	}

	@AfterEach
	void restoreSubscriptions() {
		subscriptions.set(savedSubscriptions);
		sendSubscriptions.set(savedSendSubscriptions);
	}

	@Test
	void dispatchesInputOncePerPacketAcrossProtocolVersions() {
		InputSubscriber subscriber = new InputSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		List<PlayerInputPacket> expected = new ArrayList<>();

		for (int protocolVersion : new int[] {47, 767, 768, 776}) {
			PlayerInputPacket packet = WirePackets.createPlayPacket(PacketType.Client.PLAYER_INPUT, protocolVersion);
			expected.add(packet);
			PacketEventBus.dispatch(new PacketReceiveEvent(user(true), PacketType.Client.PLAYER_INPUT, packet));
			assertEquals(expected, subscriber.received, "Input delivery for protocol " + protocolVersion);
		}
	}

	@Test
	void retainsEveryDistinctPacketTypeMatchedByAContract() {
		ContractSubscriber subscriber = new ContractSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		PacketContract input = WirePackets.createPlayPacket(PacketType.Client.PLAYER_INPUT, 768);
		PacketContract abilities = WirePackets.createPlayPacket(PacketType.Client.ABILITIES_IN, 768);

		PacketEventBus.dispatch(new PacketReceiveEvent(user(true), PacketType.Client.PLAYER_INPUT, input));
		PacketEventBus.dispatch(new PacketReceiveEvent(user(true), PacketType.Client.ABILITIES_IN, abilities));
		PacketEventBus.dispatch(new PacketReceiveEvent(user(true), PacketType.Client.PLAYER_INPUT, input));

		assertEquals(Arrays.asList(input, abilities, input), subscriber.received);
	}

	@Test
	void dispatchesSendSubscriptionsOncePerPacketAcrossVersionsAndOnlyOutbound() {
		KeepAliveSubscriber subscriber = new KeepAliveSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		List<KeepAlivePacket> expected = new ArrayList<>();

		for (int protocolVersion : new int[]{47, 340, 768, 776}) {
			KeepAlivePacket outbound = WirePackets.createPlayPacket(PacketType.Server.KEEP_ALIVE_OUT, protocolVersion);
			expected.add(outbound);
			assertEquals(Collections.singletonList(outbound.typedTo(PacketType.Server.KEEP_ALIVE_OUT)),
				PacketEventBus.dispatch(new PacketSendEvent(user(true), PacketType.Server.KEEP_ALIVE_OUT, outbound)));
			KeepAlivePacket inbound = WirePackets.createPlayPacket(PacketType.Client.KEEP_ALIVE_IN, protocolVersion);
			PacketEventBus.dispatch(new PacketReceiveEvent(user(true), PacketType.Client.KEEP_ALIVE_IN, inbound));
			assertEquals(expected, subscriber.received, "Send delivery for protocol " + protocolVersion);
		}
	}

	@Test
	void retainsEveryDistinctOutgoingPacketTypeMatchedByAContract() {
		SendContractSubscriber subscriber = new SendContractSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		List<PacketContract> expected = new ArrayList<>();
		for (PacketType.Server type : new PacketType.Server[]{
			PacketType.Server.KEEP_ALIVE_OUT, PacketType.Server.TRANSACTION_OUT, PacketType.Server.CLOSE_WINDOW
		}) {
			PacketContract packet = WirePackets.createPlayPacket(type, 47);
			expected.add(packet);
			PacketEventBus.dispatch(new PacketSendEvent(user(true), type, packet));
		}
		assertEquals(expected, subscriber.received);
	}

	@Test
	void ordersOutgoingObserversAndMutatorsByStageAndPriority() {
		OrderedSendSubscriber subscriber = new OrderedSendSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		KeepAliveWirePacket packet = new KeepAliveWirePacket(0);

		List<TypedPacketContract> result = PacketEventBus.dispatch(new PacketSendEvent(user(true), PacketType.Server.KEEP_ALIVE_OUT, packet));

		assertEquals(Arrays.asList("before:0", "first:0", "last:1", "after:2"), subscriber.calls);
		assertEquals(2, packet.id());
		assertEquals(Collections.singletonList(packet.typedTo(PacketType.Server.KEEP_ALIVE_OUT)), result);
	}

	@Test
	void injectsSendArgumentsAndAllowsCancellationWithExtraPackets() {
		SendArgumentsSubscriber subscriber = new SendArgumentsSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
			(proxy, method, arguments) -> null);
		User user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class},
			(proxy, method, arguments) -> {
				if (method.getName().equals("hasPlayer")) return true;
				return method.getName().equals("player") ? player : null;
			});
		KeepAliveWirePacket packet = new KeepAliveWirePacket(42);
		PacketSendEvent event = new PacketSendEvent(user, PacketType.Server.KEEP_ALIVE_OUT, packet);

		List<TypedPacketContract> result = PacketEventBus.dispatch(event);

		assertSame(event, subscriber.event);
		assertSame(event, subscriber.cancellable);
		assertSame(user, subscriber.user);
		assertSame(player, subscriber.player);
		assertSame(packet, subscriber.packet);
		assertTrue(event.isCancelled());
		assertEquals(Arrays.asList(subscriber.before.typedTo(PacketType.Server.PING), subscriber.after.typedTo(PacketType.Server.PING)), result);
	}

	@Test
	void allowsOutgoingPacketReplacementInMutableStage() {
		ReplacementSubscriber subscriber = new ReplacementSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		PacketSendEvent event = new PacketSendEvent(user(true), PacketType.Server.KEEP_ALIVE_OUT, new KeepAliveWirePacket());

		List<TypedPacketContract> result = PacketEventBus.dispatch(event);

		assertSame(subscriber.replacement, event.packet());
		assertEquals(Collections.singletonList(subscriber.replacement.typedTo(PacketType.Server.KEEP_ALIVE_OUT)), result);
	}

	@Test
	void rejectsEventsFromTheWrongDirection() {
		PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
		IllegalStateException sendFailure = assertThrows(IllegalStateException.class,
			() -> linker.linkSubscriptionsIn(new WrongSendEventSubscriber()));
		assertTrue(sendFailure.getMessage().contains("PacketReceiveEvent"));
		assertTrue(sendFailure.getMessage().contains("@OnSend"));
		assertThrows(IllegalStateException.class, () -> linker.linkSubscriptionsIn(new WrongReceiveEventSubscriber()));
	}

	@Test
	void rejectsOutgoingContractAndStageMismatches() {
		PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
		assertThrows(IllegalStateException.class, () -> linker.linkSubscriptionsIn(new MutableObserverSubscriber()));
		assertThrows(IllegalStateException.class, () -> linker.linkSubscriptionsIn(new ImmutableProcessorSubscriber()));
	}

	@Test
	void rejectsMutationByAnOutgoingImmutableObserver() {
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(new MutatingObserverSubscriber());
		PacketSendEvent event = new PacketSendEvent(user(true), PacketType.Server.KEEP_ALIVE_OUT, new KeepAliveWirePacket());
		assertThrows(UnsupportedOperationException.class, () -> PacketEventBus.dispatch(event));
	}

	@Test
	void unregistersOnlyTheExactSubscriberAndAllowsRelinkingWithoutDuplicates() {
		PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
		LifecycleSubscriber first = new LifecycleSubscriber();
		LifecycleSubscriber second = new LifecycleSubscriber();
		assertEquals(first, second);
		linker.linkSubscriptionsIn(first);
		linker.linkSubscriptionsIn(first);
		linker.linkSubscriptionsIn(second);
		dispatchBothDirections();
		assertEquals(2, first.calls);
		assertEquals(2, second.calls);

		linker.removeSubscriptionsOf(first);
		linker.removeSubscriptionsOf(first);
		dispatchBothDirections();
		assertEquals(2, first.calls);
		assertEquals(4, second.calls);

		linker.linkSubscriptionsIn(first);
		linker.linkSubscriptionsIn(first);
		dispatchBothDirections();
		assertEquals(4, first.calls);
		assertEquals(6, second.calls);
		linker.removeSubscriptionsOf(first);
		linker.removeSubscriptionsOf(second);
		assertTrue(subscriptions.get().isEmpty());
		assertTrue(sendSubscriptions.get().isEmpty());
	}

	@Test
	void disablesOnlySubscriptionsOwnedByThatLinker() {
		PacketPerfectSubscriptionLinker firstLinker = new PacketPerfectSubscriptionLinker();
		PacketPerfectSubscriptionLinker secondLinker = new PacketPerfectSubscriptionLinker();
		LifecycleSubscriber first = new LifecycleSubscriber();
		LifecycleSubscriber second = new LifecycleSubscriber();
		firstLinker.linkSubscriptionsIn(first);
		secondLinker.linkSubscriptionsIn(second);
		firstLinker.disable();
		firstLinker.disable();
		dispatchBothDirections();
		assertEquals(0, first.calls);
		assertEquals(2, second.calls);

		firstLinker.linkSubscriptionsIn(first);
		dispatchBothDirections();
		assertEquals(2, first.calls);
		assertEquals(4, second.calls);
		firstLinker.disable();
		secondLinker.disable();
		assertTrue(subscriptions.get().isEmpty());
		assertTrue(sendSubscriptions.get().isEmpty());
	}

	@Test
	void respectsRuntimeProcessorEnablement() {
		LifecycleSubscriber subscriber = new LifecycleSubscriber();
		PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
		linker.linkSubscriptionsIn(subscriber);
		dispatchBothDirections();
		subscriber.enabled = false;
		dispatchBothDirections();
		assertEquals(2, subscriber.calls);
		subscriber.enabled = true;
		linker.linkSubscriptionsIn(subscriber);
		dispatchBothDirections();
		assertEquals(4, subscriber.calls);
	}

	@Test
	void rollsBackPartialRegistrationWhenAMethodIsInvalid() {
		PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
		PartiallyInvalidSubscriber subscriber = new PartiallyInvalidSubscriber();
		for (int attempt = 0; attempt < 2; attempt++) {
			assertThrows(IllegalStateException.class, () -> linker.linkSubscriptionsIn(subscriber));
			assertTrue(subscriptions.get().isEmpty());
			assertTrue(sendSubscriptions.get().isEmpty());
		}
	}

	@Test
	void skipsSubscriptionsWithoutAPlayer() {
		LifecycleSubscriber subscriber = new LifecycleSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		for (User user : new User[]{null, user(false)}) {
			PacketEventBus.dispatch(new PacketReceiveEvent(user, PacketType.Client.KEEP_ALIVE_IN, new KeepAliveWirePacket()));
			PacketEventBus.dispatch(new PacketSendEvent(user, PacketType.Server.KEEP_ALIVE_OUT, new KeepAliveWirePacket()));
		}
		assertEquals(0, subscriber.calls);
		dispatchBothDirections();
		assertEquals(2, subscriber.calls);
	}

	private static User user(boolean hasPlayer) {
		return (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class},
			(proxy, method, arguments) -> method.getName().equals("hasPlayer") ? hasPlayer : null);
	}

	private static void dispatchBothDirections() {
		PacketEventBus.dispatch(new PacketReceiveEvent(user(true), PacketType.Client.KEEP_ALIVE_IN, new KeepAliveWirePacket()));
		PacketEventBus.dispatch(new PacketSendEvent(user(true), PacketType.Server.KEEP_ALIVE_OUT, new KeepAliveWirePacket()));
	}

	public static final class LifecycleSubscriber implements EventProcessor {
		private boolean enabled = true;
		private int calls;

		@OnReceive
		@OnSend
		public void onPacket(PacketContract packet) {
			calls++;
		}

		@Override
		public boolean shouldProcessEvents() {
			return enabled;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof LifecycleSubscriber;
		}

		@Override
		public int hashCode() {
			return 0;
		}
	}

	public static final class PartiallyInvalidSubscriber implements PacketEventSubscriber {
		@OnReceive
		@OnSend
		public void onPacket(KeepAlivePacket packet, PacketReceiveEvent event) {
		}
	}

	public static final class KeepAliveSubscriber implements PacketEventSubscriber {
		private final List<KeepAlivePacket> received = new ArrayList<>();

		@OnSend
		public void onKeepAlive(KeepAlivePacket packet) {
			received.add(packet);
		}
	}

	public static final class SendContractSubscriber implements PacketEventSubscriber {
		private final List<PacketContract> received = new ArrayList<>();

		@OnSend
		public void onPacket(PacketContract packet) {
			received.add(packet);
		}
	}

	public static final class OrderedSendSubscriber implements PacketEventSubscriber {
		private final List<String> calls = new ArrayList<>();

		@OnSend(stage = ListenerStage.BEFORE_IMMUTABLE, priority = ListenerPriority.LAST)
		public void before(KeepAlivePacket packet) {
			calls.add("before:" + packet.id());
		}

		@OnSend(priority = ListenerPriority.FIRST)
		public void first(MutableKeepAlivePacket packet) {
			calls.add("first:" + packet.id());
			packet.setId(1);
		}

		@OnSend(priority = ListenerPriority.LAST)
		public void last(MutableKeepAlivePacket packet) {
			calls.add("last:" + packet.id());
			packet.setId(2);
		}

		@OnSend(priority = ListenerPriority.FIRST)
		public void after(KeepAlivePacket packet) {
			calls.add("after:" + packet.id());
		}
	}

	public static final class SendArgumentsSubscriber implements PacketEventSubscriber {
		private PacketSendEvent event;
		private Cancellable cancellable;
		private User user;
		private Player player;
		private KeepAlivePacket packet;
		private final PingWirePacket before = new PingWirePacket(1);
		private final PingWirePacket after = new PingWirePacket(2);

		@OnSend
		public void onKeepAlive(PacketSendEvent event, Cancellable cancellable, User user, Player player, KeepAlivePacket packet) {
			this.event = event;
			this.cancellable = cancellable;
			this.user = user;
			this.player = player;
			this.packet = packet;
			cancellable.setCancelled(true);
			event.addSendBefore(before.typedTo(PacketType.Server.PING));
			event.addSendAfter(after.typedTo(PacketType.Server.PING));
		}
	}

	public static final class ReplacementSubscriber implements PacketEventSubscriber {
		private final KeepAliveWirePacket replacement = new KeepAliveWirePacket(42);

		@OnSend
		public void onKeepAlive(MutableKeepAlivePacket packet, PacketSendEvent event) {
			event.setPacket(replacement);
		}
	}

	public static final class WrongSendEventSubscriber implements PacketEventSubscriber {
		@OnSend
		public void onPacket(KeepAlivePacket packet, PacketReceiveEvent event) {
		}
	}

	public static final class WrongReceiveEventSubscriber implements PacketEventSubscriber {
		@OnReceive
		public void onPacket(KeepAlivePacket packet, PacketSendEvent event) {
		}
	}

	public static final class MutableObserverSubscriber implements PacketEventSubscriber {
		@OnSend(stage = ListenerStage.AFTER_IMMUTABLE)
		public void onPacket(MutableKeepAlivePacket packet) {
		}
	}

	public static final class ImmutableProcessorSubscriber implements PacketEventSubscriber {
		@OnSend(stage = ListenerStage.PROCESSING_MUTABLE)
		public void onPacket(KeepAlivePacket packet) {
		}
	}

	public static final class MutatingObserverSubscriber implements PacketEventSubscriber {
		@OnSend
		public void onPacket(KeepAlivePacket packet) {
			((MutableKeepAlivePacket) packet).setId(42);
		}
	}

	public static final class InputSubscriber implements PacketEventSubscriber {
		private final List<PlayerInputPacket> received = new ArrayList<>();

		@OnReceive
		public void onInput(User user, PlayerInputPacket input) {
			received.add(input);
		}
	}

	public static final class ContractSubscriber implements PacketEventSubscriber {
		private final List<PacketContract> received = new ArrayList<>();

		@OnReceive
		public void onPacket(PacketContract packet) {
			received.add(packet);
		}
	}
}
