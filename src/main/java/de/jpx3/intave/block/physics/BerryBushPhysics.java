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
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

final class BerryBushPhysics implements BlockPhysic {
  private List<Material> material;
  private boolean supported;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    Material sweetBerryBush = Material.getMaterial("SWEET_BERRY_BUSH");
    material = Collections.singletonList(sweetBerryBush);
    supported = sweetBerryBush != null;
  }

  @Override
  public Motion entityInside(
    User user, SimulationEnvironment environment,
    Location location, Location from,
    double motionX, double motionY, double motionZ
  ) {
    environment.setMotionMultiplier(new Vector(0.8f, 0.75, 0.8f));
    return null;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return supported;
  }

  @Override
  public List<Material> applicableMaterials() {
    return material;
  }
}
