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

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.test.record.MovementFrameState;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.meta.MovementMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.DeflaterOutputStream;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public final class PhysicsTestRecorder extends Module {
	private final UserLocal<AtomicBoolean> recording = UserLocal.withInitial(() -> new AtomicBoolean(false));
	private final UserLocal<MovementRecording> recordingData = UserLocal.withInitial(MovementRecording::createFor);

	@PacketSubscription(packetsIn = {FLYING, LOOK, POSITION, POSITION_LOOK})
	public void on(User user, PlayerMoveReader reader) {
		MovementRecording movementRecording = recordingSessionOf(user);
		if (movementRecording == null) {
			return;
		}

		Position position = reader.position();
		Rotation rotation = reader.rotation();
		MovementMetadata movement = user.meta().movement();
		BoundingBox boundingBox = movement.boundingBox();
		Input input = Input.none();
		if (MinecraftVersions.VER1_21_3.atOrAbove() && user.meta().protocol().sendsInputs()) {
			input = movement.input;
		}
		input = input.overrideFromPartial(Input.partialFrom(movement));
		MovementFrameState frameState = MovementFrameState.capture(user);

		if (position == null && !movementRecording.firstPositionHasBeenSent()) {
			position = movement.position();
		}
		if (rotation == null && !movementRecording.firstRotationHasBeenSent()) {
			rotation = movement.rotation();
		}
		movementRecording.insertFrame(
			boundingBox, input,
			position, rotation,
			user.blockCache(),
			user.meta().abilities().attributeSnapshot(),
			movement.gliding,
			movement.pose(),
			frameState
		);
		ActionBar.sendActionBar(user.player(), movementRecording.frameCount() + " frames, " + movementRecording.actions().size() + " actions, " + movementRecording.collisionShapes().size() + " block-types");
	}

	public void saveRecordingDataTo(User user, File file) throws IOException {
		MovementRecording movementRecording = recordingData.get(user);
		movementRecording.materializeVelocities();
		Files.write(file.toPath(), compressedBytes(movementRecording), CREATE, TRUNCATE_EXISTING);
		movementRecording.clear();
	}

	private static byte[] compressedBytes(MovementRecording recording) throws IOException {
		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DeflaterOutputStream compressed = new DeflaterOutputStream(bytes)) {
				buffer.readBytes(compressed, buffer.readableBytes());
			}
			return bytes.toByteArray();
		} finally {
			buffer.release();
		}
	}

	public @Nullable VelocityCapture beginVelocity(User user, Motion motion) {
		MovementRecording movementRecording = recordingSessionOf(user);
		return movementRecording == null ? null : new VelocityCapture(movementRecording, movementRecording.beginVelocity(motion));
	}

	public void completeVelocity(@Nullable VelocityCapture capture) {
		if (capture != null) {
			capture.recording.completeVelocity(capture.velocity);
		}
	}

	/** Captures one attack-induced client motion reduction between movement frames. */
	public void recordAttackReduction(User user) {
		MovementRecording movementRecording = recordingSessionOf(user);
		if (movementRecording != null) {
			movementRecording.recordAttackReduction();
		}
	}

	public static final class VelocityCapture {
		private final MovementRecording recording;
		private final MovementRecording.VelocityToken velocity;

		private VelocityCapture(MovementRecording recording, MovementRecording.VelocityToken velocity) {
			this.recording = recording;
			this.velocity = velocity;
		}
	}

	public @Nullable MovementRecording recordingSessionOf(User user) {
		return isRecording(user) ? recordingData.get(user) : null;
	}

	public void setRecordingStatus(User user, boolean recording) {
		this.recording.get(user).set(recording);
	}

	public boolean isRecording(User user) {
		return recording.get(user).get();
	}
}
