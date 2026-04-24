package de.jpx3.intave.block.physics;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

final class SlimePhysics implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    material = Collections.singletonList(Material.SLIME_BLOCK);
  }

  @Override
  public void fallenUpon(User user) {
    MovementMetadata movementData = user.meta().movement();
    if (!movementData.sneaking) {
      movementData.artificialFallDistance = 0;
    }
  }

  @Override
  // SlimeBlock.bounceUp
  public Motion landed(User user, double motionX, double motionY, double motionZ) {
    MovementMetadata movementData = user.meta().movement();
    if (motionY < 0.0 && !movementData.sneaking) {
      return new Motion(motionX, -motionY, motionZ);
    } else {
      return null;
    }
  }

  @Override
  // SlimeBlock.stepOn
  public Motion entityCollidedWithBlock(User user, double motionX, double motionY, double motionZ) {
    MovementMetadata movementData = user.meta().movement();
    if (Math.abs(motionY) < 0.1D && !movementData.sneaking) {
      double d0 = 0.4D + Math.abs(motionY) * 0.2D;
      motionX *= d0;
      motionZ *= d0;
      return new Motion(motionX, motionY, motionZ);
    } else {
      return null;
    }
  }

  @Override
  public List<Material> applicableMaterials() {
    return material;
  }
}
