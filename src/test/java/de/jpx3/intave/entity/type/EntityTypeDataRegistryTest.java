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
}
