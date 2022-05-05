package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class EntityMoveEvent extends Event {
  private int entityId;
  private double x;
  private double y;
  private double z;
  private float yaw;
  private float pitch;
  private boolean inSight;

  public EntityMoveEvent() {}

  public EntityMoveEvent(int entityId, double x, double y, double z, float yaw, float pitch) {
    this.entityId = entityId;
    this.x = x;
    this.y = y;
    this.z = z;
    this.yaw = yaw;
    this.pitch = pitch;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(entityId);
    out.writeDouble(x);
    out.writeDouble(y);
    out.writeDouble(z);
    out.writeFloat(yaw);
    out.writeFloat(pitch);
    out.writeBoolean(inSight);
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    entityId = in.readInt();
    x = in.readDouble();
    y = in.readDouble();
    z = in.readDouble();
    yaw = in.readFloat();
    pitch = in.readFloat();
    inSight = in.readBoolean();
  }

  @Override
  public void accept(EventSink sink) {
    sink.on(this);
  }
}
