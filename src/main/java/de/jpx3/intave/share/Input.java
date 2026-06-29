package de.jpx3.intave.share;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.concurrent.ThreadLocalRandom;

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
      flags = (byte)(flags | (value.sprinting() ? 64 : 0));
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

  private final boolean forward;
  private final boolean backward;
  private final boolean left;
  private final boolean right;
  private final boolean jump;
  private final boolean shift;
  private final boolean sprint;

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

  public boolean sprinting() {
    return sprint;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Input input = (Input) obj;
    return forward == input.forward &&
      backward == input.backward &&
      left == input.left &&
      right == input.right &&
      jump == input.jump &&
      shift == input.shift &&
      sprint == input.sprint;
  }

  @Override
  public int hashCode() {
    int result = Boolean.hashCode(forward);
    result = 31 * result + Boolean.hashCode(backward);
    result = 31 * result + Boolean.hashCode(left);
    result = 31 * result + Boolean.hashCode(right);
    result = 31 * result + Boolean.hashCode(jump);
    result = 31 * result + Boolean.hashCode(shift);
    result = 31 * result + Boolean.hashCode(sprint);
    return result;
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

  public static Input partialFrom(
    SimulationEnvironment environment
  ) {
    return new Input(
      false, false, false, false, false,
     environment.isSneaking(), environment.isSprinting()
    );
  }

  public static Input random() {
    ThreadLocalRandom current = ThreadLocalRandom.current();
    return new Input(
      current.nextBoolean(), current.nextBoolean(),
      current.nextBoolean(), current.nextBoolean(), current.nextBoolean(),
      current.nextBoolean(), current.nextBoolean()
    );
  }

  public static Input none() {
    return new Input(false, false, false, false, false, false, false);
  }
}
