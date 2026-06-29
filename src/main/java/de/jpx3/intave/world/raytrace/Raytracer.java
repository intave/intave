package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.RawVector3d;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface Raytracer {
  MovingObjectPosition raytrace(World world, Player player, RawVector3d eyeVector, RawVector3d targetVector);
}
