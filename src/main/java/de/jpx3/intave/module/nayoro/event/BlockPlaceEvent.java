package de.jpx3.intave.module.nayoro.event;

import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class BlockPlaceEvent extends Event {
  private Position placedBlock;
  @Nullable
  private Position againstBlock;

  private Direction direction;

  private Rotation rotation;

  // not always correct due to flying packets
  private Position eyePosition;
  private Position endOfRaytrace;

  // not always correct, can also be 0.0 or NaN
  private float facingX;
  private float facingY;
  private float facingZ;

  private InteractionHand hand;
  private String typeName;
  private int amountInHand;

  public BlockPlaceEvent() {
  }

  public BlockPlaceEvent(
    Position placedBlock,
    @Nullable Position againstBlock,
    Direction direction,
    Rotation rotation,
    Position eyePosition,
    Position endOfRaytrace,
    InteractionHand hand,
    String typeName,
    int amountInHand,
    float facingX,
    float facingY,
    float facingZ
  ) {
    this.placedBlock = placedBlock;
    this.againstBlock = againstBlock;
    this.direction = direction;
    this.rotation = rotation;
    this.eyePosition = eyePosition;
    this.endOfRaytrace = endOfRaytrace;
    this.hand = hand;
    this.typeName = typeName;
    this.amountInHand = amountInHand;
    this.facingX = facingX;
    this.facingY = facingY;
    this.facingZ = facingZ;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    writeBlockPosition(out, placedBlock);
    if (againstBlock == null) {
      out.writeBoolean(false);
    } else {
      out.writeBoolean(true);
      writeBlockPosition(out, againstBlock);
    }
    out.writeByte(direction.ordinal());
    out.writeFloat(rotation.yaw());
    out.writeFloat(rotation.pitch());
    writePosition(out, eyePosition);
    writePosition(out, endOfRaytrace);
    out.writeByte(hand.ordinal());
    out.writeUTF(typeName);
    out.writeInt(amountInHand);
    out.writeFloat(facingX);
    out.writeFloat(facingY);
    out.writeFloat(facingZ);
  }

  private void writePosition(DataOutput out, Position position) throws IOException {
    out.writeDouble(position.getX());
    out.writeDouble(position.getY());
    out.writeDouble(position.getZ());
  }

  private void writeBlockPosition(DataOutput out, Position position) throws IOException {
    out.writeInt(position.getBlockX());
    out.writeInt(position.getBlockY());
    out.writeInt(position.getBlockZ());
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    placedBlock = readBlockPosition(in);
    if (in.readBoolean()) {
      againstBlock = readBlockPosition(in);
    } else {
      againstBlock = null;
    }
    direction = Direction.values()[in.readByte()];
    rotation = new Rotation(in.readFloat(), in.readFloat());
    eyePosition = readPosition(in);
    endOfRaytrace = readPosition(in);
    hand = InteractionHand.values()[in.readByte()];
    typeName = in.readUTF();
    amountInHand = in.readInt();
    facingX = in.readFloat();
    facingY = in.readFloat();
    facingZ = in.readFloat();
  }

  private Position readPosition(DataInput in) throws IOException {
    return new Position(in.readDouble(), in.readDouble(), in.readDouble());
  }

  private Position readBlockPosition(DataInput in) throws IOException {
    return new Position(in.readInt(), in.readInt(), in.readInt());
  }

  public Position placedBlock() {
    return placedBlock;
  }

  @Nullable
  public Position againstBlock() {
    return againstBlock;
  }

  public Direction direction() {
    return direction;
  }

  public Rotation rotation() {
    return rotation;
  }

  public Position eyePosition() {
    return eyePosition;
  }

  public Position endOfRaytrace() {
    return endOfRaytrace;
  }

  public InteractionHand hand() {
    return hand;
  }

  public String typeName() {
    return typeName;
  }

  public int amountInHand() {
    return amountInHand;
  }

  public float facingX() {
    return facingX;
  }

  public float facingY() {
    return facingY;
  }

  public float facingZ() {
    return facingZ;
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public static BlockPlaceEvent create(
    Position placedBlock,
    @Nullable Position againstBlock,
    Direction direction,
    Rotation rotation,
    Position eyePosition,
    Position endOfRaytrace,
    InteractionHand hand,
    String typeName,
    int amountInHand,
    float facingX,
    float facingY,
    float facingZ
  ) {
    return new BlockPlaceEvent(
      placedBlock,
      againstBlock,
      direction,
      rotation,
      eyePosition,
      endOfRaytrace,
      hand,
      typeName,
      amountInHand,
      facingX,
      facingY,
      facingZ
    );
  }
}
