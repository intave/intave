package de.jpx3.intave.block.shape;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleSet;

import java.util.List;

public final class ComparisonAlertShape implements BlockShape {
  public static final StreamCodec<ByteBuf, ByteBuf, ComparisonAlertShape> STREAM_CODEC = StreamCodec.of(
    (output, shape) -> {
      BlockShape.STREAM_CODEC.encode(output, shape.firstShape());
      BlockShape.STREAM_CODEC.encode(output, shape.secondShape());
    },
    input -> new ComparisonAlertShape(
      BlockShape.STREAM_CODEC.decode(input),
      BlockShape.STREAM_CODEC.decode(input)
    )
  );

  private final BlockShape firstShape;
  private final BlockShape secondShape;

  private ComparisonAlertShape(BlockShape firstShape, BlockShape secondShape) {
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
    double first = firstShape.allowedOffset(axis, entity, offset);
    double second = secondShape.allowedOffset(axis, entity, offset);
    if (Math.abs(first - second) > 0.001) {
      System.err.println("Difference in allowed offset: " + first + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }

  @Override
  public double min(Direction.Axis axis) {
    double first = firstShape.min(axis);
    double second = secondShape.min(axis);
    if (Math.abs(first - second) > 0.001) {
      System.err.println("Difference in min: " + first + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }

  @Override
  public double max(Direction.Axis axis) {
    double first = firstShape.max(axis);
    double second = secondShape.max(axis);
    if (Math.abs(first - second) > 0.001) {
      System.err.println("Difference in max: " + first + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }

  @Override
  public boolean intersectsWith(BoundingBox boundingBox) {
    boolean first = firstShape.intersectsWith(boundingBox);
    boolean second = secondShape.intersectsWith(boundingBox);
    if (first != second) {
      System.err.println("Difference in intersectsWith: " + first + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }

  @Override
  public BlockShape contextualized(int posX, int posY, int posZ) {
    return new ComparisonAlertShape(
      firstShape.contextualized(posX, posY, posZ),
      secondShape.contextualized(posX, posY, posZ)
    );
  }

  @Override
  public BlockShape normalized(int posX, int posY, int posZ) {
    return new ComparisonAlertShape(
      firstShape.normalized(posX, posY, posZ),
      secondShape.normalized(posX, posY, posZ)
    );
  }

  @Override
  public void appendUnsortedCoordsTo(Direction.Axis axis, DoubleSet appendTo) {
    firstShape.appendUnsortedCoordsTo(axis, appendTo);
//    secondShape.appendUnsortedCoordsTo(axis, appendTo);
  }

  @Override
  public BlockRaytrace raytrace(Position origin, Position target) {
    BlockRaytrace first = firstShape.raytrace(origin, target);
    BlockRaytrace second = secondShape.raytrace(origin, target);
    if (!first.equals(second)) {
      System.err.println("Difference in raytrace: " + first
        + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }

  @Override
  public BoundingBox outline() {
    BoundingBox first = firstShape.outline();
    BoundingBox second = secondShape.outline();
    if (!first.equals(second)) {
      System.err.println("Difference in outline: " + first + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }

  @Override
  public List<BoundingBox> elementaryBoxes() {
    List<BoundingBox> first = firstShape.elementaryBoxes();
//    List<BoundingBox> second = secondShape.boundingBoxes();
//    if (!first.equals(second)) {
//      System.err.println("Difference in boundingBoxes: " + first + " vs " + second);
//      System.err.println("First shape: " + firstShape);
//      System.err.println("Second shape: " + secondShape);
//    }
    return first;
  }

  @Override
  public boolean isEmpty() {
    boolean first = firstShape.isEmpty();
    boolean second = secondShape.isEmpty();
    if (first != second) {
      System.err.println("Difference in isEmpty: " + first + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }

  @Override
  public boolean isCubic() {
    boolean first = firstShape.isCubic();
    boolean second = secondShape.isCubic();
    if (first != second) {
      System.err.println("Difference in isCubic: " + first + " vs " + second);
      System.err.println("First shape: " + firstShape);
      System.err.println("Second shape: " + secondShape);
    }
    return first;
  }
}
