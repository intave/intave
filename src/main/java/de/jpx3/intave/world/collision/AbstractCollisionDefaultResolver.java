package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.entity.Player;

import java.util.List;

public abstract class AbstractCollisionDefaultResolver {
  abstract void setup() throws Exception;
  public abstract List<WrappedAxisAlignedBB> collidingBoundingBoxes(Player player, WrappedAxisAlignedBB boundingBox);
}