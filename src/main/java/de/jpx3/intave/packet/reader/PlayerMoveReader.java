package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionAndRotation;
import de.jpx3.intave.packet.plib.converter.PositionAndRotationConverter;
import de.jpx3.intave.share.Rotation;

public final class PlayerMoveReader extends AbstractPacketReader {
	private final static boolean CONTAINS_COLLISION_INFORMATION = MinecraftVersions.VER1_21_3.atOrAbove();
	private final static int HAS_MOVEMENT_FIELD_INDEX = CONTAINS_COLLISION_INFORMATION ? 2 : 1;
	private final static int HAS_ROTATION_FIELD_INDEX = CONTAINS_COLLISION_INFORMATION ? 3 : 2;

	public boolean isVehicleMove() {
		return packet().getType() == PacketType.Play.Client.VEHICLE_MOVE;
	}

	public boolean couldBeTeleport() {
		if (packet().getType() != PacketType.Play.Client.POSITION_LOOK) {
			return false;
		}
		if (onGround()) {
			return false;
		}
		Boolean horizontalCollision = horizontalCollision();
		return horizontalCollision == null || !horizontalCollision;
	}

	public double positionX() {
		return hasNativePositionAndRotation() ? nativePositionAndRotation().x() : movements().read(0);
	}

	public double positionY() {
		return hasNativePositionAndRotation() ? nativePositionAndRotation().y() : movements().read(1);
	}

	public double positionZ() {
		return hasNativePositionAndRotation() ? nativePositionAndRotation().z() : movements().read(2);
	}

	public @Nullable Position position() {
		if (!hasMovement()) {
			return null;
		}
		if (hasNativePositionAndRotation()) return nativePositionAndRotation().position();
		StructureModifier<Double> movements = movements();
		return new Position(movements.read(0), movements.read(1), movements.read(2));
	}

	public float yaw() {
		return hasNativePositionAndRotation() ? nativePositionAndRotation().yaw() : rotations().read(0);
	}

	public float pitch() {
		return hasNativePositionAndRotation() ? nativePositionAndRotation().pitch() : rotations().read(1);
	}

	public @Nullable Rotation rotation() {
		if (!hasRotation()) {
			return null;
		}
		if (hasNativePositionAndRotation()) return nativePositionAndRotation().rotation();
		StructureModifier<Float> rotations = rotations();
		return new Rotation(rotations.read(0), rotations.read(1));
	}

	public boolean onGround() {
		return packet().getBooleans().read(0);
	}

	public void setOnGround(boolean onGround) {
		packet().getBooleans().write(0, onGround);
	}

	public @Nullable Boolean horizontalCollision() {
		if (!CONTAINS_COLLISION_INFORMATION) {
			return null;
		}
		return packet().getBooleans().read(1);
	}

	public void setHorizontalCollision(boolean horizontalCollision) {
		if (!CONTAINS_COLLISION_INFORMATION) {
			return;
		}
		packet().getBooleans().write(1, horizontalCollision);
	}

	public void setPositionX(double x) {
		if (hasNativePositionAndRotation()) {
			PositionAndRotation value = nativePositionAndRotation();
			writeNativePositionAndRotation(new PositionAndRotation(x, value.y(), value.z(), value.yaw(), value.pitch()));
			return;
		}
		movements().write(0, x);
	}

	public void setPositionY(double y) {
		if (hasNativePositionAndRotation()) {
			PositionAndRotation value = nativePositionAndRotation();
			writeNativePositionAndRotation(new PositionAndRotation(value.x(), y, value.z(), value.yaw(), value.pitch()));
			return;
		}
		movements().write(1, y);
	}

	public void setPositionZ(double z) {
		if (hasNativePositionAndRotation()) {
			PositionAndRotation value = nativePositionAndRotation();
			writeNativePositionAndRotation(new PositionAndRotation(value.x(), value.y(), z, value.yaw(), value.pitch()));
			return;
		}
		movements().write(2, z);
	}

	public void setPosition(Position position) {
		if (hasNativePositionAndRotation()) {
			writeNativePositionAndRotation(nativePositionAndRotation().withPosition(position));
			return;
		}
		setPositionX(position.getX());
		setPositionY(position.getY());
		setPositionZ(position.getZ());
	}

	public void setYaw(float yaw) {
		if (hasNativePositionAndRotation()) {
			PositionAndRotation value = nativePositionAndRotation();
			writeNativePositionAndRotation(new PositionAndRotation(value.x(), value.y(), value.z(), yaw, value.pitch()));
			return;
		}
		rotations().write(0, yaw);
	}

	public void setPitch(float pitch) {
		if (hasNativePositionAndRotation()) {
			PositionAndRotation value = nativePositionAndRotation();
			writeNativePositionAndRotation(new PositionAndRotation(value.x(), value.y(), value.z(), value.yaw(), pitch));
			return;
		}
		rotations().write(1, pitch);
	}

	public boolean hasMovement() {
		return isVehicleMove() || packet().getBooleans().read(HAS_MOVEMENT_FIELD_INDEX);
	}

	public boolean hasRotation() {
		return isVehicleMove() || packet().getBooleans().read(HAS_ROTATION_FIELD_INDEX);
	}

	public boolean anyNaNOrInfiniteValue() {
		if (hasNativePositionAndRotation()) return !nativePositionAndRotation().isFinite();
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

	/** Returns a complete snapshot only when both fields are present in the packet. */
	public @Nullable PositionAndRotation positionAndRotation() {
		if (!hasMovement() || !hasRotation()) return null;
		if (hasNativePositionAndRotation()) return nativePositionAndRotation();
		return new PositionAndRotation(position(), rotation());
	}

	/** Does not promote partial movement packets to a different packet type. */
	public void setPositionAndRotation(PositionAndRotation value) {
		if (!hasMovement() || !hasRotation()) {
			throw new IllegalStateException("Packet does not contain both position and rotation");
		}
		if (hasNativePositionAndRotation()) {
			writeNativePositionAndRotation(value);
		} else {
			setPosition(value.position());
			setYaw(value.yaw());
			setPitch(value.pitch());
		}
	}

	private boolean hasNativePositionAndRotation() {
		return MinecraftVersions.VER26_3.atOrAbove() && isVehicleMove();
	}

	private StructureModifier<PositionAndRotation> nativeTransform() {
		PositionAndRotationConverter converter = PositionAndRotationConverter.INSTANCE;
		return packet().getModifier().withType(converter.nativeType(), converter);
	}

	private PositionAndRotation nativePositionAndRotation() {
		return nativeTransform().read(0);
	}

	private void writeNativePositionAndRotation(PositionAndRotation value) {
		nativeTransform().write(0, value);
	}

	private StructureModifier<Double> movements() {
		if (MinecraftVersions.VER1_21_4.atOrAbove() && isVehicleMove()) {
			return packet().getStructures().read(0).getDoubles();
		}
		return packet().getDoubles();
	}

	private StructureModifier<Float> rotations() {
		return packet().getFloat();
	}
}
