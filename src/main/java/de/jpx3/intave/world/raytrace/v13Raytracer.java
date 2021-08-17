package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.world.wrapper.WrappedVector;
import net.minecraft.server.v1_13_R2.FluidCollisionOption;
import net.minecraft.server.v1_13_R2.MovingObjectPosition;
import net.minecraft.server.v1_13_R2.Vec3D;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class v13Raytracer implements Raytracer {
  @Override
  @PatchyAutoTranslation
  public WrappedMovingObjectPosition raytrace(World world, Player player, WrappedVector eyeVector, WrappedVector targetVector) {
    MovingObjectPosition movingObjectPosition = ((CraftWorld) world).getHandle().rayTrace(
      (Vec3D) eyeVector.convertToNativeVec3(),
      (Vec3D) targetVector.convertToNativeVec3(),
      FluidCollisionOption.NEVER,
      false, false
    );
    return WrappedMovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPosition);
  }
}
