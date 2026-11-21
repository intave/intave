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

package de.jpx3.intave.packet;

import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.injection.Binding;
import de.jpx3.intave.user.User;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

@SuppressWarnings("resource")
public final class PacketSender {
	private PacketSender() {
	}

	public static Binding bindingOf(User user) {
		return user.binding();
	}

	public static ChannelFuture send(User user, TypedPacketContract packet) {
		return bindingOf(user).send(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
	}

	public static ChannelFuture sendSilently(User user, TypedPacketContract packet) {
		return bindingOf(user).sendSilently(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
	}
}
