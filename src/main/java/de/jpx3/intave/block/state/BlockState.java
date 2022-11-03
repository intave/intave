package de.jpx3.intave.block.state;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Material;

import java.util.Objects;

/**
 * A {@link BlockState} serves as a block-snapshot by capturing the bounding box,
 * the type and variant index of a block. It is primarily used for block-caching and
 * block-overrides.
 *
 * @see BlockStateCache
 * @see BoundingBox
 * @see Material
 * @see BlockVariantRegister
 */
final class BlockState extends MemoryTraced {
  private static final BlockState EMPTY = new BlockState(BlockShapes.emptyShape(), BlockShapes.emptyShape(), Material.AIR, 0);
  private final BlockShape outlineShape;
  private final BlockShape collisionShape;
  private final Material type;
  private final int variantIndex;
  private final long creation = System.currentTimeMillis();
  private int hashCode = 0;

  BlockState(BlockShape outlineShape, BlockShape collisionShape, Material type, int variantIndex) {
    this.outlineShape = outlineShape;
    this.collisionShape = collisionShape;
    this.type = type;
    this.variantIndex = variantIndex;
  }

  /**
   * Returns the bounding box of this block state.
   * @return the bounding box of this block state
   */
  BlockShape outlineShape() {
    return outlineShape;
  }

  /**
   * Retrieve the blocks bounding boxes
   * @return the blocks bounding boxes
   */
  BlockShape collisionShape() {
    return collisionShape;
  }

  /**
   * Retrieve the blocks type
   *
   * @return the blocks type
   */
  Material type() {
    return type;
  }

  /**
   * Retrieve the blocks variant
   *
   * @return the blocks variant
   */
  int variantIndex() {
    return variantIndex;
  }

  /**
   * Indicates if this entry effectively expired.
   * Expiries neither have to be acknowledged nor followed - this only serves as a possible indicator
   *
   * @return whether the state is expired
   */
  boolean expired() {
    return !IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT && age() > 10000;
  }

  long age() {
    return System.currentTimeMillis() - creation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlockState that = (BlockState) o;
    if (variantIndex != that.variantIndex) return false;
    if (creation != that.creation) return false;
    if (!Objects.equals(collisionShape, that.collisionShape)) return false;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = collisionShape != null ? collisionShape.hashCode() : 0;
      result = 31 * result + (type != null ? type.hashCode() : 0);
      result = 31 * result + variantIndex;
      result = 31 * result + (int) (creation ^ (creation >>> 32));
      hashCode = result;
    }
    return hashCode;
  }

  public static BlockState empty() {
    return EMPTY;
  }
}
