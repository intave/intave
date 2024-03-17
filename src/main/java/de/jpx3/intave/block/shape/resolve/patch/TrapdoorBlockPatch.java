package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import static de.jpx3.intave.block.shape.resolve.patch.TrapdoorBlockPatch.EnumTrapdoorHalf.TOP;

final class TrapdoorBlockPatch extends BlockShapePatch {

  /*
   makes variant-control constraint redundant
   */
  @Override
  public BlockShape collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, BlockShape shape) {
    BoundingBoxBuilder boundingBoxBuilder = BoundingBoxBuilder.create();
    BlockVariant variant = BlockVariantRegister.variantOf(type, blockState);
    boolean isTop = variant.enumProperty(EnumTrapdoorHalf.class, "half") == TOP;
    boolean isOpen = variant.propertyOf("open");
//    Direction facing = variant.enumProperty(Direction.class, "facing");
    // using the variant index literally here is fine, because we only use this patch before 1.9
    if (isOpen) {
      switch (blockState & 3) { // excluded
        case 0:
          boundingBoxBuilder.shape(0.0F, 0.0F, 0.8125F, 1.0F, 1.0F, 1.0F);
          break;
        case 1:
          boundingBoxBuilder.shape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.1875F);
          break;
        case 2:
          boundingBoxBuilder.shape(0.8125F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
          break;
        case 3:
          boundingBoxBuilder.shape(0.0F, 0.0F, 0.0F, 0.1875F, 1.0F, 1.0F);
          break;
      }
    } else {
      if (isTop) {
        boundingBoxBuilder.shape(0.0F, 0.8125F, 0.0F, 1.0F, 1.0F, 1.0F);
      } else {
        boundingBoxBuilder.shape(0.0F, 0.0F, 0.0F, 1.0F, 0.1875F, 1.0F);
      }
    }
    return boundingBoxBuilder.applyAndResolveAsShape();
  }

  @Override
  public boolean appliesTo(Material material) {
    if (MinecraftVersions.VER1_12_0.atOrAbove()) {
      return false;
    }
    String name = material.name();
    return name.contains("TRAP_DOOR") || name.contains("TRAPDOOR");
  }

  @KeepEnumInternalNames
  public enum EnumTrapdoorHalf {
    TOP("top"),
    BOTTOM("bottom");

    private final String name;

    EnumTrapdoorHalf(String s) {
      this.name = s;
    }

    public String toString() {
      return this.name;
    }

    public String getName() {
      return this.name;
    }
  }
}