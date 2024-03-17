package de.jpx3.intave.share;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.block.shape.BlockRaytrace;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.share.link.WrapperConverter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MovingObjectPosition {
  private static final boolean NEW_RESOLVER = MinecraftVersions.VER1_14_0.atOrAbove();

  private BlockPosition blockPos;

  /**
   * What type of ray trace hit was this? 0 = block, 1 = entity
   */
  public MovingObjectPosition.MovingObjectType typeOfHit;
  public Direction sideHit;

  /**
   * The vector position of the hit
   */
  public NativeVector hitVec;

  /**
   * The hit entity
   */
  public Entity entityHit;

  public MovingObjectPosition(NativeVector hitVecIn, Direction facing, BlockPosition blockPosIn) {
    this(MovingObjectPosition.MovingObjectType.BLOCK, hitVecIn, facing, blockPosIn);
  }

  public MovingObjectPosition(NativeVector p_i45552_1_, Direction facing) {
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
    NativeVector hitVecIn, Direction sideHitIn, BlockPosition blockPosIn
  ) {
    this.typeOfHit = typeOfHitIn;
    this.sideHit = sideHitIn;
    this.hitVec = new NativeVector(hitVecIn.xCoord, hitVecIn.yCoord, hitVecIn.zCoord);
    this.blockPos = blockPosIn;
  }

  public MovingObjectPosition(Entity entityHitIn, NativeVector hitVecIn) {
    this.typeOfHit = MovingObjectPosition.MovingObjectType.ENTITY;
    this.entityHit = entityHitIn;
    this.hitVec = hitVecIn;
  }

  public static MovingObjectPosition fromBlockRaytrace(BlockRaytrace blockRaytrace, Position from, Position to, BlockPosition blockPos) {
    if (blockRaytrace == null) {
      return none();
    }
    Vector direction = to.clone().subtract(from).normalize();
    double distance = blockRaytrace.lengthOffset();
    Position hit = Position.of(from.clone().add(direction.multiply(distance)));

    return new MovingObjectPosition(
      MovingObjectType.BLOCK,
      new NativeVector(hit.getX(), hit.getY(), hit.getZ()),
      blockRaytrace.direction(),
      blockPos
    );
  }

  public BlockPosition getBlockPos() {
    return this.blockPos;
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
//      Class<?> movingObjectPositionBase = Lookup.serverClass("MovingObjectPosition");
      Class<?> movingObjectPositionEntity = Lookup.serverClass("MovingObjectPositionEntity");
      Class<?> movingObjectPositionBlock = Lookup.serverClass("MovingObjectPositionBlock");

      Class<?> movingObjectPositionType = Lookup.serverClass("MovingObjectPosition$EnumMovingObjectType");
      Method typeResolveMethod = Lookup.serverMethod("MovingObjectPosition", "getType", movingObjectPositionType);
      String typeName = (String) Enum.class.getMethod("name").invoke(typeResolveMethod.invoke(movingObjectPosition));
      MovingObjectType movingObjectType = MovingObjectType.valueOf(typeName);
      switch (movingObjectType) {
        case ENTITY:
          Field field = movingObjectPositionEntity.getDeclaredField("entity");
          if (!field.isAccessible()) {
            field.setAccessible(true);
          }
          Object entity = field.get(movingObjectPosition);
          return new MovingObjectPosition(serverEntityByIdentifier((int) entity.getClass().getMethod("getId").invoke(entity)));
        case BLOCK:
          Field movingObjectPositionBaseField = Lookup.serverField("MovingObjectPosition", "pos");
          if (!movingObjectPositionBaseField.isAccessible())
            movingObjectPositionBaseField.setAccessible(true);
          Object pos = movingObjectPositionBaseField.get(movingObjectPosition);
          NativeVector wrappedPos = WrapperConverter.vectorFromVec3D(pos);
          Field bField = movingObjectPositionBlock.getDeclaredField("b");
          if (!bField.isAccessible())
            bField.setAccessible(true);
          Object direction = bField.get(movingObjectPosition);
          String directionName = (String) Enum.class.getMethod("name").invoke(direction);
          Direction wrappedDirection = Direction.valueOf(directionName);
          Field cField = movingObjectPositionBlock.getDeclaredField("c");
          if (!cField.isAccessible())
            cField.setAccessible(true);
          Object blockPosition = cField.get(movingObjectPosition);
          BlockPosition wrappedBlockPosition = WrapperConverter.blockPositionFromNativeBlockPosition(blockPosition);
          return new MovingObjectPosition(movingObjectType, wrappedPos, wrappedDirection, wrappedBlockPosition);
        case MISS:
          return none();
      }
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
    return none();
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
      NativeVector wrappedPos = WrapperConverter.vectorFromVec3D(pos);
      if (entity == null) {
        BlockPosition wrappedBlockPosition = WrapperConverter.blockPositionFromNativeBlockPosition(blockPosition);
        String typeName = (String) Enum.class.getMethod("name").invoke(type);
        MovingObjectType movingObjectType = MovingObjectType.valueOf(typeName);
        String directionName = (String) Enum.class.getMethod("name").invoke(direction);
        Direction wrappedDirection = Direction.valueOf(directionName);
        return new MovingObjectPosition(movingObjectType, wrappedPos, wrappedDirection, wrappedBlockPosition);
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

  public static MovingObjectPosition none() {
    return null;
  }

  @KeepEnumInternalNames
  public enum MovingObjectType {
    MISS,
    BLOCK,
    ENTITY
  }
}
