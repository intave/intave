package de.jpx3.intave.tools.wrapper;

import de.jpx3.intave.reflect.ReflectiveAccess;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class WrappedMovingObjectPosition {
  private WrappedBlockPosition blockPos;

  /** What type of ray trace hit was this? 0 = block, 1 = entity */
  public WrappedMovingObjectPosition.MovingObjectType typeOfHit;
  public WrappedEnumDirection sideHit;

  /** The vector position of the hit */
  public WrappedVector hitVec;

  /** The hit entity */
  public Entity entityHit;

  public WrappedMovingObjectPosition(WrappedVector hitVecIn, WrappedEnumDirection facing, WrappedBlockPosition blockPosIn) {
    this(WrappedMovingObjectPosition.MovingObjectType.BLOCK, hitVecIn, facing, blockPosIn);
  }

  public WrappedMovingObjectPosition(WrappedVector p_i45552_1_, WrappedEnumDirection facing) {
    this(WrappedMovingObjectPosition.MovingObjectType.BLOCK, p_i45552_1_, facing, WrappedBlockPosition.ORIGIN);
  }

  public WrappedMovingObjectPosition(Entity entity) {
    this(entity, new WrappedVector(
      entity.getLocation().getX(),
      entity.getLocation().getY(),
      entity.getLocation().getZ())
    );
  }

  public WrappedMovingObjectPosition(
    WrappedMovingObjectPosition.MovingObjectType typeOfHitIn,
    WrappedVector hitVecIn, WrappedEnumDirection sideHitIn, WrappedBlockPosition blockPosIn
  ) {
    this.typeOfHit = typeOfHitIn;
    this.blockPos = blockPosIn;
    this.sideHit = sideHitIn;
    this.hitVec = new WrappedVector(hitVecIn.xCoord, hitVecIn.yCoord, hitVecIn.zCoord);
  }

  public WrappedMovingObjectPosition(Entity entityHitIn, WrappedVector hitVecIn) {
    this.typeOfHit = WrappedMovingObjectPosition.MovingObjectType.ENTITY;
    this.entityHit = entityHitIn;
    this.hitVec = hitVecIn;
  }

  public WrappedBlockPosition getBlockPos() {
    return this.blockPos;
  }

  public String toString() {
    return "HitResult{type=" + this.typeOfHit + ", blockpos=" + this.blockPos + ", f=" + this.sideHit + ", pos=" + this.hitVec + ", entity=" + this.entityHit + '}';
  }

  public static WrappedMovingObjectPosition fromNativeMovingObjectPosition(Object movingObjectPosition) {
    if(movingObjectPosition == null) {
      return null;
    }
    try {
      Class<?> movingObjectPositionClass = ReflectiveAccess.lookupServerClass("MovingObjectPosition");
      Field eField = movingObjectPositionClass.getDeclaredField("e");
      if(!eField.isAccessible()) {
        eField.setAccessible(true);
      }
      Object blockPosition = eField.get(movingObjectPosition);
      Object type = movingObjectPositionClass.getField("type").get(movingObjectPosition);
      Object direction = movingObjectPositionClass.getField("direction").get(movingObjectPosition);
      Object pos = movingObjectPositionClass.getField("pos").get(movingObjectPosition);
      Object entity = movingObjectPositionClass.getField("entity").get(movingObjectPosition);
      WrappedVector wrappedPos = WrappedVector.fromVec3D(pos);
      if(entity == null) {
        WrappedBlockPosition wrappedBlockPosition = WrappedBlockPosition.fromBlockPosition(blockPosition);
        String typeName = (String) Enum.class.getMethod("name").invoke(type);
        MovingObjectType movingObjectType = MovingObjectType.valueOf(typeName);
        String directionName = (String) Enum.class.getMethod("name").invoke(direction);
        WrappedEnumDirection wrappedEnumDirection = WrappedEnumDirection.valueOf(directionName);
        return new WrappedMovingObjectPosition(movingObjectType, wrappedPos, wrappedEnumDirection, wrappedBlockPosition);
      } else {
        Entity bukkitEntity = serverEntityByIdentifier((int) entity.getClass().getMethod("getId").invoke(entity));
        return new WrappedMovingObjectPosition(bukkitEntity, wrappedPos);
      }
    } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Entity serverEntityByIdentifier(int entityID) {
    for (World world : Bukkit.getWorlds()) {
      for (Entity entity : world.getEntities()) {
        if (entity.getEntityId() == entityID) {
          return entity;
        }
      }
    }
    return null;
  }

  public enum MovingObjectType {
    MISS,
    BLOCK,
    ENTITY
  }
}
