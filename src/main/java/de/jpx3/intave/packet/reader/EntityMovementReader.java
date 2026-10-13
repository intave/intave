package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.EntityRelativeMoveStep;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.RawVector3d;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.meta.ProtocolMetadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native server layouts for relative entity movement, before any protocol translation. */
public final class EntityMovementReader {
  private EntityMovementReader() {}

  public static Delta read(PacketContainer packet, int serverProtocol) {
    if (packet.getType() == PacketType.Play.Server.ENTITY_LOOK) {
      int divisor = serverProtocol < 107 ? 32 : 4096;
      return new Delta(Collections.singletonList(new Step(0, 0, 0, 0, divisor)), false, divisor);
    }
    if (serverProtocol >= ProtocolMetadata.VER_26_3) {
      return Native263.read(packet);
    }
    if (serverProtocol >= 477) {
      StructureModifier<Short> fields = packet.getShorts();
      return linear(fields.read(0), fields.read(1), fields.read(2), 4096);
    }
    if (serverProtocol >= 107) {
      StructureModifier<Integer> fields = packet.getIntegers();
      return linear(fields.read(1), fields.read(2), fields.read(3), 4096);
    }
    StructureModifier<Byte> fields = packet.getBytes();
    return linear(fields.read(0), fields.read(1), fields.read(2), 32);
  }

  private static Delta linear(int x, int y, int z, int divisor) {
    return new Delta(Collections.singletonList(new Step(x, y, z, 0, divisor)), false, divisor);
  }

  public static void writeLinear263(PacketContainer packet, short x, short y, short z) {
    try {
      packet.getModifier().withType(Native263.DELTA).write(0, Native263.LINEAR_CONSTRUCTOR.newInstance(x, y, z));
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot create 26.3 relative entity movement", exception);
    }
  }

  public static PositionMoveRotation readPositionSync(PacketContainer packet, int serverProtocol) {
    if (serverProtocol < ProtocolMetadata.VER_26_3) return PositionMoveRotation.firstFrom(packet);
    try {
      Object path = packet.getModifier().withType(Native263.POSITION_PATH).read(0);
      Object end = Native263.END_POSITION.invoke(path);
      StructureModifier<Double> axes = new StructureModifier<Object>(end.getClass()).withTarget(end).withType(double.class);
      Position position = new Position(axes.read(0), axes.read(1), axes.read(2));
      return new PositionMoveRotation(position, new Motion(), new Rotation(packet.getFloat().read(0), packet.getFloat().read(1)));
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot read 26.3 entity position sync", exception);
    }
  }

  public static final class Delta {
    private final List<Step> steps;
    private final boolean stepped;
    private final int divisor;

    private Delta(List<Step> steps, boolean stepped, int divisor) {
      this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
      this.stepped = stepped;
      this.divisor = divisor;
    }

    public List<Step> steps() { return steps; }
    public boolean stepped() { return stepped; }
    public int divisor() { return divisor; }
    public long x() { long value = 0; for (Step step : steps) value += step.x; return value; }
    public long y() { long value = 0; for (Step step : steps) value += step.y; return value; }
    public long z() { long value = 0; for (Step step : steps) value += step.z; return value; }
  }

  public static final class Step implements EntityRelativeMoveStep {
    public final int x, y, z, ticks;
    private final int divisor;

    private Step(int x, int y, int z, int ticks, int divisor) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.ticks = ticks;
      this.divisor = divisor;
    }

    @Override
    public RawVector3d delta() {
      return new RawVector3d((double) x / divisor, (double) y / divisor, (double) z / divisor);
    }

    @Override
    public int delayTicks() {
      return ticks;
    }
  }

  private static final class Native263 {
    private static final Class<?> DELTA;
    private static final Class<?> LINEAR;
    private static final Class<?> STEPPED;
    private static final Class<?> POSITION_PATH;
    private static final Constructor<?> LINEAR_CONSTRUCTOR;
    private static final Method STEPS;
    private static final Method END_POSITION;

    static {
      try {
        DELTA = Class.forName("net.minecraft.network.protocol.game.VecDelta");
        LINEAR = Class.forName("net.minecraft.network.protocol.game.VecDelta$Linear");
        STEPPED = Class.forName("net.minecraft.network.protocol.game.VecDelta$Stepped");
        LINEAR_CONSTRUCTOR = LINEAR.getConstructor(short.class, short.class, short.class);
        STEPS = STEPPED.getMethod("steps");
        POSITION_PATH = Class.forName("net.minecraft.world.entity.PositionPath");
        END_POSITION = POSITION_PATH.getMethod("endPosition");
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Cannot resolve 26.3 entity movement layout", exception);
      }
    }

    private static Delta read(PacketContainer packet) {
      Object delta = packet.getModifier().withType(DELTA).read(0);
      if (LINEAR.isInstance(delta)) {
        return new Delta(Collections.singletonList(step(delta, false)), false, 4096);
      }
      if (!STEPPED.isInstance(delta)) throw new IllegalArgumentException("Missing or unknown entity movement delta");
      try {
        List<Step> steps = new ArrayList<>();
        for (Object entry : (List<?>) STEPS.invoke(delta)) steps.add(step(entry, true));
        return new Delta(steps, true, 4096);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Cannot read stepped entity movement", exception);
      }
    }

    private static Step step(Object value, boolean stepped) {
      StructureModifier<Object> fields = new StructureModifier<>(value.getClass()).withTarget(value);
      StructureModifier<Short> axes = fields.withType(short.class);
      return new Step(
        axes.read(0), axes.read(1), axes.read(2),
        stepped ? fields.<Integer>withType(int.class).read(0) : 0,
        4096
      );
    }
  }
}
