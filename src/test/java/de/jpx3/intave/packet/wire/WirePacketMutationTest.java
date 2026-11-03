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

package de.jpx3.intave.packet.wire;

import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.immutable.ClientSettingsPacket;
import de.jpx3.intave.packet.contract.mutable.Mutable;
import de.jpx3.intave.packet.contract.mutable.MutableTransactionPacket;
import de.jpx3.intave.packet.event.PacketEvent;
import de.jpx3.intave.packet.wire.client.settings.ClientSettingsWithHandWirePacket;
import de.jpx3.intave.packet.wire.cookie.response.CookieResponseWirePacket;
import de.jpx3.intave.packet.wire.entity.mount.MountEntityWirePacket;
import de.jpx3.intave.packet.wire.entity.relativemove.EntityRelativeMoveWirePacket;
import de.jpx3.intave.packet.wire.entity.velocity.EntityVelocityWirePacket;
import de.jpx3.intave.packet.wire.payload.inbound.PayloadInWirePacket;
import de.jpx3.intave.packet.wire.player.ground.PlayerGroundWirePacket;
import de.jpx3.intave.packet.wire.player.position.PlayerPositionWirePacket;
import de.jpx3.intave.packet.wire.player.rotation.PlayerRotationWirePacket;
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.packet.wire.world.border.WorldBorderSetCenterWirePacket;
import de.jpx3.intave.packet.wire.world.border.WorldBorderWirePacket;
import de.jpx3.intave.registry.PacketIdRegistry;
import de.jpx3.intave.share.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class WirePacketMutationTest {
	@Test
	void scalarPacketsStayCleanUntilASetterIsCalled() {
		TransactionWirePacket packet = new TransactionWirePacket(3, (short) 7, true);
		MutableTransactionPacket mutable = packet;
		assertFalse(mutable.couldHaveBeenMutated());
		assertEquals(3, packet.containerId());
		assertEquals(7, packet.actionNumber());
		assertTrue(packet.accepted());
		assertFalse(mutable.couldHaveBeenMutated());

		mutable.setActionNumber((short) 8);
		assertTrue(mutable.couldHaveBeenMutated());
		mutable.setActionNumber((short) 7);
		assertTrue(mutable.couldHaveBeenMutated(), "Returning to the original value must not clear tracking");
	}

	@Test
	void decodedScalarPacketsStartCleanAndEncodeTheirMutations() {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			input.writeByte(3).writeShort(7).writeBoolean(true);
			TransactionWirePacket packet = decodePayload(TransactionWirePacket.class, input);
			assertFalse(packet.couldHaveBeenMutated());
			packet.setAccepted(false);
			encodePayload(packet, output);
			assertTrue(packet.couldHaveBeenMutated());
			assertFalse(decodePayload(TransactionWirePacket.class, output).accepted());
		} finally {
			input.release();
			output.release();
		}
	}

	@Test
	void decodedPositionsStayCleanUntilEditingIsRequested() {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			encodePayload(new PlayerPositionWirePacket(new Position(1, 2, 3), 1), input);
			PlayerPositionWirePacket packet = decodePayload(PlayerPositionWirePacket.class, input);
			PacketEvent event = new PacketEvent(null, PacketDirection.SERVERBOUND, packet);
			Position original = packet.position();
			assertTrue(original.isImmutable());
			assertEquals(new Position(1, 2, 3), original);
			assertTrue(packet.hasPosition());
			encodePayload(packet, output);
			assertFalse(event.dirty());

			Position editable = packet.mutablePosition();
			assertFalse(editable.isImmutable());
			assertNotSame(original, editable);
			assertSame(editable, packet.mutablePosition());
			assertTrue(event.dirty());
			editable.setX(9);
			assertEquals(1, original.getX());
			output.clear();
			encodePayload(packet, output);
			PlayerPositionWirePacket decoded = decodePayload(PlayerPositionWirePacket.class, output);
			assertEquals(new Position(9, 2, 3), decoded.position());
			assertEquals(1, decoded.flags());
		} finally {
			input.release();
			output.release();
		}
	}

	@Test
	void alreadyMutablePositionsAreTrackedAndReused() {
		Position position = Position.mutableOf(1, 2, 3);
		PlayerPositionWirePacket packet = new PlayerPositionWirePacket(position, 0);
		assertFalse(packet.couldHaveBeenMutated());
		assertSame(position, packet.position());
		assertTrue(packet.couldHaveBeenMutated());
		assertSame(position, packet.mutablePosition());
		packet.mutablePosition().setY(7);
		assertEquals(7, packet.position().getY());

		PlayerPositionWirePacket replaced = new PlayerPositionWirePacket();
		replaced.setPosition(new Position(4, 5, 6));
		assertTrue(replaced.couldHaveBeenMutated(), "Replacing a position still changes the packet");
	}

	@Test
	void absentPositionsStayCleanAndAreNotCreatedForEditing() {
		PlayerGroundWirePacket ground = new PlayerGroundWirePacket();
		assertNull(ground.mutablePosition());
		assertFalse(ground.couldHaveBeenMutated());
		PlayerRotationWirePacket rotation = new PlayerRotationWirePacket();
		assertNull(rotation.mutablePosition());
		assertFalse(rotation.couldHaveBeenMutated());
		PlayerPositionWirePacket nullable = new PlayerPositionWirePacket(null, 0);
		assertNull(nullable.mutablePosition());
		assertFalse(nullable.couldHaveBeenMutated());
		WorldBorderWirePacket border = new WorldBorderWirePacket(
			WorldBorderWirePacket.Action.SET_SIZE, Position.mutableOf(1, 0, 2), 100, 200, 0, 29999984, 5, 15);
		assertNull(border.center());
		assertNull(border.mutableCenter());
		assertFalse(border.couldHaveBeenMutated());
	}

	@Test
	void borderCentersBecomeMutableOnlyWhenRequested() {
		Position original = new Position(1, 0, 2);
		WorldBorderSetCenterWirePacket packet = new WorldBorderSetCenterWirePacket(original);
		assertSame(original, packet.center());
		assertFalse(packet.couldHaveBeenMutated());
		Position editable = packet.mutableCenter();
		assertFalse(editable.isImmutable());
		assertSame(editable, packet.mutableCenter());
		assertTrue(packet.couldHaveBeenMutated());
		editable.setZ(9);
		assertEquals(2, original.getZ());
		ByteBuf output = Unpooled.buffer();
		try {
			encodePayload(packet, output);
			assertEquals(new Position(1, 0, 9), decodePayload(WorldBorderSetCenterWirePacket.class, output).center());
		} finally {
			output.release();
		}
	}

	@Test
	void sameValueSetterCallsAreConservativelyMarked() {
		TransactionWirePacket packet = new TransactionWirePacket();
		packet.setContainerId(packet.containerId());
		assertTrue(packet.couldHaveBeenMutated());
	}

	@Test
	void subclassSettersShareTheMutationFlag() {
		ClientSettingsWithHandWirePacket packet = new ClientSettingsWithHandWirePacket();
		assertFalse(packet.couldHaveBeenMutated());
		packet.setMainHand(ClientSettingsPacket.MainHand.LEFT);
		assertTrue(packet.couldHaveBeenMutated());
	}

	@Test
	void returnedArraysMarkPossibleChanges() {
		byte[] payload = {1, 2};
		CookieResponseWirePacket packet = new CookieResponseWirePacket(MinecraftKey.withDefaultNamespace("test"), payload);
		assertFalse(packet.couldHaveBeenMutated());
		byte[] exposed = packet.payload();
		assertTrue(packet.couldHaveBeenMutated());
		exposed[0] = 9;
		exposed[1] = 8;
		ByteBuf output = Unpooled.buffer();
		try {
			encodePayload(packet, output);
			assertArrayEquals(new byte[]{9, 8}, decodePayload(CookieResponseWirePacket.class, output).payload());
			assertTrue(packet.couldHaveBeenMutated());
		} finally {
			output.release();
		}
	}

	@Test
	void decodedPayloadStartsCleanUntilItsArrayIsExposed() {
		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			encodePayload(new PayloadInWirePacket("test", new byte[]{1, 2}), input);
			PayloadInWirePacket packet = decodePayload(PayloadInWirePacket.class, input);
			PacketEvent event = new PacketEvent(null, PacketDirection.SERVERBOUND, packet);
			assertEquals("test", packet.tag());
			assertFalse(packet.couldHaveBeenMutated());
			assertFalse(event.dirty());
			encodePayload(packet, output);
			assertFalse(packet.couldHaveBeenMutated());
			byte[] exposed = packet.payload();
			assertTrue(event.dirty());
			exposed[0] = 9;
			output.clear();
			encodePayload(packet, output);
			assertArrayEquals(new byte[]{9, 2}, decodePayload(PayloadInWirePacket.class, output).payload());
		} finally {
			input.release();
			output.release();
		}
	}

	@Test
	void exposedMutableValuesAndNestedElementsAreConservativelyMarked() {
		Motion motion = new Motion(1, 2, 3);
		EntityVelocityWirePacket velocity = new EntityVelocityWirePacket(12, motion);
		assertFalse(velocity.couldHaveBeenMutated());
		Motion exposed = velocity.motion();
		assertTrue(velocity.couldHaveBeenMutated());
		exposed.motionX = 5;
		assertEquals(5, velocity.motion().motionX);

		RawVector3d[] delta = {new RawVector3d(1, 2, 3)};
		EntityRelativeMoveStep step = new EntityRelativeMoveStep() {
			@Override
			public RawVector3d delta() {
				return delta[0];
			}

			@Override
			public int delayTicks() {
				return 0;
			}
		};
		EntityRelativeMoveWirePacket movement = new EntityRelativeMoveWirePacket(
			12, false, false, new ArrayList<>(Collections.singletonList(step)));
		assertFalse(movement.couldHaveBeenMutated());
		assertSame(step, movement.steps().get(0));
		assertTrue(movement.couldHaveBeenMutated());
		delta[0] = new RawVector3d(4, 2, 3);
		assertEquals(4, movement.steps().get(0).delta().x());
	}

	@Test
	void eventsDetectInPlaceEditsAndReplacement() {
		TransactionWirePacket packet = new TransactionWirePacket();
		PacketEvent event = new PacketEvent(null, PacketDirection.CLIENTBOUND, packet);
		assertFalse(event.dirty());
		packet.setAccepted(true);
		assertTrue(event.dirty());

		PacketEvent replaced = new PacketEvent(null, PacketDirection.CLIENTBOUND, new TransactionWirePacket());
		assertFalse(replaced.dirty());
		replaced.setPacket(new TransactionWirePacket());
		assertTrue(replaced.dirty());

		MountEntityWirePacket mount = new MountEntityWirePacket(1, new int[]{2});
		PacketEvent aliased = new PacketEvent(null, PacketDirection.CLIENTBOUND, mount);
		assertFalse(aliased.dirty());
		mount.passengerIds();
		assertTrue(aliased.dirty());
	}

	@Test
	void untrackedImplementationsUseTheConservativeDefault() {
		class UntrackedPacket implements PacketContract, Mutable {
			@Override
			public boolean appliesTo(ConnectionState state, PacketType type) {
				return true;
			}
		}
		UntrackedPacket packet = new UntrackedPacket();
		assertTrue(packet.couldHaveBeenMutated());
		assertTrue(new PacketEvent(null, PacketDirection.CLIENTBOUND, packet).dirty());
	}

	@Test
	void everyRegisteredWirePacketSupportsTrackingAndEverySetterMarksIt() throws ReflectiveOperationException {
		Set<Class<? extends PacketContract>> packetClasses = new HashSet<>();
		for (PacketDirection direction : PacketDirection.values()) {
			PacketIdRegistry<?> registry = registry(direction);
			for (int protocol : registry.supportedProtocolVersions()) {
				for (ConnectionState state : ConnectionState.values()) {
					for (Map.Entry<? extends PacketType, Integer> entry : registry.packetIds(state, protocol).entrySet()) {
						if (WirePackets.packetIdKnown(direction, state, entry.getValue(), protocol)) {
							packetClasses.add(WirePackets.createPacket(direction, state, entry.getValue(), protocol).getClass());
						}
					}
				}
			}
		}
		assertTrue(packetClasses.size() > 100, "Exercise version-specific implementations as well as base packets");
		Set<Method> checked = new HashSet<>();
		for (Class<? extends PacketContract> packetClass : packetClasses) {
			assertTrue(Mutable.class.isAssignableFrom(packetClass), packetClass.getName());
			assertFalse(((Mutable) packetClass.getConstructor().newInstance()).couldHaveBeenMutated(), packetClass.getName());
			for (Method method : packetClass.getMethods()) {
				if (method.getParameterCount() == 0 && !method.getName().startsWith("mutable")
					&& (method.getReturnType() == Position.class || method.getReturnType() == BlockPosition.class
					|| method.getReturnType() == PositionAndRotation.class)) {
					PacketContract packet = packetClass.getConstructor().newInstance();
					Object position = method.invoke(packet);
					if (position instanceof Position) assertTrue(((Position) position).isImmutable(), method.toString());
					assertFalse(((Mutable) packet).couldHaveBeenMutated(), method.toString());
				}
				if (!method.getName().startsWith("set") || Modifier.isStatic(method.getModifiers()) || !checked.add(method)) continue;
				PacketContract packet = packetClass.getConstructor().newInstance();
				Object[] arguments = Arrays.stream(method.getParameterTypes()).map(WirePacketMutationTest::argument).toArray();
				try {
					method.invoke(packet, arguments);
				} catch (InvocationTargetException failure) {
					// Invalid values and unsupported setters may also conservatively mark the packet.
					assertInstanceOf(RuntimeException.class, failure.getCause());
				}
				assertTrue(((Mutable) packet).couldHaveBeenMutated(), method.toString());
			}
		}
		assertTrue(checked.size() >= 200, "Cover both contract setters and concrete wire setters");
	}

	private static Object argument(Class<?> type) {
		if (type == boolean.class) return false;
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		if (type == double.class) return 0D;
		if (type == String.class) return "";
		if (type == PositionMoveRotation.class) return new PositionMoveRotation(new Position(), Motion.newEmpty(), Rotation.zero());
		if (type.isEnum()) return type.getEnumConstants()[0];
		if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
		return null;
	}
}
