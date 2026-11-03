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

package de.jpx3.intave.entity.type;

import de.jpx3.intave.adapter.MinecraftVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EntityTypeDataRegistryTest {
  @Test
  void loadsNearestPatchRegistryAndCachesResults() {
    EntityTypeDataRegistry registry = new EntityTypeDataRegistry(new MinecraftVersion("1.8.8"));

    EntityTypeData creeper = registry.resolveFor(50, true);
    assertEquals("Creeper", creeper.name());
    assertEquals(0.6F, creeper.size().width());
    assertEquals(1.8F, creeper.size().height());
    assertTrue(creeper.isLivingEntity());
    assertSame(creeper, registry.resolveFor(50, true));

    EntityTypeData primedTnt = registry.resolveFor(50, false);
    assertNull(primedTnt);

    primedTnt = registry.resolveFor(20, false);
    assertEquals("PrimedTnt", primedTnt.name());
    assertFalse(primedTnt.isLivingEntity());

    EntityTypeData boat = registry.resolveFor(41, false);
    assertEquals("Boat", boat.name());
    assertTrue(boat.isBoat());
    assertEquals(0.1, boat.mountedYOffset());
  }

  @Test
  void recognizesBoatsAndRaftsFromModernRegistries() {
    EntityTypeDataRegistry registry = new EntityTypeDataRegistry(new MinecraftVersion("1.21.1"));
    EntityTypeData chestBoat = registry.resolveFor(17, false);
    assertEquals("chest_boat", chestBoat.name());
    assertTrue(chestBoat.isBoat());
    assertEquals(0.1, chestBoat.mountedYOffset());
    assertEquals(RideableEntityType.CHEST_BOAT, RideableEntityType.identifyFrom("ChestBoat"));

    registry = new EntityTypeDataRegistry(new MinecraftVersion("26.3"));
    for (int identifier : new int[]{0, 1, 8, 9, 90, 91, 95, 96, 106, 107}) {
      EntityTypeData boat = registry.resolveFor(identifier, false);
      assertTrue(boat.isBoat(), boat.name());
      assertEquals(0.1, boat.mountedYOffset(), boat.name());
    }
    assertFalse(registry.resolveFor(59, true).isBoat());
    assertEquals(RideableEntityType.BOAT, RideableEntityType.identifyFrom("BAMBOO_RAFT"));
    assertEquals(RideableEntityType.CHEST_BOAT, RideableEntityType.identifyFrom("BAMBOO_CHEST_RAFT"));
  }

  @Test
  void keepsLivingAndNonLivingLookupsSeparate() {
    EntityTypeDataRegistry registry = new EntityTypeDataRegistry(new MinecraftVersion("1.21.11"));

    EntityTypeData living = registry.resolveFor(0, true);
    EntityTypeData nonLiving = registry.resolveFor(0, false);
    assertEquals("acacia_boat", living.name());
    assertTrue(living.isLivingEntity());
    assertFalse(nonLiving.isLivingEntity());
  }

  @Test
  void returnsNullForUnknownIdentifiers() {
    EntityTypeDataRegistry registry = new EntityTypeDataRegistry(new MinecraftVersion("26.2"));

    assertNull(registry.resolveFor(-1, true));
    assertNull(registry.resolveFor(Integer.MAX_VALUE, false));
  }

  @Test
  void loads263RegistryWithShiftedIdsAndNewEntities() {
    EntityTypeDataRegistry registry = new EntityTypeDataRegistry(new MinecraftVersion("26.3"));
    assertEquals("cushion", registry.resolveFor(33, false).name());
    assertEquals(0.25F, registry.resolveFor(33, false).size().height());
    assertEquals("poplar_boat", registry.resolveFor(106, false).name());
    assertEquals("poplar_chest_boat", registry.resolveFor(107, false).name());
    assertEquals("player", registry.resolveFor(159, true).name());
    assertEquals("fishing_bobber", registry.resolveFor(160, false).name());
    assertEquals(0.55F, registry.resolveFor(11, true).size().width());
    for (int id = 0; id <= 160; id++) {
      assertTrue(registry.resolveFor(id, true) != null, "Missing entity " + id);
      assertTrue(registry.resolveFor(id, false) != null, "Missing entity " + id);
    }
    assertNull(registry.resolveFor(161, false));
    assertEquals("player", new EntityTypeDataRegistry(new MinecraftVersion("26.2")).resolveFor(155, true).name());
  }
}
