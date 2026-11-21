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

package de.jpx3.intave.packet.wire.world.respawn;

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableRespawnPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;
import static de.jpx3.intave.packet.wire.world.respawn.RespawnWirePacket.Field.*;

public abstract class RespawnWirePacket extends AbstractMutable implements MutableRespawnPacket {
	private final Layout layout;
	private int dimensionId;
	private String dimensionKey = "minecraft:overworld";
	private CompoundTag dimensionData;
	private String world = "minecraft:overworld";
	private String levelType = "default";
	private int difficulty;
	private long seed;
	private int gameMode;
	private int previousMode = -1;
	private boolean debug, flat, keepAttributes;
	private byte keepMask;
	private DeathPosition deathLocation;
	private int portalCooldown, seaLevel;

	private RespawnWirePacket(Layout layout) {
		this.layout = layout;
		if (isDimensionTypeDataAvailable()) dimensionData = new CompoundTag();
	}

	@Override
	public final boolean appliesTo(int protocol) {
		return protocol >= layout.firstProtocol && protocol < layout.endProtocol;
	}

	private void requireAvailable(Field field) {
		if (!layout.available.contains(field)) {
			throw new UnsupportedOperationException(field + " is unavailable in " + getClass().getSimpleName());
		}
	}

	private void edit(Field field) {
		markMutated();
		requireAvailable(field);
	}

	private static int checkedRange(int value, int min, int max, String name) {
		if (value < min || value > max) throw new IllegalArgumentException(name + " out of range: " + value);
		return value;
	}

	@Override
	public final boolean isLegacyDimensionIdAvailable() {
		return layout.available.contains(DIMENSION_INT);
	}

	@Override
	public final int legacyDimensionId() {
		requireAvailable(DIMENSION_INT);
		return dimensionId;
	}

	@Override
	public final void setLegacyDimensionId(int legacyDimensionId) {
		edit(DIMENSION_INT);
		dimensionId = legacyDimensionId;
	}

	@Override
	public final boolean isDimensionTypeIdAvailable() {
		return layout.available.contains(DIMENSION_VARINT);
	}

	@Override
	public final int dimensionTypeId() {
		requireAvailable(DIMENSION_VARINT);
		return dimensionId;
	}

	@Override
	public final void setDimensionTypeId(int dimensionTypeId) {
		edit(DIMENSION_VARINT);
		dimensionId = checkedRange(dimensionTypeId, 0, Integer.MAX_VALUE, "dimensionTypeId");
	}

	@Override
	public final boolean isDimensionTypeKeyAvailable() {
		return layout.available.contains(DIMENSION_KEY);
	}

	@Override
	public final MinecraftKey dimensionTypeKey() {
		requireAvailable(DIMENSION_KEY);
		return MinecraftKey.parse(dimensionKey);
	}

	@Override
	public final void setDimensionTypeKey(MinecraftKey dimensionTypeKey) {
		edit(DIMENSION_KEY);
		dimensionKey = dimensionTypeKey.fullKey();
	}

	@Override
	public final boolean isDimensionTypeDataAvailable() {
		return layout.available.contains(DIMENSION_NBT);
	}

	@Override
	public final CompoundTag dimensionTypeData() {
		requireAvailable(DIMENSION_NBT);
		return dimensionData.copy();
	}

	@Override
	public final void setDimensionTypeData(CompoundTag dimensionTypeData) {
		edit(DIMENSION_NBT);
		dimensionData = dimensionTypeData.copy();
	}

	@Override
	public final boolean isWorldKeyAvailable() {
		return layout.available.contains(WORLD);
	}

	@Override
	public final MinecraftKey worldKey() {
		requireAvailable(WORLD);
		return MinecraftKey.parse(world);
	}

	@Override
	public final void setWorldKey(MinecraftKey worldKey) {
		edit(WORLD);
		world = worldKey.fullKey();
	}

	@Override
	public final boolean isLevelTypeAvailable() {
		return layout.available.contains(LEVEL_TYPE);
	}

	@Override
	public final String levelType() {
		requireAvailable(LEVEL_TYPE);
		return levelType;
	}

	@Override
	public final void setLevelType(String levelType) {
		edit(LEVEL_TYPE);
		if (levelType.length() > 16) throw new IllegalArgumentException("levelType exceeds 16 characters");
		this.levelType = levelType;
	}

