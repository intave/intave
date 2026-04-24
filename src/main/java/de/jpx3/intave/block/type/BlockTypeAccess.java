package de.jpx3.intave.block.type;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.diagnostic.timings.Timings.SERVICE_TYPE_LOOKUP;

public final class BlockTypeAccess {
  public static final Material WEB = resolveFrom("COBWEB", "WEB");
  public static final Material SNOW_LAYER = resolveFrom("SNOW", "SNOW_LAYER");
  public static final Material TRAP_DOOR = resolveFrom("TRAP_DOOR", "LEGACY_TRAP_DOOR");
  public static final Material FARMLAND = resolveFrom("FARMLAND", "SOIL");
  public static final Material NETHER_PORTAL = resolveFrom("PORTAL", "NETHER_PORTAL");
  public static final Material END_PORTAL_FRAME = resolveFrom("END_PORTAL_FRAME", "ENDER_PORTAL_FRAME");
  public static final Material SKULL = resolveFrom("SKULL", "LEGACY_SKULL");
  public static final Material COBBLESTONE_WALL = resolveFrom("COBBLESTONE_WALL", "COBBLE_WALL");

  public static void setup() {
  }

  private static Material resolveFrom(String... names) {
    for (String name : names) {
      Material material = Material.getMaterial(name);
      if (material != null) {
        return material;
      } else {
        material = Material.getMaterial("LEGACY_" + name);
        if (material != null) {
          return material;
        }
      }
    }
    throw new IntaveInternalException("Unable to find block for " + Arrays.toString(names));
  }

  private static final Resource MAPPING_RESOURCE = Resources.localServiceCacheResource("bbm/" + IntavePlugin.version(),  "bbm", TimeUnit.DAYS.toMillis(14));
  private static final TypeTranslations TYPE_TRANSLATIONS = MAPPING_RESOURCE.collectLines(VerTraFileTypeTranslator.lineCollector());

  public static void setupTranslationsFor(User user) {
    MinecraftVersion serverVersion = MinecraftVersion.getCurrentVersion();
    MinecraftVersion clientVersion = user.meta().protocol().minecraftVersion();
    user.clearTypeTranslations();
    TYPE_TRANSLATIONS.specifiedTo(serverVersion, clientVersion).forEachType(user::applyTypeTranslation);
  }

  /**
   * This method performs a direct type lookup, which will cause lag if the underlying chunk has not been loaded yet.
   * To avoid this, use {@link VolatileBlockAccess#typeAccess(User, World, int, int, int)} instead,
   * providing fast performance, a robust cache implementation and stable fallback
   */
  @Deprecated
  public static Material typeAccess(Block block) {
    try {
      SERVICE_TYPE_LOOKUP.start();
      return BlockAccess.global().typeOf(block);
    } finally {
      SERVICE_TYPE_LOOKUP.stop();
    }
  }

  /**
   * This method performs a direct type lookup, which will cause lag if the underlying chunk has not been loaded yet.
   * To avoid this, use {@link VolatileBlockAccess#typeAccess(User, World, int, int, int)} instead,
   * providing fast performance, a robust cache implementation and stable fallback
   */
  @Deprecated
  public static Material typeAccess(Block block, Player player) {
    Material type = typeAccess(block);
    return player == null ? type : translate(UserRepository.userOf(player), type);
  }

  public static boolean hasTranslation(User user, Material origin) {
    return user.typeTranslationOf(origin) != null;
  }

  private static Material translate(User user, Material origin) {
    Material alternative = user.typeTranslationOf(origin);
    return alternative == null ? origin : alternative;
  }
}
