package de.jpx3.intave.block.shape;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.doubles.DoubleSet;

import java.util.List;

public interface BlockShape {
  StreamCodec<ByteBuf, ByteBuf, BlockShape> STREAM_CODEC =
    StreamCodec.dispatchBuilder(BlockShape.class, ByteBufStreamCodecs.UNSIGNED_BYTE)
      .subtype(0, EmptyBlockShape.class, () -> EmptyBlockShape.STREAM_CODEC)
      .subtype(1, CubeShape.class, () -> CubeShape.STREAM_CODEC)
      .subtype(2, BoundingBox.class, () -> BoundingBox.STREAM_CODEC)
      .subtype(3, VoxelShape.class, () -> VoxelShape.STREAM_CODEC)
      .subtype(4, ArrayBlockShape.class, () -> ArrayBlockShape.STREAM_CODEC)
      .subtype(5, MergeBlockShape.class, () -> MergeBlockShape.STREAM_CODEC)
      .subtype(6, ComparisonAlertShape.class, () -> ComparisonAlertShape.STREAM_CODEC)
      .build();

  double allowedOffset(Direction.Axis axis, BoundingBox entity, double offset);
  double min(Direction.Axis axis);
  double max(Direction.Axis axis);

  boolean intersectsWith(BoundingBox boundingBox);
  BlockShape contextualized(int posX, int posY, int posZ);

  default BlockShape contextualized(BlockPosition position) {
    return contextualized(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  BlockShape normalized(int posX, int posY, int posZ);

  default BlockShape normalized(BlockPosition position) {
    return normalized(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  void appendUnsortedCoordsTo(Direction.Axis axis, DoubleSet appendTo);

  @Nullable
  BlockRaytrace raytrace(Position origin, Position target);
  BoundingBox outline();

  List<BoundingBox> elementaryBoxes();
  boolean isEmpty();
  boolean isCubic();
}
