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

package de.jpx3.intave.packet.plib.reader;

import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.WrappedParticle;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Motion;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Optional;

public final class ExplosionReader extends AbstractPacketReader {
	private final static boolean NEW_EXPLOSION = MinecraftVersions.VER1_21_2.atOrAbove();
	private static Object emptyBlockParticles;
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

	public void setMotion(Motion motion) {
		if (NEW_EXPLOSION) {
			packet().getOptionals(vectorConverter)
				.write(0, Optional.of(new Vector(motion.motionX(), motion.motionY(), motion.motionZ())));
		} else {
			StructureModifier<Float> floats = packet().getFloat();
			floats.writeSafely(1, (float) motion.motionX());
			floats.writeSafely(2, (float) motion.motionY());
			floats.writeSafely(3, (float) motion.motionZ());
		}
	}

	/** Keep explosion effects out of range without changing its motion. */
	public void setSilentDefaults() {
		if (NEW_EXPLOSION) {
			packet().getVectors().write(0, new Vector(0, -1.0E9D, 0));
			// 1.21.9 adds radius, block count and weighted block particles.
			if (packet().getFloat().size() > 0) {
				packet().getFloat().write(0, 0.0F);
				packet().getIntegers().write(0, 0);
				packet().getModifier().write(6, emptyBlockParticles());
			}
			// 26.3 adds an explicit sound switch.
			packet().getBooleans().writeSafely(0, false);
		} else {
			packet().getDoubles().write(0, 0.0D).write(1, -1.0E9D).write(2, 0.0D);
			packet().getFloat().write(0, 0.0F);
			packet().getBlockPositionCollectionModifier().write(0, Collections.emptyList());
		}
		// These fields were added to the explosion packet in 1.20.3.
		if (MinecraftVersions.VER1_20_2.atOrAbove() && packet().getNewParticles().size() > 0) {
			Particle particle = Particle.valueOf(MinecraftVersions.VER1_20_5.atOrAbove()
				? "EXPLOSION" : "EXPLOSION_NORMAL");
			for (int i = 0; i < packet().getNewParticles().size(); i++) {
				packet().getNewParticles().write(i, WrappedParticle.create(particle, null));
			}
			Sound sound = Sound.valueOf("ENTITY_GENERIC_EXPLODE");
			if (MinecraftVersions.VER1_20_5.atOrAbove()) {
				packet().getSoundEffects().write(0, sound);
			} else {
				// 1.20.3/1.20.4 explosions store SoundEvent directly, unlike sound packets.
				packet().getModifier().withType(MinecraftReflection.getSoundEffectClass(),
					BukkitConverters.getSoundConverter()).write(0, sound);
			}
		}
	}

	private Object emptyBlockParticles() {
		if (emptyBlockParticles != null) {
			return emptyBlockParticles;
		}
		Class<?> type = packet().getModifier().getField(6).getType();
		for (Method method : type.getDeclaredMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0
				&& method.getReturnType() == type) {
				try {
					method.setAccessible(true);
					emptyBlockParticles = method.invoke(null);
					return emptyBlockParticles;
				} catch (ReflectiveOperationException exception) {
					throw new IllegalStateException("Cannot initialize explosion block particles", exception);
				}
			}
		}
		throw new IllegalStateException("Missing empty explosion block-particle factory on " + type);
	}
}
