package de.jpx3.intave.module.test.record;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.action.Action;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Material;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

public final class MovementRecording {
	private static final StreamCodec<ByteBuf, ByteBuf, Map<Material, Map<Integer, BlockShape>>> COLLISION_SHAPES_CODEC =
		ByteBufStreamCodecs.mapCodec(
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

	public static final StreamCodec<ByteBuf, ByteBuf, MovementRecording> STREAM_CODEC = ByteBufStreamCodecs
		.smartReflectionCodecBuilder(MovementRecording.class)
		.field("internalId", ByteBufStreamCodecs.UUID)
		.field("clientProtocolVersion", ByteBufStreamCodecs.INTEGER, () -> 47)
		.field("serverVersion", MinecraftVersion.STREAM_CODEC, () -> MinecraftVersions.VER1_21_4)
		.field("frames", MoveFrame.LIST_STREAM_CODEC)
		.field("actions", Action.LIST_STREAM_CODEC, LinkedList::new)
		.field("collisionShapes", COLLISION_SHAPES_CODEC, HashMap::new)
		.field("fluids", FLUIDS_CODEC, HashMap::new)
		.build();

	private final UUID internalId;
	private final int clientProtocolVersion;
	private final MinecraftVersion serverVersion;
	private final List<Action> actions = new LinkedList<>();
	private final List<MoveFrame> frames = new LinkedList<>();
	private final Map<BlockPosition, MaterialVariantStore> blocks = new HashMap<>();
	private final Map<Material, Map<Integer, BlockShape>> collisionShapes;
	private final Map<Material, Map<Integer, Fluid>> fluids;

	private MovementRecording(
		UUID internalId,
		int clientProtocolVersion,
		MinecraftVersion serverVersion,
		List<MoveFrame> frames,
		List<Action> actions,
		Map<Material, Map<Integer, BlockShape>> collisionShapes,
		Map<Material, Map<Integer, Fluid>> fluids
	) {
		this.internalId = Objects.requireNonNull(internalId, "internalId cannot be null");
		this.clientProtocolVersion = clientProtocolVersion;
		this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion cannot be null");
		this.frames.addAll(Objects.requireNonNull(frames, "frames cannot be null"));
		this.actions.addAll(Objects.requireNonNull(actions, "actions cannot be null"));
		this.collisionShapes = Objects.requireNonNull(collisionShapes, "collisionShapes cannot be null");
		this.fluids = Objects.requireNonNull(fluids, "fluids cannot be null");
	}

	private void appendFrame(MoveFrame frame) {
		frames.add(frame);
	}

	public void insertFrame(
		BoundingBox boundingBox,
		Input input,
		@Nullable Position position,
		@Nullable Rotation rotation,
		BlockCache blockCache
	) {
		Map<BlockPosition, MaterialVariantStore> dirtyBlocks = insertAndDelta(
			nearbyBlocks(blockCache, boundingBox, position)
		);
		appendFrame(new MoveFrame(position, rotation, dirtyBlocks, input));
	}

	public void insertAction(Action action) {
		actions.add(action);
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
		blocks.clear();
	}

	private Map<BlockPosition, MaterialVariantStore> nearbyBlocks(
		BlockCache blockCache,
		BoundingBox boundingBox,
		@Nullable Position position
	) {
		if (position == null) {
			return Collections.emptyMap();
		}

		Map<BlockPosition, MaterialVariantStore> nearbyBlocks = new HashMap<>();
		List<BlockPosition> nearbyPositions = Collision.collectRasterizedCollisions(
			boundingBox.grow(2),
			blockPosition -> blockPosition,
			blockPosition -> false,
			Collectors.toList()
		);
		for (BlockPosition blockPosition : nearbyPositions) {
			Material type = blockCache.typeAt(blockPosition);
			int index = blockCache.variantIndexAt(blockPosition);
			MaterialVariantStore store = MaterialVariantStore.of(type, index);
			nearbyBlocks.put(blockPosition, store);
			// check if collision shapes has the block
			if (!collisionShapes.containsKey(type) || !collisionShapes.get(type).containsKey(index)) {
				BlockShape shape = blockCache.collisionShapeAt(blockPosition);
				collisionShapes.computeIfAbsent(type, k -> new HashMap<>()).put(index, shape.normalized(
					blockPosition
				));
			}
			if (!fluids.containsKey(type) || !fluids.get(type).containsKey(index)) {
				Fluid fluid = Fluids.fluidStateOf(type, index);
				fluids.computeIfAbsent(type, k -> new HashMap<>()).put(index, fluid);
			}
		}
		return nearbyBlocks;
	}

	private Map<BlockPosition, MaterialVariantStore> insertAndDelta(
		Map<BlockPosition, MaterialVariantStore> nearbyBlocks
	) {
		Map<BlockPosition, MaterialVariantStore> delta = new HashMap<>();
		for (Map.Entry<BlockPosition, MaterialVariantStore> entry : nearbyBlocks.entrySet()) {
			BlockPosition pos = entry.getKey();
			MaterialVariantStore newStore = entry.getValue();
			MaterialVariantStore oldStore = blocks.put(pos, newStore);

			// we don't set air as initial
			if ((oldStore == null || oldStore.type() == Material.AIR) && newStore.type() == Material.AIR) {
				continue;
			}
			if (!newStore.equals(oldStore)) {
				delta.put(pos, newStore);
			}
		}
		return delta;
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

	List<MoveFrame> frames() {
		return frames;
	}

	public Map<Material, Map<Integer, Fluid>> fluids() {
		return fluids;
	}

	@Override
	public String toString() {
		return "MovementRecording{" +
			"internalId=" + internalId +
			", clientProtocolVersion=" + clientProtocolVersion +
			", serverVersion='" + serverVersion + '\'' +
			", frames=" + frames +
			", actions=" + actions +
			", collisionShapes=" + collisionShapes +
			", fluids=" + fluids +
			'}';
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		MovementRecording that = (MovementRecording) obj;
		return Objects.equals(internalId, that.internalId) &&
			clientProtocolVersion == that.clientProtocolVersion &&
			Objects.equals(serverVersion, that.serverVersion) &&
			Objects.equals(frames, that.frames) &&
			Objects.equals(collisionShapes, that.collisionShapes) &&
			Objects.equals(actions, that.actions) &&
			Objects.equals(fluids, that.fluids);
	}

	@Override
	public int hashCode() {
		return Objects.hash(internalId, clientProtocolVersion, serverVersion);
	}

	public int frameCount() {
		return frames.size();
	}

	public static MovementRecording create() {
		return create(47, MinecraftVersions.VER1_21_4);
	}

	public static MovementRecording createFor(
		User user
	) {
		return create(user.protocolVersion(), MinecraftVersion.current());
	}

	public static MovementRecording create(
		int clientProtocolVersion,
		MinecraftVersion serverVersion
	) {
		return new MovementRecording(
			UUID.randomUUID(),
			clientProtocolVersion,
			serverVersion,
			new LinkedList<>(),
			new ArrayList<>(),
			new HashMap<>(),
			new HashMap<>()
		);
	}

	public static MovementRecording loadFrom(
		Resource resource
	) throws RuntimeException {
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
			movementRecording.insertFrame(
				BoundingBox.empty(),
				Input.random(),
				ThreadLocalRandom.current().nextBoolean() ? Position.immutableRandom() : null,
				ThreadLocalRandom.current().nextBoolean() ? Rotation.zero() : null,
				blockCache
			);
		}
		return movementRecording;
	}
}
