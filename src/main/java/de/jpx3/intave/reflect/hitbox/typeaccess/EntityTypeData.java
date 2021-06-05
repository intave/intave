package de.jpx3.intave.reflect.hitbox.typeaccess;

import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;

public final class EntityTypeData {
  private final String entityName;
  private final HitBoxBoundaries hitBoxBoundaries;
  private final int entityTypeId;
  private final boolean isLivingEntity;

  /*
  This Constructor will be removed in the future, so please don't make new usages with it.
   */
  @Deprecated
  public EntityTypeData(String entityName, HitBoxBoundaries hitBoxBoundaries, int entityTypeId) {
    this.entityName = entityName;
    this.hitBoxBoundaries = hitBoxBoundaries;
    this.entityTypeId = entityTypeId;
    this.isLivingEntity = true;
  }

  public EntityTypeData(String entityName, HitBoxBoundaries hitBoxBoundaries, int entityTypeId, boolean isLivingEntity) {
    this.entityName = entityName;
    this.hitBoxBoundaries = hitBoxBoundaries;
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

  public HitBoxBoundaries hitBoxBoundaries() {
    return hitBoxBoundaries;
  }
}