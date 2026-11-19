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

package de.jpx3.intave.registry;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import de.jpx3.intave.share.MaterialAndVariant;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class BlockCacheEntryResolverTest {
	@BeforeEach
	void setup() {
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
		install(81923, 91047);
	}

	@AfterEach
	void cleanup() {
		BlockVariantRegister.overrideVariants(Collections.emptyMap());
		MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
	}

	@Test
	void blockUpdateExposesMaterialAndIntaveVariantForItsSourceProtocol() {
		BlockUpdate stairs = new BlockUpdate(new BlockPosition(-1, -64, 32), 1719);
		assertEquals(Optional.of(Material.OAK_STAIRS), stairs.material(393));
		assertEquals(OptionalInt.of(81923), stairs.variantId(393));
		assertEquals(1719, stairs.stateId());
		MaterialAndVariant resolved = stairs.resolveFor(393).orElseThrow();
		assertEquals(Material.OAK_STAIRS, resolved.material());
		assertEquals(81923, resolved.variantId());
		assertSame(resolved, stairs.resolveFor(393).orElseThrow());
		assertEquals(OptionalInt.of(91047), new BlockUpdate(BlockPosition.ORIGIN, 72).variantId(404));
	}

	@Test
	void legacyWireIdsUseModernMaterialsAndLiveVariantIds() {
		BlockUpdate legacy = new BlockUpdate(BlockPosition.ORIGIN, 53 << 4);
		assertEquals(Optional.of(Material.OAK_STAIRS), legacy.material(47));
		assertEquals(OptionalInt.of(81923), legacy.variantId(47));
		assertEquals(Optional.of(Material.GRANITE), new BlockUpdate(BlockPosition.ORIGIN, 17).material(47));
		assertEquals(OptionalInt.of(65209), new BlockUpdate(BlockPosition.ORIGIN, 17).variantId(47));
	}

	@Test
	void rebuildingTheVariantRegisterInvalidatesResolvedAndMaterialCaches() {
		BlockUpdate stairs = new BlockUpdate(BlockPosition.ORIGIN, 3000);
		MaterialAndVariant before = stairs.resolveFor(769).orElseThrow();
		install(123456, 234567);
		assertEquals(OptionalInt.of(123456), stairs.variantId(769));
		assertNotSame(before, stairs.resolveFor(769).orElseThrow());
	}

	@Test
	void unknownStatesUnsupportedMappingsAndMissingVariantsFailExplicitly() {
		assertThrows(IllegalArgumentException.class, () -> new BlockUpdate(BlockPosition.ORIGIN, 999999).material(769));
		assertThrows(IllegalArgumentException.class, () -> new BlockUpdate(BlockPosition.ORIGIN, 0).variantId(9999));
		// The fixture register intentionally has no variant for this other stair shape.
		assertThrows(UnsupportedOperationException.class, () -> new BlockUpdate(BlockPosition.ORIGIN, 3002).variantId(769));
		BlockVariantRegister.overrideVariants(Collections.emptyMap());
		assertThrows(IllegalStateException.class, () -> new BlockUpdate(BlockPosition.ORIGIN, 3000).variantId(769));
	}

	@Test
	void concurrentLookupsShareOnlyImmutableResults() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(4);
		try {
			List<Future<MaterialAndVariant>> futures = new ArrayList<>();
			for (int i = 0; i < 32; i++) futures.add(executor.submit(() -> new BlockUpdate(BlockPosition.ORIGIN, 1720).resolveFor(404).orElseThrow()));
			MaterialAndVariant first = futures.get(0).get(10, TimeUnit.SECONDS);
			for (Future<MaterialAndVariant> future : futures) assertSame(first, future.get(10, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}
	}

	private static void install(int stairId, int logId) {
		Map<Material, Map<Integer, BlockVariant>> variants = new EnumMap<>(Material.class);
		variants.put(Material.OAK_STAIRS, Map.of(stairId, variant(stairId,
			Map.of("facing", Facing.EAST, "half", "BOTTOM", "shape", "STRAIGHT", "waterlogged", false))));
		variants.put(Material.OAK_LOG, Map.of(logId, variant(logId, Map.of("axis", "X"))));
		variants.put(Material.GRANITE, Map.of(65209, variant(65209, Collections.emptyMap())));
		BlockVariantRegister.overrideVariants(variants);
	}

	private enum Facing { EAST }

	private static BlockVariant variant(int id, Map<String, Comparable<?>> properties) {
		return new BlockVariant() {
			public Set<String> propertyNames() { return properties.keySet(); }
			@SuppressWarnings("unchecked")
			public <T> T propertyOf(String name) { return (T) properties.get(name); }
			public <T extends Enum<T>> T enumProperty(Class<T> type, String name) { return Enum.valueOf(type, properties.get(name).toString()); }
			public int index() { return id; }
			public void dumpStates() {}
		};
	}
}

