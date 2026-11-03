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

package de.jpx3.intave.block.interact;

import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.cache.BlockCaches;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.check.world.InteractionRaytrace;
import de.jpx3.intave.check.world.interaction.Interaction;
import de.jpx3.intave.check.world.interaction.InteractionEmulator;
import de.jpx3.intave.check.world.interaction.InteractionType;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.test.After;
import de.jpx3.intave.test.Before;
import de.jpx3.intave.test.BlockStorage;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.MockEmptyInventory;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class DoorInteractionTests extends IntegrationTests {
  private static final int LEGACY_HALF_BIT = 8;
  private static final int LEGACY_OPEN_BIT = 4;
  private static final boolean LEGACY_VARIANTS = !MinecraftVersions.VER1_13_0.atOrAbove();

  private World world;
  private Block lowerBlock;
  private Block upperBlock;
  private BlockStorage priorLower;
  private BlockStorage priorUpper;
  private Player player;
  private User user;
  private BlockCache blockCache;

  public DoorInteractionTests() {
    super("DI");
  }

  @Before
  public void setup() {
    world = Bukkit.getWorlds().get(0);
    lowerBlock = world.getBlockAt(0, 1, 0);
    upperBlock = world.getBlockAt(0, 2, 0);
    priorLower = BlockStorage.store(lowerBlock);
    priorUpper = BlockStorage.store(upperBlock);
    player = FakePlayerFactory.createPlayer(
      (methodName, args) -> {
        switch (methodName) {
          case "getWorld":
            return world;
          case "getInventory":
            return new MockEmptyInventory();
          case "getLocation":
            return new Location(world, 0.5D, 1.0D, 0.5D);
          case "getUniqueId":
            return UUID.randomUUID();
          case "getActivePotionEffects":
            return Collections.emptyList();
        }
        return null;
      }
    );
    blockCache = BlockCaches.cacheForPlayer(player);
    user = UserFactory.createTestUserFor(player, (usr, key) -> {
      if ("blockCache".equals(key)) {
        return blockCache;
      }
      if ("protocolVersion".equals(key)) {
        return 0;
      }
      return null;
    });
    UserRepository.manuallyRegisterUser(player, user);
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void doorVariantsHaveReversibleOpenTransitions() {
    int testedVariants = 0;
    for (Material door : doorMaterials()) {
      if (!isUsableDoor(door) || !DoorInteractionInteractionPatch.canOpenByHand(door)) {
        continue;
      }
      for (Integer variantId : BlockVariantRegister.variantIdsOf(door)) {
        if (LEGACY_VARIANTS) {
          if ((variantId & LEGACY_HALF_BIT) != 0) {
            continue;
          }
          int toggledVariant = variantId ^ LEGACY_OPEN_BIT;
          assertContains(BlockVariantRegister.variantIdsOf(door), toggledVariant);
          assertEquals(variantId, toggledVariant ^ LEGACY_OPEN_BIT);
          testedVariants++;
          continue;
        }

        Map<String, Comparable<?>> properties = BlockVariantRegister.propertiesOf(door, variantId);
        Comparable<?> open = properties.get("open");
        if (!(open instanceof Boolean)) {
          continue;
        }
        int toggledVariant = BlockInteractionPatch.toggleBooleanProperty(door, variantId, "open");
        assertTrue(toggledVariant >= 0);
        Map<String, Comparable<?>> expected = new HashMap<>(properties);
        expected.put("open", !((Boolean) open));
        assertEquals(expected, BlockVariantRegister.propertiesOf(door, toggledVariant));
        testedVariants++;
      }
    }
    assertTrue(testedVariants > 0);
  }

  @Test(
    testCode = "B",
    severity = Severity.ERROR
  )
  public void clickingEitherDoorHalfUpdatesBothCachedBlocks() {
    DoorPair pair = doorPair(material -> DoorInteractionInteractionPatch.canOpenByHand(material));
    prepareDoor(pair);

    BlockShape closedLowerShape = blockCache.collisionShapeAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    );
    BlockShape closedUpperShape = blockCache.collisionShapeAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    );

    assertTrue(BlockInteractionPatches.interact(
      user, world, blockCache,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      "DOOR_TEST_LOWER"
    ));
    assertDoorOpen(pair, true);
    assertNotEquals(closedLowerShape, blockCache.collisionShapeAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    ));
    assertNotEquals(closedUpperShape, blockCache.collisionShapeAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    ));

    assertTrue(BlockInteractionPatches.interact(
      user, world, blockCache,
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
      "DOOR_TEST_UPPER"
    ));
    assertDoorOpen(pair, false);
    assertEquals(closedLowerShape, blockCache.collisionShapeAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    ));
    assertEquals(closedUpperShape, blockCache.collisionShapeAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    ));
  }

  @Test(
    testCode = "C",
    severity = Severity.ERROR
  )
  public void ironDoorsDoNotOpenByHand() {
    DoorPair pair = doorPair(material -> !DoorInteractionInteractionPatch.canOpenByHand(material));
    prepareDoor(pair);
    int lowerVariant = blockCache.variantIndexAt(lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ());
    int upperVariant = blockCache.variantIndexAt(upperBlock.getX(), upperBlock.getY(), upperBlock.getZ());

    assertFalse(BlockInteractionPatches.interact(
      user, world, blockCache,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      "DOOR_TEST_IRON"
    ));
    assertEquals(lowerVariant, blockCache.variantIndexAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    ));
    assertEquals(upperVariant, blockCache.variantIndexAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    ));
  }

  @Test(
    testCode = "D",
    severity = Severity.ERROR
  )
  public void orphanedLowerDoorRetainsCollisionAndToggles() {
    DoorPair pair = doorPair(material -> DoorInteractionInteractionPatch.canOpenByHand(material));
    lowerBlock.setType(pair.type, false);
    upperBlock.setType(Material.STONE, false);
    blockCache.override(
      world,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      pair.type, pair.lowerVariant,
      "DOOR_TEST_INVALID"
    );
    blockCache.override(
      world,
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
      Material.STONE, 0,
      "DOOR_TEST_INVALID"
    );

    BlockShape closedShape = blockCache.collisionShapeAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    );
    assertFalse(closedShape.isEmpty());
    assertTrue(BlockInteractionPatches.interact(
      user, world, blockCache,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      "DOOR_TEST_INVALID"
    ));
    assertBlockOpen(pair.type, lowerBlock, true);
    assertEquals(Material.STONE, blockCache.typeAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    ));
    BlockShape openShape = blockCache.collisionShapeAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    );
    assertFalse(openShape.isEmpty());
    assertNotEquals(closedShape, openShape);
  }

  @Test(
    testCode = "E",
    severity = Severity.ERROR
  )
  public void orphanedUpperDoorRetainsCollisionAndUsesVersionInteraction() {
    DoorPair pair = doorPair(material -> DoorInteractionInteractionPatch.canOpenByHand(material));
    lowerBlock.setType(Material.STONE, false);
    upperBlock.setType(pair.type, false);
    blockCache.override(
      world,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      Material.STONE, 0,
      "DOOR_TEST_ORPHAN_UPPER"
    );
    blockCache.override(
      world,
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
      pair.type, pair.upperVariant,
      "DOOR_TEST_ORPHAN_UPPER"
    );

    assertFalse(blockCache.collisionShapeAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    ).isEmpty());
    assertEquals(!LEGACY_VARIANTS, BlockInteractionPatches.interact(
      user, world, blockCache,
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
      "DOOR_TEST_ORPHAN_UPPER"
    ));
    assertBlockOpen(pair.type, upperBlock, !LEGACY_VARIANTS);
    assertEquals(Material.STONE, blockCache.typeAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    ));
    assertFalse(blockCache.collisionShapeAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    ).isEmpty());
  }

  @Test(
    testCode = "F",
    severity = Severity.ERROR
  )
  public void legacyOrphanDoorHitboxesMatchClientBounds() {
    if (!LEGACY_VARIANTS) {
      return;
    }

    user.meta().protocol().setProtocolVersion(ProtocolMetadata.VER_1_8);
    DoorPair pair = doorPair(material -> DoorInteractionInteractionPatch.canOpenByHand(material));
    Set<Integer> variants = BlockVariantRegister.variantIdsOf(pair.type);
    double[][] lowerBounds = {
      {0.0D, 0.0D, 0.0D, 0.1875D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.1875D},
      {0.8125D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.8125D, 1.0D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.1875D},
      {0.8125D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.8125D, 1.0D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.0D, 0.1875D, 1.0D, 1.0D}
    };

    lowerBlock.setType(pair.type, false);
    upperBlock.setType(Material.STONE, false);
    blockCache.override(
      world,
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
      Material.STONE, 0,
      "DOOR_TEST_LEGACY_BOUNDS"
    );
    for (int variant = 0; variant < lowerBounds.length; variant++) {
      assertContains(variants, variant);
      blockCache.override(
        world,
        lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
        pair.type, variant,
        "DOOR_TEST_LEGACY_BOUNDS"
      );
      assertShapeBounds(
        blockCache.collisionShapeAt(lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()),
        lowerBlock,
        lowerBounds[variant]
      );
    }

    lowerBlock.setType(Material.STONE, false);
    upperBlock.setType(pair.type, false);
    blockCache.override(
      world,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      Material.STONE, 0,
      "DOOR_TEST_LEGACY_BOUNDS"
    );
    double[] upperBounds = {0.0D, 0.0D, 0.0D, 0.1875D, 1.0D, 1.0D};
    for (int variant = 8; variant < 12; variant++) {
      assertContains(variants, variant);
      blockCache.override(
        world,
        upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
        pair.type, variant,
        "DOOR_TEST_LEGACY_BOUNDS"
      );
      assertShapeBounds(
        blockCache.collisionShapeAt(upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()),
        upperBlock,
        upperBounds
      );
    }
  }

  @Test(
    testCode = "G",
    severity = Severity.ERROR
  )
  public void viaVersionModernClientUsesTranslatedOrphanDoorHitboxes() {
    if (!LEGACY_VARIANTS) {
      return;
    }

    user.meta().protocol().setProtocolVersion(ProtocolMetadata.VER_1_13);
    DoorPair pair = doorPair(material -> DoorInteractionInteractionPatch.canOpenByHand(material));
    Set<Integer> variants = BlockVariantRegister.variantIdsOf(pair.type);
    double[][] translatedLowerBounds = {
      {0.0D, 0.0D, 0.0D, 0.1875D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.1875D},
      {0.8125D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.8125D, 1.0D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.8125D, 1.0D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.0D, 0.1875D, 1.0D, 1.0D},
      {0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.1875D},
      {0.8125D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D}
    };

    lowerBlock.setType(pair.type, false);
    upperBlock.setType(Material.STONE, false);
    blockCache.override(
      world,
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
      Material.STONE, 0,
      "DOOR_TEST_VIAVERSION_BOUNDS"
    );
    for (int variant = 0; variant < translatedLowerBounds.length; variant++) {
      assertContains(variants, variant);
      blockCache.override(
        world,
        lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
        pair.type, variant,
        "DOOR_TEST_VIAVERSION_BOUNDS"
      );
      assertShapeBounds(
        blockCache.collisionShapeAt(lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()),
        lowerBlock,
        translatedLowerBounds[variant]
      );
    }
  }

  @After
  public void teardown() {
    if (priorUpper != null) {
      priorUpper.restore();
    }
    if (priorLower != null) {
      priorLower.restore();
    }
    if (blockCache != null) {
      blockCache.invalidateAll();
    }
    if (player != null) {
      UserRepository.unregisterUser(player);
    }
  }

  @Test(
    testCode = "H",
    severity = Severity.ERROR
  )
  public void breakingAnOpenedDoorDoesNotToggleItsCachedState() throws ReflectiveOperationException {
    InteractionRaytrace check = IntavePlugin.singletonInstance().checks().searchCheck(InteractionRaytrace.class);
    Field emulatorField = InteractionRaytrace.class.getDeclaredField("interactionEmulator");
    emulatorField.setAccessible(true);
    InteractionEmulator emulator = (InteractionEmulator) emulatorField.get(check);
    DoorPair pair = doorPair(DoorInteractionInteractionPatch::canOpenByHand);

    for (Block half : new Block[]{lowerBlock, upperBlock}) {
      prepareDoor(pair);
      assertEquals(InteractionEmulator.EmulationResult.SUCCEEDED,
        emulate(emulator, half, InteractionType.INTERACT, null));
      assertDoorOpen(pair, true);
      BlockShape lowerShape = blockCache.collisionShapeAt(0, 1, 0);
      BlockShape upperShape = blockCache.collisionShapeAt(0, 2, 0);

      for (PlayerDigType action : new PlayerDigType[]{
        PlayerDigType.START_DESTROY_BLOCK, PlayerDigType.ABORT_DESTROY_BLOCK, PlayerDigType.START_DESTROY_BLOCK
      }) {
        assertEquals(InteractionEmulator.EmulationResult.SUCCEEDED,
          emulate(emulator, half, InteractionType.START_BREAK, action));
        assertDoorOpen(pair, true);
        assertEquals(lowerShape, blockCache.collisionShapeAt(0, 1, 0));
        assertEquals(upperShape, blockCache.collisionShapeAt(0, 2, 0));
      }

      assertEquals(InteractionEmulator.EmulationResult.SUCCEEDED,
        emulate(emulator, half, InteractionType.EMPTY_INTERACT, null));
      assertDoorOpen(pair, true);
      assertEquals(InteractionEmulator.EmulationResult.SUCCEEDED,
        emulate(emulator, half, InteractionType.BREAK, PlayerDigType.STOP_DESTROY_BLOCK));
      assertEquals(Material.AIR, blockCache.typeAt(half.getX(), half.getY(), half.getZ()));
    }
  }

  private InteractionEmulator.EmulationResult emulate(
    InteractionEmulator emulator, Block block, InteractionType type, PlayerDigType action
  ) {
    return emulator.emulate(new Interaction(
      0, null, world, player,
      new BlockPosition(block.getX(), block.getY(), block.getZ()), 1, type,
      Material.AIR, new ItemStack(Material.AIR), PlayerHand.MAIN_HAND, action,
      Float.NaN, Float.NaN, Float.NaN, -1
    ));
  }

  private void prepareDoor(DoorPair pair) {
    lowerBlock.setType(pair.type, false);
    upperBlock.setType(pair.type, false);
    blockCache.stateAt(lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ());
    blockCache.stateAt(upperBlock.getX(), upperBlock.getY(), upperBlock.getZ());
    overrideDoorPair(pair.lowerVariant, pair.upperVariant, "DOOR_TEST_INITIAL");
  }

  private void overrideDoorPair(int lowerVariant, int upperVariant, String reason) {
    blockCache.override(
      world,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      lowerBlock.getType(), lowerVariant,
      reason
    );
    blockCache.override(
      world,
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ(),
      upperBlock.getType(), upperVariant,
      reason
    );
    blockCache.override(
      world,
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ(),
      lowerBlock.getType(), lowerVariant,
      reason
    );
  }

  private void assertDoorOpen(DoorPair pair, boolean expectedOpen) {
    int lowerVariant = blockCache.variantIndexAt(
      lowerBlock.getX(), lowerBlock.getY(), lowerBlock.getZ()
    );
    int upperVariant = blockCache.variantIndexAt(
      upperBlock.getX(), upperBlock.getY(), upperBlock.getZ()
    );
    if (LEGACY_VARIANTS) {
      assertEquals(expectedOpen, (lowerVariant & LEGACY_OPEN_BIT) != 0);
      assertEquals(pair.upperVariant, upperVariant);
      return;
    }
    assertEquals(expectedOpen, BlockVariantRegister.propertiesOf(pair.type, lowerVariant).get("open"));
    assertEquals(expectedOpen, BlockVariantRegister.propertiesOf(pair.type, upperVariant).get("open"));
  }

  private void assertBlockOpen(Material type, Block block, boolean expectedOpen) {
    int variant = blockCache.variantIndexAt(block.getX(), block.getY(), block.getZ());
    if (LEGACY_VARIANTS) {
      assertEquals(expectedOpen, (variant & LEGACY_OPEN_BIT) != 0);
    } else {
      assertEquals(expectedOpen, BlockVariantRegister.propertiesOf(type, variant).get("open"));
    }
  }

  private void assertShapeBounds(BlockShape shape, Block block, double[] bounds) {
    assertEquals(Collections.singletonList(new BoundingBox(
      bounds[0], bounds[1], bounds[2],
      bounds[3], bounds[4], bounds[5]
    )), shape.normalized(block.getX(), block.getY(), block.getZ()).elementaryBoxes());
  }

  private DoorPair doorPair(Predicate<Material> materialFilter) {
    for (Material door : doorMaterials()) {
      if (!isUsableDoor(door) || !materialFilter.test(door)) {
        continue;
      }
      DoorPair pair = LEGACY_VARIANTS ? legacyDoorPair(door) : modernDoorPair(door);
      if (pair != null) {
        return pair;
      }
    }
    throw new AssertionError("No matching complete door pair found");
  }

  private DoorPair legacyDoorPair(Material door) {
    Integer lowerVariant = null;
    Integer upperVariant = null;
    for (Integer variant : BlockVariantRegister.variantIdsOf(door)) {
      if ((variant & LEGACY_HALF_BIT) == 0 && (variant & LEGACY_OPEN_BIT) == 0) {
        lowerVariant = variant;
      } else if ((variant & LEGACY_HALF_BIT) != 0) {
        upperVariant = variant;
      }
      if (lowerVariant != null && upperVariant != null) {
        return new DoorPair(door, lowerVariant, upperVariant);
      }
    }
    return null;
  }

  private DoorPair modernDoorPair(Material door) {
    Set<Integer> variants = BlockVariantRegister.variantIdsOf(door);
    for (Integer lowerVariant : variants) {
      Map<String, Comparable<?>> lower = BlockVariantRegister.propertiesOf(door, lowerVariant);
      if (!isHalf(lower, "lower") || !Boolean.FALSE.equals(lower.get("open"))) {
        continue;
      }
      for (Integer upperVariant : variants) {
        Map<String, Comparable<?>> upper = BlockVariantRegister.propertiesOf(door, upperVariant);
        if (isHalf(upper, "upper") && sameExceptHalf(lower, upper)) {
          return new DoorPair(door, lowerVariant, upperVariant);
        }
      }
    }
    return null;
  }

  private Set<Material> doorMaterials() {
    return MaterialSearch.materialsThatContain("DOOR");
  }

  private boolean isUsableDoor(Material material) {
    return DoorInteractionInteractionPatch.isDoor(material)
      && !BlockVariantRegister.variantIdsOf(material).isEmpty();
  }

  private static boolean sameExceptHalf(
    Map<String, Comparable<?>> first,
    Map<String, Comparable<?>> second
  ) {
    Map<String, Comparable<?>> normalizedFirst = new HashMap<>(first);
    Map<String, Comparable<?>> normalizedSecond = new HashMap<>(second);
    normalizedFirst.remove("half");
    normalizedSecond.remove("half");
    return normalizedFirst.equals(normalizedSecond);
  }

  private static boolean isHalf(Map<String, Comparable<?>> properties, String expected) {
    Comparable<?> half = properties.get("half");
    return half != null && expected.equalsIgnoreCase(String.valueOf(half));
  }

  private static final class DoorPair {
    private final Material type;
    private final int lowerVariant;
    private final int upperVariant;

    private DoorPair(Material type, int lowerVariant, int upperVariant) {
      this.type = type;
      this.lowerVariant = lowerVariant;
      this.upperVariant = upperVariant;
    }
  }
}
