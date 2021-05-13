package de.jpx3.intave.reflect.hitbox.typeaccess;

import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;

public final class EntityTypeData {
  private final String entityName;
  private final HitBoxBoundaries hitBoxBoundaries;

  public EntityTypeData(String entityName, HitBoxBoundaries hitBoxBoundaries) {
    this.entityName = entityName;
    this.hitBoxBoundaries = hitBoxBoundaries;
  }

  public String entityName() {
    return entityName;
  }

  public HitBoxBoundaries hitBoxBoundaries() {
    return hitBoxBoundaries;
  }
}