package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.block.shape.BlockRaytrace;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.share.ClientMath.floor;
import static de.jpx3.intave.share.Direction.*;

public final class UniversalRaytracer implements Raytracer {
  @Override
  public MovingObjectPosition raytrace(World world, Player player, NativeVector eyeVector, NativeVector targetVector) {
    return raytrace(player, eyeVector.toPosition(), targetVector.toPosition());
  }

  public MovingObjectPosition raytrace(Player player, Position observerPosition, Position targetPosition) {
    User user = UserRepository.userOf(player);
    BlockCache blockStateAccess = user.blockCache();
    if (observerPosition.hasNaNCoordinate() || targetPosition.hasNaNCoordinate()) {
      return MovingObjectPosition.none();
    }
    Position initialPosition = observerPosition.clone();
    MovingObjectPosition movingObjectPosition;
    int targetX = floor(targetPosition.getX());
    int targetY = floor(targetPosition.getY());
    int targetZ = floor(targetPosition.getZ());
    int lookX = floor(observerPosition.getX());
    int lookY = floor(observerPosition.getY());
    int lookZ = floor(observerPosition.getZ());
    BlockPosition blockposition = observerPosition.toBlockPosition();
    BlockShape currentShape = blockStateAccess.outlineShapeAt(lookX, lookY, lookZ);
    Material material = blockStateAccess.typeAt(lookX, lookY, lookZ);
    movingObjectPosition = innerBlockRaytrace(material, currentShape, blockposition, initialPosition, observerPosition, targetPosition);
    if (movingObjectPosition != MovingObjectPosition.none()) {
      player.playEffect(movingObjectPosition.hitVec.toLocation(player.getWorld()), Effect.HAPPY_VILLAGER, 1);
      return movingObjectPosition;
    }
    int jumps = 50;
    while (jumps-- >= 0) {
      Direction direction;
      if (observerPosition.hasNaNCoordinate()) {
        return MovingObjectPosition.none();
      }
      if (lookX == targetX && lookY == targetY && lookZ == targetZ) {
        return MovingObjectPosition.none();
      }
      boolean arrivedAtX = true;
      boolean arrivedAtY = true;
      boolean arrivedAtZ = true;
      double lookXStep = 999.0;
      double lookYStep = 999.0;
      double lookZStep = 999.0;
      if (targetX > lookX) {
        lookXStep = (double) lookX + 1.0;
      } else if (targetX < lookX) {
        lookXStep = (double) lookX + 0.0;
      } else {
        arrivedAtX = false;
      }
      if (targetY > lookY) {
        lookYStep = (double) lookY + 1.0;
      } else if (targetY < lookY) {
        lookYStep = (double) lookY + 0.0;
      } else {
        arrivedAtY = false;
      }
      if (targetZ > lookZ) {
        lookZStep = (double) lookZ + 1.0;
      } else if (targetZ < lookZ) {
        lookZStep = (double) lookZ + 0.0;
      } else {
        arrivedAtZ = false;
      }
      double stepScaleX = 999.0;
      double stepScaleY = 999.0;
      double stepScaleZ = 999.0;
      double finalDistanceX = targetPosition.getX() - observerPosition.getX();
      double finalDistanceY = targetPosition.getY() - observerPosition.getY();
      double finalDistanceZ = targetPosition.getZ() - observerPosition.getZ();
      if (arrivedAtX) {
        stepScaleX = (lookXStep - observerPosition.getX()) / finalDistanceX;
      }
      if (arrivedAtY) {
        stepScaleY = (lookYStep - observerPosition.getY()) / finalDistanceY;
      }
      if (arrivedAtZ) {
        stepScaleZ = (lookZStep - observerPosition.getZ()) / finalDistanceZ;
      }
      if (stepScaleX == -0.0) {
        stepScaleX = -0.0001;
      }
      if (stepScaleY == -0.0) {
        stepScaleY = -0.0001;
      }
      if (stepScaleZ == -0.0) {
        stepScaleZ = -0.0001;
      }
      if (stepScaleX < stepScaleY && stepScaleX < stepScaleZ) {
        direction = targetX > lookX ? WEST : EAST;
        observerPosition = new Position(lookXStep, observerPosition.getY() + finalDistanceY * stepScaleX, observerPosition.getZ() + finalDistanceZ * stepScaleX);
      } else if (stepScaleY < stepScaleZ) {
        direction = targetY > lookY ? DOWN : UP;
        observerPosition = new Position(observerPosition.getX() + finalDistanceX * stepScaleY, lookYStep, observerPosition.getZ() + finalDistanceZ * stepScaleY);
      } else {
        direction = targetZ > lookZ ? NORTH : SOUTH;
        observerPosition = new Position(observerPosition.getX() + finalDistanceX * stepScaleZ, observerPosition.getY() + finalDistanceY * stepScaleZ, lookZStep);
      }
//      player.playEffect(observerPosition.toLocation(player.getWorld()), Effect.HAPPY_VILLAGER, 1);
      lookX = floor(observerPosition.getX()) - (direction == EAST ? 1 : 0);
      lookY = floor(observerPosition.getY()) - (direction == UP ? 1 : 0);
      lookZ = floor(observerPosition.getZ()) - (direction == SOUTH ? 1 : 0);
      blockposition = new BlockPosition(lookX, lookY, lookZ);
      currentShape = blockStateAccess.outlineShapeAt(lookX, lookY, lookZ);
      material = blockStateAccess.typeAt(lookX, lookY, lookZ);
      movingObjectPosition = innerBlockRaytrace(material, currentShape, blockposition, initialPosition, observerPosition, targetPosition);
      if (movingObjectPosition != MovingObjectPosition.none()) {
        player.playEffect(movingObjectPosition.hitVec.toLocation(player.getWorld()), Effect.HAPPY_VILLAGER, 1);
//        player.spigot().playEffect(movingObjectPosition.hitVec.toLocation(player.getWorld()), Effect.CLICK1, 1, 1, 0, 0, 0, 0, 100, 0);
        return movingObjectPosition;
      }
    }
    System.out.println("No hit found");
    return MovingObjectPosition.none();
  }

