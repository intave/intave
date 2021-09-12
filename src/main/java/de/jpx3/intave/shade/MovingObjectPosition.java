package de.jpx3.intave.shade;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.clazz.Lookup;
import de.jpx3.intave.shade.link.WrapperLinkage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;

public class MovingObjectPosition {
  private final static boolean NEW_RESOLVER = MinecraftVersions.VER1_14_0.atOrAbove();

  private BlockPosition blockPos;

  /** What type of ray trace hit was this? 0 = block, 1 = entity */
  public MovingObjectPosition.MovingObjectType typeOfHit;
  public EnumDirection sideHit;

  /** The vector position of the hit */
  public NativeVector hitVec;

  /** The hit entity */
  public Entity entityHit;

  public MovingObjectPosition(NativeVector hitVecIn, EnumDirection facing, BlockPosition blockPosIn) {
    this(MovingObjectPosition.MovingObjectType.BLOCK, hitVecIn, facing, blockPosIn);
  }

  public MovingObjectPosition(NativeVector p_i45552_1_, EnumDirection facing) {
    this(MovingObjectPosition.MovingObjectType.BLOCK, p_i45552_1_, facing, BlockPosition.ORIGIN);
  }

  public MovingObjectPosition(Entity entity) {
    this(entity, new NativeVector(
      entity.getLocation().getX(),
      entity.getLocation().getY(),
      entity.getLocation().getZ())
    );
  }

  public MovingObjectPosition(
    MovingObjectPosition.MovingObjectType typeOfHitIn,
    NativeVector hitVecIn, EnumDirection sideHitIn, BlockPosition blockPosIn
  ) {
    this.typeOfHit = typeOfHitIn;
    this.blockPos = blockPosIn;
    this.sideHit = sideHitIn;
    this.hitVec = new NativeVector(hitVecIn.xCoord, hitVecIn.yCoord, hitVecIn.zCoord);
  }

  public MovingObjectPosition(Entity entityHitIn, NativeVector hitVecIn) {
    this.typeOfHit = MovingObjectPosition.MovingObjectType.ENTITY;
    this.entityHit = entityHitIn;
    this.hitVec = hitVecIn;
  }

  public BlockPosition getBlockPos() {
    return this.blockPos;
  }

  public String toString() {
    return "HitResult{type=" + this.typeOfHit + ", blockpos=" + this.blockPos + ", f=" + this.sideHit + ", pos=" + this.hitVec + ", entity=" + this.entityHit + '}';
  }

  public static MovingObjectPosition fromNativeMovingObjectPosition(Object movingObjectPosition) {
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

  private static MovingObjectPosition modernResolve(Object movingObjectPosition) {
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
        return new MovingObjectPosition(serverEntityByIdentifier((int) entity.getClass().getMethod("getId").invoke(entity)));
      } else {
        Field movingObjectPositionBaseField = Lookup.serverField("MovingObjectPosition", "pos");
        if (!movingObjectPositionBaseField.isAccessible())
          movingObjectPositionBaseField.setAccessible(true);
        Object pos = movingObjectPositionBaseField.get(movingObjectPosition);
        NativeVector wrappedPos = WrapperLinkage.vectorOf(pos);
        Field bField = movingObjectPositionBlock.getDeclaredField("b");
        if (!bField.isAccessible())
          bField.setAccessible(true);
        Object direction = bField.get(movingObjectPosition);
        String directionName = (String) Enum.class.getMethod("name").invoke(direction);
        EnumDirection wrappedEnumDirection = EnumDirection.valueOf(directionName);
        Field cField = movingObjectPositionBlock.getDeclaredField("c");
        if (!cField.isAccessible())
          cField.setAccessible(true);
        Object blockPosition = cField.get(movingObjectPosition);
        BlockPosition wrappedBlockPosition = WrapperLinkage.blockPositionOf(blockPosition);
        return new MovingObjectPosition(movingObjectType, wrappedPos, wrappedEnumDirection, wrappedBlockPosition);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static MovingObjectPosition legacyResolve(Object movingObjectPosition) {
    try {
      Class<?> movingObjectPositionClass = Lookup.serverClass("MovingObjectPosition");
      Field eField = movingObjectPositionClass.getDeclaredField("e");
      ensureAccessibility(eField);
      Object blockPosition = eField.get(movingObjectPosition);
      Object type = movingObjectPositionClass.getField("type").get(movingObjectPosition);
      Object direction = movingObjectPositionClass.getField("direction").get(movingObjectPosition);
      Object pos = movingObjectPositionClass.getField("pos").get(movingObjectPosition);
      Object entity = movingObjectPositionClass.getField("entity").get(movingObjectPosition);
      NativeVector wrappedPos = WrapperLinkage.vectorOf(pos);
      if (entity == null) {
        BlockPosition wrappedBlockPosition = WrapperLinkage.blockPositionOf(blockPosition);
        String typeName = (String) Enum.class.getMethod("name").invoke(type);
        MovingObjectType movingObjectType = MovingObjectType.valueOf(typeName);
        String directionName = (String) Enum.class.getMethod("name").invoke(direction);
        EnumDirection wrappedEnumDirection = EnumDirection.valueOf(directionName);
        return new MovingObjectPosition(movingObjectType, wrappedPos, wrappedEnumDirection, wrappedBlockPosition);
      } else {
        Entity bukkitEntity = serverEntityByIdentifier((int) entity.getClass().getMethod("getId").invoke(entity));
        return new MovingObjectPosition(bukkitEntity, wrappedPos);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void ensureAccessibility(Field field) {
    if (!field.isAccessible()) {
      field.setAccessible(true);
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
