package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.packet.plib.converter.PosMoveRotConverter;
import de.jpx3.intave.share.*;

import java.util.OptionalInt;
import java.util.Set;
import java.util.EnumSet;

/** Object getters return snapshots; changes must be written through the setters. */
public final class PlayerTeleportReader extends AbstractPacketReader {
  private final static boolean DIRECT_PMR_USED = MinecraftVersions.VER1_21_3.atOrAbove();

  public OptionalInt teleportId() {
    StructureModifier<Integer> integers = packet().getIntegers();
    if (integers.size() < 1) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(integers.read(0));
  }

  public void setTeleportId(int id) {
    StructureModifier<Integer> integers = packet().getIntegers();
	  if (integers.size() > 0) {
	    integers.write(0, id);
	  }
  }

  public double positionX() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().position().getX();
    }
    return packet().getDoubles().read(0);
  }

  public void setPositionX(double x) {
    if (DIRECT_PMR_USED) {
      PositionMoveRotation value = internalPosMoveRotation();
      value.position().setX(x);
      setPositionMoveRotation(value);
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
      PositionMoveRotation value = internalPosMoveRotation();
      value.position().setY(y);
      setPositionMoveRotation(value);
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
      PositionMoveRotation value = internalPosMoveRotation();
      value.position().setZ(z);
      setPositionMoveRotation(value);
    } else {
      packet().getDoubles().write(2, z);
    }
  }

  public Position position() {
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
      PositionMoveRotation value = internalPosMoveRotation();
      value.rotation().setYaw(yaw);
      setPositionMoveRotation(value);
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
      PositionMoveRotation value = internalPosMoveRotation();
      value.rotation().setPitch(pitch);
      setPositionMoveRotation(value);
    } else {
      packet().getFloat().write(1, pitch);
    }
  }

  public Rotation rotation() {
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
      PositionMoveRotation value = internalPosMoveRotation();
      value.motion().setMotionX(x);
      setPositionMoveRotation(value);
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
      PositionMoveRotation value = internalPosMoveRotation();
      value.motion().setMotionY(y);
      setPositionMoveRotation(value);
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
      PositionMoveRotation value = internalPosMoveRotation();
      value.motion().setMotionZ(z);
      setPositionMoveRotation(value);
    }
  }

  public Motion motion() {
    if (DIRECT_PMR_USED) {
      return internalPosMoveRotation().motion();
    }
    return Motion.newEmpty();
  }

  public PositionMoveRotation positionMoveRotation() {
    return internalPosMoveRotation();
  }

  private PositionMoveRotation internalPosMoveRotation() {
    if (DIRECT_PMR_USED) {
      return packet().getModifier().withType(
        PosMoveRotConverter.nativePositionMoveRotClass,
        PosMoveRotConverter.INSTANCE
      ).read(0);
    }
    StructureModifier<Double> doubles = packet().getDoubles();
    StructureModifier<Float> floats = packet().getFloat();
    return new PositionMoveRotation(
      Position.mutableOf(
        doubles.read(0),
        doubles.read(1),
        doubles.read(2)
      ),
      Motion.newEmpty(),
      new Rotation(
        floats.read(0),
        floats.read(1)
      )
    );
  }

  public void setPositionMoveRotation(PositionMoveRotation posMoveRot) {
    if (DIRECT_PMR_USED) {
      packet().getModifier().withType(
        PosMoveRotConverter.nativePositionMoveRotClass,
        PosMoveRotConverter.INSTANCE
      ).write(0, posMoveRot);
    } else {
      StructureModifier<Double> doubles = packet().getDoubles();
      StructureModifier<Float> floats = packet().getFloat();
      doubles.write(0, posMoveRot.position().getX());
      doubles.write(1, posMoveRot.position().getY());
      doubles.write(2, posMoveRot.position().getZ());
      floats.write(0, posMoveRot.rotation().yaw());
      floats.write(1, posMoveRot.rotation().pitch());
    }
  }

  // if it is just adding motion (all relative and motions are the only thing with chg),
  //  we can replace it with an explosion packet
  public boolean couldBeAnExplosionPacketInstead() {
    if (!DIRECT_PMR_USED) {
      return false;
    }
    PositionMoveRotation pmr = internalPosMoveRotation();
    return pmr.position().isZero() && pmr.rotation().isZero() && !pmr.motion().isZero();
  }

  public Teleport readTeleport(long uniqueId) {
    return new Teleport(uniqueId, teleportId(), positionMoveRotation(), flags());
  }

  public void writeTeleport(Teleport teleport) {
		if (teleport == null) {
			throw new IllegalArgumentException("teleport must not be null");
		}
		setPositionMoveRotation(teleport.change());
		setFlags(teleport.relativeSet());
    if (teleport.id().isPresent()) {
			setTeleportId(teleport.id().getAsInt());
		}
	}

  public Set<Relative> flags() {
    return Relative.flagsFrom(packet());
  }

  public void setFlags(Set<Relative> flags) {
    if (!DIRECT_PMR_USED) {
      Set<Relative> legacyFlags = EnumSet.noneOf(Relative.class);
      legacyFlags.addAll(flags);
      legacyFlags.retainAll(Relative.RELATIVE_POSITION_AND_ROTATION);
      Relative.writeFlags(packet(), legacyFlags);
    } else {
      Relative.writeFlags(packet(), flags);
    }
  }
}
