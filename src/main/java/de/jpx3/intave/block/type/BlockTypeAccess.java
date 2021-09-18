package de.jpx3.intave.block.type;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import static de.jpx3.intave.diagnostic.timings.Timings.SERVICE_TYPE_LOOKUP;

@Relocate
@Deprecated
public final class BlockTypeAccess {
  public static final Material WEB = resolveFrom("WEB", "COBWEB");
  public static final Material SNOW_LAYER = resolveFrom("SNOW", "SNOW_LAYER");
  public static final Material TRAP_DOOR = resolveFrom("TRAP_DOOR", "LEGACY_TRAP_DOOR");
  public static final Material NETHER_PORTAL = resolveFrom("PORTAL", "NETHER_PORTAL");
  public static final Material SKULL = resolveFrom("SKULL", "LEGACY_SKULL");

  public static void setup() {
  }

  private static Material resolveFrom(String name, String alternativeName) {
    Material material = Material.getMaterial(name);
    if (material != null) {
      return material;
    }
    Material alternativeMaterial = Material.getMaterial(alternativeName);
    if (alternativeMaterial != null) {
      return alternativeMaterial;
    } else {
      throw new IntaveInternalException("Unable to find block " + name + " or " + alternativeName);
    }
  }
  private static final FileTypeTranslator translator = new VerTraFileTypeTranslator();
  private static final TypeTranslations typeTranslations;

  static {
    typeTranslations = translator.fromWithinJar("/mappings/bb-mappings");
  }

  public static void setupTranslationsFor(User user) {
    MinecraftVersion serverVersion = MinecraftVersion.getCurrentVersion();
    MinecraftVersion clientVersion = new MinecraftVersion(user.meta().protocol().versionString());
    user.clearTypeTranslations();
    typeTranslations.specifiedTo(serverVersion, clientVersion).asMap().forEach(user::applyTypeTranslation);
  }

  /**
   * This method performs a direct type lookup, which will be incorrect if the underlying chunk has not been loaded yet.
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
   * This method performs a direct type lookup, which will be incorrect if the underlying chunk has not been loaded yet.
   * To avoid this, use {@link VolatileBlockAccess#typeAccess(User, World, int, int, int)} instead,
   * providing fast performance, a robust cache implementation and stable fallback
   */
  @Deprecated
  public static Material typeAccess(Block block, Player player) {
    return translate(UserRepository.userOf(player), typeAccess(block));
  }

  public static boolean hasTranslation(User user, Material origin) {
    return user.typeTranslationOf(origin) != null;
  }

  private static Material translate(User user, Material origin) {
    Material alternative = user.typeTranslationOf(origin);
    return alternative == null ? origin : alternative;
  }
}