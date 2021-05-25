package de.jpx3.intave.event.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.event.packet.*;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.reflect.hitbox.typeaccess.EntityTypeData;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ClientSideEntityService implements PacketEventSubscriber {
  private final IntavePlugin plugin;
  private final PacketEntityTypeResolver entityTypeResolver;

  private final static boolean NEW_POSITION_PROCESSING_1_9 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);
  private final static boolean HEALTH_PROCESSING_1_10 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_10_0);
  private final static boolean HEALTH_PROCESSING_1_14 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_14_0);

  public ClientSideEntityService(IntavePlugin plugin) {
    this.plugin = plugin;
    this.entityTypeResolver = new PacketEntityTypeResolver(plugin);
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.setupSynchronizer();
  }

  private void setupSynchronizer() {
    // async required?
    //noinspection deprecation
    Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::reevaluateTracingEntities, 0, 20);
  }

  private void reevaluateTracingEntities() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      reevaluteTracingEntitiesFor(player);
    }
  }

  private final static int REQUIRED_DISTANCE = 16;
  private final static int MAX_TRACED_ENTITIES = 4;

  private void reevaluteTracingEntitiesFor(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Vector location = new Vector(0, 0, 0);
    Vector playerLocation = player.getLocation().toVector();
    List<WrappedEntity> validEntities = new ArrayList<>();
    for (WrappedEntity entity : synchronizeData.synchronizedEntityMap().values()) {
      boolean firstSurvive = false;
      if (entity.isEntityLiving) {
        WrappedEntity.EntityPositionContext positions = entity.position;
        location.setX(positions.posX);
        location.setY(positions.posY);
        location.setZ(positions.posZ);
        double distance = location.distance(playerLocation);
        if (distance <= REQUIRED_DISTANCE) {
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
    /* IMPORTANT: If the entity spawn packet gets synchronized the player could be spammed with transaction packets
     *   which could cause a too many packets kick
     */
//    plugin.eventService().transactionFeedbackService().requestPong(event.getPlayer(), event, this::processEntitySpawn);
    processEntitySpawn(event.getPlayer(), event);
  }

  private void processEntitySpawn(Player player, PacketEvent event) {
    User user = UserRepository.userOf(player);
    UserMetaAttackData attackData = user.meta().attackData();
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();
    EntityTypeData entityTypeData;
    boolean livingEntity;
    Integer entityId = packet.getIntegers().read(0);
    if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
      // dead entities
      entityTypeData = entityTypeResolver.entityTypeDataOfDeadEntity(event);
      livingEntity = false;
    } else if (packetType == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
      // entities
      entityTypeData = entityTypeResolver.entityTypeDataOfLivingEntity(event);
      livingEntity = true;
    } else {
      // player
      FakePlayer fakePlayer = attackData.fakePlayer();
      String entityName;
      if (fakePlayer != null && fakePlayer.fakePlayerEntityId() == entityId) {
        entityName = "Intave-Bot";
      } else {
        entityName = "Player";
      }

      HitBoxBoundaries hitBoxBoundaries = HitBoxBoundaries.player();
      livingEntity = true;
      entityTypeData = new EntityTypeData(entityName, hitBoxBoundaries, 105);
    }
    processPacketSpawnMob(user, event.getPacketType(), entityTypeData, packet, livingEntity, entityId);
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
    /* IMPORTANT: If the entity destroy packet gets synchronized the player could be spammed with transaction packets
     *   which could cause a too many packets kick
     */
    // plugin.eventService().transactionFeedbackService().requestPong(player, entityIDs, this::processEntityDestroy);

    processEntityDestroy(player, entityIDs);
  }

  private void processEntityDestroy(Player player, int[] entityIDs) {
    User user = UserRepository.userOf(player);
    UserMetaAttackData attackData = user.meta().attackData();
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    for (int entityID : entityIDs) {
      synchronizedEntityMap.remove(entityID);
      if (attackData.lastAttackedEntity() != null && attackData.lastAttackedEntityID() == entityID) {
        attackData.nullifyLastAttackedEntity();
      }
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
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    UserMetaMovementData movementData = user.meta().movementData();

    if (movementData.lastTeleport == 0) {
      return;
    }
    for (Map.Entry<Integer, WrappedEntity> entry : synchronizeData.synchronizedEntityMap().entrySet()) {
      WrappedEntity entity = entry.getValue();
      entity.onUpdate();
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_TELEPORT"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "REL_ENTITY_MOVE"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "REL_ENTITY_MOVE_LOOK"),
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_LOOK")
    }
  )
  public void receiveUpstreamEntityMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    WrappedEntity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      entity = createEntityByMovePacket(event);
    }
    if (entity == null) {
//      IntaveLogger.logger().info("Unable to create entity (id " + entityId + ")");
//        throw new NullPointerException("entity could not be created");
      return;
    }
    if (entity.isEntityLiving && entity.tracingEnabled()) {
      WrappedEntity finalEntity = entity;
      plugin.eventService().feedback().clientSynchronize(player, event, (player1, event1) -> {
        processEntityMovement(event1, finalEntity);
        if (event1.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
          finalEntity.clientSynchronized = true;
        }
      });
    } else {
      processEntityMovement(event, entity);
      entity.clientSynchronized = false;
    }
  }

  private void processEntityMovement(PacketEvent event, WrappedEntity entity) {
    if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
      entity.handleEntityTeleport(event.getPacket());
    } else {
      entity.handleEntityMovement(event.getPacket());
    }
  }

  private WrappedEntity createEntityByMovePacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    int entityId = event.getPacket().getIntegers().read(0);
    Entity serverEntity = serverEntityByIdentifier(player, entityId);
    if (serverEntity != null) {
      return spawnMobByBukkitEntity(user, serverEntity);
    }
    return null;
  }

  private WrappedEntity spawnMobByBukkitEntity(User user, Entity bukkitEntity) {
    Location location = bukkitEntity.getLocation();
    boolean isEntityLiving = !bukkitEntity.isDead();
    int entityID = bukkitEntity.getEntityId();

    long serverPosX;
    long serverPosY;
    long serverPosZ;
    if (NEW_POSITION_PROCESSING_1_9) {
      serverPosX = WrappedMathHelper.getPositionLong(location.getX());
      serverPosY = WrappedMathHelper.getPositionLong(location.getY());
      serverPosZ = WrappedMathHelper.getPositionLong(location.getZ());
    } else {
      serverPosX = WrappedMathHelper.floor(location.getX() * 32d);
      serverPosY = WrappedMathHelper.floor(location.getY() * 32d);
      serverPosZ = WrappedMathHelper.floor(location.getZ() * 32d);
    }

    EntityTypeData entityTypeData = entityTypeResolver.entityTypeDataOfBukkitEntity(bukkitEntity);

    WrappedEntity entity = processEntitySpawn(
      user,
      isEntityLiving, entityID, entityTypeData,
      serverPosX, serverPosY, serverPosZ
    );

    if (bukkitEntity instanceof LivingEntity) {
      LivingEntity livingEntity = (LivingEntity) bukkitEntity;
      entity.health = (float) livingEntity.getHealth();
    }

    return entity;
  }

  private void processPacketSpawnMob(
    User user,
    PacketType packetType,
    EntityTypeData entityTypeData, PacketContainer packet,
    boolean isEntityLiving, int entityId
  ) {
    if (NEW_POSITION_PROCESSING_1_9) {
      double posX = packet.getDoubles().read(0);
      double posY = packet.getDoubles().read(1);
      double posZ = packet.getDoubles().read(2);

      processEntitySpawnNewVersion(
        user, entityTypeData, isEntityLiving, entityId,
        posX, posY, posZ
      );
    } else {
      // 1.8.x
      Integer serverPosX;
      Integer serverPosY;
      Integer serverPosZ;

      if (packetType == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
        // dead or living entities
        serverPosX = packet.getIntegers().read(2);
        serverPosY = packet.getIntegers().read(3);
        serverPosZ = packet.getIntegers().read(4);
      } else {
        // players
        serverPosX = packet.getIntegers().read(1);
        serverPosY = packet.getIntegers().read(2);
        serverPosZ = packet.getIntegers().read(3);
      }

      processEntitySpawn(
        user, isEntityLiving, entityId, entityTypeData,
        serverPosX, serverPosY, serverPosZ
      );

//      WrappedEntity wrappedEntity = entityByIdentifier(user, entityID);
//      if(wrappedEntity != null)
//        Bukkit.broadcastMessage("pt " + packetType.name() + " p " + user.player().getName() + " e " + wrappedEntity.position);
    }
  }

  private void processEntitySpawnNewVersion(
    User user, EntityTypeData entityTypeData,
    boolean isEntityLiving, int entityId,
    double posX, double posY, double posZ
  ) {
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    WrappedEntity entity = new WrappedEntity(entityId, entityTypeData, isEntityLiving);
    entity.serverPosX = WrappedMathHelper.getPositionLong(posX);
    entity.serverPosY = WrappedMathHelper.getPositionLong(posY);
    entity.serverPosZ = WrappedMathHelper.getPositionLong(posZ);
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
    synchronizedEntityMap.put(entityId, entity);
  }

  private WrappedEntity processEntitySpawn(
    User user,
    boolean isEntityLiving, int entityId, EntityTypeData entityTypeData,
    long serverPosX, long serverPosY, long serverPosZ
  ) {
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    double posX = serverPosX / 32d;
    double posY = serverPosY / 32d;
    double posZ = serverPosZ / 32d;
    WrappedEntity entity = new WrappedEntity(entityId, entityTypeData, isEntityLiving);
    entity.serverPosX = serverPosX;
    entity.serverPosY = serverPosY;
    entity.serverPosZ = serverPosZ;
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
    synchronizedEntityMap.put(entityId, entity);

    return entity;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_STATUS")
    }
  )
  public void receiveEntityStatus(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    Integer entityID = packet.getIntegers().read(0);
    Byte type = packet.getBytes().read(0);
    WrappedEntity entity = entityByIdentifier(user, entityID);
    if (entity == null || type != 3) {
      return;
    }
    boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
    if (synchronize) {
      plugin.eventService().feedback().clientSynchronize(player, entity, (p, e) -> updateDeadState(e));
    } else {
      updateDeadState(entity);
    }
  }

  private void updateDeadState(WrappedEntity entity) {
    entity.fakeDead = true;
    entity.health = 0f;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "ENTITY_METADATA")
    }
  )
  public void receiveEntityMetaData(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    Integer entityId = packet.getIntegers().read(0);
    if (player.getEntityId() == entityId) {
      synchronizePlayerHealth(player, packet);
      return;
    }
    WrappedEntity entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      return;
    }
    if (!entity.isEntityLiving) {
      return;
    }

    List<WrappedWatchableObject> watchableObjects = packet.getWatchableCollectionModifier().read(0);
    if(watchableObjects != null) {
      int entityTypeId = entity.entityTypeData.entityTypeId();
      EntityTypeData entityTypeData = entityTypeResolver.entityTypeDataOfEntityMetaData(event, entityTypeId, watchableObjects);
      if (entityTypeData != null) {
        entity.entityTypeData = entityTypeData;
      } else {
        IntaveLogger.logger().info("Unable to update entity metadata of entity " + entityId + " of type " + entityTypeId);
      }

      Float health = readHealthOf(watchableObjects);
      if (health != null) {
        boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
        if (synchronize) {
          plugin.eventService().feedback().clientSynchronize(player, entity, (p, e) -> updateHealthState(e, health));
        } else {
          updateHealthState(entity, health);
        }
      }
    }
  }

  private void synchronizePlayerHealth(Player player, PacketContainer packet) {
    Float health = readHealthOf(packet.getWatchableCollectionModifier().read(0));
    if (health != null) {
      plugin.eventService().feedback().clientSynchronize(player, health, (p, retrievedHealth) -> {
        UserMetaAbilityData abilityData = UserRepository.userOf(p).meta().abilityData();
        abilityData.health = retrievedHealth;
        abilityData.ticksToLastHealthUpdate = 0;
      });
    }
  }

  private Float readHealthOf(List<WrappedWatchableObject> watchableObjects) {
    for (WrappedWatchableObject watchableObject : watchableObjects) {
      int index = watchableObject.getIndex();

      int requiredIndex = HEALTH_PROCESSING_1_14 ? 8 : (HEALTH_PROCESSING_1_10 ? 7 : 6);
      if (index == requiredIndex) {
        Object rawValue = watchableObject.getRawValue();
        return ((Number) rawValue).floatValue();
      }
    }
    return null;
  }

  private void updateHealthState(WrappedEntity entity, float health) {
    entity.health = health;
  }

  @Nullable
  public static Entity serverEntityByIdentifier(Player player, int entityID) {
    for (Entity entity : player.getWorld().getEntities()) {
      if (entity.getEntityId() == entityID) {
        return entity;
      }
    }
    return null;
  }

  @Nullable
  public static WrappedEntity entityByIdentifier(User user, int entityID) {
    UserMetaConnectionData synchronizeData = user.meta().connectionData();
    return synchronizeData.synchronizedEntityMap().getOrDefault(entityID, null);
  }
}