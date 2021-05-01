package de.jpx3.intave.world.blockphysics;

import com.comphenix.protocol.utility.MinecraftVersion;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.List;

public final class BlockPhysicHoney implements BlockPhysic {
  private Material honeyBlock;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    honeyBlock = Material.getMaterial("HONEY_BLOCK");
  }

  @Override
  public Vector entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    if (doBlockPhysics(user, location, motionY)) {
      return updateMovement(user, motionX, motionY, motionZ);
    }
    return null;
  }

  private boolean doBlockPhysics(User user, Location blockPos, double motionY) {
    UserMetaMovementData movementData = user.meta().movementData();
    if (movementData.onGround) {
      return false;
    } else if (movementData.positionY > blockPos.getY() + 0.9375D - 1.0E-7D) {
      return false;
    } else if (motionY >= -0.08D) {
      return false;
    } else {
      double d0 = Math.abs(blockPos.getX() + 0.5D - movementData.positionX);
      double d1 = Math.abs(blockPos.getZ() + 0.5D - movementData.positionZ);
      double d2 = 0.4375D + (double)(movementData.width / 2.0F);
      return d0 + 1.0E-7D > d2 || d1 + 1.0E-7D > d2;
    }
  }

  private Vector updateMovement(User user, double motionX, double motionY, double motionZ) {
    UserMetaMovementData movementData = user.meta().movementData();
    movementData.artificialFallDistance = 0.0F;
    if (motionY< -0.13D) {
      double d0 = -0.05D / motionY;
      return new Vector(motionX * d0, -0.05D, motionZ * d0);
    } else {
      return new Vector(motionX, -0.05D, motionZ);
    }
  }

  @Override
  public float speedFactor(User user) {
    return 0.4f;
  }

  @Override
  public float jumpFactor(User user) {
    return 0.5f;
  }

  @Override
  public boolean supportedOnServerVersion() {
    return honeyBlock != null;
  }

  @Override
  public List<Material> materials() {
    return ImmutableList.of(honeyBlock);
  }
}