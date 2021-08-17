package de.jpx3.intave.event.violation;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public final class EntityNoDamageTickChanger {
  private static boolean hitDelayLinkageError = false;

  public static void applyHurtTimeChangeTo(Player player, int durationTicks, int additionalHurtTime) {
    if (hitDelayLinkageError) {
      return;
    }

    User user = UserRepository.userOf(player);
    PunishmentMetadata punishmentData = user.meta().punishment();

    // Already changed
    if (punishmentData.damageTicksBefore != -1) {
      return;
    }

    int noDamageTicksBefore = resolveNoDamageTicksOf(player);
    int newNoDamageTicks = calculateNewNoDamageTicks(noDamageTicksBefore, additionalHurtTime);
    punishmentData.damageTicksBefore = noDamageTicksBefore;
    punishmentData.appliedDamageTicks = newNoDamageTicks;
    setNoDamageTicksOf(player, newNoDamageTicks);
    Synchronizer.synchronizeDelayed(() -> removeNoDamageTickChangeOf(user), durationTicks);
  }

  private static int calculateNewNoDamageTicks(int noDamageTicks, int ticks) {
    return Math.max(0, noDamageTicks + ticks);
  }

  public static void removeNoDamageTickChangeOf(User user) {
    Player player = user.player();
    PunishmentMetadata punishmentData = user.meta().punishment();
    if (punishmentData.appliedDamageTicks != resolveNoDamageTicksOf(player)) {
      // The server has changed the noDamageTicks field, do not override
      punishmentData.damageTicksBefore = -1;
      return;
    }
    int noDamageTicksBefore = punishmentData.damageTicksBefore;
    setNoDamageTicksOf(player, noDamageTicksBefore);
    punishmentData.damageTicksBefore = -1;
  }

  private static int resolveNoDamageTicksOf(Player player) {
    try {
      Object handle = ReflectiveHandleAccess.handleOf(player);
      Field maxDamageTicks = Lookup.serverField("EntityLiving", "maxNoDamageTicks");;
      return (int) maxDamageTicks.get(handle);
    } catch (IllegalAccessException exception) {
      exception.printStackTrace();
      IntaveLogger.logger().error("Intave has problems accessing an entity field");
      hitDelayLinkageError = true;
    }
    return -1;
  }

  public static void setNoDamageTicksOf(Player player, int noDamageTicks) {
    try {
      Field maxDamageTicks = Lookup.serverField("EntityLiving", "maxNoDamageTicks");//handle.getClass().getField("maxNoDamageTicks");
      Object handle = ReflectiveHandleAccess.handleOf(player);
      maxDamageTicks.set(handle, noDamageTicks);
    } catch (IllegalAccessException exception) {
      exception.printStackTrace();
      IntaveLogger.logger().error("Intave has problems accessing an entity field");
      hitDelayLinkageError = true;
    }
  }
}