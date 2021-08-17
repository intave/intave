package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.world.wrapper.WrappedVector;
import net.minecraft.server.v1_14_R1.MovingObjectPositionBlock;
import net.minecraft.server.v1_14_R1.RayTrace;
import net.minecraft.server.v1_14_R1.Vec3D;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class v14Raytracer implements Raytracer {
  @Override
  @PatchyAutoTranslation
  public WrappedMovingObjectPosition raytrace(World world, Player player, WrappedVector eyeVector, WrappedVector targetVector) {
    RayTrace raytraceConfiguration = new RayTrace(
      (Vec3D) eyeVector.convertToNativeVec3(),
      (Vec3D) targetVector.convertToNativeVec3(),
      RayTrace.BlockCollisionOption.OUTLINE,
      RayTrace.FluidCollisionOption.NONE,
      ((CraftPlayer) player).getHandle()
    );
    MovingObjectPositionBlock movingObjectPositionBlock = ((CraftWorld) world).getHandle().rayTrace(raytraceConfiguration);
    return WrappedMovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPositionBlock);
  }
}