	@Override
	public final boolean isDifficultyIdAvailable() {
		return layout.available.contains(DIFFICULTY);
	}

	@Override
	public final int difficultyId() {
		requireAvailable(DIFFICULTY);
		return difficulty;
	}

	@Override
	public final void setDifficultyId(int difficultyId) {
		edit(DIFFICULTY);
		difficulty = checkedRange(difficultyId, 0, 255, "difficultyId");
	}

	@Override
	public final boolean isHashedSeedAvailable() {
		return layout.available.contains(SEED);
	}

	@Override
	public final long hashedSeed() {
		requireAvailable(SEED);
		return seed;
	}

	@Override
	public final void setHashedSeed(long hashedSeed) {
		edit(SEED);
		seed = hashedSeed;
	}

	@Override
	public final int gameModeId() {
		return gameMode;
	}

	@Override
	public final void setGameModeId(int gameModeId) {
		markMutated();
		gameMode = checkedRange(gameModeId, 0, layout.available.contains(GAME_MODE) ? 255 : Integer.MAX_VALUE, "gameModeId");
	}

	@Override
	public final boolean isPreviousGameModeAvailable() {
		return layout.available.contains(PREVIOUS_MODE) || layout.available.contains(PREVIOUS_MODE_VARINT);
	}

	@Override
	public final int previousGameModeId() {
		return previousMode;
	}

	@Override
	public final void setPreviousGameModeId(int previousGameModeId) {
		boolean varInt = layout.available.contains(PREVIOUS_MODE_VARINT);
		edit(varInt ? PREVIOUS_MODE_VARINT : PREVIOUS_MODE);
		previousMode = checkedRange(previousGameModeId, -1, varInt ? Integer.MAX_VALUE - 1 : Byte.MAX_VALUE, "previousGameModeId");
	}

	@Override
	public final boolean isDebugWorldAvailable() {
		return layout.available.contains(DEBUG);
	}

	@Override
	public final boolean debugWorld() {
		requireAvailable(DEBUG);
		return debug;
	}

	@Override
	public final void setDebugWorld(boolean debugWorld) {
		edit(DEBUG);
		debug = debugWorld;
	}

	@Override
	public final boolean isFlatWorldAvailable() {
		return layout.available.contains(FLAT);
	}

	@Override
	public final boolean flatWorld() {
		requireAvailable(FLAT);
		return flat;
	}

	@Override
	public final void setFlatWorld(boolean flatWorld) {
		edit(FLAT);
		flat = flatWorld;
	}

	@Override
	public final boolean isKeepAttributesAvailable() {
		return layout.available.contains(KEEP_ATTRIBUTES);
	}

	@Override
	public final boolean keepAttributes() {
		requireAvailable(KEEP_ATTRIBUTES);
		return keepAttributes;
	}

	@Override
	public final void setKeepAttributes(boolean keepAttributes) {
		edit(KEEP_ATTRIBUTES);
		this.keepAttributes = keepAttributes;
	}

	@Override
	public final boolean isKeepMaskAvailable() {
		return layout.available.contains(KEEP_MASK);
	}

	@Override
	public final byte keepMask() {
		requireAvailable(KEEP_MASK);
		return keepMask;
	}

	@Override
	public final void setKeepMask(byte keepMask) {
		edit(KEEP_MASK);
		this.keepMask = keepMask;
	}

	@Override
	public final boolean isLastDeathLocationAvailable() {
		return layout.available.contains(DEATH_LOCATION);
	}

	@Override
	public final @Nullable MinecraftKey lastDeathDimension() {
		return deathLocation == null ? null : MinecraftKey.parse(deathLocation.dimension);
	}

	@Override
	public final @Nullable BlockPosition lastDeathPosition() {
		return deathLocation == null ? null : deathLocation.position;
	}

	@Override
	public final void setLastDeathLocation(MinecraftKey dimension, BlockPosition position) {
		edit(DEATH_LOCATION);
		deathLocation = new DeathPosition(dimension.fullKey(), Objects.requireNonNull(position, "position"));
	}

	@Override
	public final void clearLastDeathLocation() {
		edit(DEATH_LOCATION);
		deathLocation = null;
	}

	@Override
	public final boolean isPortalCooldownAvailable() {
		return layout.available.contains(COOLDOWN);
	}

	@Override
	public final int portalCooldown() {
		requireAvailable(COOLDOWN);
		return portalCooldown;
	}

