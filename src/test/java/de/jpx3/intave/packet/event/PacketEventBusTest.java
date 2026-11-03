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

package de.jpx3.intave.packet.event;

import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.ListenerPriority;
import de.jpx3.intave.packet.ListenerSlot;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

final class PacketEventBusTest {
	private static final PacketType.Server ROOT = PacketType.Server.KEEP_ALIVE_OUT;
	private static final PacketType.Server CHILD = PacketType.Server.TRANSACTION_OUT;
	private AtomicReference<Map<PacketType.Server, PacketSubscription[]>> subscriptions;
	private Map<PacketType.Server, PacketSubscription[]> savedSubscriptions;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void isolateSubscriptions() throws ReflectiveOperationException {
		Field field = PacketEventBus.class.getDeclaredField("SERVER_SUBSCRIPTIONS");
		field.setAccessible(true);
		subscriptions = (AtomicReference<Map<PacketType.Server, PacketSubscription[]>>) field.get(null);
		savedSubscriptions = subscriptions.getAndSet(new EnumMap<>(PacketType.Server.class));
	}

	@AfterEach
	void restoreSubscriptions() {
		subscriptions.set(savedSubscriptions);
	}

	@Test
	void returnsTheOriginalPacketWithoutSubscriptions() {
		PacketContract root = packet("root");
		assertEquals(Collections.singletonList(root.typedTo(ROOT)), PacketEventBus.dispatch(event(root)));
	}

	@Test
	void processesPrequeuedPacketsWithoutSubscriptions() {
		PacketContract root = packet("root");
		PacketContract before = packet("before");
		PacketContract after = packet("after");
		PacketSendEvent event = event(root);
		event.addSendBefore(before.typedTo(CHILD));
		event.addSendAfter(after.typedTo(CHILD));

		assertEquals(Arrays.asList(before.typedTo(CHILD), root.typedTo(ROOT), after.typedTo(CHILD)), PacketEventBus.dispatch(event));
	}

	@Test
	void dispatchesInsertionsOnceIncludingThoseFromTheLastMutableListener() {
		PacketContract root = packet("root");
		PacketContract beforeFirst = packet("before first");
		PacketContract beforeLast = packet("before last");
		PacketContract afterFirst = packet("after first");
		PacketContract afterLast = packet("after last");
		List<PacketContract> observed = new ArrayList<>();
		on(CHILD, ListenerPriority.NORMAL, ListenerSlot.AFTER_IMMUTABLE, child -> {
			assertEquals(CHILD, child.packetType());
			assertEquals(PacketDirection.CLIENTBOUND, child.direction());
			observed.add(child.packet());
		});
		on(ROOT, ListenerPriority.FIRST, parent -> {
			parent.addSendBefore(beforeFirst.typedTo(CHILD));
			parent.addSendAfter(afterFirst.typedTo(CHILD));
		});
		on(ROOT, ListenerPriority.NORMAL, parent ->
			assertEquals(Collections.singletonList(beforeFirst), observed));
		on(ROOT, ListenerPriority.LAST, parent -> {
			parent.addSendBefore(beforeLast.typedTo(CHILD));
			parent.addSendAfter(afterLast.typedTo(CHILD));
		});
		on(ROOT, ListenerPriority.NORMAL, ListenerSlot.AFTER_IMMUTABLE,
			parent -> observed.add(parent.packet()));

		List<PacketContract> expected = Arrays.asList(beforeFirst, beforeLast, root, afterFirst, afterLast);
		assertEquals(Arrays.asList(beforeFirst.typedTo(CHILD), beforeLast.typedTo(CHILD), root.typedTo(ROOT),
			afterFirst.typedTo(CHILD), afterLast.typedTo(CHILD)), PacketEventBus.dispatch(event(root)));
		assertEquals(expected, observed);
	}

	@Test
	void processesPacketsQueuedByTheFinalSubscription() {
		PacketContract root = packet("root");
		PacketContract before = packet("before");
		PacketContract after = packet("after");
		on(ROOT, ListenerPriority.LAST, parent -> {
			parent.addSendBefore(before.typedTo(CHILD));
			parent.addSendAfter(after.typedTo(CHILD));
		});

		assertEquals(Arrays.asList(before.typedTo(CHILD), root.typedTo(ROOT), after.typedTo(CHILD)), PacketEventBus.dispatch(event(root)));
	}

