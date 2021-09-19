package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.feedback.FeedbackTracker;
import de.jpx3.intave.module.feedback.PendingCountingFeedbackTracker;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.ClientMathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WrappedEntity {
  /*
  Dead entities are used to identify recently removed entities.
  Some packets are synchronized and some are processed immediately so
  this type of entity ensures that the synchrosized packets are handled correctly.
  */
  private static WrappedEntity DESTROYED_ENTITY;
  private final static boolean NEW_POSITION_PROCESSING_1_9 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);
  private final static boolean NEW_POSITION_PROCESSING_1_14 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_14_0);
  public EntityTypeData typeData;

  private final int entityId;

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
  public final boolean player;
  private int deathTime;
  private WrappedEntity mountedOnEntity;
  private BoundingBox boundingBox;
  private boolean enabledResponseTracing;

  /**
   * Internal value - do not change
   */
  public double distanceToPlayerCache;
  private boolean temporaryCopy;

  private final PendingCountingFeedbackTracker feedbackTracker;

  public WrappedEntity(
    int entityId,
    EntityTypeData typeData,
    boolean player
  ) {
    this.player = player;
    this.entityId = entityId;
    this.typeData = typeData;

    this.position = new EntityPositionContext();
    this.lastPosition = new EntityPositionContext();
    this.alternativePosition = new EntityPositionContext();
    this.feedbackTracker = new PendingCountingFeedbackTracker();
  }

  public static class EntityPositionContext implements Cloneable {
    public double prevPosX, prevPosY, prevPosZ;
    public double posX, posY, posZ;
    public double newPosX, newPosY, newPosZ;
    public int newPosRotationIncrements;

    public final long created = System.currentTimeMillis();

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
  void onLivingUpdate() {
    if (typeData.isLivingEntity() && position.newPosRotationIncrements > 0) {
      double newPosX = position.posX + (position.newPosX - position.posX) / (double) position.newPosRotationIncrements;
      double newPosY = position.posY + (position.newPosY - position.posY) / (double) position.newPosRotationIncrements;
      double shiftedNewY = alternativePosition.posY + (alternativePosition.newPosY - alternativePosition.posY) / (double) position.newPosRotationIncrements;
      double newPosZ = position.posZ + (position.newPosZ - position.posZ) / (double) position.newPosRotationIncrements;

      --position.newPosRotationIncrements;
      setPosition(newPosX, newPosY, newPosZ);
      setShiftedPositionY(shiftedNewY);
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
      serverPosX = ClientMathHelper.positionLong(newPosX);
      serverPosY = ClientMathHelper.positionLong(newPosY);
      serverPosZ = ClientMathHelper.positionLong(newPosZ);
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
      StructureModifier<Short> shorts = packet.getShorts();
      this.serverPosX += shorts.readSafely(0);
      this.serverPosY += shorts.readSafely(1);
      this.serverPosZ += shorts.readSafely(2);

      newPosX = (double) serverPosX / 4096d;
      newPosY = (double) serverPosY / 4096d;
      alternativeNewPosY = newPosY;
      newPosZ = (double) serverPosZ / 4096d;
    } else if (NEW_POSITION_PROCESSING_1_9) {
      StructureModifier<Integer> integers = packet.getIntegers();
      this.serverPosX += integers.readSafely(1);
      this.serverPosY += integers.readSafely(2);
      this.serverPosZ += integers.readSafely(3);

      newPosX = (double) serverPosX / 4096d;
      newPosY = (double) serverPosY / 4096d;
      alternativeNewPosY = newPosY;
      newPosZ = (double) serverPosZ / 4096d;
    } else {
      StructureModifier<Byte> bytes = packet.getBytes();
      this.serverPosX += bytes.readSafely(0);
      this.serverPosY += bytes.readSafely(1);
      this.serverPosZ += bytes.readSafely(2);

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
   * Used to set the position of an entity and moves its {@link BoundingBox}. On the client side this is also
   * applied for rotation changes.
   */
  public void setPositionAndRotationSpawnMob(double x, double y, double z, double alternativeY) {
    position.prevPosX = position.posX = x;
    position.prevPosY = position.posY = y;
    alternativePosition.prevPosY = alternativePosition.posY = alternativeY;
    position.prevPosZ = position.posZ = z;

    setPosition(position.posX, position.posY, position.posZ);
    setShiftedPositionY(alternativePosition.posY);
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

  private synchronized void updatePositionHistory() {
    if (!temporaryCopy) {
      if (positionHistory.size() > 25) {
        positionHistory.remove(0);
      }
      positionHistory.add(position.clone());
    }
  }

  public void setShiftedPositionY(double alternativeNewPosY) {
    alternativePosition.posY = alternativeNewPosY;
  }

  /**
   * Sets the position of the entity and the newPosRotationIncrements which is used to interpolate the entity position
   * in new ticks (Client side it also updates the rotation of the entity)
   *
   * @param newPosRotationIncrements the value which is used to interpolate the movement of the entity in new ticks
   */
  public void setPositionAndRotationEntityLiving(double x, double y, double z, int newPosRotationIncrements) {
    if (!typeData.isLivingEntity()) {
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
    if (!typeData.isLivingEntity()) {
      setShiftedPositionY(alternativeY);
      return;
    }
    alternativePosition.newPosY = alternativeY;
  }

  public boolean moving(double distance) {
    EntityPositionContext positions = this.position;
    return Hypot.fast(positions.newPosX - positions.posX, positions.newPosZ - positions.posZ) >= distance;
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

  public FeedbackTracker feedbackTracker() {
    return feedbackTracker;
  }

  public long pendingFeedbackPackets() {
    return feedbackTracker.pending();
  }

  /**
   * Returns the type name of this entity.
   */
  public String entityName() {
    return typeData.name();
  }

  public int entityId() {
    return entityId;
  }

  /**
   * Resolves the current {@link BoundingBox} of the entity.
   *
   * @return the {@link BoundingBox}
   */
  public BoundingBox entityBoundingBox() {
    if (boundingBox != null) {
      return boundingBox;
    }
    boundingBox = entityBoundingBoxFrom(position, this);
    return boundingBox;
  }

  public WrappedEntity temporaryCopy()  {
    WrappedEntity clone = new WrappedEntity(entityId, typeData, player);
    clone.temporaryCopy = true;
    clone.position = position.clone();
    clone.alternativePosition = alternativePosition.clone();
    clone.positionHistory = new ArrayList<>(positionHistory); // CopyOnWriteArrayList seems to disobey our clone policy
    return clone;
  }

  public static BoundingBox entityBoundingBoxFrom(EntityPositionContext position, WrappedEntity entity) {
    double x = position.posX;
    double y = position.posY;
    double z = position.posZ;

    double halfWidth = entity.typeData.size().width() / 2.0;
    double length = entity.typeData.size().length();
    return new BoundingBox(
      x - halfWidth, y, z - halfWidth,
      x + halfWidth, y + length, z + halfWidth
    );
  }

  public static void setup() {
    /*
      Avoid possible class order deadlock
     */
    DESTROYED_ENTITY = new Destroyed();
  }

  /*
  Dead entities are used to identify recently removed entities.

  Some packets are synchronized and some are processed immediately so
  this type of entity ensures that the synchrosized packets are handled correctly.
   */
  public static WrappedEntity destroyedEntity() {
    return DESTROYED_ENTITY;
  }

  public static final class Destroyed extends WrappedEntity {
    public Destroyed() {
      super(0, new EntityTypeData("destroyed", HitboxSize.zero(),-1, false, 8), false);
    }

    @Override
    void onLivingUpdate() {}
  }
}