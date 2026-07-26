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
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_15;

final class WebPhysics implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    material = Collections.singletonList(BlockTypeAccess.WEB);
  }

  @Override
  public Motion entityInside(User user, SimulationEnvironment environment, Location location, Location from, double motionX, double motionY, double motionZ) {
    ProtocolMetadata clientData = user.meta().protocol();
    MovementMetadata movementData = user.meta().movement();
    movementData.inWeb = true;
    movementData.artificialFallDistance = 0;
    if (clientData.protocolVersion() >= VER_1_15) {
      return new Motion(motionX * 0.25, motionY * 0.05f, motionZ * 0.25);
    }
    return null;
  }

  @Override
  public List<Material> applicableMaterials() {
    return material;
  }
}
