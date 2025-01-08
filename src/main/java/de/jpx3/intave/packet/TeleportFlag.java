package de.jpx3.intave.packet;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Maps;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.klass.Lookup;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

@KeepEnumInternalNames
public enum TeleportFlag {
  X(0),
  Y(1),
  Z(2),
  Y_ROT(3),
  X_ROT(4),
  DELTA_X(5),
  DELTA_Y(6),
  DELTA_Z(7),
  ROTATE_DELTA(8);

  private final int slot;

  TeleportFlag(int slot) {
    this.slot = slot;
  }

  private int index() {
    return 1 << this.slot;
  }

  private boolean matchesIndex(int var1) {
    return (var1 & this.index()) == this.index();
  }

  private static int indexFor(Set<TeleportFlag> var0) {
    TeleportFlag var3;
    int var1 = 0;
    for (TeleportFlag flag : var0) {
      var3 = flag;
      var1 |= var3.index();
    }
    return var1;
  }

  private static final Class<?> nativeClass = Lookup.serverClass("PacketPlayOutPosition$EnumPlayerTeleportFlags");

  public static Set<?> setOfAllFlags() {
    return fromIndex(0b11111);
  }

  public static Set<?> noMovementChange() {
    return fromIndex(0b00111);
  }

  public static Set<?> noRotationChange() {
    return fromIndex(0b11000);
  }

  public static Set<?> fromSet(Set<TeleportFlag> flags) {
    return fromIndex(indexFor(flags));
  }

  public static Set<TeleportFlag> flagsFrom(PacketContainer packet) {
    return packet.getSets(EnumWrappers.getGenericConverter(nativeClass, TeleportFlag.class)).read(0);
  }

  public static void writeFlags(PacketContainer packet, Set<TeleportFlag> flags) {
    packet.getSets(EnumWrappers.getGenericConverter(nativeClass, TeleportFlag.class)).write(0, flags);
  }

  private static final Map<Integer, Set<?>> flagCache = Maps.newConcurrentMap();
  private static final Method resolverMethod;

  static {
    try {
      resolverMethod = nativeClass.getMethod("a", Integer.TYPE);
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static Set<?> fromIndex(int index) {
    return flagCache.computeIfAbsent(index, integer -> {
      try {
        return (Set<?>) resolverMethod.invoke(null, index);
      } catch (InvocationTargetException | IllegalAccessException exception) {
        throw new IllegalStateException("Something is wrong");
      }
    });
  }
}
