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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@ChannelHandler.Sharable
final class Receiver extends ChannelInboundHandlerAdapter {
	private final HandlerPlacement placement;
	private final Binding binding;

	Receiver(HandlerPlacement placement, Binding binding) {
		this.placement = placement;
		this.binding = binding;
	}

	@Override
	public void channelRead(ChannelHandlerContext context, Object message) {
		if (!(message instanceof ByteBuf)) {
			context.fireChannelRead(message);
			return;
		}

		ByteBuf packet = (ByteBuf) message;
		if (this.placement.isCurrent(context.pipeline())) {
			if (!this.binding.onPacketReceive(packet)) {
				packet.release();
				context.close();
				return;
			}
		} else if (this.placement.isSeparatedFromAnchorBy(context.pipeline(), "decompress")) {
			inspectCompressionEnvelope(packet);
			repairAfterCurrentMessage(context);
		} else {
			repairAfterCurrentMessage(context);
		}
		context.fireChannelRead(message);
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
			this.binding.onPacketReceive(packet);
		} finally {
			packet.release();
		}
	}
}
