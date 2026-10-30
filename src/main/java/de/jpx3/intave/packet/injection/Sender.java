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

package de.jpx3.intave.packet.injection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

@ChannelHandler.Sharable
final class Sender extends ChannelDuplexHandler {
	private final HandlerPlacement placement;
	private final Binding binding;

	Sender(HandlerPlacement placement, Binding binding) {
		this.placement = placement;
		this.binding = binding;
	}

	@Override
	public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
		if (!(message instanceof ByteBuf)) {
			context.write(message, promise);
			return;
		}

		ByteBuf packet = (ByteBuf) message;
		if (this.placement.isCurrent(context.pipeline())) {
			this.binding.onPacketSend(packet);
		} else if (this.placement.isSeparatedFromAnchorBy(context.pipeline(), "compress")) {
			inspectCompressionEnvelope(packet);
			repairAfterCurrentMessage(context);
		} else {
			repairAfterCurrentMessage(context);
		}
		context.write(message, promise);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext context, Object event) {
		context.fireUserEventTriggered(event);
		this.placement.repair(context, this);
	}

	private void repairAfterCurrentMessage(ChannelHandlerContext context) {
		context.executor().execute(() -> this.placement.repair(context, this));
	}

	private void inspectCompressionEnvelope(ByteBuf envelope) {
		final ByteBuf packet;
		try {
			packet = CompressionRescue.copyPacket(envelope);
		} catch (RuntimeException ignored) {
			return;
		}
		try {
			this.binding.onPacketSend(packet);
		} finally {
			packet.release();
		}
	}
}
