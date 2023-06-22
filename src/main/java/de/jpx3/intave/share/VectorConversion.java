package de.jpx3.intave.share;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class VectorConversion {
//  public static <T> T createFrom(Class<T> clazz, Object initial) {
//
//  }

  private void naiveIntExtract(Object vector) {
    int a, b, c;
    Field[] fields = vector.getClass().getFields();
    for (Field field : fields) {
      field.setAccessible(true);
    }
    try {
      a = fields[0].getInt(vector);
      b = fields[1].getInt(vector);
      c = fields[2].getInt(vector);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unchecked")
  public static <F, T> T copyInto(F from, T to) {
    Class<F> fromClass = (Class<F>) from.getClass();
    Class<T> toClass = (Class<T>) to.getClass();
    naiveDoubleCopyInto(from, to);
    return to;
  }

  private static void naiveDoubleCopyInto(Object from, Object to) {
    double a = 0, b = 0, c = 0;
    Field[] fieldsFrom = from.getClass().getFields();
    for (Field field : fieldsFrom) {
      if (!field.isAccessible()) {
        field.setAccessible(true);
      }
    }
    try {
      a = fieldsFrom[0].getDouble(from);
      b = fieldsFrom[1].getDouble(from);
      c = fieldsFrom[2].getDouble(from);
    } catch (Exception e) {
      e.printStackTrace();
    }
    Field[] fieldsTo = to.getClass().getFields();
    for (Field field : fieldsTo) {
      if (!field.isAccessible()) {
        field.setAccessible(true);
      }
    }
    try {
      fieldsTo[0].setDouble(to, a);
      fieldsTo[1].setDouble(to, b);
      fieldsTo[2].setDouble(to, c);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static final Unsafe UNSAFE;

  static {
    Unsafe unsafe = null;
    try {
      Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafe.setAccessible(true);
      unsafe = (Unsafe) theUnsafe.get(null);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    UNSAFE = unsafe;
  }

  private static final long fromXOffset;

  static {
    try {
      fromXOffset = UNSAFE.objectFieldOffset(Vektor.class.getDeclaredField("x"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static final long fromYOffset;

  static {
    try {
      fromYOffset = UNSAFE.objectFieldOffset(Vektor.class.getDeclaredField("y"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static final long fromZOffset;

  static {
    try {
      fromZOffset = UNSAFE.objectFieldOffset(Vektor.class.getDeclaredField("z"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static final long toXOffset;

  static {
    try {
      toXOffset = UNSAFE.objectFieldOffset(Motion.class.getDeclaredField("motionX"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static final long toYOffset;

  static {
    try {
      toYOffset = UNSAFE.objectFieldOffset(Motion.class.getDeclaredField("motionY"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static final long toZOffset;

  static {
    try {
      toZOffset = UNSAFE.objectFieldOffset(Motion.class.getDeclaredField("motionZ"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static void unsafeCopyInto(Object from, Object to) {
    UNSAFE.putDouble(to, toXOffset, UNSAFE.getDouble(from, fromXOffset));
    UNSAFE.putDouble(to, toYOffset, UNSAFE.getDouble(from, fromYOffset));
    UNSAFE.putDouble(to, toZOffset, UNSAFE.getDouble(from, fromZOffset));
  }

  private static void best(Vektor from, Motion to) {
    to.motionX = from.x;
    to.motionY = from.y;
    to.motionZ = from.z;
  }

  public static void main(String[] args) {
    Vektor from = new Vektor(0, 0.42, 0);
    Motion to = new Motion();

    for (int i = 0; i < 5; i++) {
      for (int h = 0; h < 1000; h++) {
        unsafeCopyInto(from, to);
//        VectorConversion.copyInto(from, to);
        best(from, to);
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      System.out.println("" + (i + 1) + "/5");
    }

    long start = System.nanoTime();
    for (int i = 0; i < 100000; i++) {
      unsafeCopyInto(from, to);
//      VectorConversion.copyInto(from, to);
//      best(from, to);
    }
    long end = System.nanoTime();
    System.out.println("Took " + ((end - start) / 100000L) + " nano seconds");
    System.out.println(to);
  }

  public static class Vektor {
    public double x, y, z;
    public Vektor(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }
}
