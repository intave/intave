package de.jpx3.intave.share;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.Relative;
import io.netty.buffer.ByteBuf;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.share.ClientMath.floor;

public final class Position implements Serializable, Cloneable {
	public static final StreamCodec<ByteBuf, ByteBuf, Position> STREAM_CODEC = StreamCodec.of(
		(byteBuf, position) -> {
			byteBuf.writeDouble(position.x);
			byteBuf.writeDouble(position.y);
			byteBuf.writeDouble(position.z);
		},
		byteBuf -> new Position(byteBuf.readDouble(), byteBuf.readDouble(), byteBuf.readDouble())
	);

	private double x;
	private double y;
	private double z;
	private boolean immutable = true;

	public Position() {
		this(0, 0, 0);
	}

	public Position(double xCoordinate, double yCoordinate, double zCoordinate) {
		this.x = xCoordinate;
		this.y = yCoordinate;
		this.z = zCoordinate;
	}

	public Position filtered(Set<Relative> flags) {
		return new Position(
			flags.contains(Relative.X) ? x : 0,
			flags.contains(Relative.Y) ? y : 0,
			flags.contains(Relative.Z) ? z : 0
		);
	}

	public Position mutable() {
		if (!immutable) {
			return this;
		}
		return mutableCopy(this);
	}

	public Position immutable() {
		if (immutable) {
			return this;
		}
		Position immutableCopy = new Position(x, y, z);
		immutableCopy.immutable = true;
		return immutableCopy;
	}

	public boolean isImmutable() {
		return immutable;
	}

	public boolean hasNaNCoordinate() {
		return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z);
	}

	public BlockPosition toBlockPosition() {
		return new BlockPosition(floor(x), floor(y), floor(z));
	}

	public Vector toBukkitVec() {
		return new Vector(x, y, z);
	}

	public Location toLocation(World world) {
		return new Location(world, x, y, z);
	}

	public double distance(Position position) {
		return distance(position.x, position.y, position.z);
	}

	public double distance(Location location) {
		return distance(location.getX(), location.getY(), location.getZ());
	}

	public double distance(double x, double y, double z) {
		double deltaX = this.x - x;
		double deltaY = this.y - y;
		double deltaZ = this.z - z;
		return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
	}

	public double distanceSquared(Position position) {
		return distanceSquared(position.x, position.y, position.z);
	}

	public double distanceSquared(Location location) {
		return distanceSquared(location.getX(), location.getY(), location.getZ());
	}

	public double distanceSquared(double x, double y, double z) {
		double deltaX = this.x - x;
		double deltaY = this.y - y;
		double deltaZ = this.z - z;
		return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
	}

	public Motion motionTo(Position to) {
		return new Motion(to.x - x, to.y - y, to.z - z);
	}

	public Position relative(
		Direction direction, double length
	) {
		Vector normal = direction.normalVec();
		return new Position(x + normal.getX() * length, y + normal.getY() * length, z + normal.getZ() * length);
	}

	public Position add(double x, double y, double z) {
		return new Position(this.x + x, this.y + y, this.z + z);
	}

	public Position add(Position position) {
		return add(position.x, position.y, position.z);
	}

	public Position add(Vector vector) {
		return add(vector.getX(), vector.getY(), vector.getZ());
	}

	public Position add(Motion motion) {
		return add(motion.motionX(), motion.motionY(), motion.motionZ());
	}

	public Vector subtract(double x, double y, double z) {
		return new Vector(this.x - x, this.y - y, this.z - z);
	}

	public Vector subtract(Position position) {
		return subtract(position.x, position.y, position.z);
	}

	public Vector subtract(Vector vector) {
		return subtract(vector.getX(), vector.getY(), vector.getZ());
	}

	public Vector subtract(Motion motion) {
		return subtract(motion.motionX(), motion.motionY(), motion.motionZ());
	}

	public void setX(double x) {
		if (immutable) {
			throw new UnsupportedOperationException("Cannot modify immutable Position");
		}
		this.x = x;
	}

	public void setY(double y) {
		if (immutable) {
			throw new UnsupportedOperationException("Cannot modify immutable Position");
		}
		this.y = y;
	}

	public void setZ(double z) {
		if (immutable) {
			throw new UnsupportedOperationException("Cannot modify immutable Position");
		}
		this.z = z;
	}

	public RawVector3d toNativeVec() {
		return new RawVector3d(x, y, z);
	}

	public Rotation rotationTo(Position otherPoint) {
		float yaw = (float) Math.toDegrees(Math.atan2(otherPoint.z - z, otherPoint.x - x) - 90f);
		float pitch = -(float) Math.toDegrees(Math.atan2(otherPoint.y - y, Math.sqrt(Math.pow(otherPoint.x - x, 2) + Math.pow(otherPoint.z - z, 2))));
		return new Rotation(yaw, pitch);
	}

	public int chunkX() {
		return floor(x) >> 4;
	}

	public int chunkZ() {
		return floor(z) >> 4;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public int getBlockX() {
		return floor(x);
	}

	public int getBlockY() {
		return floor(y);
	}

	public int getBlockZ() {
		return floor(z);
	}

	@Override
	public String toString() {
		return formatDouble(x, 2) + ", " + formatDouble(y, 2) + ", " + formatDouble(z, 2);
	}

	public String format(int decimalPlaces) {
		return formatDouble(x, decimalPlaces) + ", " + formatDouble(y, decimalPlaces) + ", " + formatDouble(z, decimalPlaces);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Position other = (Position) obj;
		return Double.compare(other.x, x) == 0 && Double.compare(other.y, y) == 0 && Double.compare(other.z, z) == 0;
	}

	@Override
	public int hashCode() {
		int result = 17;
		long xBits = Double.doubleToLongBits(x);
		long yBits = Double.doubleToLongBits(y);
		long zBits = Double.doubleToLongBits(z);
		result = 31 * result + Long.hashCode(xBits);
		result = 31 * result + Long.hashCode(yBits);
		result = 31 * result + Long.hashCode(zBits);
		return result;
	}

	@Override
	public Position clone() throws CloneNotSupportedException {
		return (Position) super.clone();
	}

	public static Position immutableEmpty() {
		return new Position();
	}

	public static Position mutableCopy(
		Position position
	) {
		Position copy = new Position(position.x, position.y, position.z);
		copy.immutable = false;
		return copy;
	}

	public static Position immutableRandom() {
		ThreadLocalRandom current = ThreadLocalRandom.current();
		return new Position(
			current.nextDouble(-1000, 1000),
			current.nextDouble(-1000, 1000),
			current.nextDouble(-1000, 1000)
		);
	}

	public static Position mutableEmpty() {
		Position position = new Position();
		position.immutable = false;
		return position;
	}


	public static Position of(int blockX, int blockY, int blockZ) {
		return new Position(blockX, blockY, blockZ);
	}

	public static Position of(double x, double y, double z) {
		return new Position(x, y, z);
	}

	public static Position of(Location location) {
		return new Position(location.getX(), location.getY(), location.getZ());
	}

	public static Position of(Position position) {
		return new Position(position.x, position.y, position.z);
	}

	public static Position of(Vector vector) {
		return new Position(vector.getX(), vector.getY(), vector.getZ());
	}

	public static Position mutableOf(Position position) {
		return mutableCopy(position);
	}

	public static Position mutableOf(int blockX, int blockY, int blockZ) {
		return mutableCopy(of(blockX, blockY, blockZ));
	}

	public static Position mutableOf(double x, double y, double z) {
		return mutableCopy(of(x, y, z));
	}
}
