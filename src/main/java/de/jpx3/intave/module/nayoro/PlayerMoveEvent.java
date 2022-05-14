package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class PlayerMoveEvent extends Event {
  private int flags;
  private double x;
  private double y;
  private double z;
  private float yaw;
  private float pitch;

  public PlayerMoveEvent() {
  }

  public PlayerMoveEvent(double x, double y, double z, float yaw, float pitch) {
    this.flags = -1;
    this.x = x;
    this.y = y;
    this.z = z;
    this.yaw = yaw;
    this.pitch = pitch;
  }

  private final static double EPSILON = 1.0E-09;

  public PlayerMoveEvent(
    double lastX, double lastY, double lastZ,
    double x, double y, double z,
    float lastYaw, float lastPitch,
    float yaw, float pitch
  ) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.yaw = yaw;
    this.pitch = pitch;
    int flags = 0;
    if (Math.abs(x - lastX) >= EPSILON) {
      flags |= Flag.X;
    }
    if (Math.abs(y - lastY) >= EPSILON) {
      flags |= Flag.Y;
    }
    if (Math.abs(z - lastZ) >= EPSILON) {
      flags |= Flag.Z;
    }
    if (Math.abs(yaw - lastYaw) >= EPSILON) {
      flags |= Flag.YAW;
    }
    if (Math.abs(pitch - lastPitch) >= EPSILON) {
      flags |= Flag.PITCH;
    }
    this.flags = flags;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeByte(flags);
    conditionalWriteDouble(out, x, Flag.X);
    conditionalWriteDouble(out, y, Flag.Y);
    conditionalWriteDouble(out, z, Flag.Z);
    conditionalWriteFloat(out, yaw, Flag.YAW);
    conditionalWriteFloat(out, pitch, Flag.PITCH);
  }

  private void conditionalWriteDouble(DataOutput out, double value, int flag) throws IOException {
    if ((flags & flag) != 0) {
      out.writeDouble(value);
    }
  }

  private void conditionalWriteFloat(DataOutput out, float value, int flag) throws IOException {
    if ((flags & flag) != 0) {
      out.writeFloat(value);
    }
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    flags = in.readByte();
    x = conditionalReadDouble(in, Flag.X);
    y = conditionalReadDouble(in, Flag.Y);
    z = conditionalReadDouble(in, Flag.Z);
    yaw = conditionalReadFloat(in, Flag.YAW);
    pitch = conditionalReadFloat(in, Flag.PITCH);
  }

  private double conditionalReadDouble(DataInput in, int flag) throws IOException {
    if ((flags & flag) != 0) {
      return in.readDouble();
    } else {
      return 0;
    }
  }

  private float conditionalReadFloat(DataInput in, int flag) throws IOException {
    if ((flags & flag) != 0) {
      return in.readFloat();
    } else {
      return 0;
    }
  }
  public double x() {
    return x;
  }

  public boolean applyX() {
    return (flags & Flag.X) != 0;
  }

  public double y() {
    return y;
  }

  public boolean applyY() {
    return (flags & Flag.Y) != 0;
  }

  public double z() {
    return z;
  }

  public boolean applyZ() {
    return (flags & Flag.Z) != 0;
  }

  public float yaw() {
    return yaw;
  }

  public boolean applyYaw() {
    return (flags & Flag.YAW) != 0;
  }

  public float pitch() {
    return pitch;
  }

  public boolean applyPitch() {
    return (flags & Flag.PITCH) != 0;
  }

  @Override
  public String toString() {
    return "PlayerMoveEvent{" +
      "flags=" + flags +
      ", x=" + x +
      ", y=" + y +
      ", z=" + z +
      ", yaw=" + yaw +
      ", pitch=" + pitch +
      '}';
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public static PlayerMoveEvent create(double x, double y, double z, float yaw, float pitch) {
    return new PlayerMoveEvent(x, y, z, yaw, pitch);
  }

  public static PlayerMoveEvent create(
    double lastX, double lastY, double lastZ,
    double x, double y, double z,
    float lastYaw, float lastPitch,
    float yaw, float pitch
  ) {
    return new PlayerMoveEvent(
      lastX, lastY, lastZ,
      x, y, z,
      lastYaw, lastPitch,
      yaw, pitch
    );
  }

  private static class Flag {
    public static int X = 1;
    public static int Y = 1 << 1;
    public static int Z = 1 << 2;
    public static int YAW = 1 << 3;
    public static int PITCH = 1 << 4;
  }
}
