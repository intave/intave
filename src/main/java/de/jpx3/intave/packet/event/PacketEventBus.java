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

import de.jpx3.intave.module.linker.packet.PacketId;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class PacketEventBus {
	// cow
	private static final AtomicReference<Map<PacketId.Client, PacketSubscription[]>> CLIENT_SUBSCRIPTIONS = new AtomicReference<>(new EnumMap<>(PacketId.Client.class));
	private static final AtomicReference<Map<PacketId.Server, PacketSubscription[]>> SERVER_SUBSCRIPTIONS = new AtomicReference<>(new EnumMap<>(PacketId.Server.class));

	public static void addListener(PacketSubscription subscription, PacketId.Client... packetId) {
		for (PacketId.Client id : packetId) {
			addListener(subscription, id);
		}
	}

	public static void addListener(PacketSubscription subscription, PacketId.Client packetId) {
		CLIENT_SUBSCRIPTIONS.updateAndGet(old -> {
			PacketSubscription[] subscriptions = old.get(packetId);
			if (subscriptions == null) {
				subscriptions = new PacketSubscription[0];
			}
			PacketSubscription[] newSubscriptions = Arrays.copyOf(subscriptions, subscriptions.length + 1);
			newSubscriptions[newSubscriptions.length - 1] = subscription;
			Arrays.sort(newSubscriptions);
			Map<PacketId.Client, PacketSubscription[]> newMap = new EnumMap<>(old);
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
}