	@Override
	public final void setPortalCooldown(int portalCooldown) {
		edit(COOLDOWN);
		this.portalCooldown = portalCooldown;
	}

	@Override
	public final boolean isSeaLevelAvailable() {
		return layout.available.contains(SEA_LEVEL);
	}

	@Override
	public final int seaLevel() {
		requireAvailable(SEA_LEVEL);
		return seaLevel;
	}

	@Override
	public final void setSeaLevel(int seaLevel) {
		edit(SEA_LEVEL);
		this.seaLevel = seaLevel;
	}

	private static final class DeathPosition {
		static final StreamCodec<ByteBuf, ByteBuf, DeathPosition> CODEC = StreamCodec.compound(
			VARINT_STRING, (DeathPosition p) -> p.dimension,
			BlockPosition.WIRE_STREAM_CODEC, p -> p.position,
			DeathPosition::new
		).optional(BOOLEAN);

		final String dimension;
		final BlockPosition position;

		DeathPosition(String dimension, BlockPosition position) {
			this.dimension = dimension;
			this.position = position;
		}
	}

	enum Field {
		DIMENSION_INT(INTEGER, p -> p.dimensionId, (p, v) -> p.dimensionId = v),
		DIMENSION_VARINT(VARINT, p -> p.dimensionId, (p, v) -> p.dimensionId = v),
		DIMENSION_KEY(VARINT_STRING, p -> p.dimensionKey, (p, v) -> p.dimensionKey = v),
		DIMENSION_NBT(NAMED_COMPOUND_TAG, p -> p.dimensionData, (p, v) -> {
			if (v == null) throw new IllegalArgumentException("Missing respawn dimension data");
			p.dimensionData = v;
		}),
		WORLD(VARINT_STRING, p -> p.world, (p, v) -> p.world = v),
		LEVEL_TYPE(stringCodec(16), p -> p.levelType, (p, v) -> p.levelType = v),
		DIFFICULTY(UNSIGNED_BYTE, p -> p.difficulty, (p, v) -> p.difficulty = v),
		SEED(LONG, p -> p.seed, (p, v) -> p.seed = v),
		GAME_MODE(UNSIGNED_BYTE, p -> p.gameMode, (p, v) -> p.gameMode = v),
		GAME_MODE_VARINT(VARINT, p -> p.gameMode, (p, v) -> p.gameMode = v),
		PREVIOUS_MODE(SIGNED_BYTE, p -> p.previousMode, (p, v) -> p.previousMode = v),
		// 26.3 encodes an absent previous game mode as zero and a present ID as ID + 1.
		PREVIOUS_MODE_VARINT(VARINT, p -> p.previousMode + 1, (p, v) -> p.previousMode = v - 1),
		DEBUG(BOOLEAN, p -> p.debug, (p, v) -> p.debug = v),
		FLAT(BOOLEAN, p -> p.flat, (p, v) -> p.flat = v),
		KEEP_ATTRIBUTES(BOOLEAN, p -> p.keepAttributes, (p, v) -> p.keepAttributes = v),
		KEEP_MASK(BYTE, p -> p.keepMask, (p, v) -> p.keepMask = v),
		DEATH_LOCATION(DeathPosition.CODEC, p -> p.deathLocation, (p, v) -> p.deathLocation = v),
		COOLDOWN(VARINT, p -> p.portalCooldown, (p, v) -> p.portalCooldown = v),
		SEA_LEVEL(VARINT, p -> p.seaLevel, (p, v) -> p.seaLevel = v);

		private final BiConsumer<ByteBuf, RespawnWirePacket> read;
		private final BiConsumer<ByteBuf, RespawnWirePacket> write;

		<T> Field(StreamCodec<ByteBuf, ByteBuf, T> codec, Function<RespawnWirePacket, T> get, BiConsumer<RespawnWirePacket, T> set) {
			read = (input, packet) -> set.accept(packet, codec.decode(input));
			write = (output, packet) -> codec.encode(output, get.apply(packet));
		}
	}

	private static final class Layout {
		private final int firstProtocol, endProtocol;
		private final Field[] fields;
		private final EnumSet<Field> available;

		private Layout(int firstProtocol, int endProtocol, Field... fields) {
			this.firstProtocol = firstProtocol;
			this.endProtocol = endProtocol;
			this.fields = fields;
			this.available = EnumSet.copyOf(Arrays.asList(fields));
		}

