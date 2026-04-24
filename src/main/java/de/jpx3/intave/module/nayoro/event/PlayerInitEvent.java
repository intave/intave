package de.jpx3.intave.module.nayoro.event;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.PlayerContainer;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.version.ProtocolVersionConverter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class PlayerInitEvent extends Event {
  private static final int CURRENT_SERVER_VERSION = ProtocolVersionConverter.protocolVersionBy(MinecraftVersion.getCurrentVersion());

  private int id;
  private int clientVersion;
  private int serverVersion;
  private Position position;
  private Rotation rotation;

  public PlayerInitEvent() {
  }

  public PlayerInitEvent(PlayerContainer player) {
    this(
      player.id(),
      player.version(),
      CURRENT_SERVER_VERSION,
      player.position(),
      player.rotation()
    );
  }

  public PlayerInitEvent(int id, int clientVersion, int serverVersion, Position position, Rotation rotation) {
    this.id = id;
    this.clientVersion = clientVersion;
    this.serverVersion = serverVersion;
    this.position = position;
    this.rotation = rotation;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(id);
    out.writeInt(clientVersion);
    out.writeInt(serverVersion);
    out.writeDouble(position.getX());
    out.writeDouble(position.getY());
    out.writeDouble(position.getZ());
    out.writeFloat(rotation.yaw());
    out.writeFloat(rotation.pitch());
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    id = in.readInt();
    clientVersion = in.readInt();
    serverVersion = in.readInt();
    position = new Position(in.readDouble(), in.readDouble(), in.readDouble());
    rotation = new Rotation(in.readFloat(), in.readFloat());
  }

  public int id() {
    return id;
  }

  public int clientVersion() {
    return clientVersion;
  }

  public int serverVersion() {
    return serverVersion;
  }

  public Position position() {
    return position;
  }

  public Rotation rotation() {
    return rotation;
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }
}
