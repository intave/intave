/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.entity.type;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.entity.size.HitboxSize;

import java.util.Locale;

public final class EntityTypeData {
  private final String entityName;
  private final HitboxSize hitBoxSize;
  private final int entityTypeId;
  private final boolean isLivingEntity;
  public final int creationId;
  private final boolean boat;
  private final boolean shulker;
  private final boolean fireball;
  private final boolean armorstand;
  private final @Nullable RideableEntityType rideableType;

  public EntityTypeData(String entityName, HitboxSize hitBoxSize, int entityTypeId, boolean isLivingEntity, int creationId) {
	  this.entityName = entityName;
    this.hitBoxSize = hitBoxSize;
    this.entityTypeId = entityTypeId;
    this.isLivingEntity = isLivingEntity;
    this.creationId = creationId;

    boolean shulker = false;
    boolean fireball = false;
    boolean armorstand = false;
    String lowercaseName = entityName.toLowerCase(Locale.ROOT);
    switch (lowercaseName) {
      case "shulker":
        shulker = true;
        break;
      case "largefireball":
      case "smallfireball":
        fireball = true;
        break;
      case "armorstand":
        armorstand = true;
        break;
    }
    this.shulker = shulker;
    this.fireball = fireball;
    this.armorstand = armorstand;
	  rideableType = RideableEntityType.identifyFrom(entityName);
    this.boat = rideableType == RideableEntityType.BOAT || rideableType == RideableEntityType.CHEST_BOAT;
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

  public boolean isFireball() {
    return fireball;
  }

  public boolean isArmorStand() {
    return armorstand;
  }

  public double mountedYOffset() {
    float height = hitBoxSize.height();
    double def = height * 0.75;
    switch (rideableType) {
      case BOAT:
      case CHEST_BOAT:
        return 0.1;
      case LLAMA:
        return height * 0.67;
      case RAVAGER:
        return 2.1;
      case MINECART:
        return 0;
      case SPIDER:
        return height * 0.5;
      case SKELETON_HORSE:
        return def - 0.1875D;
      default:
        return def;
    }
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
