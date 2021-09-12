package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.block.fluid.FluidTag;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantBoolean;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

final class BlockBubbleColumnPhysic implements BlockPhysic {
  private Material bubbleColumnBlock;
  private final BlockVariantBoolean blockDragState = BlockVariantBoolean.of("drag");

  private final BlockVariant blockVariant = BlockVariant.builder()
    .with(blockDragState)
    .build();

  @Override
  public void setup(MinecraftVersion serverVersion) {
    bubbleColumnBlock = Material.getMaterial("BUBBLE_COLUMN");
  }

  @Override
  public boolean supportedOnServerVersion() {
    return bubbleColumnBlock != null;
  }

  @Override
  public Vector entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    ProtocolMetadata clientData = user.meta().protocol();
    if (clientData.waterUpdate()) {
      boolean water = Fluids.fluidAt(user, location.clone().add(0,1,0)).isIn(FluidTag.WATER);
      Block block = location.getBlock();
      boolean downwards = blockVariant.valueOf(user, block, blockDragState);
      if (water) {
        return enterBubbleColumn(user, downwards, motionX, motionY, motionZ);
      } else {
        return enterBubbleColumnWithAirAbove(downwards, motionX, motionY, motionZ);
      }
    }
    return null;
  }

  private Vector enterBubbleColumn(User user, boolean downwards, double motionX, double motionY, double motionZ) {
    MovementMetadata movementData = user.meta().movement();
    if (downwards) {
      motionY = Math.max(-0.3D, motionY - 0.03D);
    } else {
      motionY = Math.min(0.7D, motionY + 0.06D);
    }
    movementData.artificialFallDistance = 0;
    return new Vector(motionX, motionY, motionZ);
  }

  private Vector enterBubbleColumnWithAirAbove(boolean downwards, double motionX, double motionY, double motionZ) {
    if (downwards) {
      motionY = Math.max(-0.9D, motionY - 0.03D);
    } else {
      motionY = Math.min(1.8D, motionY + 0.1D);
    }
    return new Vector(motionX, motionY, motionZ);
  }

  @Override
  public List<Material> materials() {
    return Collections.singletonList(bubbleColumnBlock);
  }
}