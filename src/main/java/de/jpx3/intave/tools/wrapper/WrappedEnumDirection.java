package de.jpx3.intave.tools.wrapper;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public enum WrappedEnumDirection {
  DOWN(0, 1, -1, "down", WrappedEnumDirection.AxisDirection.NEGATIVE, WrappedEnumDirection.Axis.Y, new WrappedVector(0, -1, 0)),
  UP(1, 0, -1, "up", WrappedEnumDirection.AxisDirection.POSITIVE, WrappedEnumDirection.Axis.Y, new WrappedVector(0, 1, 0)),
  NORTH(2, 3, 2, "north", WrappedEnumDirection.AxisDirection.NEGATIVE, WrappedEnumDirection.Axis.Z, new WrappedVector(0, 0, -1)),
  SOUTH(3, 2, 0, "south", WrappedEnumDirection.AxisDirection.POSITIVE, WrappedEnumDirection.Axis.Z, new WrappedVector(0, 0, 1)),
  WEST(4, 5, 1, "west", WrappedEnumDirection.AxisDirection.NEGATIVE, WrappedEnumDirection.Axis.X, new WrappedVector(-1, 0, 0)),
  EAST(5, 4, 3, "east", WrappedEnumDirection.AxisDirection.POSITIVE, WrappedEnumDirection.Axis.X, new WrappedVector(1, 0, 0));

  /** Ordering index for D-U-N-S-W-E */
  private final int index;

  /** Index of the opposite Facing in the VALUES array */
  private final int opposite;

  /** Ordering index for the HORIZONTALS field (S-W-N-E) */
  private final int horizontalIndex;
  private final String name;
  private final WrappedEnumDirection.Axis axis;
  private final WrappedEnumDirection.AxisDirection axisDirection;

  /** Normalized Vector that points in the direction of this Facing */
  private final WrappedVector directionVec;

  /** All facings in D-U-N-S-W-E order */
  private static final WrappedEnumDirection[] VALUES = new WrappedEnumDirection[6];

  /** All Facings with horizontal axis in order S-W-N-E */
  private static final WrappedEnumDirection[] HORIZONTALS = new WrappedEnumDirection[4];
  private static final Map<String, WrappedEnumDirection> NAME_LOOKUP = Maps.newHashMap();

  WrappedEnumDirection(int indexIn, int oppositeIn, int horizontalIndexIn, String nameIn, WrappedEnumDirection.AxisDirection axisDirectionIn, WrappedEnumDirection.Axis axisIn, WrappedVector directionVecIn) {
    this.index = indexIn;
    this.horizontalIndex = horizontalIndexIn;
    this.opposite = oppositeIn;
    this.name = nameIn;
    this.axis = axisIn;
    this.axisDirection = axisDirectionIn;
    this.directionVec = directionVecIn;
  }

  public static WrappedEnumDirection getFacingFromAxisDirection(WrappedEnumDirection.Axis axisIn, WrappedEnumDirection.AxisDirection axisDirectionIn) {
    switch(axisIn) {
      case X:
        return axisDirectionIn == WrappedEnumDirection.AxisDirection.POSITIVE ? EAST : WEST;
      case Y:
        return axisDirectionIn == WrappedEnumDirection.AxisDirection.POSITIVE ? UP : DOWN;
      case Z:
      default:
        return axisDirectionIn == WrappedEnumDirection.AxisDirection.POSITIVE ? SOUTH : NORTH;
    }
  }

  /**
   * Get the Index of this Facing (0-5). The order is D-U-N-S-W-E
   */
  public int getIndex() {
    return this.index;
  }

  /**
   * Get the index of this horizontal facing (0-3). The order is S-W-N-E
   */
  public int getHorizontalIndex() {
    return this.horizontalIndex;
  }

  /**
   * Get the AxisDirection of this Facing.
   */
  public WrappedEnumDirection.AxisDirection getAxisDirection() {
    return this.axisDirection;
  }

  /**
   * Get the opposite Facing (e.g. DOWN => UP)
   */
  public WrappedEnumDirection getOpposite() {
    return getFront(this.opposite);
  }

  /**
   * Rotate this Facing around the given axis clockwise. If this facing cannot be rotated around the given axis, returns
   * this facing without rotating.
   */
  public WrappedEnumDirection rotateAround(WrappedEnumDirection.Axis axis) {
    switch (axis) {
      case X:
        if (this != WEST && this != EAST) {
          return this.rotateX();
        }

        return this;

      case Y:
        if (this != UP && this != DOWN) {
          return this.rotateY();
        }

        return this;

      case Z:
        if (this != NORTH && this != SOUTH) {
          return this.rotateZ();
        }

        return this;

      default:
        throw new IllegalStateException("Unable to get CW facing for axis " + axis);
    }
  }

  /**
   * Rotate this Facing around the Y axis clockwise (NORTH => EAST => SOUTH => WEST => NORTH)
   */
  public WrappedEnumDirection rotateY() {
    switch (this) {
      case NORTH:
        return EAST;

      case EAST:
        return SOUTH;

      case SOUTH:
        return WEST;

      case WEST:
        return NORTH;

      default:
        throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
    }
  }

  /**
   * Rotate this Facing around the X axis (NORTH => DOWN => SOUTH => UP => NORTH)
   */
  private WrappedEnumDirection rotateX() {
    switch (this) {
      case NORTH:
        return DOWN;

      case EAST:
      case WEST:
      default:
        throw new IllegalStateException("Unable to get X-rotated facing of " + this);

      case SOUTH:
        return UP;

      case UP:
        return NORTH;

      case DOWN:
        return SOUTH;
    }
  }

  /**
   * Rotate this Facing around the Z axis (EAST => DOWN => WEST => UP => EAST)
   */
  private WrappedEnumDirection rotateZ() {
    switch (this) {
      case EAST:
        return DOWN;

      case SOUTH:
      default:
        throw new IllegalStateException("Unable to get Z-rotated facing of " + this);

      case WEST:
        return UP;

      case UP:
        return EAST;

      case DOWN:
        return WEST;
    }
  }

  /**
   * Rotate this Facing around the Y axis counter-clockwise (NORTH => WEST => SOUTH => EAST => NORTH)
   */
  public WrappedEnumDirection rotateYCCW() {
    switch (this) {
      case NORTH:
        return WEST;

      case EAST:
        return NORTH;

      case SOUTH:
        return EAST;

      case WEST:
        return SOUTH;

      default:
        throw new IllegalStateException("Unable to get CCW facing of " + this);
    }
  }

  /**
   * Returns a offset that addresses the block in front of this facing.
   */
  public int getFrontOffsetX() {
    return this.axis == WrappedEnumDirection.Axis.X ? this.axisDirection.getOffset() : 0;
  }

  public int getFrontOffsetY() {
    return this.axis == WrappedEnumDirection.Axis.Y ? this.axisDirection.getOffset() : 0;
  }

  /**
   * Returns a offset that addresses the block in front of this facing.
   */
  public int getFrontOffsetZ() {
    return this.axis == WrappedEnumDirection.Axis.Z ? this.axisDirection.getOffset() : 0;
  }

  /**
   * Same as getName, but does not override the method from Enum.
   */
  public String getName2() {
    return this.name;
  }

  public WrappedEnumDirection.Axis getAxis() {
    return this.axis;
  }

  public int getXOffset() {
    return this.axis == Axis.X ? this.axisDirection.getOffset() : 0;
  }

  public int getYOffset() {
    return this.axis == Axis.Y ? this.axisDirection.getOffset() : 0;
  }

  public int getZOffset() {
    return this.axis == Axis.Z ? this.axisDirection.getOffset() : 0;
  }
  /**
   * Get the facing specified by the given name
   */
  public static WrappedEnumDirection byName(String name) {
    return name == null ? null : NAME_LOOKUP.get(name.toLowerCase());
  }

  /**
   * Get a Facing by it's index (0-5). The order is D-U-N-S-W-E. Named getFront for legacy reasons.
   */
  public static WrappedEnumDirection getFront(int index) {
    return VALUES[WrappedMathHelper.abs_int(index % VALUES.length)];
  }

  /**
   * Get a Facing by it's horizontal index (0-3). The order is S-W-N-E.
   */
  public static WrappedEnumDirection getHorizontal(int p_176731_0_) {
    return HORIZONTALS[Math.abs(p_176731_0_ % HORIZONTALS.length)];
  }

  /**
   * Get the Facing corresponding to the given angle (0-360). An angle of 0 is SOUTH, an angle of 90 would be WEST.
   */
  public static WrappedEnumDirection fromAngle(double angle) {
    return getHorizontal(WrappedMathHelper.floor(angle / 90.0D + 0.5D) & 3);
  }

  /**
   * Choose a random Facing using the given Random
   */
  public static WrappedEnumDirection random(Random rand) {
    return values()[rand.nextInt(values().length)];
  }

  public static WrappedEnumDirection getFacingFromVector(float p_176737_0_, float p_176737_1_, float p_176737_2_) {
    WrappedEnumDirection enumfacing = NORTH;
    float f = Float.MIN_VALUE;

    for (WrappedEnumDirection enumfacing1 : values()) {
      float f1 = p_176737_0_ * (float) enumfacing1.directionVec.xCoord + p_176737_1_ * (float) enumfacing1.directionVec.yCoord + p_176737_2_ * (float) enumfacing1.directionVec.zCoord;

      if (f1 > f) {
        f = f1;
        enumfacing = enumfacing1;
      }
    }

    return enumfacing;
  }

  public String toString() {
    return this.name;
  }

  public String getName() {
    return this.name;
  }

  // not the best solution, but it should be obfuscation-compatible
  public EnumWrappers.Direction toDirection() {
    return EnumWrappers.Direction.values()[getIndex()];
  }

  public static WrappedEnumDirection func_181076_a(WrappedEnumDirection.AxisDirection p_181076_0_, WrappedEnumDirection.Axis p_181076_1_) {
    for (WrappedEnumDirection enumfacing : values()) {
      if (enumfacing.getAxisDirection() == p_181076_0_ && enumfacing.getAxis() == p_181076_1_) {
        return enumfacing;
      }
    }

    throw new IllegalArgumentException("No such direction: " + p_181076_0_ + " " + p_181076_1_);
  }

  /**
   * Get a normalized Vector that points in the direction of this Facing.
   */
  public WrappedVector getDirectionVec() {
    return this.directionVec;
  }

  static {
    for (WrappedEnumDirection enumfacing : values()) {
      VALUES[enumfacing.index] = enumfacing;

      if (enumfacing.getAxis().isHorizontal()) {
        HORIZONTALS[enumfacing.horizontalIndex] = enumfacing;
      }

      NAME_LOOKUP.put(enumfacing.getName2().toLowerCase(), enumfacing);
    }
  }

  public enum Axis {
    X("x", WrappedEnumDirection.Plane.HORIZONTAL) {
      public int getCoordinate(int x, int y, int z) {
        return x;
      }

      public double getCoordinate(double x, double y, double z) {
        return x;
      }
    },
    Y("y", WrappedEnumDirection.Plane.VERTICAL) {
      public int getCoordinate(int x, int y, int z) {
        return y;
      }

      public double getCoordinate(double x, double y, double z) {
        return y;
      }
    },
    Z("z", WrappedEnumDirection.Plane.HORIZONTAL) {
      public int getCoordinate(int x, int y, int z) {
        return z;
      }

      public double getCoordinate(double x, double y, double z) {
        return z;
      }
    };

    private static final Map<String, WrappedEnumDirection.Axis> NAME_LOOKUP = Maps.newHashMap();
    private final String name;
    private final WrappedEnumDirection.Plane plane;

    Axis(String name, WrappedEnumDirection.Plane plane) {
      this.name = name;
      this.plane = plane;
    }

    public static WrappedEnumDirection.Axis byName(String name) {
      return name == null ? null : NAME_LOOKUP.get(name.toLowerCase());
    }

    public String getName2() {
      return this.name;
    }

    public boolean isVertical() {
      return this.plane == WrappedEnumDirection.Plane.VERTICAL;
    }

    public boolean isHorizontal() {
      return this.plane == WrappedEnumDirection.Plane.HORIZONTAL;
    }

    public String toString() {
      return this.name;
    }

    public boolean apply(WrappedEnumDirection p_apply_1_) {
      return p_apply_1_ != null && p_apply_1_.getAxis() == this;
    }

    public WrappedEnumDirection.Plane getPlane() {
      return this.plane;
    }

    public String getName() {
      return this.name;
    }

    public abstract int getCoordinate(int x, int y, int z);

    public abstract double getCoordinate(double x, double y, double z);

    static {
      for (WrappedEnumDirection.Axis enumfacing$axis : values()) {
        NAME_LOOKUP.put(enumfacing$axis.getName2().toLowerCase(), enumfacing$axis);
      }
    }
  }

  public enum AxisDirection {
    POSITIVE(1, "Towards positive"),
    NEGATIVE(-1, "Towards negative");

    private final int offset;
    private final String description;

    AxisDirection(int offset, String description) {
      this.offset = offset;
      this.description = description;
    }

    public int getOffset() {
      return this.offset;
    }

    public String toString() {
      return this.description;
    }
  }

  public enum Plane implements Predicate<WrappedEnumDirection>, Iterable<WrappedEnumDirection> {
    HORIZONTAL,
    VERTICAL;

    public WrappedEnumDirection[] facings() {
      switch (this) {
        case HORIZONTAL:
          return new WrappedEnumDirection[]{WrappedEnumDirection.NORTH, WrappedEnumDirection.EAST, WrappedEnumDirection.SOUTH, WrappedEnumDirection.WEST};
        case VERTICAL:
          return new WrappedEnumDirection[]{WrappedEnumDirection.UP, WrappedEnumDirection.DOWN};
        default:
          throw new Error("Someone's been tampering with the universe!");
      }
    }

    public WrappedEnumDirection random(Random rand) {
      WrappedEnumDirection[] aenumfacing = this.facings();
      return aenumfacing[rand.nextInt(aenumfacing.length)];
    }

    public boolean apply(WrappedEnumDirection p_apply_1_) {
      return p_apply_1_ != null && p_apply_1_.getAxis().getPlane() == this;
    }

    public Iterator<WrappedEnumDirection> iterator() {
      return Iterators.forArray(this.facings());
    }
  }
}