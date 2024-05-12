package de.jpx3.intave.packet.reader;

public class EntityVelocityReader extends EntityReader {
  public double motionX() {
    return packet().getIntegers().read(1) / 8000.0D;
  }

  public double motionY() {
    return packet().getIntegers().read(2) / 8000.0D;
  }

  public double motionZ() {
    return packet().getIntegers().read(3) / 8000.0D;
  }

  public void setMotionX(double motionX) {
    packet().getIntegers().writeSafely(1, (int)(motionX * 8000.0D));
  }

  public void setMotionY(double motionY) {
    packet().getIntegers().writeSafely(2, (int)(motionY * 8000.0D));
  }

  public void setMotionZ(double motionZ) {
    packet().getIntegers().writeSafely(3, (int)(motionZ * 8000.0D));
  }
}
