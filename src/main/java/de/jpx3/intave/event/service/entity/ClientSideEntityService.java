package de.jpx3.intave.event.service.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.reflect.ReflectiveAccess;
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
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ClientSideEntityService implements PacketEventSubscriber {
  private final IntavePlugin plugin;
  private String dataWatcherEntityFieldName;

  public ClientSideEntityService(IntavePlugin plugin) {
    this.plugin = plugin;
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.setupSynchronizer();
    this.registerDataWatcherEntityFieldName();
  }

  private void setupSynchronizer() {
    // async required?
    Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::reevaluateTracingEntities, 0, 20);
  }

  private void registerDataWatcherEntityFieldName() {
    MinecraftVersion serverVersion = ProtocolLibAdapter.serverVersion();
    if (serverVersion.isAtLeast(ProtocolLibAdapter.VILLAGE_UPDATE)) {
      dataWatcherEntityFieldName = "entity";
    } else if (serverVersion.isAtLeast(ProtocolLibAdapter.FROSTBURN_UPDATE)) {
      dataWatcherEntityFieldName = "c";
    } else if (serverVersion.isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE)) {
      dataWatcherEntityFieldName = "b";
    } else {
      dataWatcherEntityFieldName = "a";
    }

    // search field

    Class<?> entityClass = ReflectiveAccess.NMS_ENTITY_CLASS;
    Class<?> dataWatcherClass = ReflectiveAccess.lookupServerClass("DataWatcher");

    for (Field declaredField : dataWatcherClass.getDeclaredFields()) {
      if(declaredField.getType() == entityClass) {
        String fieldName = declaredField.getName();
        if(!dataWatcherEntityFieldName.equals(fieldName)) {
          System.out.println("[Intave] Conflicting field name internal for entity-from-datawatcher access: Internals suggest " + dataWatcherEntityFieldName + " but found " + fieldName);
        }
        dataWatcherEntityFieldName = fieldName;
        break;
      }
    }
  }

  private void reevaluateTracingEntities() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      reevaluteTracingEntitiesFor(player);
    }
  }

  private final static int REQUIRED_DISTANCE = 16;
  private final static int MAX_TRACED_ENTITIES = 8;

  private void reevaluteTracingEntitiesFor(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaSynchronizeData synchronizeData = user.meta().synchronizeData();

    Vector location = new Vector(0, 0, 0);
    Vector playerLocation = player.getLocation().toVector();
    List<WrappedEntity> validEntities = new ArrayList<>();

    for (WrappedEntity entity : synchronizeData.synchronizedEntityMap().values()) {
      boolean firstSurvive = false;
      if(entity.isEntityLiving) {
        WrappedEntity.EntityPositionContext positions = entity.position;
        location.setX(positions.posX);
        location.setY(positions.posY);
        location.setZ(positions.posZ);
        double distance = location.distance(playerLocation);
        if(distance <= REQUIRED_DISTANCE) {
          validEntities.add(entity);
          entity.distanceToPlayerCache = distance;
          firstSurvive = true;
        }
      }
      entity.setResponseTracingEnabled(firstSurvive);
    }

    validEntities.sort(Comparator.comparingDouble(entity -> entity.distanceToPlayerCache));

    int count = 0;
    for (WrappedEntity entity : validEntities) {
      entity.setResponseTracingEnabled(count++ < MAX_TRACED_ENTITIES);
    }
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
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    WrappedEntity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      registerEntity(event);
      entity = entityByIdentifier(user, entityId);
    }
    if (entity != null) {
      if (entity.isEntityLiving && entity.tracingEnabled()) {
        WrappedEntity finalEntity = entity;
        plugin.eventService().transactionFeedbackService().requestPong(player, event, (player1, event1) -> {
          processEntityTeleport(player1, event1, true);
          finalEntity.clientSynchronized = true;
        });
      } else {
        processEntityTeleport(player, event, false);
        entity.clientSynchronized = false;
      }
    }
  }

/*
  private boolean suitableDistanceForSynchronization(Player player, WrappedEntity entity) {
    WrappedEntity.EntityPositionContext positions = entity.positions;
    Location location = player.getLocation();
    double diffX = location.getX() - positions.posX;
    double diffY = location.getY() - positions.posY;
    double diffZ = location.getZ() - positions.posZ;
    return Math.sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ) < 7.0;
  }
*/

  private void processEntityTeleport(Player player, PacketEvent event, boolean clientTickSync) {
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
      if(!clientTickSync) {
        if(entity.possiblePositions.size() > 7) {
          entity.possiblePositions.remove(0);
          entity.possibleAlternativePositions.remove(0);
        }
      } else if (!entity.possiblePositions.isEmpty()) {
        entity.possiblePositions.clear();
        entity.possibleAlternativePositions.clear();
      }
      entity.possiblePositions.add(entity.position.clone());
      entity.possibleAlternativePositions.add(entity.alternativePosition.clone());
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
    return entityNameOf(ReflectiveAccess.handleResolver().resolveEntityHandleOf(entity));
  }

  private Object entityOfDataWatcher(WrappedDataWatcher dataWatcher) {
    Object handle = dataWatcher.getHandle();
    Class<?> handleClass = handle.getClass();
    try {
      return entityByHandle(handle, handleClass.getDeclaredField(dataWatcherEntityFieldName));
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