package de.jpx3.intave.block.shape;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.diagnostic.MemoryTraced;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleSet;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class ArrayBlockShape extends MemoryTraced implements BlockShape {
  public static final StreamCodec<ByteBuf, ByteBuf, ArrayBlockShape> STREAM_CODEC =
    BlockShape.STREAM_CODEC.toListCodec(ByteBufStreamCodecs.INTEGER)
      .beforeAndAfter(ArrayBlockShape::new, arrayBlockShape -> Arrays.asList(arrayBlockShape.contents));

  private final BlockShape[] contents;

  ArrayBlockShape(BlockShape... contents) {
    this.contents = contents;
  }

  ArrayBlockShape(List<? extends BlockShape> contents) {
    this.contents = contents.toArray(new BlockShape[0]);
  }


  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox entity, double offset) {
    for (BlockShape shape : contents) {
      offset = shape.allowedOffset(axis, entity, offset);
    }
    return offset;
  }

  @Override
  public double min(Direction.Axis axis) {
    double min = Integer.MAX_VALUE;
    boolean hasMin = false;
    for (BlockShape content : contents) {
      min = Math.min(content.min(axis), min);
      hasMin = true;
    }
    return hasMin ? min : 0;
  }

  @Override
  public double max(Direction.Axis axis) {
    double max = Integer.MIN_VALUE;
    boolean hasMax = false;
    for (BlockShape content : contents) {
      max = Math.max(content.min(axis), max);
      hasMax = true;
    }
    return hasMax ? max : 0;
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    BlockShape[] array = new BlockShape[contents.length];
    for (int i = 0; i < contents.length; i++) {
      array[i] = contents[i].contextualized(posX, posY, posZ);
    }
    return new ArrayBlockShape(array);
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    BlockShape[] array = new BlockShape[contents.length];
    for (int i = 0; i < contents.length; i++) {
      array[i] = contents[i].normalized(posX, posY, posZ);
    }
    return new ArrayBlockShape(array);
  }

  @Override
  public void appendUnsortedCoordsTo(Direction.Axis axis, DoubleSet appendTo) {
    for (BlockShape content : contents) {
      content.appendUnsortedCoordsTo(axis, appendTo);
    }
  }

  @Override
  public BlockRaytrace raytrace(Position origin, Position target) {
    BlockRaytrace raytrace = null;
    for (BlockShape content : contents) {
      BlockRaytrace added = content.raytrace(origin, target);
      if (added != BlockRaytrace.none()) {
        if (raytrace == null) {
          raytrace = added;
        } else {
          raytrace = raytrace.minSelect(added);
        }
      }
    }
    return raytrace;
  }

  @Override
  public BoundingBox outline() {
    BoundingBox outline = null;
    for (BlockShape content : contents) {
      BoundingBox added = content.outline();
      if (added != BoundingBox.empty()) {
        if (outline == null) {
          outline = added;
        } else {
          outline = outline.union(added);
        }
      }
    }
    return outline == null ? BoundingBox.empty() : outline;
  }

  private static final Reference<List<BoundingBox>> NULL_REFERENCE = new WeakReference<>(null);
  private static final Reference<List<BoundingBox>> EMPTY_REFERENCE = new WeakReference<>(Collections.emptyList());
  private Reference<List<BoundingBox>> boundingBoxCache = NULL_REFERENCE;

  @Override
  public List<BoundingBox> elementaryBoxes() {
    List<BoundingBox> boundingBoxes = boundingBoxCache.get();
    if (boundingBoxes == null) {
      for (BlockShape content : contents) {
        if (content.isEmpty()) {
          continue;
        }
        List<BoundingBox> newBoxes = content.elementaryBoxes();
        if (newBoxes.isEmpty()) {
          continue;
        }
        if (boundingBoxes == null) {
          boundingBoxes = new ArrayList<>(contents.length);
        }
        if (newBoxes.size() == 1) {
          boundingBoxes.add(newBoxes.get(0));
        } else {
          boundingBoxes.addAll(newBoxes);
        }
      }
      boundingBoxCache = boundingBoxes == null ? EMPTY_REFERENCE : new WeakReference<>(boundingBoxes);
      boundingBoxes = boundingBoxes == null ? Collections.emptyList() : boundingBoxes;
    }
    return boundingBoxes;
  }

  @Override
  public boolean isEmpty() {
    for (BlockShape content : contents) {
      if (!content.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isCubic() {
    return contents.length == 1 && contents[0].isCubic();
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    for (BlockShape content : contents) {
      if (content.intersectsWith(boundingBox)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ArrayBlockShape that = (ArrayBlockShape) obj;
    return Arrays.equals(contents, that.contents);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return "[" + Arrays.stream(contents).map(Object::toString).collect(Collectors.joining("; ")) + "]";
  }
}
