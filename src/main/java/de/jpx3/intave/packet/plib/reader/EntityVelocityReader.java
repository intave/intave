/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.plib.reader;

import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Unmodifiable;
import de.jpx3.intave.share.Motion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class EntityVelocityReader extends EntityReader {
  public double motionX() {
    return hasNativeVector() ? nativeAxis(nativeVector(), Native.X)
      : packet().getIntegers().read(1) / 8000.0D;
  }

  public double motionY() {
    return hasNativeVector() ? nativeAxis(nativeVector(), Native.Y)
      : packet().getIntegers().read(2) / 8000.0D;
  }

  public double motionZ() {
    return hasNativeVector() ? nativeAxis(nativeVector(), Native.Z)
      : packet().getIntegers().read(3) / 8000.0D;
  }

  public @Unmodifiable Motion motion() {
    if (hasNativeVector()) {
      Object vector = nativeVector();
      return new Motion(
        nativeAxis(vector, Native.X),
        nativeAxis(vector, Native.Y),
        nativeAxis(vector, Native.Z)
      );
    }
    StructureModifier<Integer> integers = packet().getIntegers();
    return new Motion(
      integers.read(1) / 8000.0D,
      integers.read(2) / 8000.0D,
      integers.read(3) / 8000.0D
    );
  }

  public void setMotionX(double motionX) {
    if (hasNativeVector()) {
      Motion motion = motion();
      motion.setMotionX(motionX);
      setMotion(motion);
      return;
    }
    packet().getIntegers().writeSafely(1, (int)(motionX * 8000.0D));
  }

  public void setMotionY(double motionY) {
    if (hasNativeVector()) {
      Motion motion = motion();
      motion.setMotionY(motionY);
      setMotion(motion);
      return;
    }
    packet().getIntegers().writeSafely(2, (int)(motionY * 8000.0D));
  }

  public void setMotionZ(double motionZ) {
    if (hasNativeVector()) {
      Motion motion = motion();
      motion.setMotionZ(motionZ);
      setMotion(motion);
      return;
    }
    packet().getIntegers().writeSafely(3, (int)(motionZ * 8000.0D));
  }

  public void setMotion(Motion motion) {
    if (hasNativeVector()) {
      writeNative(motion);
      return;
    }
    StructureModifier<Integer> integers = packet().getIntegers();
    integers.writeSafely(1, (int)(motion.motionX() * 8000.0D));
    integers.writeSafely(2, (int)(motion.motionY() * 8000.0D));
    integers.writeSafely(3, (int)(motion.motionZ() * 8000.0D));
  }

  private boolean hasNativeVector() {
    return MinecraftVersions.VER26_2.atOrAbove() || packet().getIntegers().size() < 4;
  }

  private Object nativeVector() {
    try {
      return Native.MOVEMENT.invoke(packet().getHandle());
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot read native entity velocity", exception);
    }
  }

  private static double nativeAxis(Object vector, Field field) {
    try {
      return field.getDouble(vector);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Cannot read native entity velocity axis", exception);
    }
  }

  private void writeNative(Motion motion) {
    try {
      Object vector = Native.CONSTRUCTOR.newInstance(motion.motionX(), motion.motionY(), motion.motionZ());
      packet().getModifier().withType(Native.TYPE).write(0, vector);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot write native entity velocity", exception);
    }
  }

  private static final class Native {
    private static final Class<?> TYPE;
    private static final Constructor<?> CONSTRUCTOR;
    private static final Method MOVEMENT;
    private static final Field X;
    private static final Field Y;
    private static final Field Z;

    static {
      try {
        TYPE = Class.forName("net.minecraft.world.phys.Vec3");
        CONSTRUCTOR = TYPE.getConstructor(double.class, double.class, double.class);
        Class<?> packetType = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket");
        Method movement;
        try {
          movement = packetType.getMethod("movement");
        } catch (NoSuchMethodException exception) {
          movement = packetType.getMethod("getMovement");
        }
        MOVEMENT = movement;
        X = TYPE.getField("x");
        Y = TYPE.getField("y");
        Z = TYPE.getField("z");
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Cannot resolve native entity velocity packet", exception);
      }
    }
  }
}