  private MovingObjectPosition innerBlockRaytrace(Material material, BlockShape shape, BlockPosition blockPosition, Position initialObserverPosition, Position currentObserverPosition, Position targetPosition) {
    if (shape.isEmpty()) {
      System.out.println("Empty shape at " + blockPosition);
      return MovingObjectPosition.none();
    }
    System.out.println("Penetrating " + material + " at " + blockPosition + ", shape " + shape);
//    System.out.println("Raytracing " + shape + " at " + blockPosition);
    BlockRaytrace raytrace = shape.raytrace(currentObserverPosition, targetPosition);
    if (raytrace == BlockRaytrace.none()) {
      System.out.println("No raytrace");
      return MovingObjectPosition.none();
    }
    double lengthFactor = raytrace.lengthOffset();
    double differenceX = (targetPosition.getX() - initialObserverPosition.getX()) * lengthFactor;
    double differenceY = (targetPosition.getY() - initialObserverPosition.getY()) * lengthFactor;
    double differenceZ = (targetPosition.getZ() - initialObserverPosition.getZ()) * lengthFactor;
//    System.out.println(new NativeVector(differenceX, differenceY, differenceZ));
    double positionX = initialObserverPosition.getX() + differenceX;
    double positionY = initialObserverPosition.getY() + differenceY;
    double positionZ = initialObserverPosition.getZ() + differenceZ;
    BlockPosition finalPosition = new BlockPosition(positionX, positionY, positionZ);
    BlockPosition finalPositionFloored = new BlockPosition(floor(positionX), floor(positionY), floor(positionZ));
    System.out.println("Raytrace ended at " + finalPosition + " " + new NativeVector(differenceX, differenceY, differenceZ));
    return new MovingObjectPosition(finalPosition, raytrace.direction(), finalPositionFloored);
  }
}
