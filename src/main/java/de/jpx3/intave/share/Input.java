package de.jpx3.intave.share;

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class Input {
  public static final StreamCodec<ByteBuf, ByteBuf, Input> STREAM_CODEC = StreamCodec.of(
    (buf, value) -> {
      byte flags = 0;
      flags = (byte)(flags | (value.forward() ? 1 : 0));
      flags = (byte)(flags | (value.backward() ? 2 : 0));
      flags = (byte)(flags | (value.left() ? 4 : 0));
      flags = (byte)(flags | (value.right() ? 8 : 0));
      flags = (byte)(flags | (value.jump() ? 16 : 0));
      flags = (byte)(flags | (value.sneaking() ? 32 : 0));
      flags = (byte)(flags | (value.sprint() ? 64 : 0));
      buf.writeByte(flags);
    },
    buf -> {
      byte flags = buf.readByte();
      boolean forward = (flags & 1) != 0;
      boolean backward = (flags & 2) != 0;
      boolean left = (flags & 4) != 0;
      boolean right = (flags & 8) != 0;
      boolean jump = (flags & 0x10) != 0;
      boolean shift = (flags & 0x20) != 0;
      boolean sprint = (flags & 0x40) != 0;
      return new Input(forward, backward, left, right, jump, shift, sprint);
    }
  );

  private boolean forward;
  private boolean backward;
  private boolean left;
  private boolean right;
  private boolean jump;
  private boolean shift;
  private boolean sprint;

  public Input(
    boolean forward, boolean backward,
    boolean left, boolean right,
    boolean jump, boolean shift, boolean sprint
  ) {
    this.forward = forward;
    this.backward = backward;
    this.left = left;
    this.right = right;
    this.jump = jump;
    this.shift = shift;
    this.sprint = sprint;
  }

  public Input() {

  }

  public int forwardKey() {
    return forward ? 1 : backward ? -1 : 0;
  }

  public int sidewaysKey() {
    return left ? 1 : right ? -1 : 0;
  }

  public boolean forward() {
    return forward;
  }

  public boolean backward() {
    return backward;
  }

  public boolean left() {
    return left;
  }

  public boolean right() {
    return right;
  }

  public boolean jump() {
    return jump;
  }

  public boolean sneaking() {
    return shift;
  }

  public boolean sprint() {
    return sprint;
  }

  public void setForward(boolean forward) {
    this.forward = forward;
  }

  public void setBackward(boolean backward) {
    this.backward = backward;
  }

  public void setLeft(boolean left) {
    this.left = left;
  }

  public void setRight(boolean right) {
    this.right = right;
  }

  public void setJump(boolean jump) {
    this.jump = jump;
  }

  public void setShift(boolean shift) {
    this.shift = shift;
  }

  public void setSprint(boolean sprint) {
    this.sprint = sprint;
  }

  @Override
  public String toString() {
    return "{" +
      "forward=" + forward +
      ", backward=" + backward +
      ", left=" + left +
      ", right=" + right +
      ", jump=" + jump +
      ", shift=" + shift +
      ", sprint=" + sprint +
      '}';
  }
}
