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

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.OneForAll;
import de.jpx3.intave.module.linker.OneForOne;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PlayerPacketEventSubscriber;
import de.jpx3.intave.user.User;

import java.lang.reflect.Method;

public final class PacketPerfectSubscriptionLinker extends Module {

	@Override
	public void enable() {

	}

	@Override
	public void disable() {

	}

	public void linkSubscriptionsIn(PacketEventSubscriber subscriber) {
		SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProvider = instanceProviderFor(subscriber);
		for (Method declaredMethod : subscriber.getClass().getDeclaredMethods()) {
			if (eligible(declaredMethod)) {
				linkSubscription(instanceProvider, declaredMethod);
			}
		}
	}

	public void linkSubscription(SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider, Method method) {
		if (method.isAnnotationPresent(OnReceive.class)) {
			OnReceive annotation = method.getAnnotation(OnReceive.class);
			linkReceiveSubscription(provider, method, annotation.priority(), annotation.stage());
		}
		if (method.isAnnotationPresent(OnSend.class)) {
			OnSend annotation = method.getAnnotation(OnSend.class);
			linkSendSubscription(provider, method, annotation.priority(), annotation.stage());
		}
	}

	public boolean eligible(Method method) {
		boolean annotated = method.isAnnotationPresent(OnReceive.class)
			|| method.isAnnotationPresent(OnSend.class);
		boolean atLeastOneParameter = method.getParameterCount() >= 1;
		return annotated && atLeastOneParameter;
	}

	private SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProviderFor(PacketEventSubscriber subscriber) {
		if (subscriber instanceof PlayerPacketEventSubscriber) {
			PlayerPacketEventSubscriber playerListener = (PlayerPacketEventSubscriber) subscriber;
			return new OneForOne<>(playerListener::packetSubscriberFor);
		} else {
			return new OneForAll<>(subscriber);
		}
	}
}
