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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.HandshakePacket;
import de.jpx3.intave.packet.contract.Packet;
import de.jpx3.intave.packet.contract.PlayerInputPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Binding implements AutoCloseable {
	private static final String RECEIVE_HANDLER_NAME = "intave-decoder";
	private static final String SEND_HANDLER_NAME = "intave-encoder";

	private final Channel channel;
	private final Receiver receiveHandler;
	private final Sender sendHandler;
	private final AtomicBoolean closed = new AtomicBoolean();
	private volatile ConnectionState connectionState = ConnectionState.HANDSHAKING;
	private volatile int protocolVersion = -1;

	private Binding(Channel channel, HandlerPlacement receivePlacement, HandlerPlacement sendPlacement) {
		this.channel = channel;
		this.receiveHandler = new Receiver(receivePlacement, this);
		this.sendHandler = new Sender(sendPlacement, this);
	}

	public static Binding bind(Channel channel) {
		ChannelPipeline pipeline = channel.pipeline();
		HandlerPlacement receivePlacement = HandlerPlacement.beforeDecoder(RECEIVE_HANDLER_NAME, pipeline);
		HandlerPlacement sendPlacement = HandlerPlacement.beforeEncoder(SEND_HANDLER_NAME, pipeline);

		Binding binding = new Binding(channel, receivePlacement, sendPlacement);
		synchronized (channel) {
			if (pipeline.context(RECEIVE_HANDLER_NAME) != null || pipeline.context(SEND_HANDLER_NAME) != null) {
				throw new IllegalStateException("Client packet handlers are already installed on " + channel);
			}
			pipeline.addBefore(receivePlacement.initialAnchorName(), RECEIVE_HANDLER_NAME, binding.receiveHandler);
			try {
				pipeline.addBefore(sendPlacement.initialAnchorName(), SEND_HANDLER_NAME, binding.sendHandler);
			} catch (RuntimeException failure) {
				pipeline.remove(RECEIVE_HANDLER_NAME);
				throw failure;
			}
		}
		return binding;
	}

	boolean onPacketReceive(ByteBuf message) {
		if (this.connectionState == ConnectionState.HANDSHAKING) {
			HandshakePacket handshake;
			try {
				handshake = WirePackets.createVersionIndependentPacket(ConnectionState.HANDSHAKING, message);
			} catch (RuntimeException ignored) {
				return true;
			}
			if (handshake == null) {
				return true;
			}
			if (!supports(handshake.protocolVersion())) {
				return false;
			}
			this.protocolVersion = handshake.protocolVersion();
			this.connectionState = handshake.nextConnectionState();
			return true;
		}

		int packetId = packetIdFromHeader(message);
		ConnectionState nextState = nextStateAfterReceivedPacket(packetId);
//		String packetName = PacketIdRegistries.serverbound().typeNameOf(this.connectionState, packetId, this.protocolVersion);

		if (WirePackets.packetIdKnown(this.connectionState, packetId, this.protocolVersion)) {
			Packet packet = WirePackets.decodePacket(this.connectionState, this.protocolVersion, message);
//			System.out.println("Decoded packet " + packetName + " in state " + this.connectionState + " for protocol version " + this.protocolVersion);
			if (packet instanceof PlayerInputPacket) {
				PlayerInputPacket inputPacket = (PlayerInputPacket) packet;
				System.out.println(inputPacket.input());
			}
		}
		this.connectionState = nextState;
		return true;
	}

	void onPacketSend(ByteBuf message) {
		int packetId = packetIdFromHeader(message);
		this.connectionState = nextStateAfterSentPacket(packetId);
	}

	public ConnectionState connectionState() {
		return this.connectionState;
	}

	@Override
	public void close() {
		if (!this.closed.compareAndSet(false, true)) {
			return;
		}
		synchronized (this.channel) {
			removeIfOwned(this.channel.pipeline(), RECEIVE_HANDLER_NAME, this.receiveHandler);
			removeIfOwned(this.channel.pipeline(), SEND_HANDLER_NAME, this.sendHandler);
		}
	}

	private static void removeIfOwned(ChannelPipeline pipeline, String name, ChannelHandler handler) {
		ChannelHandlerContext context = pipeline.context(name);
		if (context != null && context.handler() == handler) {
			pipeline.remove(name);
		}
	}

	private ConnectionState nextStateAfterReceivedPacket(int packetId) {
		PacketIdRegistry serverbound = PacketIdRegistries.serverbound();
		switch (this.connectionState) {
			case LOGIN:
				if (packetId == serverbound.idOf(ConnectionState.LOGIN, "LOGIN_ACK", this.protocolVersion)) {
					return ConnectionState.CONFIGURATION;
				}
				break;
			case PLAY:
				if (packetId == serverbound.idOf(ConnectionState.PLAY, "CONFIGURATION_ACK", this.protocolVersion)) {
					return ConnectionState.CONFIGURATION;
				}
				break;
			case CONFIGURATION:
				if (packetId == serverbound.idOf(
					ConnectionState.CONFIGURATION,
					"FINISH_CONFIGURATION",
					this.protocolVersion
				)) {
					return ConnectionState.PLAY;
				}
				break;
			default:
				break;
		}
		return this.connectionState;
	}

	private ConnectionState nextStateAfterSentPacket(int packetId) {
		if (this.connectionState != ConnectionState.LOGIN) {
			return this.connectionState;
		}
		PacketIdRegistry clientbound = PacketIdRegistries.clientbound();
		if (packetId != clientbound.idOf(ConnectionState.LOGIN, "SUCCESS", this.protocolVersion)) {
			return this.connectionState;
		}
		return PacketIdRegistries.serverbound().idOf(
			ConnectionState.LOGIN,
			"LOGIN_ACK",
			this.protocolVersion
		) < 0
			? ConnectionState.PLAY
			: ConnectionState.LOGIN;
	}

	private static int packetIdFromHeader(ByteBuf message) {
		return ByteBufStreamCodecs.VARINT.decode(message.duplicate());
	}

	private static boolean supports(int protocolVersion) {
		return PacketIdRegistries.serverbound().supportedProtocolVersions().contains(protocolVersion);
	}
}
