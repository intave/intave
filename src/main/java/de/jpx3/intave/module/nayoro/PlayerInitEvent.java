package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class PlayerInitEvent extends Event {
  private int id;
  private int version;
  private boolean outdated;

  public PlayerInitEvent() {
  }

  public PlayerInitEvent(PlayerContainer player) {
    this(player.id(), player.version(), player.outdatedClient());
  }

  public PlayerInitEvent(int id, int version, boolean outdated) {
    this.id = id;
    this.version = version;
    this.outdated = outdated;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(id);
    out.writeInt(version);
    out.writeBoolean(outdated);
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    id = in.readInt();
    version = in.readInt();
    outdated = in.readBoolean();
  }

  public int id() {
    return id;
  }

  public int version() {
    return version;
  }

  public boolean outdated() {
    return outdated;
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }
}
