package de.jpx3.intave.module.nayoro.event;

import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.share.Position;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class EntitySpawnEvent extends Event {
  private int id;
  private HitboxSize size;
  private Position position;

  public EntitySpawnEvent() {
  }

  public EntitySpawnEvent(int id, HitboxSize size, Position position) {
    this.id = id;
    this.size = size;
    this.position = position;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(id);
    out.writeFloat(size.width());
    out.writeFloat(size.height());
    out.writeDouble(position.getX());
    out.writeDouble(position.getY());
    out.writeDouble(position.getZ());
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    id = in.readInt();
    size = HitboxSize.of(in.readFloat(), in.readFloat());
    position = new Position(in.readDouble(), in.readDouble(), in.readDouble());
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public int id() {
    return id;
  }

  public HitboxSize size() {
    return size;
  }

  public Position position() {
    return position;
  }
}
