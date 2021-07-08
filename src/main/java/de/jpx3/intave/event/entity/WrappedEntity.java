package de.jpx3.intave.event.entity;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.reflect.hitbox.typeaccess.EntityTypeData;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WrappedEntity implements Cloneable {
  private final static WrappedEntity DEAD_ENTITY = new DeadWrappedEntity();
  private final static boolean NEW_POSITION_PROCESSING_1_9 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);
  private final static boolean NEW_POSITION_PROCESSING_1_14 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_14_0);
  public EntityTypeData entityTypeData;

  private final int entityId;
  final public boolean isEntityLiving;

  /**
   * Indicates if the entity position is synchronized with the client
   */
  public volatile boolean clientSynchronized = true;

  /**
   * Indicates that the entity should endure double-verification
   */
  public volatile boolean doubleVerification = false;

  /**
   * This value is used to interpolate the positions of the Entity
   */
  public long serverPosX, serverPosY, serverPosZ;

  public EntityPositionContext position;
  public EntityPositionContext lastPosition;
  public EntityPositionContext alternativePosition;
  public List<EntityPositionContext> positionHistory = new CopyOnWriteArrayList<>();
  public boolean dead, fakeDead;
  public boolean verifiedPosition;
  public float health;
  public int ticksAlive;
  private int deathTime;
  private WrappedEntity mountedOnEntity;
  private WrappedAxisAlignedBB boundingBox;
  private boolean enabledResponseTracing;

  /**
   * Internal value - do not change
   */
  public double distanceToPlayerCache;
  private boolean isClone;

  public WrappedEntity(
    int entityId,
    EntityTypeData entityTypeData,
    boolean isEntityLiving
  ) {
    this.entityId = entityId;
    this.entityTypeData = entityTypeData;
    this.isEntityLiving = isEntityLiving;

    this.position = new EntityPositionContext();
    this.lastPosition = new EntityPositionContext();
    this.alternativePosition = new EntityPositionContext();
  }

  public static class EntityPositionContext implements Cloneable {
    public double prevPosX, prevPosY, prevPosZ;
    public double posX, posY, posZ;
    public double newPosX, newPosY, newPosZ;
    public int newPosRotationIncrements;

    public final long created = AccessHelper.now();

    @Override
    public EntityPositionContext clone()  {
      try {
        return (EntityPositionContext) super.clone();
      } catch (CloneNotSupportedException exception) {
        throw new IntaveInternalException(exception);
      }
    }

    @Override
    public String toString() {
      return "[" + posX + "," + posY + "," + posZ + "]";
    }
  }

  public void onUpdate() {
    this.ticksAlive++;
    this.onEntityUpdate();
    this.onLivingUpdate();
  }

  /**
   * Updated after a player sends his movement.
   * This method is required because the client is using a player's rotationYaw and rotationPitch
   * which will be sent in the next tick and therefore is not accessible at this moment.
   */
  public void entityPlayerMoveUpdate() {}

  /**
   * Interpolates the position of the entity between the position and the new position to make the entity move smoothly.
   * This method applies if the given entity has an instance of LivingEntity. Packets: (All types of movement packets)
   * FLYING, LOOK, POSITION, POSITION_LOOK
   */
  private void onLivingUpdate() {
    if (isEntityLiving) {
      if (position.newPosRotationIncrements > 0) {
        double newPosX = position.posX + (position.newPosX - position.posX) / (double) position.newPosRotationIncrements;
        double newPosY = position.posY + (position.newPosY - position.posY) / (double) position.newPosRotationIncrements;
        double alternativeNewPosY = alternativePosition.posY + (alternativePosition.newPosY - alternativePosition.posY) / (double) position.newPosRotationIncrements;
        double newPosZ = position.posZ + (position.newPosZ - position.posZ) / (double) position.newPosRotationIncrements;

        --position.newPosRotationIncrements;
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

    if (NEW_POSITION_PROCESSING_1_9) {
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

    if (NEW_POSITION_PROCESSING_1_14) {
      this.serverPosX += packet.getShorts().readSafely(0);
      this.serverPosY += packet.getShorts().readSafely(1);
      this.serverPosZ += packet.getShorts().readSafely(2);

      newPosX = (double) serverPosX / 4096d;
      newPosY = (double) serverPosY / 4096d;
      alternativeNewPosY = newPosY;
      newPosZ = (double) serverPosZ / 4096d;
    } else if (NEW_POSITION_PROCESSING_1_9) {
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
    updatePositionHistory();
    lastPosition.posX = position.posX;
    lastPosition.posY = position.posY;
    lastPosition.posZ = position.posZ;
    position.posX = x;
    position.posY = y;
    position.posZ = z;
    boundingBox = null;
  }

  private void updatePositionHistory() {
    if (!isClone) {
      if (positionHistory.size() > 25) {
        positionHistory.remove(0);
      }
      positionHistory.add(position.clone());
    }
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
    updatePositionHistory();
    position.newPosRotationIncrements = newPosRotationIncrements;
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

  public boolean isEntityAlive() {
    return !this.dead && this.health > 0.0f;
  }

  public void mountToEntity(WrappedEntity mountedOnEntity) {
    this.mountedOnEntity = mountedOnEntity;
  }

  public void unmountFromEntity() {
    mountedOnEntity = null;
  }

  public WrappedEntity mountedEntity() {
    return mountedOnEntity;
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
    return entityTypeData.entityName();
  }

  public int entityId() {
    return entityId;
  }

  /**
   * Resolves the current {@link WrappedAxisAlignedBB} of the entity.
   *
   * @return the {@link WrappedAxisAlignedBB}
   */
  public WrappedAxisAlignedBB entityBoundingBox() {
    if (boundingBox != null) {
      return boundingBox;
    }
    boundingBox = entityBoundingBoxFrom(position, this);
    return boundingBox;
  }

  @Override
  public WrappedEntity clone()  {
    WrappedEntity clone = new WrappedEntity(entityId, entityTypeData, isEntityLiving);
    clone.isClone = true;
    clone.position = position.clone();
    clone.alternativePosition = alternativePosition.clone();
    clone.positionHistory = new CopyOnWriteArrayList<> (positionHistory);
    return clone;
  }

  public static WrappedAxisAlignedBB entityBoundingBoxFrom(EntityPositionContext position, WrappedEntity entity) {
    double x = position.posX;
    double y = position.posY;
    double z = position.posZ;

    double halfWidth = entity.entityTypeData.hitBoxBoundaries().width() / 2.0;
    double length = entity.entityTypeData.hitBoxBoundaries().length();
    return new WrappedAxisAlignedBB(
      x - halfWidth, y, z - halfWidth,
      x + halfWidth, y + length, z + halfWidth
    );
  }

  public static WrappedEntity deadEntity() {
    return DEAD_ENTITY;
  }
}