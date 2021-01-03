package de.jpx3.intave.world.collision;

import com.google.common.collect.Lists;
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

import static de.jpx3.intave.reflect.Reflection.*;

public final class CollisionResolverLegacy extends AbstractCollisionDefaultResolver {
  private static MethodHandle cubeMethodHandle;

  @Override
  void setup() throws Exception {
    MethodType methodType = resolveLegacyMethodType();
    cubeMethodHandle = MethodHandles
      .lookup()
      .findVirtual(NMS_WORLD_SERVER_CLASS, "getCubes", methodType);
  }

  @Override
  public List<WrappedAxisAlignedBB> getCollisionBoxes(Player player, WrappedAxisAlignedBB boundingBox) {
    User user = UserRepository.userOf(player);
    Object nmsEntity = user.playerHandle();
    UserMetaMovementData movementData = user.meta().movementData();
    Object world = movementData.nmsWorld();

    try {
      List<?> boundingBoxes = (List<?>) cubeMethodHandle.invoke(world, nmsEntity, boundingBox.unwrap());
      return wrapBoundingBoxes(boundingBoxes);
    } catch (Throwable e) {
      throw new ReflectionFailureException(e);
    }
  }

  private List<WrappedAxisAlignedBB> wrapBoundingBoxes(List<?> list) {
    List<WrappedAxisAlignedBB> boundingBoxes = Lists.newArrayList();
    for (Object o : list) {
      boundingBoxes.add(WrappedAxisAlignedBB.fromClass(o));
    }
    return boundingBoxes;
  }

  private MethodType resolveLegacyMethodType() {
    return MethodType.methodType(List.class, NMS_ENTITY_CLASS, NMS_AABB_CLASS);
  }
}