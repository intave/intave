package de.jpx3.intave.diagnostic;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.world.blockshape.BlockShape;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class MemoryTraced {
  private final static Map<Class<?>, AtomicLong> objectsLoaded = new ConcurrentHashMap<>();
  private final static Map<Class<?>, Integer> BYTES = new HashMap<>();

  static {
    BYTES.put(WrappedAxisAlignedBB.class, Double.BYTES * 6);
    BYTES.put(BlockShape.class, Double.BYTES * 6 + Integer.BYTES * 3 + Long.BYTES);
  }

  public MemoryTraced() {
    if (IntaveControl.PERFORMANCE_RECORD) {
      objectsLoaded.computeIfAbsent(getClass(), aClass -> new AtomicLong()).incrementAndGet();
    }
  }

  public static Map<Class<?>, AtomicLong> tracedClasses() {
    return objectsLoaded;
  }

  public static Map<Class<?>, Long> memoryUsage() {
    Map<Class<?>, Long> memoryUsage = new HashMap<>();
    for (Map.Entry<Class<?>, AtomicLong> classAtomicLongEntry : objectsLoaded.entrySet()) {
      Class<?> key = classAtomicLongEntry.getKey();
      memoryUsage.put(key, classAtomicLongEntry.getValue().get() * BYTES.get(key));
    }
    return memoryUsage;
  }

  @Override
  protected void finalize() throws Throwable {
    if (IntaveControl.PERFORMANCE_RECORD) {
      objectsLoaded.computeIfAbsent(getClass(), aClass -> new AtomicLong()).decrementAndGet();
    }
  }
}
