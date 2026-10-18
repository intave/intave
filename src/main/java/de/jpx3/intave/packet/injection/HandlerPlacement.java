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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import java.util.List;

final class HandlerPlacement {
	private final String handlerName;
	private final String initialAnchorName;
	private final CodecAnchor codecAnchor;

	private HandlerPlacement(String handlerName, String initialAnchorName, CodecAnchor codecAnchor) {
		this.handlerName = handlerName;
		this.initialAnchorName = initialAnchorName;
		this.codecAnchor = codecAnchor;
	}

	static HandlerPlacement beforeDecoder(String handlerName, ChannelPipeline pipeline) {
		return beforeCodec(handlerName, pipeline, CodecAnchor.DECODER);
	}

	static HandlerPlacement beforeEncoder(String handlerName, ChannelPipeline pipeline) {
		return beforeCodec(handlerName, pipeline, CodecAnchor.ENCODER);
	}

	String initialAnchorName() {
		return this.initialAnchorName;
	}

	boolean isCurrent(ChannelPipeline pipeline) {
		return isImmediatelyBefore(pipeline, currentAnchorName(pipeline));
	}

	boolean isSeparatedFromAnchorBy(ChannelPipeline pipeline, String interveningHandlerName) {
		List<String> names = pipeline.names();
		int handlerIndex = names.indexOf(this.handlerName);
		int interveningHandlerIndex = names.indexOf(interveningHandlerName);
		int anchorIndex = names.indexOf(currentAnchorName(pipeline));
		return handlerIndex >= 0
			&& interveningHandlerIndex > handlerIndex
			&& anchorIndex > interveningHandlerIndex;
	}

	void repair(ChannelHandlerContext context, ChannelHandler handler) {
		ChannelPipeline pipeline = context.pipeline();
		String anchorName = currentAnchorName(pipeline);
		if (!canMove(pipeline, anchorName, handler)) {
			return;
		}
		synchronized (context.channel()) {
			if (!canMove(pipeline, anchorName, handler)) {
				return;
			}
			pipeline.remove(this.handlerName);
			pipeline.addBefore(anchorName, this.handlerName, handler);
		}
	}

	private static HandlerPlacement beforeCodec(String handlerName, ChannelPipeline pipeline, CodecAnchor codecAnchor) {
		String anchorName = codecAnchor.findIn(pipeline);
		if (anchorName == null) {
			throw new IllegalStateException("Could not find Minecraft's packet " + codecAnchor.description + " in pipeline " + pipeline.names());
		}
		return new HandlerPlacement(handlerName, anchorName, codecAnchor);
	}

	private boolean canMove(ChannelPipeline pipeline, String anchorName, ChannelHandler handler) {
		ChannelHandlerContext installed = pipeline.context(this.handlerName);
		return installed != null && installed.handler() == handler && pipeline.context(anchorName) != null && !isImmediatelyBefore(pipeline, anchorName);
	}

	private boolean isImmediatelyBefore(ChannelPipeline pipeline, String anchorName) {
		List<String> names = pipeline.names();
		int handlerIndex = names.indexOf(this.handlerName);
		return handlerIndex >= 0 && names.indexOf(anchorName) == handlerIndex + 1;
	}

	private String currentAnchorName(ChannelPipeline pipeline) {
		String currentAnchorName = this.codecAnchor.findIn(pipeline);
		return currentAnchorName == null ? this.initialAnchorName : currentAnchorName;
	}

	private enum CodecAnchor {
		DECODER("decoder", "via-decoder", "inbound_config", "decoder"),
		ENCODER("encoder", "via-encoder", "outbound_config", "encoder");

		private final String description;
		private final String[] candidateNames;

		CodecAnchor(String description, String... candidateNames) {
			this.description = description;
			this.candidateNames = candidateNames;
		}

		private String findIn(ChannelPipeline pipeline) {
			for (String candidateName : this.candidateNames) {
				if (pipeline.context(candidateName) != null) {
					return candidateName;
				}
			}
			return null;
		}
	}
}
