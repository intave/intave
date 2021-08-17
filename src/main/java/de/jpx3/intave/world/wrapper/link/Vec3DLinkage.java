package de.jpx3.intave.world.wrapper.link;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.wrapper.WrappedVector;
import net.minecraft.server.v1_8_R3.Vec3D;

public final class Vec3DLinkage {
  static ClassLinker<WrappedVector> resolveVec3DLinker() {
    boolean atLeastCombatUpdate = MinecraftVersions.VER1_9_0.atOrAbove();
    String vec3DResolverClass = atLeastCombatUpdate
      ? "de.jpx3.intave.world.wrapper.link.Vec3DLinkage$Vec3DCombatUpdateResolver"
      : "de.jpx3.intave.world.wrapper.link.Vec3DLinkage$Vec3DLegacyResolver";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), vec3DResolverClass);
    return atLeastCombatUpdate ? new Vec3DCombatUpdateResolver() : new Vec3DLegacyResolver();
  }

  @PatchyAutoTranslation
  public static final class Vec3DLegacyResolver implements ClassLinker<WrappedVector> {
    @PatchyAutoTranslation
    @Override
    public WrappedVector link(Object obj) {
      Vec3D vec3D = (Vec3D) obj;
      return new WrappedVector(vec3D.a, vec3D.b, vec3D.c);
    }
  }

  @PatchyAutoTranslation
  public static final class Vec3DCombatUpdateResolver implements ClassLinker<WrappedVector> {
    @PatchyAutoTranslation
    @Override
    public WrappedVector link(Object obj) {
      net.minecraft.server.v1_9_R2.Vec3D vec3D = (net.minecraft.server.v1_9_R2.Vec3D) obj;
      return new WrappedVector(vec3D.x, vec3D.y, vec3D.z);
    }
  }
}