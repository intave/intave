package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.share.Motion;
import org.bukkit.util.Vector;

public class EntityVelocityReader extends EntityReader {
  public double motionX() {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      return vector.getX();
    }
    return packet().getIntegers().read(1) / 8000.0D;
  }

  public double motionY() {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      return vector.getY();
    }
    return packet().getIntegers().read(2) / 8000.0D;
  }

  public double motionZ() {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      return vector.getZ();
    }
    return packet().getIntegers().read(3) / 8000.0D;
  }

  public Motion motion() {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      return Motion.fromVector(vector);
    }
    StructureModifier<Integer> integers = packet().getIntegers();
    return new Motion(
      integers.read(1) / 8000.0D,
      integers.read(2) / 8000.0D,
      integers.read(3) / 8000.0D
    );
  }

  public void setMotionX(double motionX) {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      packet().getVectors().writeSafely(0, new Vector(motionX, vector.getY(), vector.getZ()));
      return;
    }
    packet().getIntegers().writeSafely(1, (int)(motionX * 8000.0D));
  }

  public void setMotionY(double motionY) {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      packet().getVectors().writeSafely(0, new Vector(vector.getX(), motionY, vector.getZ()));
      return;
    }
    packet().getIntegers().writeSafely(2, (int)(motionY * 8000.0D));
  }

  public void setMotionZ(double motionZ) {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      packet().getVectors().writeSafely(0, new Vector(vector.getX(), vector.getY(), motionZ));
      return;
    }
    packet().getIntegers().writeSafely(3, (int)(motionZ * 8000.0D));
  }

  public void setMotion(Motion motion) {
    Vector vector = packet().getVectors().readSafely(0);
    if (vector != null) {
      packet().getVectors().writeSafely(0, motion.toBukkitVector());
      return;
    }
    StructureModifier<Integer> integers = packet().getIntegers();
    integers.writeSafely(1, (int)(motion.motionX() * 8000.0D));
    integers.writeSafely(2, (int)(motion.motionY() * 8000.0D));
    integers.writeSafely(3, (int)(motion.motionZ() * 8000.0D));
  }
}
