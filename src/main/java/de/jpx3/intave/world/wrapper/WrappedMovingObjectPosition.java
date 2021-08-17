package de.jpx3.intave.world.wrapper;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.reflect.access.ReflectiveAccess;
import de.jpx3.intave.world.wrapper.link.WrapperLinkage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;

public class WrappedMovingObjectPosition {
  private final static boolean NEW_RESOLVER = MinecraftVersions.VER1_14_0.atOrAbove();

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
    if (movingObjectPosition == null) {
      // just to make IntelliJ happy..
      return null;
    }

    if (NEW_RESOLVER) {
      return modernResolve(movingObjectPosition);
    } else {
      return legacyResolve(movingObjectPosition);
    }
  }

  private static WrappedMovingObjectPosition modernResolve(Object movingObjectPosition) {
    try {
      Class<?> movingObjectPositionBase = Lookup.serverClass("MovingObjectPosition");
      Class<?> movingObjectPositionEntity = Lookup.serverClass("MovingObjectPositionEntity");
      Class<?> movingObjectPositionBlock = Lookup.serverClass("MovingObjectPositionBlock");
      String typeName = (String) Enum.class.getMethod("name").invoke(movingObjectPositionBase.getMethod("getType").invoke(movingObjectPosition));
      MovingObjectType movingObjectType = MovingObjectType.valueOf(typeName);
      if (movingObjectType == MovingObjectType.ENTITY) {
        Field field = movingObjectPositionEntity.getDeclaredField("entity");
        if (!field.isAccessible()) {
          field.setAccessible(true);
        }
        Object entity = field.get(movingObjectPosition);
        return new WrappedMovingObjectPosition(serverEntityByIdentifier((int) entity.getClass().getMethod("getId").invoke(entity)));
      } else {
        Field movingObjectPositionBaseField = Lookup.serverField("MovingObjectPosition", "pos");
        if (!movingObjectPositionBaseField.isAccessible())
          movingObjectPositionBaseField.setAccessible(true);
        Object pos = movingObjectPositionBaseField.get(movingObjectPosition);
        WrappedVector wrappedPos = WrapperLinkage.vectorOf(pos);
        Field bField = movingObjectPositionBlock.getDeclaredField("b");
        if (!bField.isAccessible())
          bField.setAccessible(true);
        Object direction = bField.get(movingObjectPosition);
        String directionName = (String) Enum.class.getMethod("name").invoke(direction);
        WrappedEnumDirection wrappedEnumDirection = WrappedEnumDirection.valueOf(directionName);
        Field cField = movingObjectPositionBlock.getDeclaredField("c");
        if (!cField.isAccessible())
          cField.setAccessible(true);
        Object blockPosition = cField.get(movingObjectPosition);
        WrappedBlockPosition wrappedBlockPosition = WrapperLinkage.blockPositionOf(blockPosition);
        return new WrappedMovingObjectPosition(movingObjectType, wrappedPos, wrappedEnumDirection, wrappedBlockPosition);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static WrappedMovingObjectPosition legacyResolve(Object movingObjectPosition) {
    try {
      Class<?> movingObjectPositionClass = Lookup.serverClass("MovingObjectPosition");
      Field eField = movingObjectPositionClass.getDeclaredField("e");
      ReflectiveAccess.ensureAccessible(eField);
      Object blockPosition = eField.get(movingObjectPosition);
      Object type = movingObjectPositionClass.getField("type").get(movingObjectPosition);
      Object direction = movingObjectPositionClass.getField("direction").get(movingObjectPosition);
      Object pos = movingObjectPositionClass.getField("pos").get(movingObjectPosition);
      Object entity = movingObjectPositionClass.getField("entity").get(movingObjectPosition);
      WrappedVector wrappedPos = WrapperLinkage.vectorOf(pos);
      if (entity == null) {
        WrappedBlockPosition wrappedBlockPosition = WrapperLinkage.blockPositionOf(blockPosition);
        String typeName = (String) Enum.class.getMethod("name").invoke(type);
        MovingObjectType movingObjectType = MovingObjectType.valueOf(typeName);
        String directionName = (String) Enum.class.getMethod("name").invoke(direction);
        WrappedEnumDirection wrappedEnumDirection = WrappedEnumDirection.valueOf(directionName);
        return new WrappedMovingObjectPosition(movingObjectType, wrappedPos, wrappedEnumDirection, wrappedBlockPosition);
      } else {
        Entity bukkitEntity = serverEntityByIdentifier((int) entity.getClass().getMethod("getId").invoke(entity));
        return new WrappedMovingObjectPosition(bukkitEntity, wrappedPos);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
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

  @KeepEnumInternalNames
  public enum MovingObjectType {
    MISS,
    BLOCK,
    ENTITY
  }
}
