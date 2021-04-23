package de.jpx3.intave.event.service.entity;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WrappedEntity implements Cloneable {
  private final static boolean NEW_POSITION_PROCESSING = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE);
  private final String entityName;
  private final int entityId;

  final public boolean isEntityLiving;
  public final HitBoxBoundaries hitBoxBoundaries;

  /**
   * Indicates if the entity position is synchronized with the client
   */
  public volatile boolean clientSynchronized = true;

  /**
   * This value is used to interpolate the positions of the Entity
   */
  public int newPosRotationIncrements;
  public long serverPosX, serverPosY, serverPosZ;

  public EntityPositionContext position;
  public EntityPositionContext lastPosition;
  public EntityPositionContext alternativePosition;
  public List<EntityPositionContext> positionHistory = new CopyOnWriteArrayList<>();
  public boolean dead, fakeDead;
  public float health;
  private int deathTime;

  private WrappedAxisAlignedBB boundingBox;
  private boolean enabledResponseTracing;

  /**
   * Internal value - do not change
   */
  public double distanceToPlayerCache;
  private boolean isClone;

  public WrappedEntity(
    String entityName,
    int entityId,
    boolean isEntityLiving,
    HitBoxBoundaries hitBoxBoundaries
  ) {
    this.entityName = entityName;
    this.entityId = entityId;
    this.isEntityLiving = isEntityLiving;
    this.hitBoxBoundaries = hitBoxBoundaries;

    this.position = new EntityPositionContext();
    this.lastPosition = new EntityPositionContext();
    this.alternativePosition = new EntityPositionContext();
  }

  public static class EntityPositionContext implements Cloneable {
    public double prevPosX, prevPosY, prevPosZ;
    public double posX, posY, posZ;
    public double newPosX, newPosY, newPosZ;

    @Override
    public EntityPositionContext clone()  {
      try {
        return (EntityPositionContext) super.clone();
      } catch (CloneNotSupportedException exception) {
        throw new IntaveInternalException(exception);
      }
    }
  }

  public void onUpdate() {
    this.onEntityUpdate();
    this.onLivingUpdate();
  }

  /**
   * Interpolates the position of the entity between the position and the new position to make the entity move smoothly.
   * This method applies if the given entity has an instance of LivingEntity. Packets: (All types of movement packets)
   * FLYING, LOOK, POSITION, POSITION_LOOK
   */
  private void onLivingUpdate() {
    if (isEntityLiving) {
      if (this.newPosRotationIncrements > 0) {
        double newPosX = position.posX + (position.newPosX - position.posX) / (double) this.newPosRotationIncrements;
        double newPosY = position.posY + (position.newPosY - position.posY) / (double) this.newPosRotationIncrements;
        double alternativeNewPosY = alternativePosition.posY + (alternativePosition.newPosY - alternativePosition.posY) / (double) this.newPosRotationIncrements;
        double newPosZ = position.posZ + (position.newPosZ - position.posZ) / (double) this.newPosRotationIncrements;

        --this.newPosRotationIncrements;
        setPosition(newPosX, newPosY, newPosZ);
        setPosition(alternativeNewPosY);
      }
    }
  }

  private void onEntityUpdate() {
    if (this.health <= 0.0) {
      onDeathUpdate();
    }
  }

  private void onDeathUpdate() {
    ++this.deathTime;
    if (this.deathTime == 20) {
      this.dead = true;
    }
  }

  /**
   * Handles a teleportation. Packets: ENTITY_TELEPORT
   *
   * @param packet contains information about the entity teleportation
   */
  public void handleEntityTeleport(PacketContainer packet) {
    double newPosX;
    double newPosY;
    double newPosZ;
    if (NEW_POSITION_PROCESSING) {
      newPosX = packet.getDoubles().read(0);
      newPosY = packet.getDoubles().read(1);
      newPosZ = packet.getDoubles().read(2);
      serverPosX = WrappedMathHelper.getPositionLong(newPosX);
      serverPosY = WrappedMathHelper.getPositionLong(newPosY);
      serverPosZ = WrappedMathHelper.getPositionLong(newPosZ);
    } else {
      serverPosX = packet.getIntegers().read(1);
      serverPosY = packet.getIntegers().read(2);
      serverPosZ = packet.getIntegers().read(3);
      newPosX = serverPosX / 32.0;
      newPosY = serverPosY / 32.0;
      newPosZ = serverPosZ / 32.0;
    }

    if (
      Math.abs(position.posX - newPosX) < 0.03125d &&
      Math.abs(position.posY - newPosY) < 0.015625d &&
      Math.abs(position.posZ - newPosZ) < 0.03125d
    ) {
      setPositionAndRotationEntityLiving(position.posX, position.posY, position.posZ, 3);
    } else {
      setPositionAndRotationEntityLiving(newPosX, newPosY, newPosZ, 3);
    }

    double alternativeNewPosY = (double) this.serverPosY / 32d + 0.015625d;

    if (Math.abs(position.posX - newPosX) < 0.03125d &&
      Math.abs(alternativePosition.posY - alternativeNewPosY) < 0.015625d &&
      Math.abs(position.posZ - newPosZ) < 0.03125d) {
      setPositionAndRotationEntityLiving(alternativePosition.posY);
    } else {
      setPositionAndRotationEntityLiving(alternativeNewPosY);
    }
  }


  /**
   * Handles relative movement. Packets: REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK or ENTITY_LOOK
   *
   * @param packet contains information about the entity movement
   */
  public void handleEntityMovement(PacketContainer packet) {
    double newPosX;
    double newPosY;
    double alternativeNewPosY;
    double newPosZ;

    if (NEW_POSITION_PROCESSING) {
      this.serverPosX += packet.getIntegers().readSafely(1);
      this.serverPosY += packet.getIntegers().readSafely(2);
      this.serverPosZ += packet.getIntegers().readSafely(3);

      newPosX = (double) serverPosX / 4096d;
      newPosY = (double) serverPosY / 4096d;
      alternativeNewPosY = newPosY;
      newPosZ = (double) serverPosZ / 4096d;
    } else {
      this.serverPosX += packet.getBytes().readSafely(0);
      this.serverPosY += packet.getBytes().readSafely(1);
      this.serverPosZ += packet.getBytes().readSafely(2);

      newPosX = (double) serverPosX / 32d;
      newPosY = (double) serverPosY / 32d;
      alternativeNewPosY = (double) serverPosY / 32d;
      newPosZ = (double) serverPosZ / 32d;
    }

    // 3 is used to interpolate the entity position in new client ticks
    setPositionAndRotationEntityLiving(newPosX, newPosY, newPosZ, 3);
    setPositionAndRotationEntityLiving(alternativeNewPosY);
  }

  /**
   * Used to set the position of an entity and moves its {@link WrappedAxisAlignedBB}. On the client side this is also
   * applied for rotation changes.
   */
  public void setPositionAndRotationSpawnMob(double x, double y, double z, double alternativeY) {
    position.prevPosX = position.posX = x;
    position.prevPosY = position.posY = y;
    alternativePosition.prevPosY = alternativePosition.posY = alternativeY;
    position.prevPosZ = position.posZ = z;

    setPosition(position.posX, position.posY, position.posZ);
    setPosition(alternativePosition.posY);
  }

  /**
   * Sets the position of the entity.
   */
  public void setPosition(double x, double y, double z) {
    if(!isClone) {
      if(positionHistory.size() > 10) {
        positionHistory.remove(0);
      }
      positionHistory.add(position);
    }
    lastPosition.posX = position.posX;
    lastPosition.posY = position.posY;
    lastPosition.posZ = position.posZ;
    position.posX = x;
    position.posY = y;
    position.posZ = z;
    boundingBox = null;
  }

  public void setPosition(double alternativeNewPosY) {
    alternativePosition.posY = alternativeNewPosY;
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
    position.newPosX = x;
    position.newPosY = y;
    position.newPosZ = z;
    this.newPosRotationIncrements = newPosRotationIncrements;
  }

  public void setPositionAndRotationEntityLiving(double alternativeY) {
    if (!isEntityLiving) {
      setPosition(alternativeY);
      return;
    }
    alternativePosition.newPosY = alternativeY;
  }

  public boolean moving(double distance) {
    EntityPositionContext positions = this.position;
    return Math.hypot(positions.newPosX - positions.posX, positions.newPosZ - positions.posZ) >= distance;
  }

  public boolean tracingEnabled() {
    return enabledResponseTracing;
  }

  public void setResponseTracingEnabled(boolean enabledResponseTracing) {
    this.enabledResponseTracing = enabledResponseTracing;
  }

  /**
   * Returns the type name of this entity.
   */
  public String entityName() {
    return entityName;
  }

  public int entityId() {
    return entityId;
  }

  /**
   * Returns whether the entity is checkable.
   */
  public boolean living() {
    return isEntityLiving;
  }

  /**
   * Resolves the current {@link WrappedAxisAlignedBB} of the entity.
   *
   * @return the {@link WrappedAxisAlignedBB}
   */
  public WrappedAxisAlignedBB entityBoundingBox() {
    if(boundingBox != null) {
      return boundingBox;
    }
    return boundingBox = entityBoundingBoxFrom(position, this);
  }

  @Override
  public WrappedEntity clone()  {
    WrappedEntity clone = new WrappedEntity(entityName, entityId, isEntityLiving, hitBoxBoundaries);
    clone.isClone = true;
    clone.position = position.clone();
    clone.alternativePosition = alternativePosition.clone();
    clone.positionHistory = new CopyOnWriteArrayList<> (positionHistory);
    clone.newPosRotationIncrements = newPosRotationIncrements;
    return clone;
  }

  public static WrappedAxisAlignedBB entityBoundingBoxFrom(EntityPositionContext position, WrappedEntity entity) {
    double x = position.posX;
    double y = position.posY;
    double z = position.posZ;

    double halfWidth = entity.hitBoxBoundaries.width() / 2.0;
    double length = entity.hitBoxBoundaries.length();
    return new WrappedAxisAlignedBB(
      x - halfWidth, y, z - halfWidth,
      x + halfWidth, y + length, z + halfWidth
    );
  }
}