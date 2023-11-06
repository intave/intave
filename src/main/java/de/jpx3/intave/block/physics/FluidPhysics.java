package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

final class FluidPhysics implements BlockPhysic {
  private List<Material> materials;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    List<Material> materials = new ArrayList<>();
    Material stationaryLava = Material.getMaterial("STATIONARY_LAVA");
    if (stationaryLava != null) {
      materials.add(stationaryLava);
    }
    materials.add(Material.LAVA);
    this.materials = ImmutableList.copyOf(materials);
  }

  @Override
  public Motion entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.waterUpdate()) {
      MovementMetadata movementData = user.meta().movement();
      Fluid fluid = VolatileBlockAccess.fluidAccess(user, location);
      if (fluid.isOfLava()) {
        movementData.aquaticUpdateInLava = true;
      }
    }
    return null;
  }

  @Override
  public List<Material> applicableMaterials() {
    return materials;
  }
}