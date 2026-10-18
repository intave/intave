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

package de.jpx3.intave.check.movement.physics.recording;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.*;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.AttackReduction;
import de.jpx3.intave.module.test.record.action.PistonSlimeAction;
import de.jpx3.intave.module.test.record.action.ShulkerBoxAction;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.share.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class MovementRecordingSerializerTest {
	private static final StreamCodec<ByteBuf, ByteBuf, Position> NULLABLE_POSITION_CODEC = Position.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Rotation> NULLABLE_ROTATION_CODEC = Rotation.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Pose> NULLABLE_POSE_CODEC = ByteBufStreamCodecs.STRING.beforeAndAfter(Pose::valueOf, Pose::name).nullable(ByteBufStreamCodecs.BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, MovementFrameState> NULLABLE_MOVEMENT_STATE_CODEC = MovementFrameState.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Boolean> NULLABLE_CLIENT_GROUND_CODEC = ByteBufStreamCodecs.BOOLEAN.nullable(ByteBufStreamCodecs.BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Map<BlockPosition, MaterialVariantStore>> LEGACY_BLOCKS_CODEC = ByteBufStreamCodecs.mapCodec(BlockPosition.LEGACY_STREAM_CODEC, MaterialVariantStore.STREAM_CODEC);
	private static final StreamCodec<ByteBuf, ByteBuf, MoveFrame> UNVERSIONED_FRAME_CODEC = StreamCodec.of((buffer, frame) -> {
		NULLABLE_POSITION_CODEC.encode(buffer, frame.moveTo());
		NULLABLE_ROTATION_CODEC.encode(buffer, frame.rotateTo());
		LEGACY_BLOCKS_CODEC.encode(buffer, frame.blocks());
		Input.STREAM_CODEC.encode(buffer, frame.input());
	}, _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});
	private static final StreamCodec<ByteBuf, ByteBuf, List<MoveFrame>> LEGACY_FRAMES_CODEC = ByteBufStreamCodecs.listCodecOf(UNVERSIONED_FRAME_CODEC);
	private static final StreamCodec<ByteBuf, ByteBuf, List<MoveFrame>> VERSION_2_FRAMES_CODEC = StreamCodec.of((buffer, frames) -> {
		ByteBufStreamCodecs.INTEGER.encode(buffer, Integer.MIN_VALUE);
		ByteBufStreamCodecs.INTEGER.encode(buffer, 2);
		ByteBufStreamCodecs.INTEGER.encode(buffer, frames.size());
		for (MoveFrame frame : frames) {
			UNVERSIONED_FRAME_CODEC.encode(buffer, frame);
			ByteBufStreamCodecs.BOOLEAN.encode(buffer, frame.gliding());
			NULLABLE_POSE_CODEC.encode(buffer, frame.physicalPose());
		}
	}, _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});
	private static final StreamCodec<ByteBuf, ByteBuf, List<MoveFrame>> VERSION_3_FRAMES_CODEC = StreamCodec.of((buffer, frames) -> {
		ByteBufStreamCodecs.INTEGER.encode(buffer, Integer.MIN_VALUE);
		ByteBufStreamCodecs.INTEGER.encode(buffer, 3);
		ByteBufStreamCodecs.INTEGER.encode(buffer, frames.size());
		for (MoveFrame frame : frames) {
			UNVERSIONED_FRAME_CODEC.encode(buffer, frame);
			ByteBufStreamCodecs.BOOLEAN.encode(buffer, frame.gliding());
			NULLABLE_POSE_CODEC.encode(buffer, frame.physicalPose());
			NULLABLE_MOVEMENT_STATE_CODEC.encode(buffer, frame.movementState());
		}
	}, _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});
	private static final StreamCodec<ByteBuf, ByteBuf, List<MoveFrame>> VERSION_4_FRAMES_CODEC = StreamCodec.of((buffer, frames) -> {
		ByteBufStreamCodecs.INTEGER.encode(buffer, Integer.MIN_VALUE);
		ByteBufStreamCodecs.INTEGER.encode(buffer, 4);
		ByteBufStreamCodecs.INTEGER.encode(buffer, frames.size());
		for (MoveFrame frame : frames) {
			UNVERSIONED_FRAME_CODEC.encode(buffer, frame);
			ByteBufStreamCodecs.BOOLEAN.encode(buffer, frame.gliding());
			NULLABLE_POSE_CODEC.encode(buffer, frame.physicalPose());
			NULLABLE_MOVEMENT_STATE_CODEC.encode(buffer, frame.movementState());
			NULLABLE_CLIENT_GROUND_CODEC.encode(buffer, frame.clientOnGround());
		}
	}, _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});
	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, BlockShape>>> COLLISION_SHAPES_CODEC = ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.MATERIAL, ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.INTEGER, BlockShape.STREAM_CODEC));
	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, Fluid>>> FLUIDS_CODEC = ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.MATERIAL, ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.INTEGER, Fluid.STREAM_CODEC));
	private static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> FRAMES_ONLY_SMART_CODEC = ByteBufStreamCodecs.<MovementRecording>smartCodec(codec -> codec.field("frames", LEGACY_FRAMES_CODEC, MovementRecording::frames).field("internalId", ByteBufStreamCodecs.UUID, MovementRecording::internalId).field("collisionShapes", COLLISION_SHAPES_CODEC, MovementRecording::collisionShapes).field("fluids", FLUIDS_CODEC, MovementRecording::fluids), _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});
	private static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> VERSION_2_SMART_CODEC = ByteBufStreamCodecs.<MovementRecording>smartCodec(codec -> codec.field("frames", VERSION_2_FRAMES_CODEC, MovementRecording::frames).field("internalId", ByteBufStreamCodecs.UUID, MovementRecording::internalId).field("collisionShapes", COLLISION_SHAPES_CODEC, MovementRecording::collisionShapes).field("fluids", FLUIDS_CODEC, MovementRecording::fluids), _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});
	private static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> VERSION_3_SMART_CODEC = ByteBufStreamCodecs.<MovementRecording>smartCodec(codec -> codec.field("frames", VERSION_3_FRAMES_CODEC, MovementRecording::frames).field("internalId", ByteBufStreamCodecs.UUID, MovementRecording::internalId).field("collisionShapes", COLLISION_SHAPES_CODEC, MovementRecording::collisionShapes).field("fluids", FLUIDS_CODEC, MovementRecording::fluids), _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});
	private static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> FUTURE_SMART_CODEC = ByteBufStreamCodecs.<MovementRecording>smartCodec(codec -> codec.field("internalId", ByteBufStreamCodecs.UUID, MovementRecording::internalId).field("frames", LEGACY_FRAMES_CODEC, MovementRecording::frames).field("collisionShapes", COLLISION_SHAPES_CODEC, MovementRecording::collisionShapes).field("fluids", FLUIDS_CODEC, MovementRecording::fluids).field("format", ByteBufStreamCodecs.INTEGER, _ -> 2), _ -> {
		throw new UnsupportedOperationException("This codec is used for encoding test payloads only");
	});

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
	void serializeFrameAttributes() {
		MovementRecording recording = MovementRecording.create();
		AttributeModifier powderSnow = new AttributeModifier(new MinecraftKey("minecraft", "powder_snow"), UUID.randomUUID(), null, AttributeModifier.Operation.ADD_NUMBER, -0.025);
		Attribute movementSpeed = Attribute.newBuilder().withAttributeKey("movement_speed").withBaseValue(0.1).withAttributeModifiers(Set.of(powderSnow)).build();
		recording.insertFrame(BoundingBox.empty(), Input.none(), Position.immutableEmpty(), Rotation.zero(), new MockFullBlockStaticPlane(), Map.of("movement_speed", movementSpeed), true, Pose.FALL_FLYING);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertEquals(recording, decoded);
			assertTrue(decoded.frames().get(0).gliding());
			assertEquals(Pose.FALL_FLYING, decoded.frames().get(0).physicalPose());
			Attribute decodedSpeed = decoded.attributesForFrame(0).get("movement_speed");
			assertNotNull(decodedSpeed);
			assertEquals(0.1, decodedSpeed.baseValue());
			assertEquals(-0.025, decodedSpeed.modifiers().iterator().next().amount());
			assertNull(decodedSpeed.modifiers().iterator().next().name());
		} finally {
			buffer.release();
		}
	}

	@Test
	void serializesMovementRelevantFrameState() {
		MovementRecording recording = MovementRecording.create();
		MovementFrameState.ItemState elytra = new MovementFrameState.ItemState(
			Material.ELYTRA, 1, 17, Map.of()
		);
		MovementFrameState state = new MovementFrameState(
			new MovementFrameState.AbilityState(true, true, false, false, 0.08F, "CREATIVE"),
			new MovementFrameState.EffectState(
				2, 80, 1, 40, 3, 20,
				List.of(new MovementFrameState.EffectInstance("LEVITATION", 30, 2, false))
			),
			new MovementFrameState.InventoryState(
				List.of(new MovementFrameState.ItemState(Material.BOW, 1, 0, Map.of())),
				Arrays.asList(null, null, elytra, null),
				null, 0, true, 7, 0, 2, false, Material.BOW,
				false, false, Material.AIR, true, false,
				PlayerHand.MAIN_HAND,
				false, 0.2F
			),
			3, 2,
			new MovementFrameState.EntityState(
				42, true, "Boat", 1, false, 10,
				1.375F, 0.5625F, false, true, 4.0, 64.0, 8.0
			),
			List.of(new MovementFrameState.EntityState(
				43, true, "Zombie", 54, true, 9,
				0.6F, 1.95F, false, true, 4.2, 64.0, 8.1
			)),
			"IN_WATER", "IN_AIR", 0.8F, 64.7D,
			2, 0, 4
		);
		recording.insertFrame(
			BoundingBox.empty(), Input.none(), Position.immutableEmpty(), Rotation.zero(),
			new MockFullBlockStaticPlane(), Map.of(), true, Pose.FALL_FLYING, state, true
		);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertEquals(state, decoded.frames().get(0).movementState());
			assertEquals(Boolean.TRUE, decoded.frames().get(0).clientOnGround());
			assertEquals(recording, decoded);
		} finally {
			buffer.release();
		}
	}

	@Test
	void serializesPistonSlimeActions() {
		MovementRecording recording = MovementRecording.create();
		PistonSlimeAction action = new PistonSlimeAction(
			Direction.UP,
			List.of(new BlockPosition(-214, 65, 220)),
			TickRange.betweenInclusive(65, 66)
		);
		recording.insertAction(action);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertEquals(List.of(action), decoded.actions());
		} finally {
			buffer.release();
		}
	}

	@Test
	void serializesShulkerBoxActions() {
		MovementRecording recording = MovementRecording.create();
		ShulkerBoxAction action = new ShulkerBoxAction(
			new BlockPosition(-214, 65, 220),
			Direction.EAST,
			true,
			TickRange.betweenInclusive(65, 66)
		);
		recording.insertAction(action);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertEquals(List.of(action), decoded.actions());
		} finally {
			buffer.release();
		}
	}

	@Test
	void serializesAttackReductions() {
		MovementRecording recording = MovementRecording.create();
		AttackReduction action = new AttackReduction(
			false, false, TickRange.betweenExclusive(3, 4)
		);
		recording.insertAction(action);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertEquals(List.of(action), decoded.actions());
			assertFalse(((AttackReduction) decoded.actions().get(0)).lastActiveInTick());
			assertFalse(((AttackReduction) decoded.actions().get(0)).mandatory());
		} finally {
			buffer.release();
		}
	}

	@Test
	void serializes262MovementAttributesForReplay() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER26_2);
		AbilityMetadata recordedAbilities = new AbilityMetadata(
			FakePlayerFactory.createPlayer((ignored, arguments) -> null)
		);
		recordedAbilities.modifyBaseValue("air_drag_modifier", 0.875D);
		recordedAbilities.modifyBaseValue("bounciness", 0.625D);
		recordedAbilities.modifyBaseValue("friction_modifier", 1.25D);

		MovementRecording recording = MovementRecording.create(
			ProtocolMetadata.VER_26_2,
			MinecraftVersions.VER26_2
		);
		recording.insertFrame(
			BoundingBox.empty(), Input.none(), Position.immutableEmpty(), Rotation.zero(),
			new MockFullBlockStaticPlane(), recordedAbilities.attributeSnapshot(), false, Pose.STANDING
		);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);
			Map<String, Attribute> decodedAttributes = decoded.attributesForFrame(0);

			assertEquals(0.875D, decodedAttributes.get("air_drag_modifier").baseValue());
			assertEquals(0.625D, decodedAttributes.get("bounciness").baseValue());
			assertEquals(1.25D, decodedAttributes.get("friction_modifier").baseValue());

			AbilityMetadata replayAbilities = new AbilityMetadata(
				FakePlayerFactory.createPlayer((ignored, arguments) -> null)
			);
			replayAbilities.replaceAttributeSnapshot(decodedAttributes);
			assertEquals(0.875D, replayAbilities.airDragModifier());
			assertEquals(0.625D, replayAbilities.bounciness());
			assertEquals(1.25D, replayAbilities.frictionModifier());
		} finally {
			buffer.release();
		}
	}

	@Test
	void serializesBlockVariantProperties() {
		MovementRecording recording = MovementRecording.create();
		BlockVariant variant = testVariant(7, Map.of("drag", true, "distance", 3, "facing", "north"));
		recording.recordBlockVariant(Material.STONE, 7, variant);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);
			BlockVariant decodedVariant = decoded.blockVariant(Material.STONE, 7);

			assertNotNull(decodedVariant);
			assertEquals(7, decodedVariant.index());
			assertTrue(decodedVariant.<Boolean>propertyOf("drag"));
			assertEquals(3, (int) decodedVariant.propertyOf("distance"));
			assertEquals(Direction.NORTH, decodedVariant.enumProperty(Direction.class, "facing"));
		} finally {
			buffer.release();
		}
	}

	@Test
	void serializesBlockVariantEnumConstantsWithClassBodies() {
		MovementRecording recording = MovementRecording.create();
		BlockVariant variant = testVariant(7, Map.of("axis", TestAxis.X));
		recording.recordBlockVariant(Material.STONE, 7, variant);

		ByteBuf buffer = Unpooled.buffer();
		try {
			MovementRecording.STREAM_CODEC.encode(buffer, recording);
			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertEquals(TestAxis.X, decoded.blockVariant(Material.STONE, 7).enumProperty(TestAxis.class, "axis"));
		} finally {
			buffer.release();
		}
	}

	@Test
	public void deserializeOlderSmartRecordingWithoutCollisionShapes() {
		MovementRecording recording = recordingWithoutCollisionShapes();
		ByteBuf buf = Unpooled.buffer();
		try {
			FRAMES_ONLY_SMART_CODEC.encode(buf, recording);

			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buf);

			assertNull(decoded.frames().get(0).movementState());
			assertTrue(decoded.attributesForFrame(0).isEmpty());
			MinecraftVersion.setCurrent(MinecraftVersions.VER26_2);
			AbilityMetadata replayAbilities = new AbilityMetadata(
				FakePlayerFactory.createPlayer((ignored, arguments) -> null)
			);
			replayAbilities.replaceAttributeSnapshot(decoded.attributesForFrame(0));
			assertEquals(1.0D, replayAbilities.airDragModifier());
			assertEquals(0.0D, replayAbilities.bounciness());
			assertEquals(1.0D, replayAbilities.frictionModifier());
			assertFalse(decoded.packetDrivenItemUse());
			deepEqualsCheck(recording, decoded, false);
		} finally {
			buf.release();
		}
	}

	@Test
	void deserializeVersion2FrameWithoutMovementState() {
		MovementRecording recording = MovementRecording.create();
		recording.insertFrame(
			BoundingBox.empty(), Input.none(), Position.immutableEmpty(), Rotation.zero(),
			new MockFullBlockStaticPlane(), Map.of(), true, Pose.FALL_FLYING
		);
		ByteBuf buffer = Unpooled.buffer();
		try {
			VERSION_2_SMART_CODEC.encode(buffer, recording);

			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertTrue(decoded.frames().get(0).gliding());
			assertEquals(Pose.FALL_FLYING, decoded.frames().get(0).physicalPose());
			assertNull(decoded.frames().get(0).movementState());
		} finally {
			buffer.release();
		}
	}

	@Test
	void deserializeVersion3FrameWithoutClientGroundState() {
		MovementRecording recording = MovementRecording.create();
		recording.insertFrame(
			BoundingBox.empty(), Input.none(), Position.immutableEmpty(), Rotation.zero(),
			new MockFullBlockStaticPlane(), Map.of(), true, Pose.FALL_FLYING,
			MovementFrameState.empty()
		);
		ByteBuf buffer = Unpooled.buffer();
		try {
			VERSION_3_SMART_CODEC.encode(buffer, recording);

			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buffer);

			assertEquals(MovementFrameState.empty(), decoded.frames().get(0).movementState());
			assertNull(decoded.frames().get(0).clientOnGround());
		} finally {
			buffer.release();
		}
	}

	@Test
	void deserializeVersion4FrameWithPackedBlockPositions() {
		BlockPosition position = new BlockPosition(-214, 65, 220);
		MoveFrame frame = new MoveFrame(
			null, null,
			Map.of(position, MaterialVariantStore.of(Material.STONE, 3)),
			Input.none(), true, Pose.STANDING, MovementFrameState.empty(), false
		);
		ByteBuf buffer = Unpooled.buffer();
		try {
			VERSION_4_FRAMES_CODEC.encode(buffer, List.of(frame));

			List<MoveFrame> decoded = MoveFrame.LIST_STREAM_CODEC.decode(buffer);

			assertEquals(List.of(frame), decoded);
			assertEquals(MaterialVariantStore.of(Material.STONE, 3), decoded.get(0).blocks().get(position));
		} finally {
			buffer.release();
		}
	}

	@Test
	public void deserializeFutureSmartRecordingWithUnknownFields() {
		MovementRecording recording = recordingWithoutCollisionShapes();
		ByteBuf buf = Unpooled.buffer();
		try {
			FUTURE_SMART_CODEC.encode(buf, recording);

			MovementRecording decoded = MovementRecording.STREAM_CODEC.decode(buf);

			assertFalse(decoded.packetDrivenItemUse());
			deepEqualsCheck(recording, decoded, false);
		} finally {
			buf.release();
		}
	}

	private static MovementRecording recordingWithoutCollisionShapes() {
		MovementRecording movementRecording = MovementRecording.create();
		MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();
		for (int i = 0; i < 8; i++) {
			movementRecording.insertFrame(BoundingBox.empty(), Input.random(), i % 2 == 1 ? Position.immutableRandom() : null, i % 2 == 0 ? Rotation.zero() : null, blockCache, false);
		}
		return movementRecording;
	}

	private static void deepEqualsCheck(MovementRecording first, MovementRecording second) {
		deepEqualsCheck(first, second, true);
	}

	private static void deepEqualsCheck(
		MovementRecording first,
		MovementRecording second,
		boolean comparePacketDrivenItemUse
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

		if (comparePacketDrivenItemUse) {
			assertEquals(first.packetDrivenItemUse(), second.packetDrivenItemUse());
			assertEquals(first, second);
		}
	}

	@Test
	public void testActualRecording() {
//		MovementRecording movementRecording = MovementRecording.loadFrom(
//			Resources.resourceFromJarOrTestBuild("physics_test_runs/serialization/337e108a-07d2-44ab-b39e-c1ae3ed29f5b.ptr")
//		);
//		assertFalse(movementRecording.frames().isEmpty());

	}

	private static Resource compressedResourceOf(MovementRecording recording) throws IOException {
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

	private static BlockVariant testVariant(int index, Map<String, Comparable<?>> properties) {
		return new BlockVariant() {
			@Override
			public Set<String> propertyNames() {
				return properties.keySet();
			}

			@Override
			@SuppressWarnings("unchecked")
			public <T> T propertyOf(String name) {
				return (T) properties.get(name);
			}

			@Override
			public <T extends Enum<T>> T enumProperty(Class<T> klass, String name) {
				return Enum.valueOf(klass, properties.get(name).toString().toUpperCase());
			}

			@Override
			public int index() {
				return index;
			}

			@Override
			public void dumpStates() {
			}
		};
	}

	private enum TestAxis {
		X {
			@Override
			public String toString() {
				return "x";
			}
		}
	}
}
