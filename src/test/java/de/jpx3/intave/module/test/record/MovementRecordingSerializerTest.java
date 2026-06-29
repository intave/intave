package de.jpx3.intave.module.test.record;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Input;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class MovementRecordingSerializerTest {
	private static final StreamCodec<ByteBuf, ByteBuf, List<MoveFrame>> FRAMES_CODEC = ByteBufStreamCodecs.listCodecOf(
		MoveFrame.STREAM_CODEC
	);
	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, BlockShape>>> COLLISION_SHAPES_CODEC = ByteBufStreamCodecs.mapCodec(
		ByteBufStreamCodecs.MATERIAL,
		ByteBufStreamCodecs.mapCodec(
			ByteBufStreamCodecs.INTEGER,
			BlockShape.STREAM_CODEC
		)
	);
	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, Fluid>>> FLUIDS_CODEC =
		ByteBufStreamCodecs.mapCodec(
			ByteBufStreamCodecs.MATERIAL,
			ByteBufStreamCodecs.mapCodec(
				ByteBufStreamCodecs.INTEGER,
				Fluid.STREAM_CODEC
			)
		);
	private static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> FRAMES_ONLY_SMART_CODEC = ByteBufStreamCodecs.<MovementRecording>smartCodec(
		codec ->
			codec.field("frames", FRAMES_CODEC, MovementRecording::frames)
				.field("internalId", ByteBufStreamCodecs.UUID, MovementRecording::internalId)
				.field("collisionShapes", COLLISION_SHAPES_CODEC, MovementRecording::collisionShapes)
				.field("fluids", FLUIDS_CODEC, MovementRecording::fluids),
		_ -> {
			throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
		}
	);
	private static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> FUTURE_SMART_CODEC = ByteBufStreamCodecs.<MovementRecording>smartCodec(
		codec -> codec
			.field("internalId", ByteBufStreamCodecs.UUID, MovementRecording::internalId)
			.field("frames", FRAMES_CODEC, MovementRecording::frames)
			.field("collisionShapes", COLLISION_SHAPES_CODEC, MovementRecording::collisionShapes)
			.field("fluids", FLUIDS_CODEC, MovementRecording::fluids)
			.field("format", ByteBufStreamCodecs.INTEGER, _ -> 2),
		_ -> {
			throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
		}
	);

	@BeforeEach
	public void before() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_3);
	}

	@Test
	public void serializeExample() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
		com.comphenix.protocol.utility.MinecraftVersion.setCurrentVersion(com.comphenix.protocol.utility.MinecraftVersion.v1_21_4);

