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
import de.jpx3.intave.module.linker.packetperfect.PacketPerfectSubscriptionLinker;
import de.jpx3.intave.module.test.PhysicsTestRecorder;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.PingPacket;
import de.jpx3.intave.packet.contract.immutable.TransactionPacket;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.wire.connection.pong.PongWirePacket;
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.user.User;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class NativeFeedbackTestSupport implements AutoCloseable {
	public final PacketPerfectSubscriptionLinker linker = new PacketPerfectSubscriptionLinker();
	final FeedbackSender sender;
	private final Field version;
	private final MinecraftVersion previousVersion;
	private final Map<Class<? extends Module>, Module> modules;
	private final Map<Class<? extends Module>, Module> savedModules;

	@SuppressWarnings("unchecked")
	public NativeFeedbackTestSupport() throws ReflectiveOperationException {
		version = MinecraftVersion.class.getDeclaredField("currentVersion");
		version.setAccessible(true);
		previousVersion = (MinecraftVersion) version.get(null);
		MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
		Field poolField = Modules.class.getDeclaredField("pool");
		poolField.setAccessible(true);
		Object pool = poolField.get(null);
		Field mappings = pool.getClass().getDeclaredField("moduleClassMappings");
		mappings.setAccessible(true);
		modules = (Map<Class<? extends Module>, Module>) mappings.get(pool);
		savedModules = new HashMap<>(modules);
		modules.put(FeedbackAnalysis.class, new FeedbackAnalysis());
		modules.put(PhysicsTestRecorder.class, new PhysicsTestRecorder());
		FeedbackReceiver receiver = (FeedbackReceiver) ReflectionFactory.getReflectionFactory()
			.newConstructorForSerialization(FeedbackReceiver.class, Object.class.getConstructor()).newInstance();
		linker.linkSubscriptionsIn(receiver);
		sender = new FeedbackSender();
		modules.put(FeedbackSender.class, sender);
	}

	public static PacketReceiveEvent acknowledgement(User user, TypedPacketContract packet) {
		if (packet.type() == PacketType.Server.PING) {
			return new PacketReceiveEvent(user, PacketType.Client.PONG, new PongWirePacket(((PingPacket) packet.contract()).id()));
		}
		assertEquals(PacketType.Server.TRANSACTION_OUT, packet.type());
		return new PacketReceiveEvent(user, PacketType.Client.TRANSACTION_IN,
			new TransactionWirePacket(0, ((TransactionPacket) packet.contract()).actionNumber(), true));
	}

	@Override
	public void close() throws IllegalAccessException {
		linker.disable();
		modules.clear();
		modules.putAll(savedModules);
		version.set(null, previousVersion);
	}
}
