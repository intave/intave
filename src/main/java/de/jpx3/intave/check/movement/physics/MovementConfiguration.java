package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MovementConfiguration {
  private static final List<State> states;

  private static final QuadState forward = new QuadState();
  private static final QuadState strafe = new QuadState();

  private static final QuadState attackReduceTicks = new QuadState();
  private static final BiState sprintingState = new BiState();
  private static final BiState jumped = new BiState();
  private static final BiState handActive = new BiState();
  private static final BiState reduceBefore = new BiState();

  static {
    List<State> statez = new ArrayList<>();
    statez.add(forward);
    statez.add(strafe);
    statez.add(attackReduceTicks);
    statez.add(sprintingState);
    statez.add(jumped);
    statez.add(handActive);
    statez.add(reduceBefore);
    states = Collections.unmodifiableList(statez);
  }

  private static final MovementConfiguration[] UNIVERSE = new MovementConfiguration[
    1 << (states.stream().mapToInt(State::bitLength).reduce(1, Integer::sum) + 1)
  ];

  static {
    Arrays.setAll(UNIVERSE, MovementConfiguration::new);
  }

  public static StreamCodec<ByteBuf, ByteBuf, MovementConfiguration> STREAM_CODEC = ByteBufStreamCodecs.INTEGER.beforeAndAfter(
	  MovementConfiguration::fromIndex, MovementConfiguration::index
  );

  private final int index;

  private MovementConfiguration(int index) {
    this.index = index;
  }

  public static MovementConfiguration select(
    int forward, int strafe, int reduceTicks, boolean sprint, boolean jumped, boolean handActive, boolean reduceBefore
  ) {
    MovementConfiguration configuration = blank();
    configuration = configuration.withForward(forward);
    configuration = configuration.withStrafe(strafe);
    configuration = configuration.withReduceTicks(reduceTicks);
    configuration = configuration.withSprintingSetTo(sprint);
    configuration = configuration.withJumped(jumped);
    configuration = configuration.withHandActive(handActive);
    configuration = configuration.withReduceBefore(reduceBefore);
    return configuration;
  }

  public int forward() {
    int forwardRepresentation = forward.get(index);
    switch (forwardRepresentation) {
      case 0:
        return 0;
      case 1:
        return 1;
      case 2:
        return -1;
      default:
        throw new IllegalStateException("Unexpected value: " + forwardRepresentation);
    }
  }

  public int strafe() {
    // can only be 0, 1, 2
    int strafeRepresentation = strafe.get(index);
    switch (strafeRepresentation) {
      case 0:
        return 0;
      case 1:
        return 1;
      case 2:
        return -1;
      default:
        throw new IllegalStateException("Unexpected value: " + strafeRepresentation);
    }
  }

  public MovementConfiguration withForward(int forward) {
    if (forward < -1 || forward > 1) {
      throw new IllegalArgumentException("forward can only be -1, 0, 1");
    }
    switch (forward) {
      case -1:
        return UNIVERSE[MovementConfiguration.forward.set(index, 2)];
      case 0:
        return UNIVERSE[MovementConfiguration.forward.set(index, 0)];
      case 1:
        return UNIVERSE[MovementConfiguration.forward.set(index, 1)];
      default:
        throw new IllegalStateException("Unexpected value: " + forward);
    }
  }

  public MovementConfiguration pressingW() {
    return withForward(1);
  }

  public MovementConfiguration pressingS() {
    return withForward(-1);
  }

  public MovementConfiguration withStrafe(int strafe) {
    if (strafe < -1 || strafe > 1) {
      throw new IllegalArgumentException("strafe can only be -1, 0, 1");
    }
    switch (strafe) {
      case -1:
        return UNIVERSE[MovementConfiguration.strafe.set(index, 2)];
      case 0:
        return UNIVERSE[MovementConfiguration.strafe.set(index, 0)];
      case 1:
        return UNIVERSE[MovementConfiguration.strafe.set(index, 1)];
      default:
        throw new IllegalStateException("Unexpected value: " + strafe);
    }
  }

  public MovementConfiguration pressingA() {
    return withStrafe(-1);
  }

  public MovementConfiguration pressingD() {
    return withStrafe(1);
  }

  public MovementConfiguration withoutKeypress() {
    return withForward(0).withStrafe(0);
  }

  public MovementConfiguration withKeypress(int forward, int strafe) {
    if (Math.abs(forward) > 1 || Math.abs(strafe) > 1) {
      throw new IllegalArgumentException("forward and strafe can only be -1, 0, 1");
    }
    return withForward(forward).withStrafe(strafe);
  }

  public static MovementConfiguration[] values() {
    return UNIVERSE;
  }

  public boolean isReducing() {
    return attackReduceTicks.get(index) > 0;
  }

  public int reduceTicks() {
    return attackReduceTicks.get(index);
  }

  public MovementConfiguration withReduceTicks(int ticks) {
    return UNIVERSE[attackReduceTicks.set(index, minmax(ticks, 0, 3))];
  }

  public MovementConfiguration withoutReducing() {
    return UNIVERSE[attackReduceTicks.set(index, 0)];
  }

  public boolean isSprinting() {
    return sprintingState.get(index);
  }

  public MovementConfiguration withSprinting() {
    return UNIVERSE[sprintingState.set(index, true)];
  }

  public MovementConfiguration withoutSprinting() {
    return UNIVERSE[sprintingState.set(index, false)];
  }

  public MovementConfiguration withSprintingSetTo(boolean sprinting) {
    return UNIVERSE[sprintingState.set(index, sprinting)];
  }

  public boolean isJumping() {
    return jumped.get(index);
  }

  public MovementConfiguration withJumped(boolean hasJumped) {
    return UNIVERSE[jumped.set(index, hasJumped)];
  }

  public boolean isHandActive() {
    return handActive.get(index);
  }

  public MovementConfiguration withActiveHand() {
    return UNIVERSE[handActive.set(index, true)];
  }

  public MovementConfiguration withoutActiveHand() {
    return UNIVERSE[handActive.set(index, false)];
  }

  public MovementConfiguration withHandActive(boolean hasHandActive) {
    return UNIVERSE[handActive.set(index, hasHandActive)];
  }

  public boolean reduceBefore() {
    return reduceBefore.get(index);
  }

  public String bitString() {
    return String.format("%32s", Integer.toBinaryString(index)).replace(' ', '0');
  }

  public MovementConfiguration withReduceBefore(boolean hasReduceBefore) {
    return UNIVERSE[reduceBefore.set(index, hasReduceBefore)];
  }

  public static MovementConfiguration blank() {
    return UNIVERSE[0];
  }

  private static int minmax(int val, int min, int max) {
    return Math.max(min, Math.min(max, val));
  }

  private static int starterBit;

  public MovementConfiguration withJump() {
    return withJumped(true);
  }

  public MovementConfiguration withoutJump() {
    return withJumped(false);
  }

  private int index() {
    return index;
  }

  private static MovementConfiguration fromIndex(int index) {
    if (index < 0 || index >= UNIVERSE.length) {
      throw new IllegalArgumentException("Invalid movement configuration index: " + index);
    }
    return UNIVERSE[index];
  }

  private static class BiState extends State {
    private final int slot = starterBit++;

    public int set(int before, boolean val) {
      return val ? before | (1 << slot) : before & ~(1 << slot);
    }

    public int set(int before, int val) {
      return val == 1 ? before | (1 << slot) : before & ~(1 << slot);
    }

    public boolean get(int before) {
      return ((before >> slot) & 1) == 1;
    }

    @Override
    int bitLength() {
      return 1;
    }

    @Override
    int bitMask() {
      return 1 << slot;
    }
  }

  private static class QuadState extends State {
    private final int slot;

    private QuadState() {
      this.slot = starterBit;
      starterBit += 2;
    }

    public int set(int before, int val) {
      return (before & ~(0b11 << slot)) | val << slot;
    }

    public int get(int before) {
      return (before >> slot) & 0b11;
    }

    @Override
    int bitLength() {
      return 2;
    }

    @Override
    int bitMask() {
      return 0b11 << slot;
    }
  }

  private abstract static class State {
    abstract int bitLength();

    abstract int bitMask();
  }

  private String keysToString() {
    StringBuilder builder = new StringBuilder();
    int forward = forward();
    int strafe = strafe();
    if (forward == 1) {
      builder.append("W");
    } else if (forward == -1) {
      builder.append("S");
    }
    if (strafe == 1) {
      builder.append("D");
    } else if (strafe == -1) {
      builder.append("A");
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return ("(" + keysToString() + ") " +
      (isReducing() ? "_RED" + reduceTicks() : "") +
      (isSprinting() ? "_SPR" : "") +
      (isJumping() ? "_JMP" : "") +
      (isHandActive() ? "_HA" : "")
    ).trim();
  }
}
