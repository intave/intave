package de.jpx3.intave.entity.size;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.share.MinecraftKey;
import net.minecraft.server.v1_14_R1.EntitySize;
import net.minecraft.server.v1_8_R3.EntityTypes;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HitboxSizeAccess {
  private static HitboxSizeResolver resolver;

  public static void setup() {
    boolean useNewResolver = MinecraftVersions.VER1_14_0.atOrAbove();
    String className = useNewResolver
      ? "de.jpx3.intave.entity.size.HitboxSizeAccess$HitBoxAccessModern"
      : "de.jpx3.intave.entity.size.HitboxSizeAccess$HitBoxAccessLegacy";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    resolver = useNewResolver ? new HitBoxAccessModern() : new HitBoxAccessLegacy();
  }

  public static HitboxSize dimensionsOfBukkit(Entity entity) {
    return resolver.sizeOf(entity);
  }

  public static HitboxSize dimensionsOfNative(Object serverEntity) {
    return resolver.sizeOf(serverEntity);
  }

  private static final Map<Class<?>, HitboxSize> nameCache = new ConcurrentHashMap<>();

  public static HitboxSize dimensionsOfNMSEntityClass(Class<?> klass) {
    return nameCache.computeIfAbsent(klass, resolver::sizeOf);
  }

  @PatchyAutoTranslation
  public static final class HitBoxAccessLegacy implements HitboxSizeResolver {
    @PatchyAutoTranslation
    @Override
    public HitboxSize sizeOf(Entity entity) {
      net.minecraft.server.v1_8_R3.Entity serverEntity = ((CraftEntity) entity).getHandle();
      return HitboxSize.of(serverEntity.width, serverEntity.length);
    }

    @PatchyAutoTranslation
    @Override
    public HitboxSize sizeOf(Object serverEntity) {
      net.minecraft.server.v1_8_R3.Entity theServerEntity =
        (net.minecraft.server.v1_8_R3.Entity) (serverEntity);
      return HitboxSize.of(theServerEntity.width, theServerEntity.length);
    }

    @PatchyAutoTranslation
    @Override
    public HitboxSize sizeOf(Class<?> entityClass) {
      String className = entityClass.getSimpleName();
      if (className.startsWith("Entity")) {
        className = className.substring("Entity".length());
      }
      Object worldObj = ReflectiveHandleAccess.handleOf(Bukkit.getWorlds().get(0));
      Object entityObj;
      if (MinecraftVersions.VER1_13_0.atOrAbove()) {
        MinecraftKey minecraftKey = new MinecraftKey("minecraft", className.toLowerCase());
        net.minecraft.server.v1_13_R2.MinecraftKey key = (net.minecraft.server.v1_13_R2.MinecraftKey) minecraftKey.toNativeResourceLocation();
        entityObj = net.minecraft.server.v1_13_R2.EntityTypes.a((net.minecraft.server.v1_13_R2.World) worldObj, key);
      } else if (MinecraftVersions.VER1_11_0.atOrAbove()) {
        entityObj = net.minecraft.server.v1_11_R1.EntityTypes.a(
          (Class<? extends net.minecraft.server.v1_11_R1.Entity>) entityClass,
          (net.minecraft.server.v1_11_R1.World) worldObj
        );
      } else {
        entityObj = EntityTypes.createEntityByName(className, (World) worldObj);
      }
      net.minecraft.server.v1_8_R3.Entity serverEntity = (net.minecraft.server.v1_8_R3.Entity) entityObj;
      return HitboxSize.of(serverEntity.width, serverEntity.length);
    }
  }

  @PatchyAutoTranslation
  public static final class HitBoxAccessModern implements HitboxSizeResolver {
    @PatchyAutoTranslation
    @Override
    public HitboxSize sizeOf(Entity entity) {
      net.minecraft.server.v1_14_R1.Entity serverEntity = ((org.bukkit.craftbukkit.v1_14_R1.entity.CraftEntity) entity).getHandle();
      return HitboxSize.of(serverEntity.getWidth(), serverEntity.getHeight());
    }

    @PatchyAutoTranslation
    @Override
    public HitboxSize sizeOf(Object serverEntity) {
      float width = ((net.minecraft.server.v1_14_R1.Entity) (serverEntity)).getWidth();
      float length = ((net.minecraft.server.v1_14_R1.Entity) (serverEntity)).getHeight();
      return HitboxSize.of(width, length);
    }

    private final MethodHandle sizeAccess = MethodSearchBySignature.search(
      Lookup.serverClass("EntityTypes"),
      new Class[0],
      Lookup.serverClass("EntitySize")
    ).findFirstOrThrow();

    private static Method widthAccess;
    private static Method heightAccess;

    @PatchyAutoTranslation
    @Override
    public HitboxSize sizeOf(Class<?> entityClass) {
      String className = entityClass.getSimpleName();
      if (className.startsWith("Entity")) {
        className = className.substring("Entity".length());
      }
      // The registry is keyed in snake_case ("armor_stand", "ender_dragon") while the
      // class name is CamelCase, so looking it up as one lowercase word only ever
      // matched single-word types. Everything else fell through to a ZERO hitbox, which
      // makes the entity impossible to hit: every attack on an armor stand came back
      // "out of sight" and got cancelled. Ask Bukkit for the entity type's real key
      // first (it also covers the names that do not follow the class at all, such as
      // MushroomCow -> mooshroom), then fall back to name-derived guesses.
      Optional<net.minecraft.server.v1_14_R1.EntityTypes<?>> optional = Optional.empty();
      for (String candidate : registryKeyCandidates(entityClass, className)) {
        optional = net.minecraft.server.v1_14_R1.EntityTypes.a(candidate);
        if (optional.isPresent()) {
          break;
        }
      }
      if (optional.isPresent()) {
        net.minecraft.server.v1_14_R1.EntityTypes<?> entityTypes = optional.get();
        Object size;
        try {
          size = sizeAccess.invoke(entityTypes);
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
        EntitySize k = (EntitySize) size;
        if (MinecraftVersions.VER1_21.atOrAbove()) {
          if (widthAccess == null || heightAccess == null) {
            try {
              widthAccess = EntitySize.class.getMethod("width");
              heightAccess = EntitySize.class.getMethod("height");
            } catch (NoSuchMethodException e) {
              throw new RuntimeException(e);
            }
          }
          try {
            float width = (float) widthAccess.invoke(k);
            float height = (float) heightAccess.invoke(k);
            return HitboxSize.of(width, height);
          } catch (Throwable e) {
            throw new RuntimeException(e);
          }
        } else {
          return HitboxSize.of(k.width, k.height);
        }
      } else {
        // A zero hitbox is not a safe answer: nothing can ever hit the entity and every
        // attack on it is reported as a miss. Report it once and fall back to a
        // player-sized box, which is wrong by centimetres instead of by everything.
        if (REPORTED_UNRESOLVED_HITBOXES.add(className)) {
          IntaveLogger.logger().warn("Could not resolve the hitbox of " + className
            + " - using a player-sized one for it");
        }
        return HitboxSize.playerDefault();
      }
    }
  }

  static final Set<String> REPORTED_UNRESOLVED_HITBOXES = ConcurrentHashMap.newKeySet();
  static Method entityTypeKeyAccess;
  static boolean entityTypeKeyAccessResolved;

  /**
   * Registry keys to try for an entity class, best first. Bukkit's own key is
   * authoritative where it is available - it is the registry key by definition, and it
   * is the only one that gets the types whose name does not follow their class right
   * (MushroomCow is "mooshroom", Snowman is "snow_golem", PrimedTnt is "tnt"). The
   * name-derived guesses stay as a fallback for NMS classes, which have no Bukkit type.
   */
  static java.util.List<String> registryKeyCandidates(Class<?> entityClass, String className) {
    java.util.List<String> candidates = new java.util.ArrayList<>(4);
    for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
      if (type.getEntityClass() != entityClass) {
        continue;
      }
      String key = bukkitRegistryKeyOf(type);
      if (key != null) {
        candidates.add(key);
      }
      candidates.add(type.name().toLowerCase());
      break;
    }
    candidates.add(className.toLowerCase());
    candidates.add(snakeCaseKeyOf(className));
    return candidates;
  }

  /**
   * {@code EntityType#getKey()} exists since 1.14 but not in the API this is compiled
   * against, so it is called reflectively; a server without it just uses the other
   * candidates.
   */
  static String bukkitRegistryKeyOf(org.bukkit.entity.EntityType type) {
    if (!entityTypeKeyAccessResolved) {
      entityTypeKeyAccessResolved = true;
      try {
        entityTypeKeyAccess = org.bukkit.entity.EntityType.class.getMethod("getKey");
      } catch (NoSuchMethodException ignored) {
        entityTypeKeyAccess = null;
      }
    }
    if (entityTypeKeyAccess == null) {
      return null;
    }
    try {
      Object namespacedKey = entityTypeKeyAccess.invoke(type);
      return namespacedKey == null ? null
        : (String) namespacedKey.getClass().getMethod("getKey").invoke(namespacedKey);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Converts a CamelCase entity class name into the registry key it is stored under
   * ({@code ArmorStand} to {@code armor_stand}). Runs of capitals are kept together so
   * acronym-style names do not explode into single letters.
   */
  static String snakeCaseKeyOf(String className) {
    StringBuilder key = new StringBuilder(className.length() + 4);
    for (int index = 0; index < className.length(); index++) {
      char character = className.charAt(index);
      if (Character.isUpperCase(character)) {
        boolean previousIsLower = index > 0 && !Character.isUpperCase(className.charAt(index - 1));
        boolean nextIsLower = index + 1 < className.length() && !Character.isUpperCase(className.charAt(index + 1));
        if (index > 0 && (previousIsLower || nextIsLower)) {
          key.append('_');
        }
        key.append(Character.toLowerCase(character));
      } else {
        key.append(character);
      }
    }
    return key.toString();
  }
}