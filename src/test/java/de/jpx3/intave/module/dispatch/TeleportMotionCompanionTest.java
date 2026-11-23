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

package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.MotionUpdate;
import de.jpx3.intave.packet.contract.mutable.MutableExplosionPacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TeleportMotionCompanionTest {
	@Test
	void companionsUseClientWireFormatsWithoutSharingTeleportMotion() throws Exception {
		Field currentVersion = MinecraftVersion.class.getDeclaredField("currentVersion");
		currentVersion.setAccessible(true);
		MinecraftVersion previous = (MinecraftVersion) currentVersion.get(null);
		try {
			MinecraftVersion.setCurrent(new MinecraftVersion("1.20.4"));
			TeleportController controller = new TeleportController();
			Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
				(proxy, method, arguments) -> method.getName().equals("getEntityId") ? 42 : null);
			for (int protocol : new int[]{47, 755, 761, 765, 766, 767, 768, 773, 776, 777}) {
				User user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class},
					(proxy, method, arguments) -> method.getName().equals("player") ? player
						: method.getName().equals("protocolVersion") ? protocol : null);
				for (boolean additive : new boolean[]{false, true}) {
					Teleport teleport = new Teleport(1, OptionalInt.empty(),
						new PositionMoveRotation(new Position(1, 64, 2), new Motion(0.125, -0.25, 0.5), Rotation.zero()),
						EnumSet.noneOf(Relative.class));
					teleport.setAdditiveMotionPacket(additive);
					TypedPacketContract companion = controller.motionCompanionPacket(user, teleport);
					assertEquals(additive ? PacketType.Server.EXPLOSION : PacketType.Server.ENTITY_VELOCITY, companion.type());
					ByteBuf encoded = Unpooled.buffer();
					try {
						WirePackets.encodePacket(ConnectionState.PLAY, companion, protocol, encoded);
						MotionUpdate decoded = (MotionUpdate) WirePackets.decodePacket(PacketDirection.CLIENTBOUND, ConnectionState.PLAY, protocol, encoded);
						assertEquals(-0.25, decoded.motion().motionY(), 0.001);
						if (additive) {
							assertEquals(-1.0E9, ((MutableExplosionPacket) decoded).y());
							assertEquals(0, ((MutableExplosionPacket) decoded).power());
							if (protocol == 777) assertEquals(0, encoded.getByte(encoded.writerIndex() - 1));
						}
						((MotionUpdate) companion.contract()).motion().setMotionY(99);
						assertEquals(-0.25, teleport.change().motion().motionY());
					} finally {
						encoded.release();
					}
					MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
					assertNull(controller.motionCompanionPacket(user, teleport));
					MinecraftVersion.setCurrent(new MinecraftVersion("1.20.4"));
				}
			}
		} finally {
			currentVersion.set(null, previous);
		}
	}
}
