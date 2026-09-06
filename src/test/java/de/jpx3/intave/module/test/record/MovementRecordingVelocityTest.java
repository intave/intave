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

package de.jpx3.intave.module.test.record;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.share.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementRecordingVelocityTest {
	private final MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();

	@BeforeEach
	void setServerVersion() {
		de.jpx3.intave.adapter.MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
	}

	@Test
	void materializesCompletedVelocityWithNativeTickSemantics() {
		MovementRecording recording = recording();
		insert(recording, 0);
		MovementRecording.VelocityToken velocity =
			recording.beginVelocity(new Motion(1, 2, 3));
		insert(recording, 1);
		recording.completeVelocity(velocity);
		insert(recording, 2);

		recording.materializeVelocities();
		recording.materializeVelocities();

		assertEquals(1, recording.actions().size());
		ReceiveVelocity action = (ReceiveVelocity) recording.actions().get(0);
		assertEquals(new Motion(1, 2, 3), action.motion());
		assertEquals(TickRange.betweenExclusive(1, 3), action.tickRange());
	}

	@Test
	void savingClipsUnacknowledgedVelocityToRecordedFrames() {
		MovementRecording recording = recording();
		insert(recording, 0);
		recording.beginVelocity(new Motion(1, 0, 0));
		insert(recording, 1);
		insert(recording, 2);

		recording.materializeVelocities();

		assertEquals(1, recording.actions().size());
		ReceiveVelocity action = (ReceiveVelocity) recording.actions().get(0);
		assertEquals(new Motion(1, 0, 0), action.motion());
		assertEquals(TickRange.betweenExclusive(1, 3), action.tickRange());
	}

	@Test
	void clearDropsNativeVelocityTokens() {
		MovementRecording recording = recording();
		insert(recording, 0);
		MovementRecording.VelocityToken stale =
			recording.beginVelocity(new Motion(1, 0, 0));

		recording.clear();
		insert(recording, 1);
		recording.completeVelocity(stale);
		recording.materializeVelocities();

		assertTrue(recording.actions().isEmpty());
	}

	private MovementRecording recording() {
		return MovementRecording.create(47, MinecraftVersions.VER1_21_4);
	}

	private void insert(MovementRecording recording, double x) {
		recording.insertFrame(
			BoundingBox.empty(),
			Input.none(),
			new Position(x, 64, 0),
			Rotation.zero(),
			blockCache,
			Collections.emptyMap(),
			false,
			null
		);
	}
}
