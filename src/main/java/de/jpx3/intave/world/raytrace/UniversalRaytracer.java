package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.BlockRaytrace;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class UniversalRaytracer implements Raytracer {
  @Override
  public MovingObjectPosition raytrace(World world, Player player, NativeVector eyeVector, NativeVector targetVector) {
    if (eyeVector == null || targetVector == null) {
      return null;
    }
    if (includesInvalidCoordinate(eyeVector) || includesInvalidCoordinate(targetVector)) {
      return null;
    }
    Position eyePosition = new Position(eyeVector.xCoord, eyeVector.yCoord, eyeVector.zCoord);
    Position targetPosition = new Position(targetVector.xCoord, targetVector.yCoord, targetVector.zCoord);
    return performRaytrace(player, eyePosition, targetPosition);
  }


  private MovingObjectPosition performRaytrace(Player player, Position eyePosition, Position targetPosition) {
    BlockShape eyeShape = shapeAt(player, eyePosition);
    if (!eyeShape.isEmpty()) {
      BlockRaytrace raytrace = eyeShape.raytrace(eyePosition, targetPosition);
      if (raytrace != null) {
        return MovingObjectPosition.fromBlockRaytrace(raytrace, eyePosition, targetPosition, eyePosition.toBlockPosition());
      }
    }

    Position contextPosition = eyePosition;
    int targetX = floor(targetPosition.getX());
    int targetY = floor(targetPosition.getY());
    int targetZ = floor(targetPosition.getZ());
    int contextX = floor(contextPosition.getX());
    int contextY = floor(contextPosition.getY());
    int contextZ = floor(contextPosition.getZ());

    int hops = 50;
    while (hops-- >= 0) {
      if (includesInvalidCoordinate(contextPosition)) {
        return MovingObjectPosition.none();
      }
      if (contextX == targetX && contextY == targetY && contextZ == targetZ) {
        return MovingObjectPosition.none();
      }
      boolean arrivedAtX = true;
      boolean arrivedAtY = true;
      boolean arrivedAtZ = true;
      double lookXStep = 999.0D;
      double lookYStep = 999.0D;
      double lookZStep = 999.0D;
      if (targetX > contextX) {
        lookXStep = contextX + 1;
      } else if (targetX < contextX) {
        lookXStep = contextX;
      } else {
        arrivedAtX = false;
      }
      if (targetY > contextY) {
        lookYStep = contextY + 1;
      } else if (targetY < contextY) {
        lookYStep = contextY;
      } else {
        arrivedAtY = false;
      }
      if (targetZ > contextZ) {
        lookZStep = contextZ + 1;
      } else if (targetZ < contextZ) {
        lookZStep = contextZ;
      } else {
        arrivedAtZ = false;
      }
      double stepScaleX = 999.0D;
      double stepScaleY = 999.0D;
      double stepScaleZ = 999.0D;
      double finalDistanceX = targetPosition.getX() - contextPosition.getX();
      double finalDistanceY = targetPosition.getY() - contextPosition.getY();
      double finalDistanceZ = targetPosition.getZ() - contextPosition.getZ();
      if (arrivedAtX) {
        stepScaleX = (lookXStep - contextPosition.getX()) / finalDistanceX;
      }
      if (arrivedAtY) {
        stepScaleY = (lookYStep - contextPosition.getY()) / finalDistanceY;
      }
      if (arrivedAtZ) {
        stepScaleZ = (lookZStep - contextPosition.getZ()) / finalDistanceZ;
      }
      if (stepScaleX == -0.0D) {
        stepScaleX = -0.0001D;
      }
      if (stepScaleY == -0.0D) {
        stepScaleY = -0.0001D;
      }
      if (stepScaleZ == -0.0D) {
        stepScaleZ = -0.0001D;
      }
      Direction direction;
      if (stepScaleX < stepScaleY && stepScaleX < stepScaleZ) {
        direction = targetX > contextX ? Direction.WEST : Direction.EAST;
        contextPosition = new Position(lookXStep, contextPosition.getY() + stepScaleX * finalDistanceY, contextPosition.getZ() + stepScaleX * finalDistanceZ);
      } else if (stepScaleY < stepScaleZ) {
        direction = targetY > contextY ? Direction.DOWN : Direction.UP;
        contextPosition = new Position(contextPosition.getX() + stepScaleY * finalDistanceX, lookYStep, contextPosition.getZ() + stepScaleY * finalDistanceZ);
      } else {
        direction = targetZ > contextZ ? Direction.NORTH : Direction.SOUTH;
        contextPosition = new Position(contextPosition.getX() + stepScaleZ * finalDistanceX, contextPosition.getY() + stepScaleZ * finalDistanceY, lookZStep);
      }
      if (includesInvalidCoordinate(contextPosition)) {
        return MovingObjectPosition.none();
      }
      contextX = floor(contextPosition.getX() - (direction == Direction.EAST ? 1 : 0));
      contextY = floor(contextPosition.getY() - (direction == Direction.UP ? 1 : 0));
      contextZ = floor(contextPosition.getZ() - (direction == Direction.SOUTH ? 1 : 0));

      BlockShape shape = shapeAt(player, contextX, contextY, contextZ);
      if (!shape.isEmpty()) {
        BlockRaytrace raytrace = innerRaytrace(shape, contextPosition, targetPosition);
        if (raytrace != null) {
          return MovingObjectPosition.fromBlockRaytrace(raytrace, contextPosition, targetPosition, new BlockPosition(contextX, contextY, contextZ));
        }
      }
    }

    return MovingObjectPosition.none();
  }

  public static int floor(double var0) {
    int var2 = (int)var0;
    return var0 < (double)var2 ? var2 - 1 : var2;
  }

  private BlockRaytrace innerRaytrace(BlockShape shape, Position eyePosition, Position targetPosition) {
    return shape.raytrace(eyePosition, targetPosition);
  }

  private Material typeAt(Player player, int x, int y, int z) {
    return VolatileBlockAccess.typeAccess(UserRepository.userOf(player), x, y, z);
  }

  private BlockShape shapeAt(Player player, int x, int y, int z) {
    return UserRepository.userOf(player).blockStates().outlineShapeAt(x, y, z);
  }

  private BlockShape shapeAt(Player player, Position position) {
    return shapeAt(player, position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  private boolean includesInvalidCoordinate(NativeVector nativeVector) {
    return Double.isNaN(nativeVector.xCoord) || Double.isNaN(nativeVector.yCoord) || Double.isNaN(nativeVector.zCoord);
  }

  private boolean includesInvalidCoordinate(Position position) {
    return Double.isNaN(position.getX()) || Double.isNaN(position.getY()) || Double.isNaN(position.getZ());
  }
}
