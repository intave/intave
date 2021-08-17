package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.world.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.world.wrapper.WrappedVector;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface Raytracer {
  WrappedMovingObjectPosition raytrace(World world, Player player, WrappedVector eyeVector, WrappedVector targetVector);
}