		private <P extends RespawnWirePacket> StreamCodec<ByteBuf, ByteBuf, P> codec(Supplier<P> factory) {
			return StreamCodec.of((output, packet) -> {
				for (Field field : fields) field.write.accept(output, packet);
			}, input -> {
				P packet = factory.get();
				for (Field field : fields) field.read.accept(input, packet);
				return packet;
			});
		}
	}

	public static final class Legacy extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(47, 477,
			DIMENSION_INT, DIFFICULTY, GAME_MODE, LEVEL_TYPE);
		public static final StreamCodec<ByteBuf, ByteBuf, Legacy> STREAM_CODEC = LAYOUT.codec(Legacy::new);

		public Legacy() {
			super(LAYOUT);
		}
	}

	public static final class LevelType extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(477, 573,
			DIMENSION_INT, GAME_MODE, LEVEL_TYPE);
		public static final StreamCodec<ByteBuf, ByteBuf, LevelType> STREAM_CODEC = LAYOUT.codec(LevelType::new);

		public LevelType() {
			super(LAYOUT);
		}
	}

	public static final class HashedSeed extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(573, 735,
			DIMENSION_INT, SEED, GAME_MODE, LEVEL_TYPE);
		public static final StreamCodec<ByteBuf, ByteBuf, HashedSeed> STREAM_CODEC = LAYOUT.codec(HashedSeed::new);

		public HashedSeed() {
			super(LAYOUT);
		}
	}

	public static final class NamedDimension extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(735, 751,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_ATTRIBUTES);
		public static final StreamCodec<ByteBuf, ByteBuf, NamedDimension> STREAM_CODEC = LAYOUT.codec(NamedDimension::new);

		public NamedDimension() {
			super(LAYOUT);
		}
	}

	public static final class DimensionData extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(751, 759,
			DIMENSION_NBT, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_ATTRIBUTES);
		public static final StreamCodec<ByteBuf, ByteBuf, DimensionData> STREAM_CODEC = LAYOUT.codec(DimensionData::new);

		public DimensionData() {
			super(LAYOUT);
		}
	}

	public static final class DeathLocation extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(759, 761,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_ATTRIBUTES, DEATH_LOCATION);
		public static final StreamCodec<ByteBuf, ByteBuf, DeathLocation> STREAM_CODEC = LAYOUT.codec(DeathLocation::new);

		public DeathLocation() {
			super(LAYOUT);
		}
	}

	public static final class KeepMask extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(761, 763,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_MASK, DEATH_LOCATION);
		public static final StreamCodec<ByteBuf, ByteBuf, KeepMask> STREAM_CODEC = LAYOUT.codec(KeepMask::new);

		public KeepMask() {
			super(LAYOUT);
		}
	}

	public static final class PortalCooldown extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(763, 764,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_MASK, DEATH_LOCATION, COOLDOWN);
		public static final StreamCodec<ByteBuf, ByteBuf, PortalCooldown> STREAM_CODEC = LAYOUT.codec(PortalCooldown::new);

		public PortalCooldown() {
			super(LAYOUT);
		}
	}

	public static final class CommonSpawnInfo extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(764, 766,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, CommonSpawnInfo> STREAM_CODEC = LAYOUT.codec(CommonSpawnInfo::new);

		public CommonSpawnInfo() {
			super(LAYOUT);
		}
	}

	public static final class RegistryDimension extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(766, 768,
			DIMENSION_VARINT, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, RegistryDimension> STREAM_CODEC = LAYOUT.codec(RegistryDimension::new);

		public RegistryDimension() {
			super(LAYOUT);
		}
	}

	public static final class SeaLevel extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(768, 777,
			DIMENSION_VARINT, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, SEA_LEVEL, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, SeaLevel> STREAM_CODEC = LAYOUT.codec(SeaLevel::new);

		public SeaLevel() {
			super(LAYOUT);
		}
	}

	public static final class VarIntGameModes extends RespawnWirePacket {
		private static final Layout LAYOUT = new Layout(777, Integer.MAX_VALUE,
			DIMENSION_VARINT, WORLD, SEED, GAME_MODE_VARINT, PREVIOUS_MODE_VARINT, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, SEA_LEVEL, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, VarIntGameModes> STREAM_CODEC = LAYOUT.codec(VarIntGameModes::new);

		public VarIntGameModes() {
			super(LAYOUT);
		}
	}
}
