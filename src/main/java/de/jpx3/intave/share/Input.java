/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.share;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.codec.JsonStreamCodecs.booleanField;
import static de.jpx3.intave.codec.JsonStreamCodecs.object;

public final class Input {
  public static final StreamCodec<JsonReader, JsonWriter, Input> JSON_CODEC = object(
    booleanField("forward", Input::forwardKey),
    booleanField("backward", Input::backwardKey),
    booleanField("left", Input::leftKey),
    booleanField("right", Input::rightKey),
    booleanField("jump", Input::jumpKey),
    booleanField("sneaking", Input::sneakKey),
    booleanField("sprinting", Input::sprintKey),
    Input::new
  );
  public static final StreamCodec<ByteBuf, ByteBuf, Input> STREAM_CODEC = StreamCodec.of(
    (buf, value) -> {
      byte flags = 0;
      flags = (byte)(flags | (value.forwardKey() ? 1 : 0));
      flags = (byte)(flags | (value.backwardKey() ? 2 : 0));
      flags = (byte)(flags | (value.leftKey() ? 4 : 0));
      flags = (byte)(flags | (value.rightKey() ? 8 : 0));
      flags = (byte)(flags | (value.jumpKey() ? 16 : 0));
      flags = (byte)(flags | (value.sneakKey() ? 32 : 0));
      flags = (byte)(flags | (value.sprintKey() ? 64 : 0));
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

  public int forward() {
    int forward = 0;
    if (this.forward) {
      forward += 1;
    }
    if (this.backward) {
      forward -= 1;
    }
    return forward;
  }

  public int strafe() {
    int sideways = 0;
    if (this.left) {
      sideways += 1;
    }
    if (this.right) {
      sideways -= 1;
    }
    return sideways;
  }

  public boolean forwardKey() {
    return forward;
  }

  public boolean backwardKey() {
    return backward;
  }

  public boolean leftKey() {
    return left;
  }

  public boolean rightKey() {
    return right;
  }

  public boolean jumpKey() {
    return jump;
  }

  public boolean sneakKey() {
    return shift;
  }

  public boolean sprintKey() {
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
