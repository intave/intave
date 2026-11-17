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

import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.PlayerInputPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class PacketPerfectSubscriptionLinkerTest {
	private AtomicReference<Map<PacketType.Client, PacketSubscription[]>> subscriptions;
	private Map<PacketType.Client, PacketSubscription[]> savedSubscriptions;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void isolateSubscriptions() throws ReflectiveOperationException {
		Field field = PacketEventBus.class.getDeclaredField("CLIENT_SUBSCRIPTIONS");
		field.setAccessible(true);
		subscriptions = (AtomicReference<Map<PacketType.Client, PacketSubscription[]>>) field.get(null);
		savedSubscriptions = subscriptions.getAndSet(new EnumMap<>(PacketType.Client.class));
	}

	@AfterEach
	void restoreSubscriptions() {
		subscriptions.set(savedSubscriptions);
	}

	@Test
	void dispatchesInputOncePerPacketAcrossProtocolVersions() {
		InputSubscriber subscriber = new InputSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		List<PlayerInputPacket> expected = new ArrayList<>();

		for (int protocolVersion : new int[] {47, 767, 768, 776}) {
			PlayerInputPacket packet = WirePackets.createPlayPacket(PacketType.Client.PLAYER_INPUT, protocolVersion);
			expected.add(packet);
			PacketEventBus.dispatch(new PacketReceiveEvent(null, PacketType.Client.PLAYER_INPUT, packet));
			assertEquals(expected, subscriber.received, "Input delivery for protocol " + protocolVersion);
		}
	}

	@Test
	void retainsEveryDistinctPacketTypeMatchedByAContract() {
		ContractSubscriber subscriber = new ContractSubscriber();
		new PacketPerfectSubscriptionLinker().linkSubscriptionsIn(subscriber);
		PacketContract input = WirePackets.createPlayPacket(PacketType.Client.PLAYER_INPUT, 768);
		PacketContract abilities = WirePackets.createPlayPacket(PacketType.Client.ABILITIES_IN, 768);

		PacketEventBus.dispatch(new PacketReceiveEvent(null, PacketType.Client.PLAYER_INPUT, input));
		PacketEventBus.dispatch(new PacketReceiveEvent(null, PacketType.Client.ABILITIES_IN, abilities));
		PacketEventBus.dispatch(new PacketReceiveEvent(null, PacketType.Client.PLAYER_INPUT, input));

		assertEquals(Arrays.asList(input, abilities, input), subscriber.received);
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
