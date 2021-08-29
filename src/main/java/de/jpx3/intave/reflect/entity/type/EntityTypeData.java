package de.jpx3.intave.reflect.entity.type;

import de.jpx3.intave.reflect.entity.size.HitboxSize;

public final class EntityTypeData {
  private final String entityName;
  private final HitboxSize hitBoxSize;
  private final int entityTypeId;
  private final boolean isLivingEntity;

  public EntityTypeData(String entityName, HitboxSize hitBoxSize, int entityTypeId, boolean isLivingEntity) {
    this.entityName = entityName;
    this.hitBoxSize = hitBoxSize;
    this.entityTypeId = entityTypeId;
    this.isLivingEntity = isLivingEntity;
  }

  public boolean isLivingEntity() {
    return isLivingEntity;
  }

  public String entityName() {
    return entityName;
  }

  public int entityTypeId() {
    return entityTypeId;
  }

  public HitboxSize hitBoxBoundaries() {
    return hitBoxSize;
  }
}