	@Test
	void flattensNestedBeforeAndAfterPacketsInSendOrder() {
		PacketContract root = packet("root");
		PacketContract child = packet("child");
		PacketContract nestedBefore = packet("nested before");
		PacketContract nestedAfter = packet("nested after");
		PacketContract after = packet("after");
		PacketSendEvent event = event(root);
		event.addSendBefore(child.typedTo(CHILD));
		event.addSendAfter(after.typedTo(CHILD));
		on(CHILD, ListenerPriority.NORMAL, current -> {
			if (current.packet() == child) {
				current.addSendBefore(nestedBefore.typedTo(CHILD));
				current.addSendAfter(nestedAfter.typedTo(CHILD));
			}
		});

		assertEquals(Arrays.asList(nestedBefore.typedTo(CHILD), child.typedTo(CHILD), nestedAfter.typedTo(CHILD),
			root.typedTo(ROOT), after.typedTo(CHILD)),
			PacketEventBus.dispatch(event));
	}

	@Test
	void returnsTheFinalReplacementPacket() {
		PacketContract root = packet("root");
		PacketContract replacement = packet("replacement");
		on(ROOT, ListenerPriority.FIRST, parent -> parent.setPacket(replacement));
		on(ROOT, ListenerPriority.LAST, parent -> assertSame(replacement, parent.packet()));

		assertEquals(Collections.singletonList(replacement.typedTo(ROOT)), PacketEventBus.dispatch(event(root)));
	}

	@Test
	void cancellationOmitsOnlyTheCancelledPacket() {
		PacketContract root = packet("root");
		PacketContract before = packet("before");
		PacketContract cancelledChild = packet("cancelled child");
		PacketContract after = packet("after");
		on(ROOT, ListenerPriority.NORMAL, parent -> {
			parent.addSendBefore(before.typedTo(CHILD));
			parent.addSendBefore(cancelledChild.typedTo(CHILD));
			parent.addSendAfter(after.typedTo(CHILD));
			parent.setCancelled(true);
		});
		on(CHILD, ListenerPriority.NORMAL, child -> child.setCancelled(child.packet() == cancelledChild));

		assertEquals(Arrays.asList(before.typedTo(CHILD), after.typedTo(CHILD)), PacketEventBus.dispatch(event(root)));
	}

	@Test
	void cancellationWithNoInsertionsReturnsAnEmptyList() {
		PacketSendEvent event = event(packet("root"));
		event.setCancelled(true);

		assertTrue(PacketEventBus.dispatch(event).isEmpty());
	}

	@Test
	void laterListenersCanUndoCancellation() {
		PacketContract root = packet("root");
		on(ROOT, ListenerPriority.FIRST, parent -> parent.setCancelled(true));
		on(ROOT, ListenerPriority.LAST, parent -> parent.setCancelled(false));

		assertEquals(Collections.singletonList(root.typedTo(ROOT)), PacketEventBus.dispatch(event(root)));
	}

	@Test
	void rejectsServerboundInsertions() {
		PacketSendEvent event = event(packet("root"));
		event.addSendBefore(packet("invalid").typedTo(PacketType.Client.KEEP_ALIVE_IN));

		assertThrows(IllegalArgumentException.class, () -> PacketEventBus.dispatch(event));
	}

	@Test
	void rejectsRecursivePacketGeneration() {
		on(ROOT, ListenerPriority.NORMAL, parent ->
			parent.addSendBefore(packet("recursive").typedTo(ROOT)));

		assertThrows(IllegalStateException.class, () -> PacketEventBus.dispatch(event(packet("root"))));
	}

	@Test
	void boundsTheTotalNumberOfGeneratedPackets() {
		PacketSendEvent event = event(packet("root"));
		for (int i = 0; i < 5000; i++) {
			event.addSendAfter(packet("child " + i).typedTo(CHILD));
		}

		assertThrows(IllegalStateException.class, () -> PacketEventBus.dispatch(event));
		assertEquals(1, PacketEventBus.dispatch(event(packet("next write"))).size());
	}

	private static PacketSendEvent event(PacketContract packet) {
		return new PacketSendEvent(null, ROOT, packet);
	}

	private static PacketContract packet(String name) {
		return new PacketContract() {
			@Override
			public boolean appliesTo(ConnectionState state, PacketType type) {
				return true;
			}

			@Override
			public String toString() {
				return name;
			}
		};
	}

	private static void on(PacketType.Server type, ListenerPriority priority, Consumer<PacketSendEvent> handler) {
		on(type, priority, ListenerSlot.PROCESSING_MUTABLE, handler);
	}

	private static void on(
		PacketType.Server type, ListenerPriority priority, ListenerSlot slot, Consumer<PacketSendEvent> handler
	) {
		PacketEventBus.addListener(new PacketSubscription() {
			@Override
			public void onPacket(PacketEvent event) {
				handler.accept((PacketSendEvent) event);
			}

			@Override
			public ListenerPriority priority() {
				return priority;
			}

			@Override
			public ListenerSlot slot() {
				return slot;
			}
		}, type);
	}
}
