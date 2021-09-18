package de.jpx3.intave.block.state;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.shade.BoundingBox;
import org.bukkit.Material;

import java.util.Objects;

/**
 * A {@link BlockState} serves as a block-snapshot by capturing the bounding box,
 * the type and variant index of a block. It is primarily used for block-caching and
 * block-overrides.
 *
 * @see BlockStateLookup
 * @see BoundingBox
 * @see Material
 * @see BlockVariantRegister
 */
public final class BlockState extends MemoryTraced {
  private final static BlockState EMPTY = new BlockState(BlockShapes.empty(), Material.AIR, 0);
  private final BlockShape shape;
  private final Material type;
  private final int variant;
  private final long creation = System.currentTimeMillis();

  public BlockState(BlockShape shape, Material type, int variant) {
    this.shape = shape;
    this.type = type;
    this.variant = variant;
  }

  /**
   * Retrieve the blocks bounding boxes
   * @return the blocks bounding boxes
   */
  public BlockShape shape() {
    return shape;
  }

  /**
   * Retrieve the blocks type
   * @return the blocks type
   */
  public Material type() {
    return type;
  }

  /**
   * Retrieve the blocks variant
   * @return the blocks variant
   */
  public int variantIndex() {
    return variant;
  }

  /**
   * Indicates if this entry effectively expired.
   * Expiries neither have to be acknowledged nor followed - this only serves as a possible indicator
   * @return whether the state is expired
   */
  public boolean expired() {
    return !IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT && System.currentTimeMillis() - creation > 10000;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlockState that = (BlockState) o;
    if (variant != that.variant) return false;
    if (creation != that.creation) return false;
    if (!Objects.equals(shape, that.shape)) return false;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    int result = shape != null ? shape.hashCode() : 0;
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + variant;
    result = 31 * result + (int) (creation ^ (creation >>> 32));
    return result;
  }

  public static BlockState empty() {
    return EMPTY;
  }
}
