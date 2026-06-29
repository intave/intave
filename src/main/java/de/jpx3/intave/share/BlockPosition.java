package de.jpx3.intave.share;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class BlockPosition extends RawVector3d {
  public static final BlockPosition ORIGIN = new BlockPosition(0, 0, 0);
  private static final int NUM_X_BITS = 1 + ClientMath.calculateLogBaseTwo(ClientMath.roundUpToPowerOfTwo(30000000));
  private static final int NUM_Z_BITS = NUM_X_BITS;
  private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS;
  private static final int Y_SHIFT = NUM_Z_BITS;
  private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
  private static final long X_MASK = (1L << NUM_X_BITS) - 1L;
  private static final long Y_MASK = (1L << NUM_Y_BITS) - 1L;
  private static final long Z_MASK = (1L << NUM_Z_BITS) - 1L;

  public static final StreamCodec<ByteBuf, ByteBuf, BlockPosition> STREAM_CODEC = ByteBufStreamCodecs.LONG.beforeAndAfter(
    BlockPosition::fromLong, BlockPosition::toLong
  );

  public BlockPosition(int x, int y, int z) {
    super(x, y, z);
  }

  public BlockPosition(double x, double y, double z) {
    super(x, y, z);
  }

  public BlockPosition(Entity source) {
    this(source.getLocation().getX(), source.getLocation().getY(), source.getLocation().getZ());
  }

  public BlockPosition(RawVector3d source) {
    this(source.x, source.y, source.z);
  }

  public BlockPosition(Location source) {
    this(source.getBlockX(), source.getBlockY(), source.getBlockZ());
  }

  public BlockPosition(com.comphenix.protocol.wrappers.BlockPosition blockPosition) {
    this(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
  }

  /**
   * Add the given coordinates to the coordinates of this BlockPos
   */
  public BlockPosition add(double x, double y, double z) {
    return x == 0.0D && y == 0.0D && z == 0.0D ? this : new BlockPosition(
      this.x + x,
      this.y + y,
      this.z + z
    );
  }

  /**
   * Add the given coordinates to the coordinates of this BlockPos
   */
  public BlockPosition add(int x, int y, int z) {
    return x == 0 && y == 0 && z == 0 ? this : new BlockPosition(
      this.x + x,
      this.y + y,
      this.z + z
    );
  }

  /**
   * Add the given Vector to this BlockPos
   */
  public BlockPosition add(RawVector3d vec) {
    return zero(vec) ? this : new BlockPosition(
      this.x + vec.x,
      this.y + vec.y,
      this.z + vec.z
    );
  }

  /**
   * Subtract the given Vector from this BlockPos
   */
  public BlockPosition subtract(RawVector3d vec) {
    return zero(vec) ? this : new BlockPosition(
      this.x - vec.x,
      this.y - vec.y,
      this.z - vec.z
    );
  }

  /**
   * Returns whether all coordinates of the vector are zero
   */
  private boolean zero(RawVector3d vec) {
    return vec.x == 0 && vec.y == 0 && vec.z == 0;
  }

  /**
   * Offset this BlockPos 1 block up
   */
  public BlockPosition up() {
    return this.up(1);
  }

  /**
   * Offset this BlockPos n blocks up
   */
  public BlockPosition up(int n) {
    return this.offset(Direction.UP, n);
  }

  /**
   * Offset this BlockPos 1 block down
   */
  public BlockPosition down() {
    return this.down(1);
  }

  /**
   * Offset this BlockPos n blocks down
   */
  public BlockPosition down(int n) {
    return this.offset(Direction.DOWN, n);
  }

  /**
   * Offset this BlockPos 1 block in northern direction
   */
  public BlockPosition north() {
    return this.north(1);
  }

  /**
   * Offset this BlockPos n blocks in northern direction
   */
  public BlockPosition north(int n) {
    return this.offset(Direction.NORTH, n);
  }

  /**
   * Offset this BlockPos 1 block in southern direction
   */
  public BlockPosition south() {
    return this.south(1);
  }

  /**
   * Offset this BlockPos n blocks in southern direction
   */
  public BlockPosition south(int n) {
    return this.offset(Direction.SOUTH, n);
  }

  /**
   * Offset this BlockPos 1 block in western direction
   */
  public BlockPosition west() {
    return this.west(1);
  }

  /**
   * Offset this BlockPos n blocks in western direction
   */
  public BlockPosition west(int n) {
    return this.offset(Direction.WEST, n);
  }

  /**
   * Offset this BlockPos 1 block in eastern direction
   */
  public BlockPosition east() {
    return this.east(1);
  }

  /**
   * Offset this BlockPos n blocks in eastern direction
   */
  public BlockPosition east(int n) {
    return this.offset(Direction.EAST, n);
  }

  /**
   * Offset this BlockPos 1 block in the given direction
   */
  public BlockPosition offset(Direction facing) {
    return this.offset(facing, 1);
  }

  public BlockPosition move(Direction facing) {
    return move(facing, 1);
  }

  public BlockPosition move(Direction facing, int n) {
    return new BlockPosition(this.x + facing.offsetX() * n, this.y + facing.offsetY() * n, this.z + facing.offsetZ() * n);
  }

  /**
   * Offsets this BlockPos n blocks in the given direction
   */
  public BlockPosition offset(Direction facing, int n) {
    return n == 0 ? this : new BlockPosition(
      this.x + facing.getFrontOffsetX() * n,
      this.y + facing.getFrontOffsetY() * n,
      this.z + facing.getFrontOffsetZ() * n
    );
  }

  /**
   * Calculate the cross product of this and the given Vector
   */
  public BlockPosition crossProduct(RawVector3d vec) {
    return new BlockPosition(
      this.y * vec.z - this.z * vec.y,
      this.z * vec.x - this.x * vec.z,
      this.x * vec.y - this.y * vec.x
    );
  }

  public int getBlockX() {
    return (int) x;
  }

  public int getX() {
    return (int) x;
  }

  public int getBlockY() {
    return (int) y;
  }

  public int getY() {
    return (int) y;
  }

  public int getBlockZ() {
    return (int) z;
  }

  public int getZ() {
    return (int) z;
  }

  @Override
  public int hashCode() {
    long i = (long) (this.x * 3129871) ^ (long) this.z * 116129781L ^ (long) this.y;
    return (int) (i ^ i >> 32);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (!(obj instanceof BlockPosition)) {
      return false;
    } else {
      BlockPosition blockPos = (BlockPosition) obj;
      return this.x == blockPos.x && this.y == blockPos.y && this.z == blockPos.z;
    }
  }

  @Override
  public String toString() {
    return "(" + ((int) x) + ", " + ((int) y) + ", " + ((int) z) + ")";
  }

  /**
   * Serialize this BlockPos into a long value
   */
  public long toLong() {
    return ((long) this.x & X_MASK) << X_SHIFT
      | ((long) this.y & Y_MASK) << Y_SHIFT
      | ((long) this.z & Z_MASK);
  }

  /**
   * Create a BlockPos from a serialized long value (created by toLong)
   */
  public static BlockPosition fromLong(long serialized) {
    int i = (int) (serialized << 64 - X_SHIFT - NUM_X_BITS >> 64 - NUM_X_BITS);
    int j = (int) (serialized << 64 - Y_SHIFT - NUM_Y_BITS >> 64 - NUM_Y_BITS);
    int k = (int) (serialized << 64 - NUM_Z_BITS >> 64 - NUM_Z_BITS);
    return new BlockPosition(i, j, k);
  }

  public static BlockPosition of(int posX, int posY, int posZ) {
    return new BlockPosition(posX, posY, posZ);
  }
}