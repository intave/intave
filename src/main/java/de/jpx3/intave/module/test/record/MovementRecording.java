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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.module.test.record.action.AttackReduction;
import de.jpx3.intave.module.test.record.action.ReceiveVelocity;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Material;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.InflaterInputStream;

import static de.jpx3.intave.share.ClientMath.floor;

public final class MovementRecording {
	private static final double BLOCK_CAPTURE_RADIUS = 6.0D;
	private static final int GEYSER_CAPTURE_DEPTH = 24;
	private static final MaterialVariantStore AIR = MaterialVariantStore.air();
	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, BlockShape>>> COLLISION_SHAPES_CODEC = ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.MATERIAL, ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.INTEGER, BlockShape.STREAM_CODEC));

	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, Fluid>>> FLUIDS_CODEC = ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.MATERIAL, ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.INTEGER, Fluid.STREAM_CODEC));
	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, BlockVariant>>> BLOCK_VARIANTS_CODEC = ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.MATERIAL, ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.INTEGER, BlockVariant.STREAM_CODEC));
	private static final StreamCodec<ByteBuf, ByteBuf, Map<String, Attribute>> ATTRIBUTES_CODEC = ByteBufStreamCodecs.mapCodec(ByteBufStreamCodecs.STRING, Attribute.STREAM_CODEC);
	private static final StreamCodec<ByteBuf, ByteBuf, List<Map<String, Attribute>>> FRAME_ATTRIBUTES_CODEC = ByteBufStreamCodecs.listCodecOf(ATTRIBUTES_CODEC);

	public static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> STREAM_CODEC = ByteBufStreamCodecs.smartReflectionCodecBuilder(MovementRecording.class).field("internalId", ByteBufStreamCodecs.UUID).field("clientProtocolVersion", ByteBufStreamCodecs.INTEGER, () -> 47).field("serverVersion", MinecraftVersion.STREAM_CODEC, () -> MinecraftVersions.VER1_21_4).field("frames", MoveFrame.LIST_STREAM_CODEC).field("frameAttributes", FRAME_ATTRIBUTES_CODEC, LinkedList::new).field("actions", Action.LIST_STREAM_CODEC, LinkedList::new).field("collisionShapes", COLLISION_SHAPES_CODEC, HashMap::new).field("fluids", FLUIDS_CODEC, HashMap::new).field("blockVariants", BLOCK_VARIANTS_CODEC, HashMap::new).build();

	private final UUID internalId;
	private final int clientProtocolVersion;
	private final MinecraftVersion serverVersion;
	private final List<Action> actions = new LinkedList<>();
	private final List<MoveFrame> frames = new LinkedList<>();
	private final List<Map<String, Attribute>> frameAttributes = new LinkedList<>();
	private final Long2ObjectMap<MaterialVariantStore> blocks = new Long2ObjectOpenHashMap<>();
	private final Map<VelocityToken, VelocityInterval> velocities = new LinkedHashMap<>();
	private final Map<Material, Map<Integer, BlockShape>> collisionShapes;
	private final Map<Material, Map<Integer, Fluid>> fluids;
	private final Map<Material, Map<Integer, BlockVariant>> blockVariants;
	private final Set<MaterialVariantStore> recordedMetadata = Collections.newSetFromMap(new IdentityHashMap<>());

	MovementRecording(UUID internalId, int clientProtocolVersion, MinecraftVersion serverVersion, List<MoveFrame> frames, List<Map<String, Attribute>> frameAttributes, List<Action> actions, Map<Material, Map<Integer, BlockShape>> collisionShapes, Map<Material, Map<Integer, Fluid>> fluids, Map<Material, Map<Integer, BlockVariant>> blockVariants) {
		this.internalId = Objects.requireNonNull(internalId, "internalId cannot be null");
		this.clientProtocolVersion = clientProtocolVersion;
		this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion cannot be null");
		this.frames.addAll(Objects.requireNonNull(frames, "frames cannot be null"));
		this.frameAttributes.addAll(Objects.requireNonNull(frameAttributes, "frameAttributes cannot be null"));
		this.actions.addAll(Objects.requireNonNull(actions, "actions cannot be null"));
		this.collisionShapes = Objects.requireNonNull(collisionShapes, "collisionShapes cannot be null");
		this.fluids = Objects.requireNonNull(fluids, "fluids cannot be null");
		this.blockVariants = Objects.requireNonNull(blockVariants, "blockVariants cannot be null");
	}

	private void appendFrame(MoveFrame frame) {
		frames.add(frame);
	}

	public void insertFrame(BoundingBox boundingBox, Input input, @Nullable Position position, @Nullable Rotation rotation, BlockCache blockCache, boolean gliding) {
		insertFrame(boundingBox, input, position, rotation, blockCache, Collections.emptyMap(), gliding, null);
	}

	public void insertFrame(BoundingBox boundingBox, Input input, @Nullable Position position, @Nullable Rotation rotation, BlockCache blockCache, Map<String, Attribute> attributes, boolean gliding, @Nullable Pose physicalPose) {
		Map<BlockPosition, MaterialVariantStore> dirtyBlocks = nearbyBlockDelta(blockCache, boundingBox, position);
		appendFrame(new MoveFrame(position, rotation, dirtyBlocks, input, gliding, physicalPose));
		frameAttributes.add(new HashMap<>(attributes));
	}

	public void insertFrame(
		BoundingBox boundingBox,
		Input input,
		@Nullable Position position,
		@Nullable Rotation rotation,
		BlockCache blockCache,
		Map<String, Attribute> attributes,
		boolean gliding,
		@Nullable Pose physicalPose,
		MovementFrameState frameState
	) {
		Map<BlockPosition, MaterialVariantStore> dirtyBlocks = nearbyBlockDelta(blockCache, boundingBox, position);
		appendFrame(new MoveFrame(
			position, rotation, dirtyBlocks, input, gliding, physicalPose,
			Objects.requireNonNull(frameState, "frameState")
		));
		frameAttributes.add(new HashMap<>(attributes));
	}

	public void insertAction(Action action) {
		actions.add(action);
	}

	/** Records one reduction before the movement frame that will be appended next. */
	public synchronized void recordAttackReduction() {
		long tick = ticks();
		actions.add(new AttackReduction(TickRange.betweenExclusive(tick, tick + 1)));
	}

	/**
	 * Starts a velocity acknowledgement interval at the current recording tick.
	 *
	 * <p>The interval remains open until acknowledged or clipped to the final frame when saving.
	 */
	public synchronized VelocityToken beginVelocity(Motion motion) {
		VelocityToken token = new VelocityToken();
		velocities.put(token, new VelocityInterval(motion.copy(), ticks()));
		return token;
	}

	/**
	 * Completes a native velocity interval using the recorder's historical inclusive end tick.
	 */
	public synchronized void completeVelocity(@Nullable VelocityToken token) {
		if (token == null) {
			return;
		}
		VelocityInterval velocity = velocities.get(token);
		if (velocity != null && velocity.endExclusive == null) {
			velocity.endExclusive = ticks() + 1;
		}
	}

	/**
	 * Converts native velocity intervals into serialized PTR actions.
	 *
	 * <p>Call this only after the recording has been detached from live capture. Open intervals are
	 * clipped to the final recorded frame; completed intervals retain the existing inclusive-end
	 * behavior where that tick exists in this recording.
	 */
	public synchronized void materializeVelocities() {
		long recordingEnd = ticks();
		for (VelocityInterval velocity : velocities.values()) {
			long endExclusive = velocity.endExclusive == null ? recordingEnd : Math.min(velocity.endExclusive, recordingEnd);
			long startInclusive = Math.max(0, velocity.startInclusive);
			if (startInclusive >= endExclusive) {
				continue;
			}
			actions.add(new ReceiveVelocity(velocity.motion.copy(), TickRange.betweenExclusive(startInclusive, endExclusive)));
		}
		velocities.clear();
	}

	public long ticks() {
		return frames.size();
	}

	public boolean firstPositionHasBeenSent() {
		for (MoveFrame frame : frames) {
			if (frame.moveTo() != null) {
				return true;
			}
		}
		return false;
	}

	public boolean firstRotationHasBeenSent() {
		for (MoveFrame frame : frames) {
			if (frame.rotateTo() != null) {
				return true;
			}
		}
		return false;
	}

	public void clear() {
		frames.clear();
		frameAttributes.clear();
		actions.clear();
		blocks.clear();
		velocities.clear();
		collisionShapes.clear();
		fluids.clear();
		blockVariants.clear();
		recordedMetadata.clear();
	}

	private Map<BlockPosition, MaterialVariantStore> nearbyBlockDelta(BlockCache blockCache, BoundingBox boundingBox, @Nullable Position position) {
		if (position == null) {
			return Collections.emptyMap();
		}

		Map<BlockPosition, MaterialVariantStore> delta = new HashMap<>();
		int minX = floor(boundingBox.minX - BLOCK_CAPTURE_RADIUS);
		int minY = floor(boundingBox.minY - BLOCK_CAPTURE_RADIUS);
		int minZ = floor(boundingBox.minZ - BLOCK_CAPTURE_RADIUS);
		int maxX = floor(boundingBox.maxX + BLOCK_CAPTURE_RADIUS);
		int maxY = floor(boundingBox.maxY + BLOCK_CAPTURE_RADIUS);
		int maxZ = floor(boundingBox.maxZ + BLOCK_CAPTURE_RADIUS);
		captureBlocks(blockCache, delta, minX, minY, minZ, maxX, maxY, maxZ);

		int geyserMinX = floor(boundingBox.minX);
		int geyserMinY = floor(boundingBox.minY) - GEYSER_CAPTURE_DEPTH;
		int geyserMinZ = floor(boundingBox.minZ);
		int geyserMaxX = (int) Math.ceil(boundingBox.maxX) - 1;
		int geyserMaxZ = (int) Math.ceil(boundingBox.maxZ) - 1;
		captureBlocks(
			blockCache, delta,
			geyserMinX, geyserMinY, geyserMinZ,
			geyserMaxX, minY - 1, geyserMaxZ
		);
		return delta;
	}

	private void captureBlocks(
		BlockCache blockCache,
		Map<BlockPosition, MaterialVariantStore> delta,
		int minX,
		int minY,
		int minZ,
		int maxX,
		int maxY,
		int maxZ
	) {
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					captureBlock(blockCache, delta, x, y, z);
				}
			}
		}
	}

	private void captureBlock(BlockCache blockCache, Map<BlockPosition, MaterialVariantStore> delta, int x, int y, int z) {
		BlockState state = blockCache.peekStateAt(x, y, z);
		Material type;
		if (state == null) {
			type = blockCache.typeAt(x, y, z);
			state = blockCache.peekStateAt(x, y, z);
		} else {
			type = state.type();
		}
		int index = state == null ? blockCache.variantIndexAt(x, y, z) : state.variantIndex();
		MaterialVariantStore newStore = type == Material.AIR ? AIR : MaterialVariantStore.of(type, index);
		long blockKey = MutableBlockPosition.asLong(x, y, z);
		MaterialVariantStore oldStore = type == Material.AIR
			? blocks.remove(blockKey)
			: blocks.put(blockKey, newStore);
		if (newStore != oldStore && (oldStore != null || type != Material.AIR)) {
			delta.put(new BlockPosition(x, y, z), newStore);
		}
		if (recordedMetadata.contains(newStore)) {
			return;
		}

		Map<Integer, BlockShape> shapes = collisionShapes.get(type);
		if (shapes == null) {
			shapes = new HashMap<>();
			collisionShapes.put(type, shapes);
		}
		if (!shapes.containsKey(index)) {
			BlockShape shape = state == null
				? blockCache.collisionShapeAt(x, y, z)
				: state.collisionShape();
			shapes.put(index, shape.normalized(x, y, z));
		}

		Map<Integer, Fluid> typeFluids = fluids.get(type);
		if (typeFluids == null) {
			typeFluids = new HashMap<>();
			fluids.put(type, typeFluids);
		}
		if (!typeFluids.containsKey(index)) {
			typeFluids.put(index, Fluids.fluidStateOf(type, index));
		}

		if (BlockVariantRegister.isIndexed(type)) {
			Map<Integer, BlockVariant> variants = blockVariants.computeIfAbsent(type, key -> new HashMap<>());
			variants.computeIfAbsent(index, key -> BlockVariantRegister.variantOf(type, key).copy());
		}
		recordedMetadata.add(newStore);
	}

	public UUID internalId() {
		return internalId;
	}

	public int clientProtocolVersion() {
		return clientProtocolVersion;
	}

	public MinecraftVersion serverVersion() {
		return serverVersion;
	}

	public Map<Material, Map<Integer, BlockShape>> collisionShapes() {
		return collisionShapes;
	}

	public List<Action> actions() {
		return actions;
	}

	public List<MoveFrame> frames() {
		return frames;
	}

	public Map<String, Attribute> attributesForFrame(int frame) {
		if (frame < 0 || frame >= frameAttributes.size()) {
			return Collections.emptyMap();
		}
		return new HashMap<>(frameAttributes.get(frame));
	}

	public Map<Material, Map<Integer, Fluid>> fluids() {
		return fluids;
	}

	public void recordBlockVariant(Material type, int variantIndex, BlockVariant variant) {
		blockVariants.computeIfAbsent(type, key -> new HashMap<>()).computeIfAbsent(variantIndex, key -> variant.copy());
	}

	public @Nullable BlockVariant blockVariant(Material type, int variantIndex) {
		return blockVariants.getOrDefault(type, Collections.emptyMap()).get(variantIndex);
	}

	public Map<Material, Map<Integer, BlockVariant>> blockVariants() {
		return blockVariants;
	}

	@Override
	public String toString() {
		return "MovementRecording{" + "internalId=" + internalId + ", clientProtocolVersion=" + clientProtocolVersion + ", serverVersion='" + serverVersion + '\'' + ", frames=" + frames + ", actions=" + actions + ", collisionShapes=" + collisionShapes + ", fluids=" + fluids + ", blockVariants=" + blockVariants + '}';
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		MovementRecording that = (MovementRecording) obj;
		return Objects.equals(internalId, that.internalId) && clientProtocolVersion == that.clientProtocolVersion && Objects.equals(serverVersion, that.serverVersion) && Objects.equals(frames, that.frames) && frameAttributesEqual(frameAttributes, that.frameAttributes) && Objects.equals(collisionShapes, that.collisionShapes) && Objects.equals(actions, that.actions) && Objects.equals(fluids, that.fluids) && Objects.equals(blockVariants, that.blockVariants);
	}

	@Override
	public int hashCode() {
		return Objects.hash(internalId, clientProtocolVersion, serverVersion);
	}

	private static boolean frameAttributesEqual(List<Map<String, Attribute>> first, List<Map<String, Attribute>> second) {
		int size = Math.max(first.size(), second.size());
		for (int i = 0; i < size; i++) {
			Map<String, Attribute> firstFrame = i < first.size() ? first.get(i) : Collections.emptyMap();
			Map<String, Attribute> secondFrame = i < second.size() ? second.get(i) : Collections.emptyMap();
			if (!firstFrame.equals(secondFrame)) {
				return false;
			}
		}
		return true;
	}

	public int frameCount() {
		return frames.size();
	}

	public static MovementRecording create() {
		return create(47, MinecraftVersions.VER1_21_4);
	}

	public static MovementRecording createFor(User user) {
		return create(user.protocolVersion(), MinecraftVersion.current());
	}

	public static MovementRecording create(int clientProtocolVersion, MinecraftVersion serverVersion) {
		return new MovementRecording(UUID.randomUUID(), clientProtocolVersion, serverVersion, new LinkedList<>(), new LinkedList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
	}

	public static MovementRecording loadFrom(Resource resource) throws RuntimeException {
		InputStream read = new InflaterInputStream(resource.read());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int readBytes;
		try {
			while ((readBytes = read.read(buffer)) != -1) {
				baos.write(buffer, 0, readBytes);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to read movement recording from resource: " + resource, e);
		}

		ByteBuf byteBuf = Unpooled.wrappedBuffer(baos.toByteArray());
		try {
			return STREAM_CODEC.decode(byteBuf);
		} catch (Exception e) {
			throw new RuntimeException("Failed to decode movement recording from resource: " + resource, e);
		} finally {
			byteBuf.release();
		}
	}

	public static MovementRecording random() {
		MovementRecording movementRecording = MovementRecording.create();
		MockFullBlockStaticPlane blockCache = new MockFullBlockStaticPlane();
		for (int i = 0; i < 400; i++) {
			movementRecording.insertFrame(BoundingBox.empty(), Input.random(), ThreadLocalRandom.current().nextBoolean() ? Position.immutableRandom() : null, ThreadLocalRandom.current().nextBoolean() ? Rotation.zero() : null, blockCache, ThreadLocalRandom.current().nextBoolean());
		}
		return movementRecording;
	}

	public static final class VelocityToken {
		private VelocityToken() {
		}
	}

	private static final class VelocityInterval {
		private final Motion motion;
		private final long startInclusive;
		private Long endExclusive;

		private VelocityInterval(Motion motion, long startInclusive) {
			this.motion = motion;
			this.startInclusive = startInclusive;
		}
	}
}
