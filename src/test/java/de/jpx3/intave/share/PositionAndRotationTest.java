package de.jpx3.intave.share;

import de.jpx3.intave.packet.plib.converter.PositionAndRotationConverter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionAndRotationTest {
  @Test
  void mutableInputsAndReturnedObjectsCannotChangeTheSnapshot() {
    Position position = new Position(1, 2, 3).mutable();
    Rotation rotation = new Rotation(40, -20);
    PositionAndRotation value = new PositionAndRotation(position, rotation);
    position.setX(99);
    rotation.setYaw(99);
    value.position().mutable().setY(99);
    value.rotation().setPitch(99);
    assertEquals(new PositionAndRotation(1, 2, 3, 40, -20), value);
    assertEquals(new PositionAndRotation(4, 5, 6, 40, -20), value.withPosition(new Position(4, 5, 6)));
    assertEquals(new PositionAndRotation(1, 2, 3, 10, 15), value.withRotation(new Rotation(10, 15)));
  }

  @Test
  void codecPreservesCoordinatesAnglesAndSignedZero() {
    PositionAndRotation value = new PositionAndRotation(-0.0, 64.125, -1234.75, 179.5F, -0.0F);
    ByteBuf buffer = Unpooled.buffer();
    try {
      PositionAndRotation.STREAM_CODEC.encode(buffer, value);
      assertEquals(32, buffer.readableBytes());
      assertEquals(value, PositionAndRotation.STREAM_CODEC.decode(buffer));
      assertEquals(0, buffer.readableBytes());
    } finally {
      buffer.release();
    }
  }

  @Test
  void nonFiniteValuesAreRetainedForThePacketValidator() {
    assertTrue(new PositionAndRotation(1, 2, 3, 4, 5).isFinite());
    for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      for (int field = 0; field < 5; field++) {
        PositionAndRotation value = new PositionAndRotation(field == 0 ? invalid : 1,
          field == 1 ? invalid : 2, field == 2 ? invalid : 3,
          field == 3 ? (float)invalid : 4, field == 4 ? (float)invalid : 5);
        assertFalse(value.isFinite());
      }
    }
  }

  @Test
  void converterTypeIsAvailableWithoutNativeMinecraftClasses() {
    assertEquals(PositionAndRotation.class, PositionAndRotationConverter.INSTANCE.getSpecificType());
  }
}
