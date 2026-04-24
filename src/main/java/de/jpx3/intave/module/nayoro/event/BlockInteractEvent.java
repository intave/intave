package de.jpx3.intave.module.nayoro.event;

import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class BlockInteractEvent extends Event {
  private Position blockPosition;
  private Rotation rotation;
  private boolean failedBlockPlacement;

  private InteractionHand hand;
  private String blockType;
  private String itemInHand;
  private int amountInHand;

  public BlockInteractEvent() {
  }

  public BlockInteractEvent(
    Position blockPosition,
    Rotation rotation,
    boolean failedBlockPlacement,
    InteractionHand hand,
    String blockType,
    String itemInHand,
    int amountInHand
  ) {
    this.blockPosition = blockPosition;
    this.rotation = rotation;
    this.failedBlockPlacement = failedBlockPlacement;
    this.hand = hand;
    this.blockType = blockType;
    this.itemInHand = itemInHand;
    this.amountInHand = amountInHand;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    writeBlockPosition(out, blockPosition);
    out.writeFloat(rotation.yaw());
    out.writeFloat(rotation.pitch());
    out.writeBoolean(failedBlockPlacement);
    out.writeByte(hand.ordinal());
    out.writeUTF(blockType);
    out.writeUTF(itemInHand);
    out.writeInt(amountInHand);
  }

  private static void writeBlockPosition(DataOutput out, Position position) throws IOException {
    out.writeInt(position.getBlockX());
    out.writeInt(position.getBlockY());
    out.writeInt(position.getBlockZ());
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    blockPosition = readBlockPosition(in);
    rotation = new Rotation(in.readFloat(), in.readFloat());
    failedBlockPlacement = in.readBoolean();
    hand = InteractionHand.values()[in.readByte()];
    blockType = in.readUTF();
    itemInHand = in.readUTF();
    amountInHand = in.readInt();
  }

  private static Position readBlockPosition(DataInput in) throws IOException {
    return new Position(in.readInt(), in.readInt(), in.readInt());
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public static BlockInteractEvent create(
    Position blockPosition, Rotation rotation, boolean failedBlockPlacement,
    InteractionHand hand,
    String blockType, String itemInHand, int amountInHand
  ) {
    return new BlockInteractEvent(blockPosition, rotation, failedBlockPlacement, hand, blockType, itemInHand, amountInHand);
  }
}
