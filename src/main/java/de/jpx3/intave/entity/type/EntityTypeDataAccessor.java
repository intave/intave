package de.jpx3.intave.entity.type;

public final class EntityTypeDataAccessor {
  private static EntityTypeDataRegistry registry;

  public static void setup() {
    registry = new EntityTypeDataRegistry();
  }

  public static EntityTypeData resolveFromId(int entityTypeId, boolean isLivingEntity) {
    return registry.resolveFor(entityTypeId, isLivingEntity);
  }
}
