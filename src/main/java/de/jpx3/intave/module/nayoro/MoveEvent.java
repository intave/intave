package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class MoveEvent extends Event {
  private double x;
  private double y;
  private double z;
  private float yaw;
  private float pitch;

  public MoveEvent() {
  }

  public MoveEvent(double x, double y, double z, float yaw, float pitch) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.yaw = yaw;
    this.pitch = pitch;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeDouble(x);
    out.writeDouble(y);
    out.writeDouble(z);
    out.writeFloat(yaw);
    out.writeFloat(pitch);
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    x = in.readDouble();
    y = in.readDouble();
    z = in.readDouble();
    yaw = in.readFloat();
    pitch = in.readFloat();
  }

  @Override
  public void accept(EventSink sink) {
    sink.on(this);
  }

  public static MoveEvent create(double x, double y, double z, double yaw, double pitch) {
    return new MoveEvent(x, y, z, (float) yaw, (float) pitch);
  }
}
