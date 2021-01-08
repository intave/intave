package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.MovingObjectPosition;
import net.minecraft.server.v1_8_R3.Vec3D;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

public final class LegacyVersionRaytracer implements VersionRaytracer {
  @Override
  @PatchyAutoTranslation
  public WrappedMovingObjectPosition raytrace(World world, Player player, WrappedVector eyeVector, WrappedVector targetVector) {
    WorldServer handle = ((CraftWorld) world).getHandle();
    MovingObjectPosition movingObjectPosition = handle.rayTrace((Vec3D) eyeVector.convertToNativeVec3(), (Vec3D) targetVector.convertToNativeVec3());
    return WrappedMovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPosition);
  }
}
