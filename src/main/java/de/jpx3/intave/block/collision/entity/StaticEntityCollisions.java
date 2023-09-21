package de.jpx3.intave.block.collision.entity;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class StaticEntityCollisions {
  private static final UserLocal<EntityCollisionData> userData = UserLocal.withInitial(EntityCollisionData::new);

  public static @NotNull BlockShape inducedEntityShape(User user, int posX, int posY, int posZ) {
    EntityCollisionData collisionData = userData.get(user);
    if (collisionData.size == 0) {
      return BlockShapes.emptyShape();
    }
    return collisionData.shapeAt(posX, posY, posZ);
  }

  public static void enterEntitySpawn(User user, Entity entity) {
    if (collides(entity.typeData())) {
      userData.get(user).addEntity(entity);
    }
  }

  public static void enterEntityUpdate(User user, Entity entity) {
    if (collides(entity.typeData())) {
      userData.get(user).updateEntity(entity);
    }
  }

  public static void enterEntityDespawn(User user, Entity entity) {
    if (collides(entity.typeData())) {
      userData.get(user).removeEntity(entity);
    }
  }

  public static void tick(User user) {
    userData.get(user).tick();
  }

  private static boolean collides(EntityTypeData typeData) {
    return typeData.isShulker();
  }

  public static class EntityCollisionData {
    private final Map<Long, BoundingBox> entityBoxesByPositionKey = new HashMap<>();
    private int size = 0;

    /*
     * Why the shift by 0.6?
     * Figure it out when you add more entities.
     */

    public void addEntity(Entity entity) {
      Entity.EntityPositionContext position = entity.position;
      int posX = (int) (position.posX - 0.6);
      int posY = (int) (position.posY);
      int posZ = (int) (position.posZ - 0.6);
//      System.out.println("Adding entity at " + posX + "/" + position.posX + ", " + posY + "/" + position.posY + ", " + posZ + "/" + position.posZ);
      long key = bigKey(posX, posY, posZ);
      entityBoxesByPositionKey.put(key, entity.boundingBox());
      size++;
    }

    public void updateEntity(Entity entity) {
      Entity.EntityPositionContext position = entity.position;
      int posX = (int) (position.posX - 0.6);
      int posY = (int) (position.posY);
      int posZ = (int) (position.posZ - 0.6);
      long key = bigKey(posX, posY, posZ);
      entityBoxesByPositionKey.put(key, entity.boundingBox());
    }

    public void removeEntity(Entity entity) {
      Entity.EntityPositionContext position = entity.position;
      int posX = (int) (position.posX - 0.6);
      int posY = (int) (position.posY);
      int posZ = (int) (position.posZ - 0.6);
      long key = bigKey(posX, posY, posZ);
      entityBoxesByPositionKey.remove(key);
      size--;
    }

    public BlockShape shapeAt(int posX, int posY, int posZ) {
      long key = bigKey(posX, posY, posZ);
      BoundingBox box = entityBoxesByPositionKey.get(key);
      if (box == null) {
        return BlockShapes.emptyShape();
      }
      //todo return correct shape, not just a cube
      return BlockShapes.cubeAt(posX, posY, posZ);
    }

    public int size() {
      return size;
    }

    private static long bigKey(int posX, int posY, int posZ) {
      return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
    }

    public void tick() {


    }
  }
}
