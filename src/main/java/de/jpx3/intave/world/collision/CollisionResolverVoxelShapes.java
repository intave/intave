package de.jpx3.intave.world.collision;

import com.google.common.collect.Lists;
import de.jpx3.intave.reflect.Reflection;
import de.jpx3.intave.reflect.ReflectionFailureException;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.stream.Stream;

import static de.jpx3.intave.reflect.Reflection.NMS_AABB_CLASS;
import static de.jpx3.intave.reflect.Reflection.NMS_ENTITY_CLASS;

public final class CollisionResolverVoxelShapes extends AbstractCollisionDefaultResolver {
  private static MethodHandle collisionBoxesMethodHandle;
  private static MethodHandle voxelShapesToBoundingBoxHandle;

  @Override
  void setup() throws Exception {
    Class<?> collisionClass = resolveConditionalClass();
    MethodType methodType = resolveMethodType();
    collisionBoxesMethodHandle = MethodHandles
      .lookup()
      .findVirtual(collisionClass, "b", methodType);

    MethodType boundingBoxMethodType = voxelShapeToBoundingBoxesMethodType();
    Class<?> voxelShapeClass = resolveVoxelShapeClass();
    voxelShapesToBoundingBoxHandle = MethodHandles
      .lookup()
      .findVirtual(voxelShapeClass, "d", boundingBoxMethodType);
  }

  @Override
  public List<WrappedAxisAlignedBB> collidingBoundingBoxes(Player player, WrappedAxisAlignedBB boundingBox) {
    User user = UserRepository.userOf(player);
    Object nmsEntity = user.playerHandle();
    UserMetaMovementData movementData = user.meta().movementData();
    Object world = movementData.nmsWorld();

    try {
      Stream<?> voxelShapes = (Stream<?>) collisionBoxesMethodHandle.invoke(world, nmsEntity, boundingBox.unwrap());
      List<WrappedAxisAlignedBB> boundingBoxes = Lists.newArrayList();
      voxelShapes.forEach(voxelShape -> boundingBoxes.addAll(boundingBoxesFromVoxelShape(voxelShape)));
      return boundingBoxes;
    } catch (Throwable t) {
      throw new ReflectionFailureException(t);
    }
  }

  private List<WrappedAxisAlignedBB> boundingBoxesFromVoxelShape(Object voxelShapeSpliterator) {
    try {
      List<WrappedAxisAlignedBB> bbList = Lists.newArrayList();
      List<?> invoke = (List<?>) voxelShapesToBoundingBoxHandle.invoke(voxelShapeSpliterator);
      for (Object o : invoke) {
        bbList.add(WrappedAxisAlignedBB.fromClass(o));
      }
      return bbList;
    } catch (Throwable t) {
      throw new ReflectionFailureException(t);
    }
  }

  private Class<?> resolveVoxelShapeClass() {
    return Reflection.lookupServerClass("VoxelShape");
  }

  private Class<?> resolveConditionalClass() {
    String className = "IWorldReader";
    return Reflection.lookupServerClass(className);
  }

  private MethodType resolveMethodType() {
    return MethodType.methodType(Stream.class, NMS_ENTITY_CLASS, NMS_AABB_CLASS);
  }

  private MethodType voxelShapeToBoundingBoxesMethodType() {
    return MethodType.methodType(List.class);
  }
}