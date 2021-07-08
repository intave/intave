package de.jpx3.intave.world.blockphysic;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

final class BlockBerryBushPhysic implements BlockPhysic {
  private List<Material> material;
  private boolean supported;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    Material sweetBerryBush = Material.getMaterial("SWEET_BERRY_BUSH");
    material = Collections.singletonList(sweetBerryBush);
    supported = sweetBerryBush != null;
  }

  @Override
  public Vector entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.setMotionMultiplier(new Vector(0.8f, 0.75, 0.8f));
    return null;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return supported;
  }

  @Override
  public List<Material> materials() {
    return material;
  }
}