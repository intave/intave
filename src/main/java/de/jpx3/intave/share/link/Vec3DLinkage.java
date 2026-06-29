package de.jpx3.intave.share.link;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.share.RawVector3d;
import net.minecraft.server.v1_8_R3.Vec3D;

public final class Vec3DLinkage {
  static ClassConverter<RawVector3d> resolveVec3DConverter() {
    boolean atLeastCombatUpdate = MinecraftVersions.VER1_9_0.atOrAbove();
    String vec3DResolverClass = atLeastCombatUpdate
      ? "de.jpx3.intave.share.link.Vec3DLinkage$Vec3DCombatUpdateResolver"
      : "de.jpx3.intave.share.link.Vec3DLinkage$Vec3DLegacyResolver";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), vec3DResolverClass);
    return atLeastCombatUpdate ? new Vec3DCombatUpdateResolver() : new Vec3DLegacyResolver();
  }

  @PatchyAutoTranslation
  public static final class Vec3DLegacyResolver implements ClassConverter<RawVector3d> {
    @PatchyAutoTranslation
    @Override
    public RawVector3d convert(Object obj) {
      Vec3D vec3D = (Vec3D) obj;
      return new RawVector3d(vec3D.a, vec3D.b, vec3D.c);
    }
  }

  @PatchyAutoTranslation
  public static final class Vec3DCombatUpdateResolver implements ClassConverter<RawVector3d> {
    @PatchyAutoTranslation
    @Override
    public RawVector3d convert(Object obj) {
      net.minecraft.server.v1_9_R2.Vec3D vec3D = (net.minecraft.server.v1_9_R2.Vec3D) obj;
      return new RawVector3d(vec3D.x, vec3D.y, vec3D.z);
    }
  }
}