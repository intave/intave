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
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.HandshakePacket;
import de.jpx3.intave.packet.contract.mutable.Mutable;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.reflect.PlayerChannel;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;

public final class Binding implements AutoCloseable {
	private static final String RECEIVE_HANDLER_NAME = "intave-decoder";
	private static final String SEND_HANDLER_NAME = "intave-encoder";

	private User user;
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

	List<ByteBuf> onPacketReceive(ByteBuf message) {
		if (this.connectionState == ConnectionState.HANDSHAKING) {
			HandshakePacket handshake;
			try {
				handshake = WirePackets.createVersionIndependentPacket(ConnectionState.HANDSHAKING, message);
			} catch (RuntimeException ignored) {
				return Collections.emptyList();
			}
			if (handshake == null) {
				return Collections.singletonList(message);
			}
			if (!supports(handshake.protocolVersion())) {
				return Collections.emptyList();
			}
			this.protocolVersion = handshake.protocolVersion();
			this.connectionState = handshake.nextConnectionState();
			return Collections.singletonList(message);
		}

		int packetId = packetIdFromHeader(message);
		ConnectionState nextState = nextStateAfterReceivedPacket(packetId);
		PacketType.Client packetType = PacketIdRegistries.serverbound().typeOf(this.connectionState, packetId, this.protocolVersion);

		if (packetType != null && WirePackets.packetIdKnown(PacketDirection.SERVERBOUND, this.connectionState, packetId, this.protocolVersion)) {
			PacketContract packet = WirePackets.decodePacket(this.connectionState, this.protocolVersion, message);
			User nullableUser = tryGetUser();
			PacketEventBus.dispatch(new PacketReceiveEvent(nullableUser, packetType, packet));
		}
		this.connectionState = nextState;
		return Collections.singletonList(message);
	}

	List<ByteBuf> onPacketSend(ByteBuf message) {
		int packetId = packetIdFromHeader(message);
		ConnectionState nextState = nextStateAfterSentPacket(packetId);
		User nullableUser = tryGetUser();
		PacketType.Server packetType = PacketIdRegistries.clientbound().typeOf(this.connectionState, packetId, this.protocolVersion);

		if (packetType != null && WirePackets.packetIdKnown(CLIENTBOUND, this.connectionState, packetId, this.protocolVersion)) {
			PacketContract packet = WirePackets.decodePacket(this.connectionState, this.protocolVersion, message);
			List<TypedPacketContract> outgoingPackets = PacketEventBus.dispatch(
				new PacketSendEvent(nullableUser, packetType, packet)
			);
			if (outgoingPackets.isEmpty()) {
				this.connectionState = nextState;
				return Collections.emptyList();
			}
			if (outgoingPackets.size() == 1) {
				TypedPacketContract otherPacket = outgoingPackets.get(0);
				if (otherPacket.contract() == packet && packet instanceof Mutable && !((Mutable) packet).couldHaveBeenMutated()) {
					this.connectionState = nextState;
					return Collections.singletonList(message);
				}
			}
			List<ByteBuf> newMessages = new ArrayList<>(outgoingPackets.size());
			try {
				for (TypedPacketContract outgoingPacket : outgoingPackets) {
					ByteBuf newMessage = channel.alloc().buffer();
					newMessages.add(newMessage);
					WirePackets.encodePacket(this.connectionState, outgoingPacket, this.protocolVersion, newMessage);
				}
			} catch (RuntimeException | Error failure) {
				for (ByteBuf newMessage : newMessages) {
					newMessage.release();
				}
				throw failure;
			}
			this.connectionState = nextState;
			return newMessages;
		}
		this.connectionState = nextState;
		return Collections.singletonList(message);
	}

	public User tryGetUser() {
		if (this.user == null) {
			for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
				Channel otherChannel = PlayerChannel.channelOf(otherPlayer);
				if (matchesChannel(otherChannel)) {
					this.user = UserRepository.userOf(otherPlayer);
					break;
				}
			}
		}
		return this.user;
	}

	boolean matchesChannel(Channel otherChannel) {
		return otherChannel.pipeline().context(this.receiveHandler) != null;
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
		PacketIdRegistry<PacketType.Client> serverbound = PacketIdRegistries.serverbound();
		switch (this.connectionState) {
			case LOGIN:
				if (packetId == serverbound.idOf(ConnectionState.LOGIN, PacketType.Client.LOGIN_ACK, this.protocolVersion)) {
					return ConnectionState.CONFIGURATION;
				}
				break;
			case PLAY:
				if (packetId == serverbound.idOf(ConnectionState.PLAY, PacketType.Client.CONFIGURATION_ACK, this.protocolVersion)) {
					return ConnectionState.CONFIGURATION;
				}
				break;
			case CONFIGURATION:
				if (packetId == serverbound.idOf(
					ConnectionState.CONFIGURATION,
					PacketType.Client.FINISH_CONFIGURATION,
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
		PacketIdRegistry<PacketType.Server> clientbound = PacketIdRegistries.clientbound();
		if (packetId != clientbound.idOf(ConnectionState.LOGIN, PacketType.Server.SUCCESS, this.protocolVersion)) {
			return this.connectionState;
		}
		return PacketIdRegistries.serverbound().idOf(
			ConnectionState.LOGIN,
			PacketType.Client.LOGIN_ACK,
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
