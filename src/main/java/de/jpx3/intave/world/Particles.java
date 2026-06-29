package de.jpx3.intave.world;

import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Effect;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class Particles {
	public static void spawnVillagerHappyParticleAt(User user, Position position) {
		Player player = user.player();
		World world = player.getWorld();
		Object villagerHappyParticle = villagerHappyParticle();
		if (villagerHappyParticleCacheFailed) {
			player.playEffect(position.toLocation(world), Effect.HAPPY_VILLAGER, 0);
		} else {
			player.spawnParticle(
				(Particle) villagerHappyParticle,
				position.toLocation(world), 1
			);
		}
	}

	private static Object villagerHappyParticleCache;
	private static Boolean villagerHappyParticleCacheFailed = false;

	private static Object villagerHappyParticle() {
		if (villagerHappyParticleCache == null && !villagerHappyParticleCacheFailed) {
			try {
				try {
					villagerHappyParticleCache = Particle.VILLAGER_HAPPY;
				} catch (NoSuchFieldError e) {
					villagerHappyParticleCache = Particle.class.getField("HAPPY_VILLAGER").get(null);
				} catch (NoClassDefFoundError e) {
					villagerHappyParticleCacheFailed = true;
				}
			} catch (IllegalAccessException | NoSuchFieldException e) {
				villagerHappyParticleCacheFailed = true;
				throw new RuntimeException(e);
			}
		}
		return villagerHappyParticleCache;
	}
}
