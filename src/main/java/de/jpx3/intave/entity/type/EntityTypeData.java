package de.jpx3.intave.entity.type;

import de.jpx3.intave.entity.size.HitboxSize;

import java.util.Locale;

public final class EntityTypeData {
  private final String entityName;
  private final HitboxSize hitBoxSize;
  private final int entityTypeId;
  private final boolean isLivingEntity;
  public final int creationId;
  private boolean boat;
  private boolean shulker;
  private boolean fireball;
  private boolean armorstand;
  private boolean slimeLike;

  public EntityTypeData(String entityName, HitboxSize hitBoxSize, int entityTypeId, boolean isLivingEntity, int creationId) {
    this.entityName = entityName;
    this.hitBoxSize = hitBoxSize;
    this.entityTypeId = entityTypeId;
    this.isLivingEntity = isLivingEntity;
    this.creationId = creationId;

    String lowercaseName = entityName.toLowerCase(Locale.ROOT);
    switch (lowercaseName) {
      case "boat":
      case "chestboat":
        this.boat = true;
        break;
      case "shulker":
        this.shulker = true;
        break;
      case "largefireball":
      case "smallfireball":
        this.fireball = true;
        break;
      case "armorstand":
        this.armorstand = true;
        break;
      case "slime":
      case "magmacube":
        this.slimeLike = true;
        break;
    }
  }

  /**
   * Slimes and magma cubes are the entities whose hitbox is per-instance rather than
   * per-type: it scales with the size value they carry in their metadata. Everything
   * else of their type shares one box.
   */
  public boolean isSlimeLike() {
    return slimeLike;
  }

  /**
   * A copy of this type with a different hitbox, for the entities whose real size only
   * becomes known from their metadata.
   */
  public EntityTypeData withSize(HitboxSize size) {
    return new EntityTypeData(entityName, size, entityTypeId, isLivingEntity, creationId);
  }

  public boolean isLivingEntity() {
    return isLivingEntity && !"Egg".equalsIgnoreCase(entityName);
  }

  public boolean isBoat() {
    return boat;
  }

  public boolean isShulker() {
    return shulker;
  }

  public boolean fireball() {
    return fireball;
  }

  public boolean isArmorStand() {
    return armorstand;
  }

  public String name() {
    return entityName;
  }

  public int typeId() {
    return entityTypeId;
  }

  public HitboxSize size() {
    return hitBoxSize;
  }
}