package de.jpx3.intave.executor;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Teleports a player in a way that is valid on the current server. Folia forbids
 * the synchronous {@link org.bukkit.entity.Entity#teleport(Location)} on a region
 * thread ("Must use teleportAsync while in region threading") — Intave's setback,
 * dismount and desync-reset teleports all run on the owning region thread, so on
 * Folia they must go through {@code teleportAsync}. That method is resolved
 * reflectively because Intave compiles against a Spigot API that predates it.
 */
public final class FoliaSafeTeleport {
  private static final boolean FOLIA = Synchronizer.onFolia();
  private static final Method TELEPORT_ASYNC = resolveTeleportAsync();
  private static final Method IS_OWNED_BY_CURRENT_REGION = resolveOwnershipCheck();

  private FoliaSafeTeleport() {
  }

  /**
   * Whether the current thread may move an entity <b>to</b> this location. Region
   * threading owns a contiguous group of chunks, and moving an entity into a chunk
   * another region owns throws {@code Cannot move entity off-main} from
   * {@code EntityLookup.moveEntity} — which is what the packet-based setback teleport
   * (it repositions the server-side entity directly, bypassing {@code teleportAsync})
   * hits whenever the position it drags the player back to has ended up in a
   * different region than the one currently ticking them.
   * <p>
   * Answers {@code true} off Folia and whenever the check itself cannot be resolved,
   * so the caller keeps its existing behaviour rather than losing the setback.
   */
  public static boolean ownedByCurrentRegion(Location location) {
    if (!FOLIA || IS_OWNED_BY_CURRENT_REGION == null || location == null) {
      return true;
    }
    try {
      return (Boolean) IS_OWNED_BY_CURRENT_REGION.invoke(null, location);
    } catch (ReflectiveOperationException reflectionFailure) {
      return true;
    }
  }

  private static Method resolveOwnershipCheck() {
    if (!FOLIA) {
      return null;
    }
    try {
      return org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
    } catch (NoSuchMethodException noOwnershipCheck) {
      return null;
    }
  }

  public static void teleport(Player player, Location location) {
    if (FOLIA && TELEPORT_ASYNC != null) {
      try {
        TELEPORT_ASYNC.invoke(player, location);
        return;
      } catch (ReflectiveOperationException reflectionFailure) {
        // Should not happen on a server that offers teleportAsync; fall back to
        // the synchronous call below so the teleport still happens off Folia.
      }
    }
    player.teleport(location);
  }

  private static Method resolveTeleportAsync() {
    if (!FOLIA) {
      return null;
    }
    try {
      // Declared on Entity; getMethod finds the inherited public method.
      return Player.class.getMethod("teleportAsync", Location.class);
    } catch (NoSuchMethodException noAsyncTeleport) {
      return null;
    }
  }
}
