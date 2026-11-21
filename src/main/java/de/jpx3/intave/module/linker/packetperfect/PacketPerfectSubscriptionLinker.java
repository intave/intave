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

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.OneForAll;
import de.jpx3.intave.module.linker.OneForOne;
import de.jpx3.intave.module.linker.SubscriptionInstanceProvider;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PlayerPacketEventSubscriber;
import de.jpx3.intave.packet.ListenerPriority;
import de.jpx3.intave.packet.ListenerStage;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.Mutable;
import de.jpx3.intave.packet.event.*;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.user.User;

import java.lang.reflect.Method;
import java.util.*;

public final class PacketPerfectSubscriptionLinker extends Module {
	private final Map<PacketEventSubscriber, List<PacketSubscription>> subscriptions = new IdentityHashMap<>();

	@Override
	public void enable() {

	}

	@Override
	public synchronized void disable() {
		for (List<PacketSubscription> registered : subscriptions.values()) {
			registered.forEach(PacketEventBus::removeListener);
		}
		subscriptions.clear();
	}

	public synchronized void linkSubscriptionsIn(PacketEventSubscriber subscriber) {
		if (subscriptions.containsKey(subscriber)) {
			return;
		}
		SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> instanceProvider = instanceProviderFor(subscriber);
		List<PacketSubscription> registered = new ArrayList<>();
		subscriptions.put(subscriber, registered);
		try {
			for (Method method : instanceProvider.type().getMethods()) {
				if (eligible(method)) {
					linkSubscription(instanceProvider, method, registered);
				}
			}
		} catch (RuntimeException | Error failure) {
			removeSubscriptionsOf(subscriber);
			throw failure;
		}
	}

	public synchronized void removeSubscriptionsOf(PacketEventSubscriber subscriber) {
		List<PacketSubscription> registered = subscriptions.remove(subscriber);
		if (registered != null) {
			registered.forEach(PacketEventBus::removeListener);
		}
	}

	public synchronized void linkSubscription(SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider, Method method) {
		List<PacketSubscription> registered = subscriptions.computeIfAbsent(provider.fallback(), ignored -> new ArrayList<>());
		linkSubscription(provider, method, registered);
	}

	private void linkSubscription(
		SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider, Method method,
		List<PacketSubscription> registered
	) {
		if (method.isAnnotationPresent(OnReceive.class)) {
			OnReceive annotation = method.getAnnotation(OnReceive.class);
			registered.add(linkReceiveSubscription(provider, method, annotation.priority(), annotation.stage()));
		}
		if (method.isAnnotationPresent(OnSend.class)) {
			OnSend annotation = method.getAnnotation(OnSend.class);
			registered.add(linkSendSubscription(provider, method, annotation.priority(), annotation.stage()));
		}
	}

	private PacketSubscription linkReceiveSubscription(
		SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider,
		Method method, ListenerPriority priority, ListenerStage stage
	) {
		MethodHandleSubscriptionExecutor executor = new MethodHandleSubscriptionExecutor(method);
		for (Class<?> parameterType : method.getParameterTypes()) {
			if (parameterType == PacketSendEvent.class) {
				throw new IllegalStateException("Subscription method " + method + " has a parameter of type PacketSendEvent, but it is annotated with @OnReceive");
			}
		}
		PacketSubscription subscription = createSubscription(provider, executor, priority, stage);
		Set<PacketType.Client> applicableTypes = matchingTypes(executor.nativeTargetType(), WirePackets.registeredClientPackets());
		if (applicableTypes.isEmpty()) {
			IntaveLogger.logger().warning("No registered client packets match @OnReceive subscription method " + method
				+ "; this handler will never be called for incoming packets.");
		}
		PacketEventBus.addListener(subscription, applicableTypes.toArray(new PacketType.Client[0]));
		return subscription;
	}

	private PacketSubscription linkSendSubscription(
		SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider,
		Method method, ListenerPriority priority, ListenerStage stage
	) {
		MethodHandleSubscriptionExecutor executor = new MethodHandleSubscriptionExecutor(method);
		for (Class<?> parameterType : method.getParameterTypes()) {
			if (parameterType == PacketReceiveEvent.class) {
				throw new IllegalStateException("Subscription method " + method + " has a parameter of type PacketReceiveEvent, but it is annotated with @OnSend");
			}
		}
		PacketSubscription subscription = createSubscription(provider, executor, priority, stage);
		Set<PacketType.Server> applicableTypes = matchingTypes(executor.nativeTargetType(), WirePackets.registeredServerPackets());
		if (applicableTypes.isEmpty()) {
			IntaveLogger.logger().warning("No registered server packets match @OnSend subscription method " + method
				+ "; this handler will never be called for outgoing packets.");
		}
		PacketEventBus.addListener(subscription, applicableTypes.toArray(new PacketType.Server[0]));
		return subscription;
	}

	private PacketSubscription createSubscription(
		SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider,
		MethodHandleSubscriptionExecutor executor, ListenerPriority priority, ListenerStage stage
	) {
		Method method = executor.calledMethod();
		Class<?> nativeTargetType = executor.nativeTargetType();
		if (nativeTargetType == null) {
			throw new IllegalStateException("Subscription method " + method + " must declare a packet contract parameter");
		}
		boolean mutableType = Mutable.class.isAssignableFrom(nativeTargetType);

		if (mutableType) {
			if (stage == ListenerStage.AUTOMATIC) {
				stage = ListenerStage.MUTABLE;
			}
			if (!stage.allowMutations()) {
				throw new IllegalStateException("Subscription method " + method + " is for a mutable packet type, but the stage " + stage + " does not allow mutations");
			}
		} else {
			if (stage == ListenerStage.MUTABLE) {
				throw new IllegalStateException("Subscription method " + method + " is for a non-mutable packet type, but the stage " + stage + " is for mutable packets");
			} else if (stage == ListenerStage.AUTOMATIC) {
				stage = ListenerStage.AFTER;
			}
		}

		ListenerStage finalStage = stage;
		return new PacketSubscription() {
			@Override
			public void onPacket(PacketEvent event) {
				boolean dirtyBefore = event.dirty();
				provider.apply(event.user(), subscriber -> {
					if (subscriber instanceof EventProcessor && !((EventProcessor) subscriber).shouldProcessEvents()) {
						return;
					}
					executor.invoke(subscriber, event);
				});
				if (!allowForPacketMutations() && !dirtyBefore && event.dirty()) {
					throw new UnsupportedOperationException("Packet mutation is not allowed for this subscription: " + method);
				}
			}

			@Override
			public ListenerPriority priority() {
				return priority;
			}

			@Override
			public ListenerStage stage() {
				return finalStage;
			}
		};
	}

	private <T extends PacketType> Set<T> matchingTypes(Class<?> target, Map<T, List<Class<?>>> registeredPackets) {
		Set<T> matchingTypes = new HashSet<>();
		for (Map.Entry<T, List<Class<?>>> entry : registeredPackets.entrySet()) {
			for (Class<?> packetClass : entry.getValue()) {
				if (target.isAssignableFrom(packetClass)) {
					matchingTypes.add(entry.getKey());
				}
			}
		}
		return matchingTypes;
	}

	private boolean eligible(Method method) {
		boolean annotated = method.isAnnotationPresent(OnReceive.class)
			|| method.isAnnotationPresent(OnSend.class);
		boolean returnsVoid = method.getReturnType() == void.class;
		boolean atLeastOneParameter = method.getParameterCount() >= 1;
		return annotated && returnsVoid && atLeastOneParameter;
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
