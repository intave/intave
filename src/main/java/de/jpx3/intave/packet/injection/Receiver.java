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

import java.util.List;

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

		ByteBuf original = (ByteBuf) message;
		if (this.placement.isCurrent(context.pipeline())) {
			List<ByteBuf> packets;
			try {
				packets = this.binding.onPacketReceive(original);
			} catch (RuntimeException | Error failure) {
				original.release();
				throw failure;
			}
			// error/full cancel
			if (packets == null || packets.isEmpty()) {
				original.release();
				context.close();
				return;
			}
			boolean forwardsOriginal = false;
			for (ByteBuf packet : packets) {
				if (packet == original) {
					forwardsOriginal = true;
					break;
				}
			}
			if (!forwardsOriginal) {
				original.release();
			}
			for (ByteBuf packet : packets) {
				context.fireChannelRead(packet);
			}
			return;
		} else if (this.placement.isSeparatedFromAnchorBy(context.pipeline(), "decompress")) {
			this.placement.repair(context, this);
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
}
