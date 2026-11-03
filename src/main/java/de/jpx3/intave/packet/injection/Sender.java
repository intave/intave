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
import io.netty.channel.*;

import java.util.List;

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

		ByteBuf original = (ByteBuf) message;
		if (this.placement.isCurrent(context.pipeline())) {
			List<ByteBuf> packets;
			try {
				packets = this.binding.onPacketSend(original);
			} catch (RuntimeException | Error failure) {
				original.release();
				throw failure;
			}
			if (packets.isEmpty()) {
				original.release();
				promise.trySuccess();
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
			if (packets.size() == 1 || promise == context.voidPromise()) {
				for (ByteBuf packet : packets) {
					context.write(packet, promise);
				}
			} else {
				// PromiseCombiner is unavailable on the Netty versions shipped with older servers.
				ChannelFutureListener completion = new ChannelFutureListener() {
					private int remaining = packets.size();
					private Throwable failure;

					@Override
					public void operationComplete(ChannelFuture future) {
						if (!future.isSuccess() && this.failure == null) {
							this.failure = future.cause();
						}
						if (--this.remaining == 0) {
							if (this.failure == null) {
								promise.trySuccess();
							} else {
								promise.tryFailure(this.failure);
							}
						}
					}
				};
				for (ByteBuf packet : packets) {
					context.write(packet).addListener(completion);
				}
			}
			return;
		} else if (this.placement.isSeparatedFromAnchorBy(context.pipeline(), "compress")) {
			inspectCompressionEnvelope(context, original, promise);
			return;
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

	private void inspectCompressionEnvelope(ChannelHandlerContext context, ByteBuf envelope, ChannelPromise promise) {
		final ByteBuf packet;
		try {
			packet = CompressionRescue.copyPacket(envelope);
		} catch (RuntimeException ignored) {
			repairAfterCurrentMessage(context);
			context.write(envelope, promise);
			return;
		}
		envelope.release();
		try {
			this.placement.repair(context, this);
		} catch (RuntimeException | Error failure) {
			packet.release();
			throw failure;
		}
		// Outbound traversal already passed compress. Re-enter at the repaired sender so every
		// returned packet goes through the server's compressor with its configured threshold.
		write(context.pipeline().context(this), packet, promise);
	}
}
