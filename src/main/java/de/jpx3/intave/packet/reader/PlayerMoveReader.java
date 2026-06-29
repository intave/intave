package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;

public final class PlayerMoveReader extends AbstractPacketReader {
	private final static boolean CONTAINS_COLLISION_INFORMATION = MinecraftVersions.VER1_21_3.atOrAbove();
	private final static int HAS_MOVEMENT_FIELD_INDEX = CONTAINS_COLLISION_INFORMATION ? 2 : 1;
	private final static int HAS_ROTATION_FIELD_INDEX = CONTAINS_COLLISION_INFORMATION ? 3 : 2;

	public boolean isVehicleMove() {
		return packet().getType() == PacketType.Play.Client.VEHICLE_MOVE;
	}

	public double positionX() {
		return movements().read(0);
	}

	public double positionY() {
		return movements().read(1);
	}

	public double positionZ() {
		return movements().read(2);
	}

	public @Nullable Position position() {
		if (!hasMovement()) {
			return null;
		}
		StructureModifier<Double> movements = movements();
		return new Position(movements.read(0), movements.read(1), movements.read(2));
	}

	public float yaw() {
		return rotations().read(0);
	}

	public float pitch() {
		return rotations().read(1);
	}

	public @Nullable Rotation rotation() {
		if (!hasRotation()) {
			return null;
		}
		StructureModifier<Float> rotations = rotations();
		return new Rotation(rotations.read(0), rotations.read(1));
	}

	public boolean onGround() {
		return packet().getBooleans().read(0);
	}

	public void setOnGround(boolean onGround) {
		packet().getBooleans().write(0, onGround);
	}

	public void setPositionX(double x) {
		movements().write(0, x);
	}

	public void setPositionY(double y) {
		movements().write(1, y);
	}

	public void setPositionZ(double z) {
		movements().write(2, z);
	}

	public void setPosition(Position position) {
		setPositionX(position.getX());
		setPositionY(position.getY());
		setPositionZ(position.getZ());
	}

	public void setYaw(float yaw) {
		rotations().write(0, yaw);
	}

	public void setPitch(float pitch) {
		rotations().write(1, pitch);
	}

	public boolean hasMovement() {
		return isVehicleMove() || packet().getBooleans().read(HAS_MOVEMENT_FIELD_INDEX);
	}

	public boolean hasRotation() {
		return isVehicleMove() || packet().getBooleans().read(HAS_ROTATION_FIELD_INDEX);
	}

	public boolean anyNaNOrInfiniteValue() {
		if (hasMovement()) {
			StructureModifier<Double> movements = movements();
			for (int i = 0; i < 3; i++) {
				Double value = movements.read(i);
				if (Double.isNaN(value) || Double.isInfinite(value)) {
					return true;
				}
			}
		}
		if (hasRotation()) {
			StructureModifier<Float> rotations = rotations();
			for (int i = 0; i < 2; i++) {
				Float value = rotations.read(i);
				if (Float.isNaN(value) || Float.isInfinite(value)) {
					return true;
				}
			}
		}
		return false;
	}

	private StructureModifier<Double> movements() {
		StructureModifier<Double> modifier = packet().getDoubles();
		if (MinecraftVersions.VER1_21_4.atOrAbove() && isVehicleMove()) {
			modifier = packet().getStructures().read(0).getDoubles();
		}
		return modifier;
	}

	private StructureModifier<Float> rotations() {
		return packet().getFloat();
	}
}
