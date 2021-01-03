package de.jpx3.intave.event.service.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.reflect.Reflection;
import de.jpx3.intave.tools.hitbox.EntityHitBoxResolver;
import de.jpx3.intave.tools.hitbox.HitBoxBoundaries;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaSynchronizeData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;

public final class ClientSideEntityService implements PacketEventSubscriber {
  private final static int SYNCHRONIZATIONS_PER_SECOND = 80; // 4 Entities
  private final IntavePlugin plugin;

  public ClientSideEntityService(IntavePlugin plugin) {
    this.plugin = plugin;
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.setupSynchronizer();
  }

  private void setupSynchronizer() {
    Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::resetSynchronizationsAll, 0, 20);
  }

  private void resetSynchronizationsAll() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      resetSynchronizationsPerSecondFor(player);
    }
  }

  private void resetSynchronizationsPerSecondFor(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    synchronizeData.synchronizedEntitiesPerSecond = 0;
  }

  @PacketSubscription(
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "SPAWN_ENTITY_LIVING"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "SPAWN_ENTITY"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "NAMED_ENTITY_SPAWN")
    }
  )
  public void receiveEntitySpawn(PacketEvent event) {
    plugin.eventService().transactionFeedbackService().requestPong(event.getPlayer(), event, this::processEntitySpawn);
  }

  private void processEntitySpawn(Player player, PacketEvent event) {
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    String entityName;
    HitBoxBoundaries hitBoxBoundaries;
    boolean livingEntity;
    if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
      // dead entities
      hitBoxBoundaries = HitBoxBoundaries.from(0, 0);
      livingEntity = false;
      entityName = "DeadEntity";
    } else {
      // player
      Object entity = entityOfDataWatcher(packet.getDataWatcherModifier().read(0));
      hitBoxBoundaries = EntityHitBoxResolver.resolveHitBoxOf(entity);
      entityName = entityNameOf(entity);
      livingEntity = true;
    }
    processPacketSpawnMob(user, entityName, packet, livingEntity, hitBoxBoundaries);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_DESTROY")
    }
  )
  public void receiveEntityDestroy(PacketEvent event) {
    Player player = event.getPlayer();
    int[] entityIDs = event.getPacket().getIntegerArrays().read(0);
    plugin.eventService().transactionFeedbackService().requestPong(player, entityIDs, this::processEntityDestroy);
  }

  private void processEntityDestroy(Player player, int[] entityIDs) {
    User user = UserRepository.userOf(player);
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    for (int entityID : entityIDs) {
      synchronizedEntityMap.remove(entityID);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "POSITION_LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "LOOK"),
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "FLYING")
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    for (Map.Entry<Integer, WrappedEntity> entry : synchronizeData.synchronizedEntityMap().entrySet()) {
      WrappedEntity entity = entry.getValue();
      entity.onLivingUpdate();
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_TELEPORT"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "REL_ENTITY_MOVE"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "REL_ENTITY_MOVE_LOOK")
    }
  )
  public void receiveTeleport(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    WrappedEntity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      registerEntity(event);
      entity = entityByIdentifier(user, entityId);
    }
    if (entity != null) {
      boolean synchronizeEntityMovement = suitableDistanceForSynchronization(player, entity);
      boolean exceededSynchronizationLimit = synchronizeData.synchronizedEntitiesPerSecond++ > SYNCHRONIZATIONS_PER_SECOND;
      if (!entity.isEntityLiving || synchronizeEntityMovement && exceededSynchronizationLimit) {
        synchronizeEntityMovement = false;
      }
      if (synchronizeEntityMovement) {
        WrappedEntity finalEntity = entity;
        plugin.eventService().transactionFeedbackService().requestPong(player, event, (player1, event1) -> {
          finalEntity.clientSynchronized = true;
          processEntityTeleport(player1, event1);
        });
      } else {
        processEntityTeleport(player, event);
        entity.clientSynchronized = false;
      }
    }
  }

  private boolean suitableDistanceForSynchronization(Player player, WrappedEntity entity) {
    WrappedEntity.EntityPositionContext positions = entity.positions;
    Location location = player.getLocation();
    double diffX = location.getX() - positions.posX;
    double diffY = location.getY() - positions.posY;
    double diffZ = location.getZ() - positions.posZ;
    return Math.sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ) < 7.0;
  }

  private void processEntityTeleport(Player player, PacketEvent event) {
    PacketType packetType = event.getPacketType();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    WrappedEntity entity = entityByIdentifier(user, packet.getIntegers().read(0));
    if (entity != null) {
      if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
        entity.handleEntityTeleport(packet);
      } else {
        entity.handleEntityMovement(packet);
      }
    }
  }

  private void registerEntity(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(player);
    int entityId = packet.getIntegers().read(0);
    WrappedEntity entity = entityByIdentifier(user, entityId);
    if (entity != null) {
      return;
    }
    Entity serverEntity = serverEntityByIdentifier(player, entityId);
    if (serverEntity != null) {
      spawnMobByBukkitEntity(user, serverEntity);
    }
  }

  private void spawnMobByBukkitEntity(User user, Entity bukkitEntity) {
    String entityName = entityNameByBukkitEntity(bukkitEntity);
    Location location = bukkitEntity.getLocation();
    boolean isEntityLiving = !bukkitEntity.isDead();
    HitBoxBoundaries boundaries = bukkitEntity.isDead() ? HitBoxBoundaries.from(0, 0) :
      EntityHitBoxResolver.resolveHitBoxOf(bukkitEntity);
    int entityID = bukkitEntity.getEntityId();
    int serverPosX = WrappedMathHelper.floor(location.getX() * 32d);
    int serverPosY = WrappedMathHelper.floor(location.getY() * 32d);
    int serverPosZ = WrappedMathHelper.floor(location.getZ() * 32d);
    processEntitySpawn(
      user,
      entityName, isEntityLiving, entityID,
      serverPosX, serverPosY, serverPosZ,
      boundaries
    );
  }

  private void processPacketSpawnMob(
    User user,
    String entityName, PacketContainer packet,
    boolean isEntityLiving, HitBoxBoundaries boundaries
  ) {
    Integer entityID = packet.getIntegers().read(0);
    Integer serverPosX = packet.getIntegers().read(2);
    Integer serverPosY = packet.getIntegers().read(3);
    Integer serverPosZ = packet.getIntegers().read(4);
    processEntitySpawn(
      user, entityName, isEntityLiving, entityID,
      serverPosX, serverPosY, serverPosZ,
      boundaries
    );
  }

  private void processEntitySpawn(
    User user, String entityName,
    boolean isEntityLiving, int entityId,
    int serverPosX, int serverPosY, int serverPosZ,
    HitBoxBoundaries boundaries
  ) {
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    double posX = serverPosX / 32d;
    double posY = serverPosY / 32d;
    double posZ = serverPosZ / 32d;
    WrappedEntity entity = new WrappedEntity(entityName, isEntityLiving, boundaries);
    entity.serverPosX = serverPosX;
    entity.serverPosY = serverPosY;
    entity.serverPosZ = serverPosZ;
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
    synchronizedEntityMap.put(entityId, entity);
  }

  @Nullable
  private Entity serverEntityByIdentifier(Player player, int entityID) {
    for (Entity entity : player.getWorld().getEntities()) {
      if (entity.getEntityId() == entityID) {
        return entity;
      }
    }
    return null;
  }

  @Nullable
  public static WrappedEntity entityByIdentifier(User user, int entityID) {
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();
    return synchronizeData.synchronizedEntityMap().getOrDefault(entityID, null);
  }

  private String entityNameOf(Object entity) {
    String entityName = entity.getClass().getSimpleName();
    if (entityName.startsWith("Entity")) {
      entityName = entityName.substring("Entity".length());
    }
    return entityName;
  }

  private String entityNameByBukkitEntity(Entity entity) {
    return entityNameOf(Reflection.resolveEntityNMSHandle(entity));
  }

  private Object entityOfDataWatcher(WrappedDataWatcher dataWatcher) {
    Object handle = dataWatcher.getHandle();
    Class<?> handleClass = handle.getClass();
    try {
      return entityByHandle(handle, handleClass.getDeclaredField("a"));
    } catch (NoSuchFieldException e) {
      throw new IntaveInternalException(e);
    }
  }

  private Object entityByHandle(Object handle, Field entityField) {
    try {
      if (!entityField.isAccessible()) {
        entityField.setAccessible(true);
      }
      return entityField.get(handle);
    } catch (Exception e) {
      throw new IntaveInternalException(e);
    }
  }
}