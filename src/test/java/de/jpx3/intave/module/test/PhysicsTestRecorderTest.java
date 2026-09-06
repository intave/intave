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

package de.jpx3.intave.module.test;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.module.test.record.MovementFrameState;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.module.test.record.action.AttackReduction;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class PhysicsTestRecorderTest {
	@TempDir
	Path directory;

	@BeforeEach
	void setServerVersion() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
	}

	@Test
	void inactivePlayersDoNotReadMovementOrCaptureActions() {
		PhysicsTestRecorder recorder = new PhysicsTestRecorder();
		User user = user();

		assertFalse(recorder.isRecording(user));
		assertNull(recorder.recordingSessionOf(user));
		// No packet or movement state may be accessed while recording is disabled.
		recorder.on(user, null);
		recorder.recordAttackReduction(user);
		assertNull(recorder.beginVelocity(user, new Motion(1, 2, 3)));
		recorder.completeVelocity(null);
		assertNull(recorder.recordingSessionOf(user));
	}

	@Test
	void manualToggleOnlyAffectsTheSelectedPlayer() {
		PhysicsTestRecorder recorder = new PhysicsTestRecorder();
		User first = user();
		User second = user();
		assertFalse(recorder.isRecording(second));

		recorder.setRecordingStatus(first, true);
		assertTrue(recorder.isRecording(first));
		assertFalse(recorder.isRecording(second));
		assertFalse(recorder.isRecording(user()));
		recorder.on(second, null);

		recorder.setRecordingStatus(second, true);
		assertNotSame(recorder.recordingSessionOf(first), recorder.recordingSessionOf(second));
		recorder.setRecordingStatus(first, false);
		assertFalse(recorder.isRecording(first));
		assertTrue(recorder.isRecording(second));
	}

	@Test
	void manualRecordingSavesFramesAndActionsAndCanRestart() throws IOException {
		PhysicsTestRecorder recorder = new PhysicsTestRecorder();
		User user = user();
		recorder.setRecordingStatus(user, true);
		MovementRecording recording = recorder.recordingSessionOf(user);
		assertNotNull(recording);
		insert(recording, 0);
		PhysicsTestRecorder.VelocityCapture velocity = recorder.beginVelocity(user, new Motion(1, 2, 3));
		assertNotNull(velocity);
		recorder.recordAttackReduction(user);
		insert(recording, 1);
		recorder.completeVelocity(velocity);
		insert(recording, 2);

		recorder.setRecordingStatus(user, false);
		recorder.on(user, null);
		recorder.recordAttackReduction(user);
		assertNull(recorder.beginVelocity(user, new Motion(4, 5, 6)));
		Path file = directory.resolve("manual.ptr");
		recorder.saveRecordingDataTo(user, file.toFile());

		MovementRecording saved = MovementRecording.loadFrom(Resources.resourceFromFile(file.toFile()));
		assertEquals(3, saved.frameCount());
		assertEquals(new Position(1, 64, 0), saved.frames().get(1).moveTo());
		assertEquals(47, saved.clientProtocolVersion());
		assertEquals(MinecraftVersions.VER1_21_4, saved.serverVersion());
		assertEquals(2, saved.actions().size());
		AttackReduction attack = assertInstanceOf(AttackReduction.class, saved.actions().get(0));
		assertEquals(TickRange.betweenExclusive(1, 2), attack.tickRange());
		ReceiveVelocity received = assertInstanceOf(ReceiveVelocity.class, saved.actions().get(1));
		assertEquals(new Motion(1, 2, 3), received.motion());
		assertEquals(TickRange.betweenExclusive(1, 3), received.tickRange());
		assertEquals(0, recording.frameCount());
		assertTrue(recording.actions().isEmpty());
		assertNull(recorder.recordingSessionOf(user));

		recorder.setRecordingStatus(user, true);
		MovementRecording restarted = recorder.recordingSessionOf(user);
		assertNotNull(restarted);
		insert(restarted, 3);
		recorder.completeVelocity(velocity);
		restarted.materializeVelocities();
		assertEquals(1, restarted.frameCount());
		assertTrue(restarted.actions().isEmpty());
	}

	private static void insert(MovementRecording recording, double x) {
		recording.insertFrame(
			BoundingBox.empty(), Input.none(), new Position(x, 64, 0), Rotation.zero(),
			new MockFullBlockStaticPlane(), Collections.emptyMap(), false, null,
			MovementFrameState.empty()
		);
	}

	private static User user() {
		UUID id = UUID.randomUUID();
		Player player = (Player) Proxy.newProxyInstance(
			Player.class.getClassLoader(), new Class<?>[]{Player.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "getUniqueId" -> id;
				default -> throw new AssertionError("Unexpected player access: " + method.getName());
			}
		);
		return (User) Proxy.newProxyInstance(
			User.class.getClassLoader(), new Class<?>[]{User.class},
			(proxy, method, args) -> switch (method.getName()) {
				case "hasPlayer" -> true;
				case "player" -> player;
				case "protocolVersion" -> 47;
				default -> throw new AssertionError("Unexpected user access: " + method.getName());
			}
		);
	}
}
