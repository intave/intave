package de.jpx3.intave.entity.type;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.entity.size.HitboxSize;

public final class EntityTypeDataAccessor {
  private static final boolean DIRECT_RESOLVE = MinecraftVersions.VER1_14_0.atOrAbove();
  private static EntityTypeDataResolver resolver;

  public static void setup() {
    if (DIRECT_RESOLVE) {
      resolver = new ServerEntityTypeDataLookup();
    } else {
      EntityTypeDataRegistry.setup();
      resolver = new EntityTypeDataRegistry();
    }
  }

  public static EntityTypeData resolveFromId(int entityTypeId, boolean isLivingEntity) {
    return resolver.resolveFor(entityTypeId, isLivingEntity);
  }

  public static HitboxSize sizeByKey(String key) {
    return ServerEntityTypeDataLookup.dimensionsByKey(key);
  }
}
