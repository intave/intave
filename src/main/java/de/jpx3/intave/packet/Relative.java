package de.jpx3.intave.packet;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Maps;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.Locate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
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

  private final int slot;

  Relative(int slot) {
    this.slot = slot;
  }

  private int index() {
    return 1 << this.slot;
  }

  private boolean matchesIndex(int var1) {
    return (var1 & this.index()) == this.index();
  }

  private static int indexOf(Set<Relative> var0) {
    Relative var3;
    int var1 = 0;
    for (Relative flag : var0) {
      var3 = flag;
      var1 |= var3.index();
    }
    return var1;
  }

  private static final Class<?> nativeClass = Lookup.serverClass("PacketPlayOutPosition$EnumPlayerTeleportFlags");

  public static Set<?> nativeSetOfAllFlags() {
    return nativeFromIndex(0b11111);
  }

  public static Set<?> nativeSetOfMovementChange() {
    return nativeFromIndex(0b00111);
  }

  public static Set<?> nativeSetOfNoRotationChange() {
    return nativeFromIndex(0b11000);
  }

  public static Set<?> fromSet(Set<Relative> flags) {
    return nativeFromIndex(indexOf(flags));
  }

  private static EquivalentConverter<Relative> genericConverter;

  public static Set<Relative> flagsFrom(PacketContainer packet) {
    if (genericConverter == null) {
      genericConverter = EnumWrappers.getGenericConverter(nativeClass, Relative.class);
    }
    return packet.getSets(genericConverter).read(0);
  }

  public static void writeFlags(PacketContainer packet, Set<Relative> flags) {
    if (genericConverter == null) {
      genericConverter = EnumWrappers.getGenericConverter(nativeClass, Relative.class);
    }
    packet.getSets(genericConverter).write(0, flags);
  }

  private static final Map<Integer, Set<?>> flagCache = Maps.newConcurrentMap();
  private static final Method resolverMethod;

  static {
    resolverMethod = Locate.methodByKey(
      "PacketPlayOutPosition$EnumPlayerTeleportFlags",
      "unpack(I)Ljava/util/Set;"
    );
  }

  public static Set<?> nativeFromIndex(int index) {
    return flagCache.computeIfAbsent(index, integer -> {
      try {
        return (Set<?>) resolverMethod.invoke(null, index);
      } catch (InvocationTargetException | IllegalAccessException exception) {
        throw new IllegalStateException("Something is wrong");
      }
    });
  }
}
