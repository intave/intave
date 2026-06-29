package de.jpx3.intave.packet.reader;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.packet.converter.PosMoveRotConverter;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;

import java.util.Set;

public final class PlayerTeleportReader extends AbstractPacketReader {
  private final static boolean DIRECT_PMR_USED = MinecraftVersions.VER1_21_3.atOrAbove();
  private PositionMoveRotation positionMoveRotation;
  private boolean mod;

  public double positionX() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().position().getX();
    }
    return packet().getDoubles().read(0);
  }

  public void setPositionX(double x) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().position().setX(x);
      mod = true;
    } else {
      packet().getDoubles().write(0, x);
    }
  }

  public double positionY() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().position().getY();
    }
    return packet().getDoubles().read(1);
  }

  public void setPositionY(double y) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().position().setY(y);
      mod = true;
    } else {
      packet().getDoubles().write(1, y);
    }
  }

  public double positionZ() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().position().getZ();
    }
    return packet().getDoubles().read(2);
  }

  public void setPositionZ(double z) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().position().setZ(z);
      mod = true;
    } else {
      packet().getDoubles().write(2, z);
    }
  }

  public Position position() {
    mod = true;
    return internalPosMoveRotation().position();
  }

  public float yaw() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().rotation().yaw();
    }
    return packet().getFloat().read(0);
  }

  public void setYaw(float yaw) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().rotation().setYaw(yaw);
      mod = true;
    } else {
      packet().getFloat().write(0, yaw);
    }
  }

  public float pitch() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().rotation().pitch();
    }
    return packet().getFloat().read(1);
  }

  public void setPitch(float pitch) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().rotation().setPitch(pitch);
      mod = true;
    } else {
      packet().getFloat().write(1, pitch);
    }
  }

  public Rotation rotation() {
    mod = true;
    return internalPosMoveRotation().rotation();
  }

  public double motionX() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().motion().motionX();
    }
    return 0;
  }

  public void setMotionX(double x) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().motion().setMotionX(x);
      mod = true;
    }
  }

  public double motionY() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().motion().motionY();
    }
    return 0;
  }

  public void setMotionY(double y) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().motion().setMotionY(y);
      mod = true;
    }
  }

  public double motionZ() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().motion().motionZ();
    }
    return 0;
  }

  public void setMotionZ(double z) {
    if (DIRECT_PMR_USED) {
      internalPosMoveRotation().motion().setMotionZ(z);
      mod = true;
    }
  }

  private final static Motion UNUSED_MOTION = new Motion(0, 0, 0);

  public Motion motion() {
    if (DIRECT_PMR_USED) {
      mod = true;
      return internalPosMoveRotation().motion();
    }
    return UNUSED_MOTION;
  }

  public PositionMoveRotation positionMoveRotation() {
    mod = true;
    return internalPosMoveRotation();
  }

  private PositionMoveRotation internalPosMoveRotation() {
    if (positionMoveRotation == null) {
      if (DIRECT_PMR_USED) {
        positionMoveRotation = packet().getModifier().withType(
          PosMoveRotConverter.nativePositionMoveRotClass,
          PosMoveRotConverter.INSTANCE
        ).read(0);
      } else {
        positionMoveRotation = new PositionMoveRotation(
          Position.mutableOf(
            packet().getDoubles().read(0),
            packet().getDoubles().read(1),
            packet().getDoubles().read(2)
          ),
          new Motion(0, 0, 0),
          new Rotation(
            packet().getFloat().read(0),
            packet().getFloat().read(1)
          )
        );
      }
    }
    return positionMoveRotation;
  }

  private void writePositionMoveRotation(PositionMoveRotation posMoveRot) {
    if (DIRECT_PMR_USED) {
      packet().getModifier().withType(
        PosMoveRotConverter.nativePositionMoveRotClass,
        PosMoveRotConverter.INSTANCE
      ).write(0, posMoveRot);
    } else {
      packet().getDoubles().write(0, posMoveRot.position().getX());
      packet().getDoubles().write(1, posMoveRot.position().getY());
      packet().getDoubles().write(2, posMoveRot.position().getZ());
      packet().getFloat().write(0, posMoveRot.rotation().yaw());
      packet().getFloat().write(1, posMoveRot.rotation().pitch());
    }
  }

  /*
    Flushing is usually not required, but some very niece packet readers do
    require flushing before the packet is accessed.
    If you want to access a packet modified with a packet-reader, make sure
    to add a call to this method before.
   */
  @Override
  public void flush() {
    if (mod) {
      writePositionMoveRotation(positionMoveRotation);
    }
    mod = false;
    super.flush();
  }

  @Override
  public void release() {
    flush();
    positionMoveRotation = null;
    super.release();
  }

  public Set<Relative> flags() {
    return Relative.flagsFrom(packet());
  }

  public void setFlags(Set<Relative> flags) {
    Relative.writeFlags(packet(), flags);
  }
}
