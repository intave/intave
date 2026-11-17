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
import de.jpx3.intave.packet.ListenerPriority;
import de.jpx3.intave.packet.ListenerStage;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.Mutable;
import de.jpx3.intave.packet.event.PacketEvent;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.user.User;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
//			linkSendSubscription(provider, method, annotation.priority(), annotation.stage());
		}
	}

	private void linkReceiveSubscription(
		SubscriptionInstanceProvider<User, ?, PacketEventSubscriber> provider,
		Method method, ListenerPriority priority, ListenerStage stage
	) {
		MethodHandleSubscriptionExecutor executor = new MethodHandleSubscriptionExecutor(method);
		Class<?> nativeTargetType = executor.nativeTargetType();
		Set<PacketType.Client> applicableTypes = matchingClientTypes(nativeTargetType);
		boolean mutableType = Mutable.class.isAssignableFrom(nativeTargetType);

		for (Class<?> parameterType : method.getParameterTypes()) {
			if (parameterType == PacketSendEvent.class) {
				throw new IllegalStateException("Subscription method " + method + " has a parameter of type PacketSendEvent, but it is annotated with @OnReceive");
			}
		}

		if (mutableType) {
			if (stage == ListenerStage.AUTOMATIC) {
				stage = ListenerStage.PROCESSING_MUTABLE;
			}
			if (!stage.allowMutations()) {
				throw new IllegalStateException("Subscription method " + method + " is for a mutable packet type, but the stage " + stage + " does not allow mutations");
			}
		} else {
			if (stage == ListenerStage.PROCESSING_MUTABLE) {
				throw new IllegalStateException("Subscription method " + method + " is for a non-mutable packet type, but the stage " + stage + " is for mutable packets");
			} else if (stage == ListenerStage.AUTOMATIC) {
				stage = ListenerStage.AFTER_IMMUTABLE;
			}
		}

		ListenerStage finalStage = stage;
		PacketEventBus.addListener(new PacketSubscription() {
      @Override
      public void onPacket(PacketEvent event) {
				boolean dirtyBefore = event.dirty();
				provider.apply(event.user(), subscriber -> executor.invoke(subscriber, event));
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
    }, applicableTypes.toArray(new PacketType.Client[0]));
	}

	private Set<PacketType.Client> matchingClientTypes(Class<?> target) {
		Set<PacketType.Client> matchingTypes = new HashSet<>();
		for (Map.Entry<PacketType.Client, List<Class<?>>> entry : WirePackets.registeredClientPackets().entrySet()) {
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
