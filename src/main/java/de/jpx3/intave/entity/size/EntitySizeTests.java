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

package de.jpx3.intave.entity.size;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.entity.type.EntityTypeDataAccessor;
import de.jpx3.intave.module.tracker.entity.LazyEntityCollisionService;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EntitySizeTests extends IntegrationTests {
  public EntitySizeTests() {
    super("ES");
  }

  @Test(testCode = "native-registry", severity = Severity.ERROR)
  public void testNativeEntityRegistry() throws ReflectiveOperationException {
    if (MinecraftVersions.VER26_3.below()) return;
    Object registry = Lookup.serverField("IRegistry", "ENTITY_TYPE").get(null);
    java.lang.reflect.Method getId = registry.getClass().getMethod("getId", Object.class);
    java.lang.reflect.Method getKey = registry.getClass().getMethod("getKey", Object.class);
    java.lang.reflect.Method getDimensions = Lookup.serverClass("EntityTypes").getMethod("getDimensions");
    int count = 0;
    for (Object type : (Iterable<?>) registry) {
      int id = (int) getId.invoke(registry, type);
      EntityTypeData entry = EntityTypeDataAccessor.resolveFromId(id, false);
      if (entry == null) fail("Missing native entity ID " + id);
      String key = getKey.invoke(registry, type).toString();
      if (entry.typeId() != id || !("minecraft:" + entry.name()).equals(key)) {
        fail("Incorrect native entity mapping at " + id + ": " + key);
      }
      Object size = getDimensions.invoke(type);
      float width = (float) size.getClass().getMethod("width").invoke(size);
      float height = (float) size.getClass().getMethod("height").invoke(size);
      if (entry.size().width() != width || entry.size().height() != height) {
        fail("Incorrect native dimensions for " + key);
      }
      count++;
    }
    if (EntityTypeDataAccessor.resolveFromId(count, false) != null) fail("Unexpected stored entity ID " + count);
  }

  @Test(
    severity = Severity.ERROR
  )
  public void testSheep() {
    World world = Bukkit.getWorlds().get(0);
    // spawn sheep
    Sheep sheep = world.spawn(new Location(world, 0,0,0), Sheep.class);
    Object handle = ReflectiveHandleAccess.handleOf(sheep);
    // get size
    Class<?> entityClass = handle.getClass();
    HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass);
    sheep.remove();
    if (size == null || Math.abs(size.width() - 0.9) > 0.01 || Math.abs(size.height() - 1.3) > 0.01) {
      fail("Failed to fetch sheep size, is " + size);
    }
  }

  @Test(testCode = "happy-ghast-surface", severity = Severity.ERROR)
  public void testHappyGhastCollisionSurface() {
    if (new MinecraftVersion("1.21.6").below()) return;
    World world = Bukkit.getWorlds().get(0);
    Location location = new Location(world, 8, 90, 8);
    Ageable ghast = (Ageable) world.spawnEntity(location, EntityType.valueOf("HAPPY_GHAST"));
    List<Entity> nearby = new ArrayList<>(Collections.singletonList(ghast));
    Player player = FakePlayerFactory.createPlayer((name, args) -> {
      switch (name) {
        case "getWorld":
          return world;
        case "getLocation":
          return location;
        case "getNearbyEntities":
          return nearby;
      }
      return null;
    });
    User user = UserFactory.createTestUserFor(player, ProtocolMetadata.VER_1_21_6);
    UserRepository.manuallyRegisterUser(player, user);
    try {
      ghast.setAdult();
      MovementMetadata movement = user.meta().movement();
      LazyEntityCollisionService service = new LazyEntityCollisionService();
      PlayerMoveEvent move = new PlayerMoveEvent(player, location, location);
      double top = location.getY() + HitboxSizeAccess.dimensionsOfBukkit(ghast).height();
      for (double[] offset : new double[][]{
        {0, 0, 0}, {1.9, 0, 1.9}, {-1.9, 0, -1.9}, {-1.9, 0, 1.9}, {1.9, 0, -1.9}, {0, 1.25, 0}
      }) {
        movement.verifiedLastPositionX = location.getX() + offset[0];
        movement.verifiedLastPositionY = top + offset[1];
        movement.verifiedLastPositionZ = location.getZ() + offset[2];
        service.on(move);
        assertTrue(movement.collidedWithEntity());
        assertTrue(movement.immutableCopy().collidedWithEntity());
      }

      movement.verifiedLastPositionY = top - 0.01;
      assertFalse(movement.collidedWithEntity());
      service.on(move);
      assertNull(movement.nearestEntitySurface);
      movement.verifiedLastPositionY = top;
      movement.verifiedLastPositionX = location.getX() + 4;
      service.on(move);
      assertFalse(movement.collidedWithEntity());

      movement.verifiedLastPositionX = location.getX();
      ghast.setBaby();
      service.on(move);
      assertFalse(movement.collidedWithEntity());
      ghast.setAdult();
      user.meta().protocol().setProtocolVersion(ProtocolMetadata.VER_1_21_5);
      service.on(move);
      assertFalse(movement.collidedWithEntity());
      user.meta().protocol().setProtocolVersion(ProtocolMetadata.VER_1_21_6);
      service.on(move);
      assertTrue(movement.collidedWithEntity());
      nearby.clear();
      service.on(move);
      assertFalse(movement.collidedWithEntity());
      nearby.add(ghast);
      service.on(move);
      assertTrue(movement.collidedWithEntity());
      service.on(new PlayerChangedWorldEvent(player, world));
      assertFalse(movement.collidedWithEntity());
    } finally {
      ghast.remove();
      UserRepository.unregisterUser(player);
    }
  }
}
