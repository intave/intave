package de.jpx3.intave.tools.wrapper;

import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectionFailureException;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;

public final class WrappedBlockPosition extends WrappedVector {
  public static final WrappedBlockPosition ORIGIN = new WrappedBlockPosition(0, 0, 0);
  private static final int NUM_X_BITS = 1 + WrappedMathHelper.calculateLogBaseTwo(WrappedMathHelper.roundUpToPowerOfTwo(30000000));
  private static final int NUM_Z_BITS = NUM_X_BITS;
  private static final int NUM_Y_BITS = 64 - NUM_X_BITS - NUM_Z_BITS;
  private static final int Y_SHIFT = NUM_Z_BITS;
  private static final int X_SHIFT = Y_SHIFT + NUM_Y_BITS;
  private static final long X_MASK = (1L << NUM_X_BITS) - 1L;
  private static final long Y_MASK = (1L << NUM_Y_BITS) - 1L;
  private static final long Z_MASK = (1L << NUM_Z_BITS) - 1L;

  private static Field fromClassXField, fromClassYField, fromClassZField;

  public WrappedBlockPosition(int x, int y, int z) {
    super(x, y, z);
  }

  public WrappedBlockPosition(double x, double y, double z) {
    super(x, y, z);
  }

  public WrappedBlockPosition(Entity source) {
    this(source.getLocation().getX(), source.getLocation().getY(), source.getLocation().getZ());
  }

  public WrappedBlockPosition(WrappedVector source) {
    this(source.xCoord, source.yCoord, source.zCoord);
  }

  public WrappedBlockPosition(Location source) {
    this(source.getX(), source.getY(), source.getZ());
  }

  public static WrappedBlockPosition fromBlockPosition(Object blockPosition) {
    Class<?> blockPositionBase = ReflectiveAccess.lookupServerClass("BaseBlockPosition");
    try {
      Field xPosField = blockPositionBase.getDeclaredFields()[1];
      Field yPosField = blockPositionBase.getDeclaredFields()[2];
      Field zPosField = blockPositionBase.getDeclaredFields()[3];
      if(!xPosField.isAccessible()) {
        xPosField.setAccessible(true);
      }
      if(!yPosField.isAccessible()) {
        yPosField.setAccessible(true);
      }
      if(!zPosField.isAccessible()) {
        zPosField.setAccessible(true);
      }
      int xPos = (int) xPosField.get(blockPosition);
      int yPos = (int) yPosField.get(blockPosition);
      int zPos = (int) zPosField.get(blockPosition);
      return new WrappedBlockPosition(xPos, yPos, zPos);
    } catch (IllegalAccessException e) {
      throw new ReflectionFailureException(e);
    }
  }

  /**
   * Add the given coordinates to the coordinates of this BlockPos
   */
  public WrappedBlockPosition add(double x, double y, double z) {
    return x == 0.0D && y == 0.0D && z == 0.0D ? this : new WrappedBlockPosition(
      this.xCoord + x,
      this.yCoord + y,
      this.zCoord + z
    );
  }

  /**
   * Add the given coordinates to the coordinates of this BlockPos
   */
  public WrappedBlockPosition add(int x, int y, int z) {
    return x == 0 && y == 0 && z == 0 ? this : new WrappedBlockPosition(
      this.xCoord + x,
      this.yCoord + y,
      this.zCoord + z
    );
  }

  /**
   * Add the given Vector to this BlockPos
   */
  public WrappedBlockPosition add(WrappedVector vec) {
    return zero(vec) ? this : new WrappedBlockPosition(
      this.xCoord + vec.xCoord,
      this.yCoord + vec.yCoord,
      this.zCoord + vec.zCoord
    );
  }

  /**
   * Subtract the given Vector from this BlockPos
   */
  public WrappedBlockPosition subtract(WrappedVector vec) {
    return zero(vec) ? this : new WrappedBlockPosition(
      this.xCoord - vec.xCoord,
      this.yCoord - vec.yCoord,
      this.zCoord - vec.zCoord
    );
  }

  /**
   * Returns whether all coordinates of the vector are zero
   */
  private boolean zero(WrappedVector vec) {
    return vec.xCoord == 0 && vec.yCoord == 0 && vec.zCoord == 0;
  }

  /**
   * Offset this BlockPos 1 block up
   */
  public WrappedBlockPosition up() {
    return this.up(1);
  }

  /**
   * Offset this BlockPos n blocks up
   */
  public WrappedBlockPosition up(int n) {
    return this.offset(WrappedEnumDirection.UP, n);
  }

  /**
   * Offset this BlockPos 1 block down
   */
  public WrappedBlockPosition down() {
    return this.down(1);
  }

  /**
   * Offset this BlockPos n blocks down
   */
  public WrappedBlockPosition down(int n) {
    return this.offset(WrappedEnumDirection.DOWN, n);
  }

  /**
   * Offset this BlockPos 1 block in northern direction
   */
  public WrappedBlockPosition north() {
    return this.north(1);
  }

  /**
   * Offset this BlockPos n blocks in northern direction
   */
  public WrappedBlockPosition north(int n) {
    return this.offset(WrappedEnumDirection.NORTH, n);
  }

  /**
   * Offset this BlockPos 1 block in southern direction
   */
  public WrappedBlockPosition south() {
    return this.south(1);
  }

  /**
   * Offset this BlockPos n blocks in southern direction
   */
  public WrappedBlockPosition south(int n) {
    return this.offset(WrappedEnumDirection.SOUTH, n);
  }

  /**
   * Offset this BlockPos 1 block in western direction
   */
  public WrappedBlockPosition west() {
    return this.west(1);
  }

  /**
   * Offset this BlockPos n blocks in western direction
   */
  public WrappedBlockPosition west(int n) {
    return this.offset(WrappedEnumDirection.WEST, n);
  }

  /**
   * Offset this BlockPos 1 block in eastern direction
   */
  public WrappedBlockPosition east() {
    return this.east(1);
  }

  /**
   * Offset this BlockPos n blocks in eastern direction
   */
  public WrappedBlockPosition east(int n) {
    return this.offset(WrappedEnumDirection.EAST, n);
  }

  /**
   * Offset this BlockPos 1 block in the given direction
   */
  public WrappedBlockPosition offset(WrappedEnumDirection facing) {
    return this.offset(facing, 1);
  }

  /**
   * Offsets this BlockPos n blocks in the given direction
   */
  public WrappedBlockPosition offset(WrappedEnumDirection facing, int n) {
    return n == 0 ? this : new WrappedBlockPosition(
      this.xCoord + facing.getFrontOffsetX() * n,
      this.yCoord + facing.getFrontOffsetY() * n,
      this.zCoord + facing.getFrontOffsetZ() * n
    );
  }

  /**
   * Calculate the cross product of this and the given Vector
   */
  public WrappedBlockPosition crossProduct(WrappedVector vec) {
    return new WrappedBlockPosition(
      this.yCoord * vec.zCoord - this.zCoord * vec.yCoord,
      this.zCoord * vec.xCoord - this.xCoord * vec.zCoord,
      this.xCoord * vec.yCoord - this.yCoord * vec.xCoord
    );
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
  public static WrappedBlockPosition fromLong(long serialized) {
    int i = (int) (serialized << 64 - X_SHIFT - NUM_X_BITS >> 64 - NUM_X_BITS);
    int j = (int) (serialized << 64 - Y_SHIFT - NUM_Y_BITS >> 64 - NUM_Y_BITS);
    int k = (int) (serialized << 64 - NUM_Z_BITS >> 64 - NUM_Z_BITS);
    return new WrappedBlockPosition(i, j, k);
  }
}