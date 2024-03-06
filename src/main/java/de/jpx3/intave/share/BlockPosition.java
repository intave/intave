package de.jpx3.intave.share;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class BlockPosition extends NativeVector {
  public static final BlockPosition ORIGIN = new BlockPosition(0, 0, 0);
  private static final int NUM_X_BITS = 1 + ClientMath.calculateLogBaseTwo(ClientMath.roundUpToPowerOfTwo(30000000));
  private static final int NUM_Z_BITS = NUM_X_BITS;
  private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS;
  private static final int Y_SHIFT = NUM_Z_BITS;
  private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
  private static final long X_MASK = (1L << NUM_X_BITS) - 1L;
  private static final long Y_MASK = (1L << NUM_Y_BITS) - 1L;
  private static final long Z_MASK = (1L << NUM_Z_BITS) - 1L;

  public BlockPosition(int x, int y, int z) {
    super(x, y, z);
  }

  public BlockPosition(double x, double y, double z) {
    super(x, y, z);
  }

  public BlockPosition(Entity source) {
    this(source.getLocation().getX(), source.getLocation().getY(), source.getLocation().getZ());
  }

  public BlockPosition(NativeVector source) {
    this(source.xCoord, source.yCoord, source.zCoord);
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
      this.xCoord + x,
      this.yCoord + y,
      this.zCoord + z
    );
  }

  /**
   * Add the given coordinates to the coordinates of this BlockPos
   */
  public BlockPosition add(int x, int y, int z) {
    return x == 0 && y == 0 && z == 0 ? this : new BlockPosition(
      this.xCoord + x,
      this.yCoord + y,
      this.zCoord + z
    );
  }

  /**
   * Add the given Vector to this BlockPos
   */
  public BlockPosition add(NativeVector vec) {
    return zero(vec) ? this : new BlockPosition(
      this.xCoord + vec.xCoord,
      this.yCoord + vec.yCoord,
      this.zCoord + vec.zCoord
    );
  }

  /**
   * Subtract the given Vector from this BlockPos
   */
  public BlockPosition subtract(NativeVector vec) {
    return zero(vec) ? this : new BlockPosition(
      this.xCoord - vec.xCoord,
      this.yCoord - vec.yCoord,
      this.zCoord - vec.zCoord
    );
  }

  /**
   * Returns whether all coordinates of the vector are zero
   */
  private boolean zero(NativeVector vec) {
    return vec.xCoord == 0 && vec.yCoord == 0 && vec.zCoord == 0;
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
    return new BlockPosition(this.xCoord + facing.offsetX() * n, this.yCoord + facing.offsetY() * n, this.zCoord + facing.offsetZ() * n);
  }

  /**
   * Offsets this BlockPos n blocks in the given direction
   */
  public BlockPosition offset(Direction facing, int n) {
    return n == 0 ? this : new BlockPosition(
      this.xCoord + facing.getFrontOffsetX() * n,
      this.yCoord + facing.getFrontOffsetY() * n,
      this.zCoord + facing.getFrontOffsetZ() * n
    );
  }

  /**
   * Calculate the cross product of this and the given Vector
   */
  public BlockPosition crossProduct(NativeVector vec) {
    return new BlockPosition(
      this.yCoord * vec.zCoord - this.zCoord * vec.yCoord,
      this.zCoord * vec.xCoord - this.xCoord * vec.zCoord,
      this.xCoord * vec.yCoord - this.yCoord * vec.xCoord
    );
  }

  public int getBlockX() {
    return (int) xCoord;
  }

  public int getX() {
    return (int) xCoord;
  }

  public int getBlockY() {
    return (int) yCoord;
  }

  public int getY() {
    return (int) yCoord;
  }

  public int getBlockZ() {
    return (int) zCoord;
  }

  public int getZ() {
    return (int) zCoord;
  }


  @Override
  public int hashCode() {
    long i = (long) (this.xCoord * 3129871) ^ (long) this.zCoord * 116129781L ^ (long) this.yCoord;
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
      return this.xCoord == blockPos.xCoord && this.yCoord == blockPos.yCoord && this.zCoord == blockPos.zCoord;
    }
  }

  /**
   * Serialize this BlockPos into a long value
   */
  public long toLong() {
    return ((long) this.xCoord & X_MASK) << X_SHIFT
      | ((long) this.yCoord & Y_MASK) << Y_SHIFT
      | ((long) this.zCoord & Z_MASK);
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
}