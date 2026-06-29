package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BukkitConverters;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Motion;
import org.bukkit.util.Vector;

import java.util.Optional;

public final class ExplosionReader extends AbstractPacketReader {
	private final static boolean NEW_EXPLOSION = MinecraftVersions.VER1_21_3.atOrAbove();
	private final static EquivalentConverter<Vector> vectorConverter =
		NEW_EXPLOSION ? BukkitConverters.getVectorConverter() : null;

	public Motion motion() {
		if (NEW_EXPLOSION) {
			Optional<Vector> read = packet().getOptionals(vectorConverter).read(0);
			if (read.isPresent()) {
				Vector vector = read.get();
				return new Motion(vector.getX(), vector.getY(), vector.getZ());
			} else {
				return null;
			}
		} else {
			StructureModifier<Float> floats = packet().getFloat();
			double motionX = floats.readSafely(1);
			double motionY = floats.readSafely(2);
			double motionZ = floats.readSafely(3);
			return new Motion(motionX, motionY, motionZ);
		}
	}
}
