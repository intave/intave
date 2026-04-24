package de.jpx3.intave.share;

import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public enum Relative {
  X(0),
  Y(1),
  Z(2),
  Y_ROT(3),
  X_ROT(4),
  DELTA_X(5),
  DELTA_Y(6),
  DELTA_Z(7),
  ROTATE_DELTA(8);

  public static final Set<Relative> ALL_RELATIVE = new HashSet<>(Arrays.asList(values()));
  public static final Set<Relative> RELATIVE_POSITION = new HashSet<>(Arrays.asList(X, Y, Z));
  public static final Set<Relative> RELATIVE_ROTATION = new HashSet<>(Arrays.asList(Y_ROT, X_ROT));
  public static final Set<Relative> RELATIVE_MOTION = new HashSet<>(Arrays.asList(DELTA_X, DELTA_Y, DELTA_Z, ROTATE_DELTA));

  private static final Map<Integer, Set<Relative>> flagCache = Maps.newConcurrentMap();

  private final int slot;

  Relative(int slot) {
    this.slot = slot;
  }

  public static Set<Relative> setOfAllFlags() {
    return fromIndex(0b11111);
  }

  public static Set<Relative> noMovementChange() {
    return fromIndex(0b00111);
  }

  public static Set<Relative> noRotationChange() {
    return fromIndex(0b11000);
  }

  public static Set<Relative> fromSet(Set<Relative> flags) {
    return fromIndex(indexFor(flags));
  }

  private static int indexFor(Set<Relative> flags) {
    int index = 0;
    for (Relative flag : flags) {
      index |= flag.index();
    }
    return index;
  }

  private int index() {
    return 1 << slot;
  }

  public static Set<Relative> fromIndex(int index) {
    return flagCache.computeIfAbsent(index, integer -> {
      EnumSet<Relative> flags = EnumSet.noneOf(Relative.class);
      for (Relative flag : values()) {
        if ((integer & flag.index()) != 0) {
          flags.add(flag);
        }
      }
      return flags;
    });
  }
}
