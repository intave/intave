package de.jpx3.intave.block.shape;

import com.google.common.collect.Lists;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleSet;

import java.util.List;

final class MergeBlockShape implements BlockShape {
  public static final StreamCodec<ByteBuf, ByteBuf, MergeBlockShape> STREAM_CODEC = StreamCodec.of(
    (output, shape) -> {
      BlockShape.STREAM_CODEC.encode(output, shape.firstShape());
      BlockShape.STREAM_CODEC.encode(output, shape.secondShape());
    },
    input -> new MergeBlockShape(
      BlockShape.STREAM_CODEC.decode(input),
      BlockShape.STREAM_CODEC.decode(input)
    )
  );

  private final BlockShape firstShape, secondShape;

  MergeBlockShape(BlockShape firstShape, BlockShape secondShape) {
    this.firstShape = firstShape;
    this.secondShape = secondShape;
  }

  BlockShape firstShape() {
    return firstShape;
  }

  BlockShape secondShape() {
    return secondShape;
  }

  @Override
  public double allowedOffset(Direction.Axis axis, BoundingBox entity, double offset) {
    return firstShape.allowedOffset(axis, entity, secondShape.allowedOffset(axis, entity, offset));
  }

  @Override
  public double min(Direction.Axis axis) {
    return Math.min(firstShape.min(axis), secondShape.min(axis));
  }

  @Override
  public double max(Direction.Axis axis) {
    return Math.max(firstShape.max(axis), secondShape.max(axis));
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return new MergeBlockShape(
      firstShape.contextualized(posX, posY, posZ),
      secondShape.contextualized(posX, posY, posZ)
    );
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return new MergeBlockShape(
      firstShape.normalized(posX, posY, posZ),
      secondShape.normalized(posX, posY, posZ)
    );
  }

  @Override
  public void appendUnsortedCoordsTo(Direction.Axis axis, DoubleSet appendTo) {
    firstShape.appendUnsortedCoordsTo(axis, appendTo);
    secondShape.appendUnsortedCoordsTo(axis, appendTo);
  }

  @Override
  public BlockRaytrace raytrace(Position origin, Position target) {
    BlockRaytrace raytraceA = firstShape.raytrace(origin, target);
    BlockRaytrace raytraceB = secondShape.raytrace(origin, target);
    if (raytraceA == null) {
      return raytraceB;
    }
    if (raytraceB == null) {
      return raytraceA;
    }
    return raytraceA.minSelect(raytraceB);
  }

  @Override
  public BoundingBox outline() {
    return firstShape.outline().union(secondShape.outline());
  }

  @Override
  public List<BoundingBox> elementaryBoxes() {
    if (firstShape.isEmpty()) {
      return secondShape.elementaryBoxes();
    }
    if (secondShape.isEmpty()) {
      return firstShape.elementaryBoxes();
    }
    List<BoundingBox> merge = Lists.newArrayList(firstShape.elementaryBoxes());
    merge.addAll(secondShape.elementaryBoxes());
    return merge;
  }

  @Override
  public boolean isEmpty() {
    return firstShape.isEmpty() && secondShape.isEmpty();
  }

  @Override
  public boolean isCubic() {
    return (firstShape.isCubic() && secondShape.isEmpty())
      || (secondShape.isCubic() && firstShape.isEmpty());
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    return firstShape.intersectsWith(boundingBox) || secondShape.intersectsWith(boundingBox);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MergeBlockShape that = (MergeBlockShape) obj;
    return firstShape.equals(that.firstShape) && secondShape.equals(that.secondShape);
  }

  @Override
  public int hashCode() {
    int result = firstShape.hashCode();
    result = 31 * result + secondShape.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return firstShape + " and " + secondShape;
  }
}
