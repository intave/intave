/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.block.physics;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
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
  public Motion entityInside(User user, SimulationEnvironment environment, Location location, Location from, double motionX, double motionY, double motionZ) {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.aquaticUpdate()) {
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
