package de.jpx3.intave.world.blockphysic;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

final class BlockSlimePhysic implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    material = Collections.singletonList(Material.SLIME_BLOCK);
  }

  @Override
  public void fallenUpon(User user) {
    UserMetaMovementData movementData = user.meta().movementData();
    if (!movementData.sneaking) {
      movementData.artificialFallDistance = 0;
    }
  }

  @Override
  public Vector landed(User user, double motionX, double motionY, double motionZ) {
    UserMetaMovementData movementData = user.meta().movementData();
    if (motionY < 0.0 && !movementData.sneaking) {
      return new Vector(motionX, -motionY, motionZ);
    } else {
      return null;
    }
  }

  @Override
  public Vector entityCollidedWithBlock(User user, double motionX, double motionY, double motionZ) {
    UserMetaMovementData movementData = user.meta().movementData();
    if (Math.abs(motionY) < 0.1D && !movementData.sneaking) {
      double d0 = 0.4D + Math.abs(motionY) * 0.2D;
      motionX *= d0;
      motionZ *= d0;
      return new Vector(motionX, motionY, motionZ);
    } else {
      return null;
    }
  }

  @Override
  public List<Material> materials() {
    return material;
  }
}