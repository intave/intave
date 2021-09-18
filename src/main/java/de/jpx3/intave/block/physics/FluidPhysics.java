package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.block.fluid.FluidTag;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.fluid.WrappedFluid;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

final class FluidPhysics implements BlockPhysic {
  private List<Material> materials;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    List<Material> materials = new ArrayList<>();
    Material stationaryLava = Material.getMaterial("STATIONARY_LAVA");
    if (stationaryLava != null) {
      materials.add(stationaryLava);
    }
    materials.add(Material.LAVA);
    this.materials = ImmutableList.copyOf(materials);
  }

  @Override
  public Vector entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.waterUpdate()) {
      MovementMetadata movementData = user.meta().movement();
      WrappedFluid fluid = Fluids.fluidAt(user, location);
      if (fluid.isIn(FluidTag.LAVA)) {
        float f = (float) location.getY() + fluid.height();
        BoundingBox boundingBox = movementData.boundingBox();
        if (boundingBox.minY < (double) f || (double) f > boundingBox.maxY) {
          movementData.aquaticUpdateInLava = true;
        }
      }
    }
    return null;
  }

  @Override
  public List<Material> materials() {
    return materials;
  }
}