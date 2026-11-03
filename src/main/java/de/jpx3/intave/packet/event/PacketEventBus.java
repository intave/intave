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

import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class PacketEventBus {
	private static final int MAX_SEND_DEPTH = 64;
	private static final int MAX_SEND_PACKETS = 4096;

	// cow
	private static final AtomicReference<Map<PacketType.Client, PacketSubscription[]>> CLIENT_SUBSCRIPTIONS = new AtomicReference<>(new EnumMap<>(PacketType.Client.class));
	private static final AtomicReference<Map<PacketType.Server, PacketSubscription[]>> SERVER_SUBSCRIPTIONS = new AtomicReference<>(new EnumMap<>(PacketType.Server.class));

	public static void addListener(PacketSubscription subscription, PacketType.Client... packetId) {
		for (PacketType.Client id : packetId) {
			addListener(subscription, id);
		}
	}

	public static void addListener(PacketSubscription subscription, PacketType.Client packetId) {
		CLIENT_SUBSCRIPTIONS.updateAndGet(old -> {
			PacketSubscription[] subscriptions = old.get(packetId);
			if (subscriptions == null) {
				subscriptions = new PacketSubscription[0];
			}
			PacketSubscription[] newSubscriptions = Arrays.copyOf(subscriptions, subscriptions.length + 1);
			newSubscriptions[newSubscriptions.length - 1] = subscription;
			Arrays.sort(newSubscriptions);
			Map<PacketType.Client, PacketSubscription[]> newMap = new EnumMap<>(old);
			newMap.put(packetId, newSubscriptions);
			return newMap;
		});
	}

	public static void addListener(PacketSubscription subscription, PacketType.Server... packetId) {
		for (PacketType.Server id : packetId) {
			addListener(subscription, id);
		}
	}

	public static void addListener(PacketSubscription subscription, PacketType.Server packetId) {
		SERVER_SUBSCRIPTIONS.updateAndGet(old -> {
			PacketSubscription[] subscriptions = old.get(packetId);
			if (subscriptions == null) {
				subscriptions = new PacketSubscription[0];
			}
			PacketSubscription[] newSubscriptions = Arrays.copyOf(subscriptions, subscriptions.length + 1);
			newSubscriptions[newSubscriptions.length - 1] = subscription;
			Arrays.sort(newSubscriptions);
			Map<PacketType.Server, PacketSubscription[]> newMap = new EnumMap<>(old);
			newMap.put(packetId, newSubscriptions);
			return newMap;
		});
	}

	public static void dispatch(PacketReceiveEvent event) {
		PacketSubscription[] packetSubscriptions = CLIENT_SUBSCRIPTIONS.get().get(event.packetId());
		if (packetSubscriptions != null) {
			for (PacketSubscription subscription : packetSubscriptions) {
				subscription.onPacket(event);
			}
		}
	}

	public static List<TypedPacketContract> dispatch(PacketSendEvent event) {
		List<TypedPacketContract> packets = new ArrayList<>();
		dispatch(event, packets, 0, 0);
		return packets;
	}

	private static int dispatch(PacketSendEvent event, List<TypedPacketContract> result, int depth, int dispatched) {
		if (depth >= MAX_SEND_DEPTH || ++dispatched > MAX_SEND_PACKETS) {
			throw new IllegalStateException("Packet send expansion limit exceeded for " + event.packetType());
		}
		PacketSubscription[] subscriptions = SERVER_SUBSCRIPTIONS.get().get(event.packetType());
		int beforeIndex = 0;
		int subscriptionIndex = 0;
		while (true) {
			while (beforeIndex < event.sendBefore().size()) {
				dispatched = subDispatch(event, event.sendBefore().get(beforeIndex++), result, depth + 1, dispatched);
			}
			if (subscriptions == null || subscriptionIndex == subscriptions.length) {
				break;
			}
			subscriptions[subscriptionIndex++].onPacket(event);
		}
		if (!event.isCancelled()) {
			result.add(event.typedPacket());
		}
		for (int afterIndex = 0; afterIndex < event.sendAfter().size(); afterIndex++) {
			dispatched = subDispatch(event, event.sendAfter().get(afterIndex), result, depth + 1, dispatched);
		}
		return dispatched;
	}

	private static int subDispatch(
		PacketSendEvent parent, TypedPacketContract packet, List<TypedPacketContract> packets, int depth, int dispatched
	) {
		if (packet == null) {
			throw new IllegalArgumentException("Packet is null");
		}
		if (!(packet.type() instanceof PacketType.Server)) {
			throw new IllegalArgumentException("Expected a clientbound packet type, got " + packet.type());
		}
		PacketSendEvent event = new PacketSendEvent(
			parent.user(), (PacketType.Server) packet.type(), packet.contract()
		);
		return dispatch(event, packets, depth, dispatched);
	}
}
