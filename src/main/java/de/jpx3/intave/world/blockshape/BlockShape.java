package de.jpx3.intave.world.blockshape;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/**
 *  Size in bytes
 *
 *  Axis Aligned BB (99.95% of the time, it's just one box)
 *    6 * 8 = 48 bytes
 *  Type ref 4 bytes
 *  data has 4 bytes
 *  creation has 8 bytes
 *  lookups has 4 bytes
 *
 *  = 68 bytes + 4 bytes object header
 *
 *  -> 72 bytes per blockshape
 */
public final class BlockShape {
  private final List<WrappedAxisAlignedBB> boxes;
  private final Material type;
  private final int data;
  private final long creation = AccessHelper.now();
  private int lookups;

  public BlockShape(List<WrappedAxisAlignedBB> boxes, Material type, int data) {
    this.boxes = boxes;
    this.type = type;
    this.data = data;
  }

  public List<WrappedAxisAlignedBB> boundingBoxes() {
    return boxes;
  }

  public Material type() {
    return type;
  }

  public int data() {
    return data;
  }

  public boolean expired() {
    return !IntaveControl.IGNORE_CACHE_REFRESH_ON_DETECTION && AccessHelper.now() - creation > 10000;
  }

  public void successfulFallbackLookup() {
    lookups++;
  }

  public int successfulLookups() {
    return lookups;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlockShape that = (BlockShape) o;
    if (data != that.data) return false;
    if (creation != that.creation) return false;
    if (!Objects.equals(boxes, that.boxes)) return false;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    int result = boxes != null ? boxes.hashCode() : 0;
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + data;
    result = 31 * result + (int) (creation ^ (creation >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return String.valueOf(lookups);
  }
}
