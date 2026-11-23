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

package de.jpx3.intave.module.feedback;

import com.viaversion.nbt.tag.StringTag;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.command.stages.BaseStage;
import de.jpx3.intave.module.dispatch.MovementDispatcher;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.module.tracker.player.InventoryTracker;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.BundleDelimiterPacket;
import de.jpx3.intave.packet.contract.immutable.MotionUpdate;
import de.jpx3.intave.packet.contract.immutable.OpenScreenPacket;
import de.jpx3.intave.packet.contract.immutable.RespawnPacket;
import de.jpx3.intave.packet.contract.mutable.MutableClientCommandPacket;
import de.jpx3.intave.packet.contract.mutable.MutableEntityVelocityPacket;
import de.jpx3.intave.packet.contract.mutable.MutableExplosionPacket;
import de.jpx3.intave.packet.contract.mutable.MutableWindowClosePacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.packet.injection.Binding;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.connection.handshake.HandshakeWirePacket;
import de.jpx3.intave.packet.wire.connection.pong.PongWirePacket;
import de.jpx3.intave.registry.PacketTypeRegistries;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.ClientCommand;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static de.jpx3.intave.module.feedback.NativeFeedbackTestSupport.acknowledgement;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeFeedbackTest {
	@Test
	void nativeFeedbackSynchronizesPacketsAndConsumesOnlyItsOwnAcknowledgements() throws Exception {
		try (NativeFeedbackTestSupport feedback = new NativeFeedbackTestSupport()) {
			PacketPerfectSubscriptionLinker linker = feedback.linker;
			FeedbackSender sender = feedback.sender;
			for (int protocol : new int[]{47, 755, 761, 762, 764, 765, 766, 767, 768, 773, 776, 777}) {
				UUID uuid = UUID.randomUUID();
				Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
					if (method.getName().equals("getUniqueId")) return uuid;
					if (method.getName().equals("getEntityId")) return 1;
					if (method.getName().equals("getName")) return "Native feedback test";
					return null;
				});
				User fallback = UserFactory.createFallback();
				Set<MessageChannel> channels = EnumSet.noneOf(MessageChannel.class);
				List<String> messages = new ArrayList<>();
				AtomicReference<BlockCacheEntry> occupiedBlock = new AtomicReference<>(BlockCacheEntry.empty());
				BlockCache cache = (BlockCache) Proxy.newProxyInstance(BlockCache.class.getClassLoader(), new Class<?>[]{BlockCache.class},
					(proxy, method, args) -> method.getName().equals("peekStateAt") ? occupiedBlock.get() : method.invoke(fallback.blockCache(), args));
				User user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class}, (proxy, method, args) -> {
					switch (method.getName()) {
						case "player": return player;
						case "hasPlayer": return true;
						case "protocolVersion": return protocol;
						case "blockCache": return cache;
						case "receives": return channels.contains(args[0]);
						case "toggleReceive":
							MessageChannel channel = (MessageChannel) args[0];
							if (!channels.remove(channel)) channels.add(channel);
							return null;
						case "sendMessage": messages.add((String) args[0]); return null;
						default: return method.invoke(fallback, args);
					}
				});
				UserRepository.manuallyRegisterUser(player, user);
				try {
					if (protocol == 762) {
						verifyFeedbackInServerBundle(sender, user, false);
						verifyFeedbackInServerBundle(sender, user, true);
					}
					verifyInventoryPackets(user, linker, occupiedBlock);
					assertTrue(messages.isEmpty());
					if (protocol == 777) {
						BaseStage.singletonInstance().debug(user, BaseStage.DebugType.INVENTORY);
						assertTrue(user.receives(MessageChannel.DEBUG_INVENTORY));
						messages.clear();
						verifyInventoryPackets(user, linker, occupiedBlock);
						assertTrue(messages.stream().anyMatch(s -> s.contains("Server open for window 0, awaiting feedback | tracked=closed")));
						assertTrue(messages.stream().anyMatch(s -> s.contains("Open feedback received for window 0 | tracked=open")));
						assertTrue(messages.stream().anyMatch(s -> s.contains("skipped inside a nether portal | tracked=closed")));
						assertTrue(messages.stream().anyMatch(s -> s.contains("Skipping open tracking for beacon window 1 | tracked=open, forceInventoryOnClickOpen=false")));
						assertTrue(messages.stream().anyMatch(s -> s.contains("Close feedback received for window 1 | tracked=closed")));
						assertEquals(1, messages.stream().filter(s -> s.contains("[Inventory] Respawn feedback received | tracked=closed")).count());
						BaseStage.singletonInstance().debug(user, BaseStage.DebugType.INVENTORY);
						assertFalse(user.receives(MessageChannel.DEBUG_INVENTORY));
						messages.clear();
						verifyInventoryPackets(user, linker, occupiedBlock);
						assertTrue(messages.isEmpty());
					}
					List<String> callbacks = new ArrayList<>();
					PacketSendEvent event = new PacketSendEvent(user, PacketType.Server.ENTITY_VELOCITY, entityVelocity(1, new Motion()));
					sender.doubleSynchronize(user, event, () -> callbacks.add("before"), () -> callbacks.add("after"));
					List<TypedPacketContract> batch = dispatchFeedbackGroup(event);
					assertEquals(3, batch.size());
					assertEquals(PacketType.Server.ENTITY_VELOCITY, batch.get(1).type());
					assertEquals(2, user.meta().connection().feedbackQueue().size());
					assertTrue(callbacks.isEmpty());
					for (int index : new int[]{0, 2}) {
						PacketReceiveEvent acknowledgement = acknowledgement(user, batch.get(index));
						PacketEventBus.dispatch(acknowledgement);
						assertTrue(acknowledgement.isCancelled(), "Native acknowledgement must not reach ViaVersion/server");
						assertEquals(index == 0 ? List.of("before") : List.of("before", "after"), callbacks);
					}
					assertEquals(0, user.meta().connection().feedbackQueue().size());
					MovementDispatcher dispatcher = new MovementDispatcher();
					linker.linkSubscriptionsIn(dispatcher);
					try {
						MutableEntityVelocityPacket velocity = WirePackets.createPlayPacket(PacketType.Server.ENTITY_VELOCITY, protocol);
						velocity.setEntityId(1);
						velocity.setMotion(new Motion(0.125, 0.25, -0.5));
						verifyMotionFeedback(user, velocity.typedTo(PacketType.Server.ENTITY_VELOCITY));
						MutableExplosionPacket explosion = WirePackets.createPlayPacket(PacketType.Server.EXPLOSION, protocol);
						explosion.setDefaultEffects(protocol);
						explosion.setMotion(new Motion(0, -1, 0));
						verifyMotionFeedback(user, explosion.typedTo(PacketType.Server.EXPLOSION));
						for (boolean nativeSend : new boolean[]{false, true}) {
							verifyWireMotion(user, velocity.typedTo(PacketType.Server.ENTITY_VELOCITY), nativeSend);
							verifyWireMotion(user, explosion.typedTo(PacketType.Server.EXPLOSION), nativeSend);
						}
						if (protocol >= 768) {
							explosion.setMotion(null);
							assertEquals(1, PacketEventBus.dispatch(new PacketSendEvent(user, PacketType.Server.EXPLOSION,
								explosion)).size());
							assertEquals(0, user.meta().movement().pendingVelocityPackets.get());
						}
					} finally {
						linker.removeSubscriptionsOf(dispatcher);
					}
					FeedbackRequest<Object> legacy = new FeedbackRequest<>((p, target) -> fail("Must stay on legacy path"), null, null,
						(short) 1234, user.meta().connection().transactionNumCounter++, 0);
					user.meta().connection().feedbackQueue().add(legacy);
					PacketReceiveEvent unrelated = new PacketReceiveEvent(user, PacketType.Client.PONG, new PongWirePacket(FeedbackSender.PING_MASK | 1234));
					PacketEventBus.dispatch(unrelated);
					assertFalse(unrelated.isCancelled());
					assertSame(legacy, user.meta().connection().feedbackQueue().peek());
				} finally {
					UserRepository.unregisterUser(player);
				}
			}
		}
	}

	private static void verifyInventoryPackets(User user, PacketPerfectSubscriptionLinker linker, AtomicReference<BlockCacheEntry> occupiedBlock) {
		InventoryTracker tracker = new InventoryTracker();
		linker.linkSubscriptionsIn(tracker);
		try {
			verifyClientCommands(user, occupiedBlock);
			verifyInventoryOpenPackets(user, occupiedBlock);
			RespawnPacket respawn = WirePackets.createPlayPacket(PacketType.Server.RESPAWN, user.protocolVersion());
			user.meta().inventory().updateInventoryOpenState(true);
			PacketSendEvent cancelledRespawn = new PacketSendEvent(user, PacketType.Server.RESPAWN, respawn);
			cancelledRespawn.setCancelled(true);
			assertTrue(PacketEventBus.dispatch(cancelledRespawn).isEmpty());
			assertTrue(user.meta().inventory().inventoryOpen());
			assertEquals(0, user.meta().connection().feedbackQueue().size());

			OpenScreenPacket open = WirePackets.createPlayPacket(PacketType.Server.OPEN_WINDOW, user.protocolVersion());
			List<TypedPacketContract> sentOpen = PacketEventBus.dispatch(new PacketSendEvent(user, PacketType.Server.OPEN_WINDOW, open));
			List<TypedPacketContract> sentRespawn = PacketEventBus.dispatch(new PacketSendEvent(user, PacketType.Server.RESPAWN, respawn));
			assertEquals(2, sentRespawn.size());
			assertSame(respawn, sentRespawn.get(0).contract());
			assertEquals(user.protocolVersion() >= 755 ? PacketType.Server.PING : PacketType.Server.TRANSACTION_OUT, sentRespawn.get(1).type());
			assertTrue(user.meta().inventory().inventoryOpen(), "Sending respawn must leave the inventory open");
			PacketEventBus.dispatch(acknowledgement(user, sentOpen.get(1)));
			assertTrue(user.meta().inventory().inventoryOpen(), "Acknowledging the earlier open must leave the inventory open");
			PacketEventBus.dispatch(acknowledgement(user, sentRespawn.get(1)));
			assertFalse(user.meta().inventory().inventoryOpen(), "Respawn feedback must close the inventory");
			assertEquals(0, user.meta().connection().feedbackQueue().size());

			MutableWindowClosePacket receivedClose = WirePackets.createPlayPacket(PacketType.Client.CLOSE_WINDOW, user.protocolVersion());
			receivedClose.setContainerId(1);
			for (boolean cancelled : new boolean[]{false, true}) {
				user.meta().inventory().updateInventoryOpenState(true);
				PacketReceiveEvent received = new PacketReceiveEvent(user, PacketType.Client.CLOSE_WINDOW, receivedClose);
				received.setCancelled(cancelled);
				PacketEventBus.dispatch(received);
				assertFalse(user.meta().inventory().inventoryOpen(), "Client close must update inventory immediately, even when cancelled");
				assertEquals(cancelled, received.isCancelled());
				assertEquals(0, user.meta().connection().feedbackQueue().size(), "Client close must not request feedback");
			}

			MutableWindowClosePacket close = WirePackets.createPlayPacket(PacketType.Server.CLOSE_WINDOW, user.protocolVersion());
			close.setContainerId(1);
			user.meta().inventory().updateInventoryOpenState(true);
			PacketSendEvent cancelled = new PacketSendEvent(user, PacketType.Server.CLOSE_WINDOW, close);
			cancelled.setCancelled(true);
			assertTrue(PacketEventBus.dispatch(cancelled).isEmpty());
			assertEquals(0, user.meta().connection().feedbackQueue().size());
			assertTrue(user.meta().inventory().inventoryOpen());

			List<TypedPacketContract> sent = PacketEventBus.dispatch(new PacketSendEvent(user, PacketType.Server.CLOSE_WINDOW, close));
			assertEquals(2, sent.size());
			assertEquals(PacketType.Server.CLOSE_WINDOW, sent.get(0).type());
			assertSame(close, sent.get(0).contract());
			assertEquals(user.protocolVersion() >= 755 ? PacketType.Server.PING : PacketType.Server.TRANSACTION_OUT, sent.get(1).type());
			assertEquals(1, user.meta().connection().feedbackQueue().size());
			assertTrue(user.meta().inventory().inventoryOpen(), "Inventory must stay open until feedback is acknowledged");
			PacketReceiveEvent acknowledgement = acknowledgement(user, sent.get(1));
			PacketEventBus.dispatch(acknowledgement);
			assertTrue(acknowledgement.isCancelled());
			assertFalse(user.meta().inventory().inventoryOpen());
			assertEquals(0, user.meta().connection().feedbackQueue().size());
		} finally {
			linker.removeSubscriptionsOf(tracker);
		}
	}

	private static void verifyClientCommands(User user, AtomicReference<BlockCacheEntry> occupiedBlock) {
		int protocol = user.protocolVersion();
		MutableClientCommandPacket packet = WirePackets.createPlayPacket(PacketType.Client.CLIENT_COMMAND, protocol);
		InventoryMetadata inventory = user.meta().inventory();
		inventory.updateInventoryOpenState(false);
		for (ClientCommand command : ClientCommand.values()) {
			if (command == ClientCommand.OPEN_INVENTORY || (command == ClientCommand.REQUEST_GAMERULE_VALUES && protocol < 775)) continue;
			packet.setCommand(command);
			PacketEventBus.dispatch(new PacketReceiveEvent(user, PacketType.Client.CLIENT_COMMAND, packet));
			assertFalse(inventory.inventoryOpen(), command + " in " + protocol);
		}
		if (protocol < 335) {
			packet.setCommand(ClientCommand.OPEN_INVENTORY);
			occupiedBlock.set(new BlockCacheEntry(BlockShapes.emptyShape(), BlockShapes.emptyShape(), BlockTypeAccess.NETHER_PORTAL, 0));
			PacketEventBus.dispatch(new PacketReceiveEvent(user, PacketType.Client.CLIENT_COMMAND, packet));
			assertFalse(inventory.inventoryOpen(), "Opening inside a nether portal is ignored");
			occupiedBlock.set(BlockCacheEntry.empty());
			for (boolean cancelled : new boolean[]{false, true}) {
				inventory.updateInventoryOpenState(false);
				PacketReceiveEvent event = new PacketReceiveEvent(user, PacketType.Client.CLIENT_COMMAND, packet);
				event.setCancelled(cancelled);
				PacketEventBus.dispatch(event);
				assertTrue(inventory.inventoryOpen(), "Client open must take effect immediately, even when cancelled");
				assertEquals(cancelled, event.isCancelled());
			}
		}
		assertEquals(0, user.meta().connection().feedbackQueue().size());
	}

	private static void verifyInventoryOpenPackets(User user, AtomicReference<BlockCacheEntry> occupiedBlock) {
		int protocol = user.protocolVersion();
		OpenScreenPacket ordinary = WirePackets.createPlayPacket(PacketType.Server.OPEN_WINDOW, protocol);
		OpenScreenPacket beacon = roundTrip(PacketDirection.CLIENTBOUND, PacketType.Server.OPEN_WINDOW, protocol, output -> {
			if (protocol < 477) {
				output.writeByte(1);
				ByteBufStreamCodecs.VARINT_STRING.encode(output, "minecraft:beacon");
				ByteBufStreamCodecs.VARINT_STRING.encode(output, "\"Custom\"");
				output.writeByte(1);
			} else {
				ByteBufStreamCodecs.VARINT.encode(output, 1);
				ByteBufStreamCodecs.VARINT.encode(output, protocol < 765 ? 8 : 9);
				if (protocol < 765) ByteBufStreamCodecs.VARINT_STRING.encode(output, "\"Custom\"");
				else ByteBufStreamCodecs.TRUSTED_TAG.encode(output, new StringTag("Custom"));
			}
		});
		InventoryMetadata inventory = user.meta().inventory();
		for (OpenScreenPacket packet : new OpenScreenPacket[]{ordinary, beacon}) {
			inventory.updateInventoryOpenState(false);
			inventory.forceInventoryOnClickOpen = packet.isBeaconMenu();
			PacketSendEvent cancelled = new PacketSendEvent(user, PacketType.Server.OPEN_WINDOW, packet);
			cancelled.setCancelled(true);
			assertTrue(PacketEventBus.dispatch(cancelled).isEmpty());
			assertEquals(packet.isBeaconMenu(), inventory.forceInventoryOnClickOpen);
			assertFalse(inventory.inventoryOpen());
			assertEquals(0, user.meta().connection().feedbackQueue().size());
		}
		inventory.updateInventoryOpenState(true);
		inventory.forceInventoryOnClickOpen = true;
		List<TypedPacketContract> sentBeacon = PacketEventBus.dispatch(new PacketSendEvent(user, PacketType.Server.OPEN_WINDOW, beacon));
		assertEquals(1, sentBeacon.size());
		assertSame(beacon, sentBeacon.get(0).contract());
		assertEquals(0, user.meta().connection().feedbackQueue().size());
		assertFalse(inventory.forceInventoryOnClickOpen);
		assertTrue(inventory.inventoryOpen());

		BlockCacheEntry portal = new BlockCacheEntry(BlockShapes.emptyShape(), BlockShapes.emptyShape(), BlockTypeAccess.NETHER_PORTAL, 0);
		for (boolean inPortalAtAck : new boolean[]{true, false}) {
			inventory.updateInventoryOpenState(false);
			inventory.forceInventoryOnClickOpen = false;
			occupiedBlock.set(inPortalAtAck ? BlockCacheEntry.empty() : portal);
			List<TypedPacketContract> sent = dispatchFeedbackGroup(new PacketSendEvent(user, PacketType.Server.OPEN_WINDOW, ordinary));
			assertEquals(2, sent.size());
			assertSame(ordinary, sent.get(0).contract());
			assertEquals(protocol >= 755 ? PacketType.Server.PING : PacketType.Server.TRANSACTION_OUT, sent.get(1).type());
			assertEquals(1, user.meta().connection().feedbackQueue().size());
			assertTrue(inventory.forceInventoryOnClickOpen);
			assertFalse(inventory.inventoryOpen());
			occupiedBlock.set(inPortalAtAck ? portal : BlockCacheEntry.empty());
			PacketReceiveEvent ack = acknowledgement(user, sent.get(1));
			PacketEventBus.dispatch(ack);
			assertTrue(ack.isCancelled());
			assertEquals(0, user.meta().connection().feedbackQueue().size());
			assertEquals(!inPortalAtAck, inventory.inventoryOpen());
		}
	}

	private static void verifyFeedbackInServerBundle(FeedbackSender sender, User user, boolean doubleSync) throws Exception {
		int protocol = user.protocolVersion();
		PacketType.Server delimiter = PacketType.Server.BUNDLE_DELIMITER;
		PacketType.Server target = PacketType.Server.KEEP_ALIVE_OUT;
		PacketType.Server ping = PacketType.Server.PING;
		List<String> callbacks = new ArrayList<>();
		PacketSubscription listener = event -> {
			PacketSendEvent send = (PacketSendEvent) event;
			if (doubleSync) {
				sender.doubleSynchronize(send.user(), send, () -> callbacks.add("before"), () -> callbacks.add("after"));
			} else {
				sender.synchronize(send.user(), send, () -> callbacks.add("after"));
			}
		};
		EmbeddedChannel channel = new EmbeddedChannel();
		channel.pipeline().addLast("via-decoder", new ChannelInboundHandlerAdapter());
		channel.pipeline().addLast("via-encoder", new ChannelOutboundHandlerAdapter());
		PacketEventBus.addListener(listener, target);
		try (Binding binding = Binding.bind(channel)) {
			Field boundUser = Binding.class.getDeclaredField("user");
			boundUser.setAccessible(true);
			boundUser.set(binding, user);
			HandshakeWirePacket handshake = new HandshakeWirePacket(protocol, "localhost", 25565, ConnectionState.LOGIN);
			assertTrue(channel.writeInbound(encode(ConnectionState.HANDSHAKING, handshake.typedTo(PacketType.Client.SET_PROTOCOL), protocol)));
			channel.<ByteBuf>readInbound().release();
			ByteBuf loginSuccess = Unpooled.buffer();
			ByteBufStreamCodecs.VARINT.encode(loginSuccess, PacketTypeRegistries.clientbound().idOf(ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocol));
			assertTrue(channel.writeOutbound(loginSuccess));
			channel.<ByteBuf>readOutbound().release();
			assertEquals(ConnectionState.PLAY, binding.connectionState());

			TypedPacketContract boundary = WirePackets.createPlayPacket(delimiter, protocol).typedTo(delimiter);
			TypedPacketContract payload = keepAlive(123).typedTo(target);
			List<PacketType.Server> expectedPackets = doubleSync
				? List.of(delimiter, ping, target, ping, delimiter)
				: List.of(delimiter, target, ping, delimiter);
			List<String> expectedCallbacks = doubleSync ? List.of("before", "after") : List.of("after");
			for (boolean serverBundle : new boolean[]{true, false}) {
				if (serverBundle) {
					assertTrue(channel.writeOutbound(encode(ConnectionState.PLAY, boundary, protocol)));
				}
				assertTrue(channel.writeOutbound(encode(ConnectionState.PLAY, payload, protocol)));
				if (serverBundle) {
					assertTrue(channel.writeOutbound(encode(ConnectionState.PLAY, boundary, protocol)));
				}
				assertTrue(callbacks.isEmpty());
				assertEquals(expectedPackets, readAndAcknowledgeFeedback(channel, protocol),
					serverBundle ? "Within the server's bundle" : "After the server closed its bundle");
				assertEquals(expectedCallbacks, callbacks);
				callbacks.clear();
			}
		} finally {
			PacketEventBus.removeListener(listener);
			channel.finishAndReleaseAll();
		}
	}

	private static ByteBuf encode(ConnectionState state, TypedPacketContract packet, int protocol) {
		ByteBuf bytes = Unpooled.buffer();
		WirePackets.encodePacket(state, packet, protocol, bytes);
		return bytes;
	}

	private static List<PacketType.Server> readAndAcknowledgeFeedback(EmbeddedChannel channel, int protocol) {
		List<PacketType.Server> types = new ArrayList<>();
		ByteBuf bytes;
		while ((bytes = channel.readOutbound()) != null) {
			try {
				int id = ByteBufStreamCodecs.VARINT.decode(bytes);
				PacketType.Server type = PacketTypeRegistries.clientbound().typeOf(ConnectionState.PLAY, id, protocol);
				types.add(type);
				if (type == PacketType.Server.PING) {
					TypedPacketContract pong = new PongWirePacket(bytes.readInt()).typedTo(PacketType.Client.PONG);
					assertFalse(channel.writeInbound(encode(ConnectionState.PLAY, pong, protocol)));
				}
			} finally {
				bytes.release();
			}
		}
		return types;
	}

	private static List<TypedPacketContract> dispatchFeedbackGroup(PacketSendEvent event) {
		int protocol = event.user().protocolVersion();
		BundleDelimiterPacket delimiter = protocol >= 762 ? WirePackets.createPlayPacket(PacketType.Server.BUNDLE_DELIMITER, protocol) : null;
		List<TypedPacketContract> sent = PacketEventBus.dispatchSendPlan(event).outgoingPackets(delimiter, false);
		if (delimiter == null) {
			return sent;
		}
		assertEquals(PacketType.Server.BUNDLE_DELIMITER, sent.get(0).type());
		assertEquals(PacketType.Server.BUNDLE_DELIMITER, sent.get(sent.size() - 1).type());
		return sent.subList(1, sent.size() - 1);
	}

	private static void verifyMotionFeedback(User user, TypedPacketContract packet) {
		PacketSendEvent cancelled = new PacketSendEvent(user, (PacketType.Server) packet.type(), packet.contract());
		cancelled.setCancelled(true);
		assertTrue(PacketEventBus.dispatch(cancelled).isEmpty());
		assertEquals(0, user.meta().movement().pendingVelocityPackets.get());
		List<TypedPacketContract> sent = PacketEventBus.dispatch(new PacketSendEvent(user, (PacketType.Server) packet.type(), packet.contract()));
		assertEquals(3, sent.size());
		assertEquals(1, user.meta().movement().pendingVelocityPackets.get());
		assertEquals(2, user.meta().connection().feedbackQueue().size());
		PacketEventBus.dispatch(acknowledgement(user, sent.get(0)));
		assertEquals(1, user.meta().movement().pendingVelocityPackets.get());
		PacketEventBus.dispatch(acknowledgement(user, sent.get(2)));
		assertEquals(0, user.meta().movement().pendingVelocityPackets.get());
		assertEquals(0, user.meta().connection().feedbackQueue().size());
	}


	private static void verifyWireMotion(User user, TypedPacketContract packet, boolean nativeSend) throws Exception {
		EmbeddedChannel channel = new EmbeddedChannel();
		channel.pipeline().addLast("via-decoder", new ChannelInboundHandlerAdapter());
		channel.pipeline().addLast("via-encoder", new ChannelOutboundHandlerAdapter());
		try (Binding binding = Binding.bind(channel)) {
			for (Map.Entry<String, Object> entry : Map.<String, Object>of("user", user,
				"protocolVersion", user.protocolVersion(), "connectionState", ConnectionState.PLAY).entrySet()) {
				Field field = Binding.class.getDeclaredField(entry.getKey());
				field.setAccessible(true);
				field.set(binding, entry.getValue());
			}
			if (nativeSend) {
				assertTrue(binding.send(packet).isSuccess());
			} else {
				ByteBuf original = Unpooled.buffer();
				WirePackets.encodePacket(ConnectionState.PLAY, packet, user.protocolVersion(), original);
				assertTrue(channel.writeOutbound(original));
				assertEquals(0, original.refCnt());
			}
			assertEquals(1, user.meta().movement().pendingVelocityPackets.get());
			assertEquals(2, user.meta().connection().feedbackQueue().size());
			for (int index = 0; index < 3; index++) {
				ByteBuf output = channel.readOutbound();
				assertNotNull(output);
				try {
					de.jpx3.intave.packet.contract.PacketContract decoded = WirePackets.decodePacket(
						PacketDirection.CLIENTBOUND, ConnectionState.PLAY, user.protocolVersion(), output);
					if (index == 1) {
						assertEquals(((MotionUpdate) packet.contract()).motion().motionY(), ((MotionUpdate) decoded).motion().motionY(), 0.001);
						continue;
					}
					PacketType.Server feedbackType = user.protocolVersion() >= 755 ? PacketType.Server.PING : PacketType.Server.TRANSACTION_OUT;
					PacketReceiveEvent ack = acknowledgement(user, decoded.typedTo(feedbackType));
					ByteBuf response = Unpooled.buffer();
					WirePackets.encodePacket(ConnectionState.PLAY, ack.packet().typedTo(ack.packetId()), user.protocolVersion(), response);
					assertFalse(channel.writeInbound(response));
					assertEquals(0, response.refCnt());
				} finally {
					output.release();
				}
			}
			assertNull(channel.readOutbound());
			assertEquals(0, user.meta().movement().pendingVelocityPackets.get());
			assertEquals(0, user.meta().connection().feedbackQueue().size());
		} finally {
			channel.finishAndReleaseAll();
		}
	}
}