//		MovementRecording random = MovementRecording.loadFrom(
//			Resources.resourceFromJarOrTestBuild("phy")
//		);
		MovementRecording random = MovementRecording.random();
		ByteBuf buf = Unpooled.buffer();
		MovementRecording.STREAM_CODEC.encode(buf, random);
		MovementRecording replica = MovementRecording.STREAM_CODEC.decode(buf);

		deepEqualsCheck(random, replica);
	}

	@Test
	public void deserializeCompressedSmartRecording() throws IOException {
		MovementRecording recording = MovementRecording.random();
		Resource resource = compressedResourceOf(recording);
		MovementRecording movementRecording = MovementRecording.loadFrom(resource);
		assertFalse(movementRecording.frames().isEmpty());
		assertEquals(recording, movementRecording);
	}

	@Test
	public void deserializeOlderSmartRecordingWithoutCollisionShapes() {
		MovementRecording recording = recordingWithoutCollisionShapes();
		ByteBuf buf = Unpooled.buffer();
		try {
			FRAMES_ONLY_SMART_CODEC.encode(buf, recording);

			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buf);

			deepEqualsCheck(recording, decoded);
		} finally {
			buf.release();
		}
	}

	@Test
	public void deserializeFutureSmartRecordingWithUnknownFields() {
		MovementRecording recording = recordingWithoutCollisionShapes();
		ByteBuf buf = Unpooled.buffer();
		try {
			FUTURE_SMART_CODEC.encode(buf, recording);

			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buf);

			deepEqualsCheck(recording, decoded);
		} finally {
			buf.release();
		}
	}

	private static MovementRecording recordingWithoutCollisionShapes() {
		MovementRecording movementRecording = MovementRecording.create();
		MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();
		for (int i = 0; i < 8; i++) {
			movementRecording.insertFrame(
				BoundingBox.empty(),
				Input.random(),
				i % 2 == 1 ? Position.immutableRandom() : null,
				i % 2 == 0 ? Rotation.zero() : null,
				blockCache
			);
		}
		return movementRecording;
	}

	private static void deepEqualsCheck(
		MovementRecording first, MovementRecording second
	) {
		assertEquals(first.internalId(), second.internalId());
		assertEquals(first.clientProtocolVersion(), second.clientProtocolVersion());
		assertEquals(first.serverVersion(), second.serverVersion());

		List<MoveFrame> frames = first.frames();
		List<MoveFrame> replicaFrames = second.frames();
		for (int i = 0; i < frames.size(); i++) {
			MoveFrame frame = frames.get(i);
			MoveFrame replicaFrame = replicaFrames.get(i);

			assertEquals(frame, replicaFrame, "Frame " + i + " does not match");
		}

		List<Action> actions = first.actions();
		List<Action> replicaActions = second.actions();
		for (int i = 0; i < actions.size(); i++) {
			Action action = actions.get(i);
			Action replicaAction = replicaActions.get(i);

			assertEquals(action, replicaAction, "Action " + i + " does not match");
		}

		Map<Material, Map<Integer, BlockShape>> boxes = first.collisionShapes();
		Map<Material, Map<Integer, BlockShape>> replicaBoxes = second.collisionShapes();
		for (Material material : boxes.keySet()) {
			Map<Integer, BlockShape> shapes = boxes.get(material);
			Map<Integer, BlockShape> replicaShapes = replicaBoxes.get(material);

			if (replicaShapes == null) {
				fail("Replica is missing material " + material);
			}

			for (Integer data : shapes.keySet()) {
				BlockShape shape = shapes.get(data);
				BlockShape replicaShape = replicaShapes.get(data);
				assertEquals(shape, replicaShape, "Collision shape for material " + material + " and data " + data + " does not match");
			}
		}

		Map<Material, Map<Integer, Fluid>> fluids = first.fluids();
		Map<Material, Map<Integer, Fluid>> replicaFluids = second.fluids();
		for (Material material : fluids.keySet()) {
			Map<Integer, Fluid> shapes = fluids.get(material);
			Map<Integer, Fluid> replicaShapes = replicaFluids.get(material);
			for (Integer data : shapes.keySet()) {
				Fluid shape = shapes.get(data);
				Fluid replicaShape = replicaShapes.get(data);
				assertEquals(shape, replicaShape, "Fluid for material " + material + " and data " + data + " does not match");
			}
		}

		assertEquals(first, second);
	}

	@Test
	public void testActualRecording() {
//		MovementRecording movementRecording = MovementRecording.loadFrom(
//			Resources.resourceFromJarOrTestBuild("physics_test_runs/serialization/337e108a-07d2-44ab-b39e-c1ae3ed29f5b.ptr")
//		);
//		assertFalse(movementRecording.frames().isEmpty());

	}

	private static Resource compressedResourceOf(
		MovementRecording recording
	) throws IOException {
		ByteBuf buf = Unpooled.buffer();
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try (DeflaterOutputStream compressedOutputStream = new DeflaterOutputStream(byteArrayOutputStream)) {
			MovementRecording.STREAM_CODEC.encode(buf, recording);
			buf.readBytes(compressedOutputStream, buf.readableBytes());
		} finally {
			buf.release();
		}
		Resource resource = Resources.memoryResource();
		resource.write(byteArrayOutputStream.toByteArray());
		return resource;
	}
}
