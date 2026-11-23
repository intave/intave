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

package de.jpx3.intave.packet.wire.world;

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableRespawnPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;
import static de.jpx3.intave.packet.wire.world.RespawnWirePacket.Field.*;

public final class RespawnWirePacket {
	private RespawnWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol477.class,
			Protocol573.class,
			Protocol735.class,
			Protocol751.class,
			Protocol759.class,
			Protocol761.class,
			Protocol763.class,
			Protocol764.class,
			Protocol766.class,
			Protocol768.class,
			Protocol777.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableRespawnPacket {
		protected final Layout layout;
		protected int dimensionId;
		protected String dimensionKey = "minecraft:overworld";
		protected CompoundTag dimensionData;
		protected String world = "minecraft:overworld";
		protected String levelType = "default";
		protected int difficulty;
		protected long seed;
		protected int gameMode;
		protected int previousMode = -1;
		protected boolean debug, flat, keepAttributes;
		protected byte keepMask;
		protected DeathPosition deathLocation;
		protected int portalCooldown, seaLevel;

		private Base(Layout layout) {
			this.layout = layout;
			if (isDimensionTypeDataAvailable()) dimensionData = new CompoundTag();
		}

		@Override
		public final boolean appliesTo(int protocol) {
			return protocol >= layout.firstProtocol && protocol < layout.endProtocol;
		}

		protected void requireAvailable(Field field) {
			if (!layout.available.contains(field)) {
				throw new UnsupportedOperationException(field + " is unavailable in " + getClass().getSimpleName());
			}
		}

		protected void edit(Field field) {
			markMutated();
			requireAvailable(field);
		}

		protected static int checkedRange(int value, int min, int max, String name) {
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
	}

	static final class Protocol47 extends Base {
		private static final Layout LAYOUT = new Layout(47, 477,
			DIMENSION_INT, DIFFICULTY, GAME_MODE, LEVEL_TYPE);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = LAYOUT.codec(Protocol47::new);

		public Protocol47() {
			super(LAYOUT);
		}
	}

	static final class Protocol477 extends Base {
		private static final Layout LAYOUT = new Layout(477, 573,
			DIMENSION_INT, GAME_MODE, LEVEL_TYPE);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC = LAYOUT.codec(Protocol477::new);

		public Protocol477() {
			super(LAYOUT);
		}
	}

	static final class Protocol573 extends Base {
		private static final Layout LAYOUT = new Layout(573, 735,
			DIMENSION_INT, SEED, GAME_MODE, LEVEL_TYPE);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol573> STREAM_CODEC = LAYOUT.codec(Protocol573::new);

		public Protocol573() {
			super(LAYOUT);
		}
	}

	static final class Protocol735 extends Base {
		private static final Layout LAYOUT = new Layout(735, 751,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_ATTRIBUTES);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC = LAYOUT.codec(Protocol735::new);

		public Protocol735() {
			super(LAYOUT);
		}
	}

	static final class Protocol751 extends Base {
		private static final Layout LAYOUT = new Layout(751, 759,
			DIMENSION_NBT, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_ATTRIBUTES);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol751> STREAM_CODEC = LAYOUT.codec(Protocol751::new);

		public Protocol751() {
			super(LAYOUT);
		}
	}

	static final class Protocol759 extends Base {
		private static final Layout LAYOUT = new Layout(759, 761,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_ATTRIBUTES, DEATH_LOCATION);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC = LAYOUT.codec(Protocol759::new);

		public Protocol759() {
			super(LAYOUT);
		}
	}

	static final class Protocol761 extends Base {
		private static final Layout LAYOUT = new Layout(761, 763,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_MASK, DEATH_LOCATION);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol761> STREAM_CODEC = LAYOUT.codec(Protocol761::new);

		public Protocol761() {
			super(LAYOUT);
		}
	}

	static final class Protocol763 extends Base {
		private static final Layout LAYOUT = new Layout(763, 764,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			KEEP_MASK, DEATH_LOCATION, COOLDOWN);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC = LAYOUT.codec(Protocol763::new);

		public Protocol763() {
			super(LAYOUT);
		}
	}

	static final class Protocol764 extends Base {
		private static final Layout LAYOUT = new Layout(764, 766,
			DIMENSION_KEY, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC = LAYOUT.codec(Protocol764::new);

		public Protocol764() {
			super(LAYOUT);
		}
	}

	static final class Protocol766 extends Base {
		private static final Layout LAYOUT = new Layout(766, 768,
			DIMENSION_VARINT, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol766> STREAM_CODEC = LAYOUT.codec(Protocol766::new);

		public Protocol766() {
			super(LAYOUT);
		}
	}

	static final class Protocol768 extends Base {
		private static final Layout LAYOUT = new Layout(768, 777,
			DIMENSION_VARINT, WORLD, SEED, GAME_MODE, PREVIOUS_MODE, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, SEA_LEVEL, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC = LAYOUT.codec(Protocol768::new);

		public Protocol768() {
			super(LAYOUT);
		}
	}

	static final class Protocol777 extends Base {
		private static final Layout LAYOUT = new Layout(777, Integer.MAX_VALUE,
			DIMENSION_VARINT, WORLD, SEED, GAME_MODE_VARINT, PREVIOUS_MODE_VARINT, DEBUG, FLAT,
			DEATH_LOCATION, COOLDOWN, SEA_LEVEL, KEEP_MASK);
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC = LAYOUT.codec(Protocol777::new);

		public Protocol777() {
			super(LAYOUT);
		}
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

		private final BiConsumer<ByteBuf, Base> read;
		private final BiConsumer<ByteBuf, Base> write;

		<T> Field(StreamCodec<ByteBuf, ByteBuf, T> codec, Function<Base, T> get, BiConsumer<Base, T> set) {
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

		private <P extends Base> StreamCodec<ByteBuf, ByteBuf, P> codec(Supplier<P> factory) {
			return StreamCodec.of((output, packet) -> {
				for (Field field : fields) field.write.accept(output, packet);
			}, input -> {
				P packet = factory.get();
				for (Field field : fields) field.read.accept(input, packet);
				return packet;
			});
		}
	}
}
