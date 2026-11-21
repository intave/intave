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

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.BundleDelimiterPacket;
import de.jpx3.intave.packet.contract.immutable.HandshakePacket;
import de.jpx3.intave.packet.contract.mutable.Mutable;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSendPlan;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.reflect.PlayerChannel;
import de.jpx3.intave.registry.PacketTypeRegistries;
import de.jpx3.intave.registry.PacketTypeRegistry;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;

public final class Binding implements AutoCloseable {
	private static final String RECEIVE_HANDLER_NAME = "intave-decoder";
	private static final String SEND_HANDLER_NAME = "intave-encoder";

	private User user;
	private final Channel channel;
	private final Receiver receiver;
	private final Sender sender;
	private final AtomicBoolean closed = new AtomicBoolean();
	private volatile ConnectionState connectionState = ConnectionState.HANDSHAKING;
	private volatile int protocolVersion = -1;
	private int bundleDelimiterId = -1;
	private BundleDelimiterPacket bundleDelimiter;
	private boolean bundleOpen;

	private Binding(Channel channel, HandlerPlacement receivePlacement, HandlerPlacement sendPlacement) {
		this.channel = channel;
		this.receiver = new Receiver(receivePlacement, this);
		this.sender = new Sender(sendPlacement, this);
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
			pipeline.addBefore(receivePlacement.initialAnchorName(), RECEIVE_HANDLER_NAME, binding.receiver);
			try {
				pipeline.addBefore(sendPlacement.initialAnchorName(), SEND_HANDLER_NAME, binding.sender);
			} catch (RuntimeException failure) {
				pipeline.remove(RECEIVE_HANDLER_NAME);
				throw failure;
			}
		}
		return binding;
	}

	public static Binding of(Channel channel) {
		ChannelHandlerContext context = channel.pipeline().context(SEND_HANDLER_NAME);
		if (context == null || !(context.handler() instanceof Sender)) {
			throw new IllegalStateException("Client packet handlers are not installed on " + channel);
		}
		return ((Sender) context.handler()).binding();
	}

	public int protocolVersion() {
		return protocolVersion;
	}

	public ChannelFuture send(TypedPacketContract packet) {
		return send(packet, true);
	}

	public ChannelFuture sendSilently(TypedPacketContract packet) {
		return send(packet, false);
	}

	private ChannelFuture send(TypedPacketContract packet, boolean withEvent) {
		Objects.requireNonNull(packet, "packet");
		ChannelPromise promise = channel.newPromise();
		Runnable send = () -> {
			if (!promise.setUncancellable()) {
				return;
			}
			try {
				if (closed.get() || !channel.isActive()) {
					throw new IllegalStateException("Connection is closed");
				}
				if (connectionState != ConnectionState.PLAY || !(packet.type() instanceof PacketType.Server)) {
					throw new IllegalArgumentException("Expected a clientbound play packet on a play connection");
				}
				ByteBuf encoded = channel.alloc().buffer();
				try {
					WirePackets.encodePacket(connectionState, packet, protocolVersion, encoded);
				} catch (RuntimeException | Error failure) {
					encoded.release();
					throw failure;
				}
				sender.send(channel.pipeline(), encoded, promise, withEvent);
			} catch (RuntimeException | Error failure) {
				promise.tryFailure(failure);
			}
		};
		try {
			if (channel.eventLoop().inEventLoop()) {
				send.run();
			} else {
				channel.eventLoop().execute(send);
			}
		} catch (RuntimeException failure) {
			promise.tryFailure(failure);
		}
		return promise;
	}

	List<ByteBuf> onPacketReceive(ByteBuf message) {
		try {
			if (this.connectionState == ConnectionState.HANDSHAKING) {
				HandshakePacket handshake;
				try {
					handshake = WirePackets.createPacket(SERVERBOUND, ConnectionState.HANDSHAKING, message);
				} catch (RuntimeException failure) {
					logPacketFailure(SERVERBOUND, "decoding handshake", failure);
					channel.close();
					return Collections.emptyList();
				}
				if (handshake == null) {
					return Collections.singletonList(message);
				}
				if (!supports(handshake.protocolVersion())) {
					channel.close();
					return Collections.emptyList();
				}
				this.protocolVersion = handshake.protocolVersion();
				this.bundleDelimiterId = PacketTypeRegistries.clientbound().idOf(ConnectionState.PLAY, PacketType.Server.BUNDLE_DELIMITER, protocolVersion);
				this.bundleDelimiter = bundleDelimiterId >= 0
					? WirePackets.createPlayPacket(PacketType.Server.BUNDLE_DELIMITER, protocolVersion)
					: null;
				this.connectionState = handshake.nextConnectionState();
				return Collections.singletonList(message);
			}

			int packetId = packetIdFromHeader(message);
			ConnectionState nextState = nextStateAfterReceiving(packetId);
			try {
				// id -> type
				PacketType.Client packetType = PacketTypeRegistries.serverbound().typeOf(this.connectionState, packetId, this.protocolVersion);
				if (packetType != null) {
					PacketContract packet = WirePackets.decodePacket(SERVERBOUND, this.connectionState, this.protocolVersion, message);
					if (packet == null) {
						return Collections.singletonList(message);
					}
					User nullableUser = tryGetUser();
					PacketReceiveEvent event = new PacketReceiveEvent(nullableUser, packetType, packet);
					PacketEventBus.dispatch(event);
					if (event.isCancelled()) {
						return Collections.emptyList();
					}
					if (event.dirty()) {
						ByteBuf newMessage = channel.alloc().buffer();
						try {
							WirePackets.encodePacket(this.connectionState, packetType, this.protocolVersion, event.packet(), newMessage);
						} catch (RuntimeException | Error failure) {
							newMessage.release();
							throw failure;
						}
						return Collections.singletonList(newMessage);
					}
				}
				return Collections.singletonList(message);
			} finally {
				if (nextState != ConnectionState.PLAY) {
					bundleOpen = false;
				}
				this.connectionState = nextState;
			}
		} catch (RuntimeException | Error failure) {
			logPacketFailure(SERVERBOUND, "processing", failure);
			throw failure;
		}
	}

	List<ByteBuf> onPacketSend(ByteBuf message) {
		try {
			return processPacketSend(message);
		} catch (RuntimeException | Error failure) {
			logPacketFailure(CLIENTBOUND, "processing", failure);
			throw failure;
		}
	}

	private List<ByteBuf> processPacketSend(ByteBuf message) {
		int packetId = packetIdFromHeader(message);
		ConnectionState nextState = nextStateAfterSentPacket(packetId);
		try {
			User nullableUser = tryGetUser();
			PacketType.Server packetType = PacketTypeRegistries.clientbound().typeOf(this.connectionState, packetId, this.protocolVersion);

			if (packetType != null) {
				PacketContract packet = WirePackets.decodePacket(CLIENTBOUND, this.connectionState, this.protocolVersion, message);
				if (packet == null) {
					return Collections.singletonList(message);
				}
				PacketSendEvent sendEvent = new PacketSendEvent(nullableUser, packetType, packet);
				PacketSendPlan plan = PacketEventBus.dispatchSendPlan(sendEvent);
				BundleDelimiterPacket delimiterPacket = connectionState == ConnectionState.PLAY ? bundleDelimiter : null;
				List<TypedPacketContract> outgoingPackets = plan.outgoingPackets(delimiterPacket, bundleOpen);
				// Dispatch already omits cancelled packets while preserving queued packets.
				if (outgoingPackets.isEmpty()) {
					return Collections.emptyList();
				}
				boolean originalUnchanged = packet instanceof Mutable && !((Mutable) packet).couldHaveBeenMutated();
				if (originalUnchanged && outgoingPackets.size() == 1) {
					TypedPacketContract only = outgoingPackets.get(0);
					if (only.type() == packetType && only.contract() == packet) {
						return Collections.singletonList(message);
					}
				}
				List<ByteBuf> newMessages = new ArrayList<>(outgoingPackets.size());
				try {
					for (TypedPacketContract outgoingPacket : outgoingPackets) {
						if (originalUnchanged && outgoingPacket.type() == packetType && outgoingPacket.contract() == packet) {
							newMessages.add(message.duplicate().retain());
							continue;
						}
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
				return newMessages;
			}
			return Collections.singletonList(message);
		} finally {
			this.connectionState = nextState;
		}
	}

	boolean isBundleDelimiter(ByteBuf packet) {
		// A delimiter consists solely of its VarInt ID.
		if (connectionState != ConnectionState.PLAY || bundleDelimiterId < 0) {
			return false;
		}
		int size = packet.readableBytes();
		if (size == 1) {
			return packet.getUnsignedByte(packet.readerIndex()) == bundleDelimiterId;
		}
		if (size > 5 || packet.getByte(packet.readerIndex()) >= 0 || packet.getByte(packet.writerIndex() - 1) < 0) {
			return false;
		}
		ByteBuf header = packet.duplicate();
		return ByteBufStreamCodecs.VARINT.decode(header) == bundleDelimiterId && !header.isReadable();
	}

	void onBundleDelimiterWrite() {
		bundleOpen = !bundleOpen;
	}

	void logPacketFailure(PacketDirection direction, String operation, Throwable failure) {
		String message = "Exception while " + operation + " " + direction + " packet"
			+ " (state=" + this.connectionState + ", protocol=" + this.protocolVersion
			+ ", channel=" + this.channel + ")";
		IntaveLogger logger = IntaveLogger.logger();
		if (logger != null) {
			logger.error(message, failure);
		} else {
			System.err.println("[Intave] " + message);
			failure.printStackTrace();
		}
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
		return otherChannel.pipeline().context(this.receiver) != null;
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
			removeIfOwned(this.channel.pipeline(), RECEIVE_HANDLER_NAME, this.receiver);
			removeIfOwned(this.channel.pipeline(), SEND_HANDLER_NAME, this.sender);
		}
	}

	private static void removeIfOwned(ChannelPipeline pipeline, String name, ChannelHandler handler) {
		ChannelHandlerContext context = pipeline.context(name);
		if (context != null && context.handler() == handler) {
			pipeline.remove(name);
		}
	}

	private ConnectionState nextStateAfterReceiving(int packetId) {
		PacketTypeRegistry<PacketType.Client> serverbound = PacketTypeRegistries.serverbound();
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
		PacketTypeRegistry<PacketType.Server> clientbound = PacketTypeRegistries.clientbound();
		if (packetId != clientbound.idOf(ConnectionState.LOGIN, PacketType.Server.SUCCESS, this.protocolVersion)) {
			return this.connectionState;
		}
		return PacketTypeRegistries.serverbound().idOf(
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
		return PacketTypeRegistries.serverbound().supportedProtocolVersions().contains(protocolVersion);
	}
}
