package de.jpx3.intave.entity.size;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import de.jpx3.intave.share.ResourceLocation;
import net.minecraft.server.v1_13_R2.MinecraftKey;
import net.minecraft.server.v1_14_R1.EntitySize;
import net.minecraft.server.v1_8_R3.EntityTypes;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
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
        ResourceLocation resourceLocation = new ResourceLocation("minecraft", className.toLowerCase());
        MinecraftKey key = (MinecraftKey) resourceLocation.toNativeResourceLocation();
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
      Optional<net.minecraft.server.v1_14_R1.EntityTypes<?>> optional = net.minecraft.server.v1_14_R1.EntityTypes.a(className.toLowerCase());
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
        return HitboxSize.zero();
      }
    }
  }
}