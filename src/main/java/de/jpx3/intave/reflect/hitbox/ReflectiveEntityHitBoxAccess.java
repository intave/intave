package de.jpx3.intave.reflect.hitbox;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;

public final class ReflectiveEntityHitBoxAccess {
  private static EntityHitBoxResolver resolver;

  public static void setup() {
    boolean useNewResolver = MinecraftVersions.VER1_14_0.atOrAbove();
    String className = useNewResolver
      ? "de.jpx3.intave.reflect.hitbox.ReflectiveEntityHitBoxAccess$HitBoxAccessNew"
      : "de.jpx3.intave.reflect.hitbox.ReflectiveEntityHitBoxAccess$HitBoxAccessLegacy";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    resolver = useNewResolver ? new HitBoxAccessNew() : new HitBoxAccessLegacy();
  }

  public static HitBoxBoundaries boundariesOf(Entity entity) {
    return resolver.hitBoxOf(entity);
  }

  public static HitBoxBoundaries boundariesOf(Object serverEntity) {
    return resolver.hitBoxOf(serverEntity);
  }

  @PatchyAutoTranslation
  public static final class HitBoxAccessLegacy implements EntityHitBoxResolver {
    @PatchyAutoTranslation
    @Override
    public HitBoxBoundaries hitBoxOf(Entity entity) {
      net.minecraft.server.v1_8_R3.Entity serverEntity = ((CraftEntity) entity).getHandle();
      return HitBoxBoundaries.of(serverEntity.width, serverEntity.length);
    }

    @PatchyAutoTranslation
    @Override
    public HitBoxBoundaries hitBoxOf(Object serverEntity) {
      float width = ((net.minecraft.server.v1_8_R3.Entity) (serverEntity)).width;
      float length = ((net.minecraft.server.v1_8_R3.Entity) (serverEntity)).length;
      return HitBoxBoundaries.of(width, length);
    }
  }

  @PatchyAutoTranslation
  public static final class HitBoxAccessNew implements EntityHitBoxResolver {
    @PatchyAutoTranslation
    @Override
    public HitBoxBoundaries hitBoxOf(Entity entity) {
      net.minecraft.server.v1_14_R1.Entity serverEntity = ((org.bukkit.craftbukkit.v1_14_R1.entity.CraftEntity) entity).getHandle();
      return HitBoxBoundaries.of(serverEntity.getWidth(), serverEntity.getHeight());
    }

    @PatchyAutoTranslation
    @Override
    public HitBoxBoundaries hitBoxOf(Object serverEntity) {
      float width = ((net.minecraft.server.v1_14_R1.Entity) (serverEntity)).getWidth();
      float length = ((net.minecraft.server.v1_14_R1.Entity) (serverEntity)).getHeight();
      return HitBoxBoundaries.of(width, length);
    }
  }
}