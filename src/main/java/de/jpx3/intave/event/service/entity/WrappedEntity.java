package de.jpx3.intave.event.service.entity;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.tools.hitbox.HitBoxBoundaries;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;

public class WrappedEntity {
  private final String entityName;

  public boolean isEntityLiving;
  public final HitBoxBoundaries hitBoxBoundaries;

  /**
   * Indicates if the entity position is synchronized with the client
   */
  public volatile boolean clientSynchronized;

  /**
   * This value is used to interpolate the positions of the Entity
   */
  protected int newPosRotationIncrements;
  public int serverPosX, serverPosY, serverPosZ;

  public EntityPositionContext positions;
  public EntityPositionContext alternativePositions;

  private WrappedAxisAlignedBB boundingBox;

  public WrappedEntity(
    String entityName,
    boolean isEntityLiving,
    HitBoxBoundaries hitBoxBoundaries
  ) {
    this.isEntityLiving = isEntityLiving;
    this.hitBoxBoundaries = hitBoxBoundaries;
    this.entityName = entityName;

    this.positions = new EntityPositionContext();
    this.alternativePositions = new EntityPositionContext();
  }

  public static class EntityPositionContext {
    public double prevPosX, prevPosY, prevPosZ;
    public double posX, posY, posZ;
    public double newPosX, newPosY, newPosZ;
  }

  /**
   * Interpolates the position of the entity between the position and the new position to make the entity move smoothly.
   * This method applies if the given entity has an instance of LivingEntity. Packets: (All types of movement packets)
   * FLYING, LOOK, POSITION, POSITION_LOOK
   */
  public void onLivingUpdate() {
    if (isEntityLiving) {
      if (this.newPosRotationIncrements > 0) {
        double newPosX = positions.posX + (positions.newPosX - positions.posX) / (double) this.newPosRotationIncrements;
        double newPosY = positions.posY + (positions.newPosY - positions.posY) / (double) this.newPosRotationIncrements;
        double alternativeNewPosY = alternativePositions.posY + (alternativePositions.newPosY - alternativePositions.posY) / (double) this.newPosRotationIncrements;
        double newPosZ = positions.posZ + (positions.newPosZ - positions.posZ) / (double) this.newPosRotationIncrements;

        --this.newPosRotationIncrements;
        setPosition(newPosX, newPosY, newPosZ);
        setPosition(alternativeNewPosY);
      }
    }
  }

  /**
   * Handles a teleportation. Packets: ENTITY_TELEPORT
   *
   * @param packet contains information about the entity teleportation
   */
  public void handleEntityTeleport(PacketContainer packet) {
    this.serverPosX = packet.getIntegers().read(1);
    this.serverPosY = packet.getIntegers().read(2);
    this.serverPosZ = packet.getIntegers().read(3);

    double newPosX = (double) this.serverPosX / 32d;
    double newPosY = (double) this.serverPosY / 32d;
    double newPosZ = (double) this.serverPosZ / 32d;

    if (Math.abs(positions.posX - newPosX) < 0.03125d &&
      Math.abs(positions.posY - newPosY) < 0.015625d &&
      Math.abs(positions.posZ - newPosZ) < 0.03125d) {
      setPositionAndRotationEntityLiving(positions.posX, positions.posY, positions.posZ, 3);
    } else {
      setPositionAndRotationEntityLiving(newPosX, newPosY, newPosZ, 3);
    }

    double alternativeNewPosY = (double) this.serverPosY / 32d + 0.015625d;

    if (Math.abs(positions.posX - newPosX) < 0.03125d &&
      Math.abs(alternativePositions.posY - alternativeNewPosY) < 0.015625d &&
      Math.abs(positions.posZ - newPosZ) < 0.03125d) {
      setPositionAndRotationEntityLiving(alternativePositions.posY);
    } else {
      setPositionAndRotationEntityLiving(alternativeNewPosY);
    }
  }

  /**
   * Handles relative movement. Packets: REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK
   *
   * @param packet contains information about the entity movement
   */
  public void handleEntityMovement(PacketContainer packet) {
    this.serverPosX += packet.getBytes().readSafely(0);
    this.serverPosY += packet.getBytes().readSafely(1);
    this.serverPosZ += packet.getBytes().readSafely(2);

    double newPosX = (double) serverPosX / 32d;
    double newPosY = (double) serverPosY / 32d;
    double alternativeNewPosY = (double) serverPosY / 32d;
    double newPosZ = (double) serverPosZ / 32d;

    // 3 is used to interpolate the entity position in new client ticks
    setPositionAndRotationEntityLiving(newPosX, newPosY, newPosZ, 3);
    setPositionAndRotationEntityLiving(alternativeNewPosY);
  }

  /**
   * Used to set the position of an entity and moves its {@link WrappedAxisAlignedBB}. On the client side this is also
   * applied for rotation changes.
   */
  public void setPositionAndRotationSpawnMob(double x, double y, double z, double alternativeY) {
    positions.prevPosX = positions.posX = x;
    positions.prevPosY = positions.posY = y;
    alternativePositions.prevPosY = alternativePositions.posY = alternativeY;
    positions.prevPosZ = positions.posZ = z;

    setPosition(positions.posX, positions.posY, positions.posZ);
    setPosition(alternativePositions.posY);
  }

  /**
   * Sets the position of the entity and updates its {@link WrappedAxisAlignedBB}.
   */
  public void setPosition(double x, double y, double z) {
    positions.posX = x;
    positions.posY = y;
    positions.posZ = z;

    double halfWidth = this.hitBoxBoundaries.width() / 2.0;
    double length = this.hitBoxBoundaries.length();
    this.boundingBox = new WrappedAxisAlignedBB(
      x - halfWidth, y, z - halfWidth,
      x + halfWidth, y + length, z + halfWidth
    );
  }

  public void setPosition(double alternativeNewPosY) {
    alternativePositions.posY = alternativeNewPosY;
  }

  /**
   * Sets the position of the entity and the newPosRotationIncrements which is used to interpolate the entity position
   * in new ticks (Client side it also updates the rotation of the entity)
   *
   * @param newPosRotationIncrements the value which is used to interpolate the movement of the entity in new ticks
   */
  public void setPositionAndRotationEntityLiving(double x, double y, double z, int newPosRotationIncrements) {
    if (!isEntityLiving) {
      setPosition(x, y, z);
      return;
    }

    positions.newPosX = x;
    positions.newPosY = y;
    positions.newPosZ = z;
    this.newPosRotationIncrements = newPosRotationIncrements;
  }

  public void setPositionAndRotationEntityLiving(double alternativeY) {
    if (!isEntityLiving) {
      setPosition(alternativeY);
      return;
    }

    alternativePositions.newPosY = alternativeY;
  }

  public boolean moving(double distance) {
    EntityPositionContext positions = this.positions;
    return Math.hypot(positions.newPosX - positions.prevPosX, positions.newPosZ - positions.prevPosZ) >= distance;
  }

  /**
   * Returns the type name of this entity.
   */
  public String entityName() {
    return entityName;
  }

  /**
   * Returns whether the entity is checkable.
   */
  public boolean checkable() {
    return isEntityLiving && clientSynchronized;
  }

  /**
   * Resolves the current {@link WrappedAxisAlignedBB} of the entity.
   *
   * @return the {@link WrappedAxisAlignedBB}
   */
  public WrappedAxisAlignedBB entityBoundingBox() {
    return this.boundingBox;
  }
}