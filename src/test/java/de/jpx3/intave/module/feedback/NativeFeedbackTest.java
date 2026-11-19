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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.dispatch.MovementDispatcher;
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.module.test.PhysicsTestRecorder;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.MotionUpdate;
import de.jpx3.intave.packet.contract.immutable.PingPacket;
import de.jpx3.intave.packet.contract.immutable.TransactionPacket;
import de.jpx3.intave.packet.contract.mutable.MutableEntityVelocityPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.injection.Binding;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.connection.pong.PongWirePacket;
import de.jpx3.intave.packet.wire.entity.velocity.EntityVelocityWirePacket;
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.packet.wire.world.explosion.ExplosionWirePacket;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NativeFeedbackTest {
	@Test
	@SuppressWarnings("unchecked")
	void nativeFeedbackBracketsMotionAndConsumesOnlyItsOwnAcknowledgements() throws Exception {
		Field version = MinecraftVersion.class.getDeclaredField("currentVersion");
		version.setAccessible(true);
		MinecraftVersion previousVersion = (MinecraftVersion) version.get(null);
		MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
		Field poolField = Modules.class.getDeclaredField("pool");
		poolField.setAccessible(true);
		Object pool = poolField.get(null);
		Field mappings = pool.getClass().getDeclaredField("moduleClassMappings");
		mappings.setAccessible(true);
		Map<Class<? extends Module>, Module> modules = (Map<Class<? extends Module>, Module>) mappings.get(pool);
		Map<Class<? extends Module>, Module> savedModules = new HashMap<>(modules);
		modules.put(FeedbackAnalysis.class, new FeedbackAnalysis());
		modules.put(PhysicsTestRecorder.class, new PhysicsTestRecorder());
		PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
		FeedbackReceiver receiver = (FeedbackReceiver) ReflectionFactory.getReflectionFactory()
			.newConstructorForSerialization(FeedbackReceiver.class, Object.class.getConstructor()).newInstance();
		linker.linkSubscriptionsIn(receiver);
		try {
			FeedbackSender sender = new FeedbackSender();
			modules.put(FeedbackSender.class, sender);
			for (int protocol : new int[]{47, 755, 761, 764, 765, 766, 767, 768, 773, 776, 777}) {
				UUID uuid = UUID.randomUUID();
				Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
					if (method.getName().equals("getUniqueId")) return uuid;
					if (method.getName().equals("getEntityId")) return 1;
					if (method.getName().equals("getName")) return "Native feedback test";
					return null;
				});
				User fallback = UserFactory.createFallback();
				User user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class}, (proxy, method, args) -> {
					switch (method.getName()) {
						case "player": return player;
						case "hasPlayer": return true;
						case "protocolVersion": return protocol;
						default: return method.invoke(fallback, args);
					}
				});
				UserRepository.manuallyRegisterUser(player, user);
				try {
					List<String> callbacks = new ArrayList<>();
					PacketSendEvent event = new PacketSendEvent(user, PacketType.Server.ENTITY_VELOCITY, new EntityVelocityWirePacket(1, new Motion()));
					sender.doubleSynchronize(user, event, () -> callbacks.add("before"), () -> callbacks.add("after"));
					List<TypedPacketContract> batch = PacketEventBus.dispatch(event);
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
						ExplosionWirePacket explosion = WirePackets.createPlayPacket(PacketType.Server.EXPLOSION, protocol);
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
		} finally {
			linker.disable();
			modules.clear();
			modules.putAll(savedModules);
			version.set(null, previousVersion);
		}
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

	private static PacketReceiveEvent acknowledgement(User user, TypedPacketContract packet) {
		if (packet.type() == PacketType.Server.PING) {
			return new PacketReceiveEvent(user, PacketType.Client.PONG, new PongWirePacket(((PingPacket) packet.contract()).id()));
		}
		assertEquals(PacketType.Server.TRANSACTION_OUT, packet.type());
		return new PacketReceiveEvent(user, PacketType.Client.TRANSACTION_IN,
			new TransactionWirePacket(0, ((TransactionPacket) packet.contract()).actionNumber(), true));
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
