package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

final class BubbleColumnPhysics implements BlockPhysic {
  private Material bubbleColumnBlock;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    bubbleColumnBlock = Material.getMaterial("BUBBLE_COLUMN");
  }

  @Override
  public boolean supportedOnServerVersion() {
    return bubbleColumnBlock != null;
  }

  @Override
  public Motion entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    ProtocolMetadata protocol = user.meta().protocol();
    if (protocol.waterUpdate()) {
      boolean water = VolatileBlockAccess.fluidAccess(user, location.clone().add(0, 1, 0)).isOfWater();
      BlockVariant variant = VolatileBlockAccess.variantAccess(user, location);
      boolean downwards = variant.propertyOf("drag");
      if (water) {
        return enterBubbleColumn(user, downwards, motionX, motionY, motionZ);
      } else {
        return enterBubbleColumnWithAirAbove(downwards, motionX, motionY, motionZ);
      }
    }
    return null;
  }

  private Motion enterBubbleColumn(User user, boolean downwards, double motionX, double motionY, double motionZ) {
    MovementMetadata movementData = user.meta().movement();
    if (downwards) {
      motionY = Math.max(-0.3D, motionY - 0.03D);
    } else {
      motionY = Math.min(0.7D, motionY + 0.06D);
    }
    movementData.artificialFallDistance = 0;
    return new Motion(motionX, motionY, motionZ);
  }

  private Motion enterBubbleColumnWithAirAbove(boolean downwards, double motionX, double motionY, double motionZ) {
    if (downwards) {
      motionY = Math.max(-0.9D, motionY - 0.03D);
    } else {
      motionY = Math.min(1.8D, motionY + 0.1D);
    }
    return new Motion(motionX, motionY, motionZ);
  }

  @Override
  public List<Material> applicableMaterials() {
    return Collections.singletonList(bubbleColumnBlock);
  }
}