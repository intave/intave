package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

final class BerryBushPhysics implements BlockPhysic {
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
    MovementMetadata movementData = user.meta().movement();
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