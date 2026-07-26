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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Set;

final class PowderSnowPhysics implements BlockPhysic {
  private Set<Material> materials;
  private boolean supported;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    materials = MaterialSearch.materialsThatContain("POWDER_SNOW");
    supported = !materials.isEmpty();
  }

  @Override
  public Motion entityInside(User user, SimulationEnvironment environment, Location location, Location from, double motionX, double motionY, double motionZ) {
    MovementMetadata movementData = user.meta().movement();
    Material block = VolatileBlockAccess.typeAccess(
      user, user.player().getWorld(),
      movementData.positionX,
      movementData.positionY,
      movementData.positionZ
    );
    if (materials.contains(block)) {
      environment.setMotionMultiplier(new Vector(0.9f, 1.5f, 0.9f));
    }
    return null;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return supported;
  }

  @Override
  public Set<Material> applicableMaterials() {
    return materials;
  }
}
