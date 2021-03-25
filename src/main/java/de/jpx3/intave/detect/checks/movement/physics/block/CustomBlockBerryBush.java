package de.jpx3.intave.detect.checks.movement.physics.block;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

final class CustomBlockBerryBush implements CustomBlock {
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
    return new Vector(motionX * 0.8f, motionY * 0.75, motionZ * 0.8f);
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