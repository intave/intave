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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.packet.event.PacketEvent;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

public final class MethodHandleSubscriptionExecutor {
	private final Method calledMethod;
	private final MethodHandle handle;
	private final Function<PacketEvent, Object>[] argumentResolvers;
	private final int argumentCount;
	private final int targetIndex;
	private final Class<?> nativeTargetType;

	public MethodHandleSubscriptionExecutor(Method calledMethod) {
		this.calledMethod = calledMethod;
		Class<?>[] parameterTypes = calledMethod.getParameterTypes();
		this.handle = handleOf(calledMethod);
		this.targetIndex = findTargetPosition(parameterTypes);
		this.nativeTargetType = targetIndex == -1 ? null : parameterTypes[targetIndex];
		this.argumentResolvers = argumentResolversFor(calledMethod, targetIndex, nativeTargetType);
		this.argumentCount = calledMethod.getParameterCount();
	}

	public void invoke(PacketEventSubscriber subscriber, PacketEvent event) {
		User user = event.user();
		if (user == null || !user.hasPlayer()) {
			return;
		}
		switch (argumentCount) {
			case 0:
				try {
					handle.invoke(subscriber);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				break;
			case 1:
				try {
					handle.invoke(subscriber, argumentResolvers[0].apply(event));
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				break;
			case 2:
				try {
					handle.invoke(
						subscriber,
						argumentResolvers[0].apply(event),
						argumentResolvers[1].apply(event)
					);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				break;
			case 3:
				try {
					handle.invoke(
						subscriber,
						argumentResolvers[0].apply(event),
						argumentResolvers[1].apply(event),
						argumentResolvers[2].apply(event)
					);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				break;
			case 4:
				try {
					handle.invoke(
						subscriber,
						argumentResolvers[0].apply(event),
						argumentResolvers[1].apply(event),
						argumentResolvers[2].apply(event),
						argumentResolvers[3].apply(event)
					);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				break;
			case 5:
				try {
					handle.invoke(
						subscriber,
						argumentResolvers[0].apply(event),
						argumentResolvers[1].apply(event),
						argumentResolvers[2].apply(event),
						argumentResolvers[3].apply(event),
						argumentResolvers[4].apply(event)
					);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				break;
			default:
				Object[] arguments = new Object[argumentCount];
				for (int i = 0; i < argumentCount; i++) {
					arguments[i] = argumentResolvers[i].apply(event);
				}
				try {
					handle.invokeWithArguments(subscriber, arguments);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				break;
		}
	}

	public Method calledMethod() {
		return calledMethod;
	}

	public @Nullable Class<?> nativeTargetType() {
		return nativeTargetType;
	}

	private static Function<PacketEvent, Object>[] argumentResolversFor(
		Method method, int targetIndex, Class<?> nativeTargetType
	) {
		Class<?>[] parameterTypes = method.getParameterTypes();
		int userIndex = findParameterPosition(parameterTypes, User.class);
		int playerIndex = findParameterPosition(parameterTypes, Player.class);
		int cancelableIndex = findParameterPosition(parameterTypes, Cancellable.class);
		int packetEventIndex = findParameterPosition(parameterTypes, PacketEvent.class);

		//noinspection unchecked
		Function<PacketEvent, Object>[] resolvers = new Function[parameterTypes.length];
		Arrays.fill(resolvers, (Function<Object, PacketEvent>) o -> null);

		set(userIndex, resolvers, PacketEvent::user);
		set(playerIndex, resolvers, o -> o.user().player());
		set(cancelableIndex, resolvers, o -> o);
		set(packetEventIndex, resolvers, o -> o);
		set(targetIndex, resolvers, o -> nativeTargetType.cast(o.packet()));

		return resolvers;
	}

	private static void set(int index, Function<PacketEvent, Object>[] args, Function<PacketEvent, Object> value) {
		if (index >= 0) {
			args[index] = value;
		}
	}

	private static int findTargetPosition(Class<?>[] parameterTypes) {
		int target = -1;
		for (int i = 0; i < parameterTypes.length; i++) {
			if (isTarget(parameterTypes[i])) {
				if (target != -1) {
					throw new IllegalStateException("Multiple target parameters found");
				}
				target = i;
			}
		}
		return target;
	}

	private static boolean isTarget(Class<?> clazz) {
		return clazz.isInterface() &&
			clazz != User.class &&
			clazz != Player.class &&
			clazz != Cancellable.class;
	}

	private static MethodHandle handleOf(Method method) {
		try {
			return MethodHandles.lookup().unreflect(method);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private static int findParameterPosition(Class<?>[] parameterTypes, Class<?> parameterType) {
		for (int i = 0; i < parameterTypes.length; i++) {
			if (parameterTypes[i] == parameterType) {
				return i;
			}
		}
		// Prefer an explicit Cancellable parameter over a PacketEvent that also implements it.
		for (int i = 0; i < parameterTypes.length; i++) {
			if (parameterType.isAssignableFrom(parameterTypes[i])) {
				return i;
			}
		}
		return -1;
	}
}
