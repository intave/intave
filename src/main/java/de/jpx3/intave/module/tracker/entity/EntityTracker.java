package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.event.feedback.Callback;
import de.jpx3.intave.fakeplayer.FakePlayer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.reflect.hitbox.typeaccess.EntityTypeData;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.wrapper.WrappedMathHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.*;

import static de.jpx3.intave.event.feedback.FeedbackService.TransactionOptions.APPEND_ON_OVERFLOW;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class EntityTracker extends Module {
  /*
  TODO: when a entity gets spawned and the spawn packet gets send to the client and the entity gets teleported right after,
   the check will try to create the entity by the teleport packet bevor the entity spawn packet can be executed

   TODO: maybe remove entities when their live gets below 0 for 20 ticks. Or debug if entities gets really removed in some kind of root command
   */
  private final IntavePlugin plugin;
  private final EntityTypeResolver entityTypeResolver;

  private final boolean NEW_POSITION_PROCESSING_1_9 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);
  private final boolean HEALTH_PROCESSING_1_10 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_10_0);
  private final boolean HEALTH_PROCESSING_1_14 = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_14_0);

  public EntityTracker(IntavePlugin plugin) {
    this.plugin = plugin;
    this.entityTypeResolver = new EntityTypeResolver(plugin);
//    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
//    this.setupSynchronizer();
  }

  @Override
  public void enable() {
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
  private final static int MAX_DOUBLE_TRACED_ENTITIES = 1;

  private void reevaluteTracingEntitiesFor(Player player) {
    User user = UserRepository.userOf(player);
    ConnectionMetadata synchronizeData = user.meta().connection();
    Vector location = new Vector(0, 0, 0);
    Vector playerLocation = player.getLocation().toVector();
    List<WrappedEntity> validEntities = new ArrayList<>();
    for (WrappedEntity entity : synchronizeData.synchronizedEntityMap().values()) {
      boolean firstSurvive = false;
      if (entity.entityTypeData != null && entity.entityTypeData.isLivingEntity()) {
        WrappedEntity.EntityPositionContext positions = entity.position;
        location.setX(positions.posX);
        location.setY(positions.posY);
        location.setZ(positions.posZ);
        double distance = location.distance(playerLocation);
        if (distance <= REQUIRED_DISTANCE) {
          validEntities.add(entity);
          entity.distanceToPlayerCache = distance;
          entity.doubleVerification = false;
          firstSurvive = true;
        }
      }
      entity.setResponseTracingEnabled(firstSurvive);
    }
    validEntities.sort(Comparator.comparingDouble(entity -> entity.distanceToPlayerCache));
    int count = 0;
    for (WrappedEntity entity : validEntities) {
      entity.doubleVerification = count < MAX_DOUBLE_TRACED_ENTITIES;
      entity.setResponseTracingEnabled(count < MAX_TRACED_ENTITIES);
      count++;
    }
  }

  @PacketSubscription(
    packetsOut = {
      MOUNT, ATTACH_ENTITY
    },
    ignoreCancelled = false
  )
  public void sendAttachEntityPacket(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    Player player = event.getPlayer();

    if(event.getPacketType() == PacketType.Play.Server.MOUNT) {
      //1.9+ servers
      int[] entityIDs = event.getPacket().getIntegerArrays().read(0);
      int vehicleEntityID = packet.getIntegers().read(0);

      for (int entityID : entityIDs) {
        processAttachEntity(player, entityID, vehicleEntityID);
      }
    } else if(event.getPacketType() == PacketType.Play.Server.ATTACH_ENTITY && !NEW_POSITION_PROCESSING_1_9) {
      // TODO: check if "&& !NEW_POSITION_PROCESSING_1_9" is useless
      // 1.8 servers
      int type = packet.getIntegers().read(0);
      if (type == 0) {
        int entityID = packet.getIntegers().read(1);
        int vehicleEntityID = packet.getIntegers().read(2);

        processAttachEntity(player, entityID, vehicleEntityID);
      }
    }
  }

  private void processAttachEntity(Player player, int entityID, int vehicleEntityID) {
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    WrappedEntity sittingEntity = synchronizedEntityMap.get(entityID);

    if (sittingEntity != null) {
      // Another entity
      if (vehicleEntityID == -1) {
        // when an entity dismounts
        sittingEntity.unmountFromEntity();
      } else {
        // mounts on entity
        WrappedEntity sittingOnEntity = synchronizedEntityMap.get(vehicleEntityID);
        if (sittingOnEntity != null) {
          sittingEntity.mountToEntity(sittingOnEntity);
        } else {
          if (IntaveControl.DISABLE_LICENSE_CHECK) {
            IntaveLogger.logger().error("mounted On Entity could not be found");
          }
        }
      }
    } else if (entityID == player.getEntityId()) {
      // The Player
      // ID -1 => undo attachment
      tryCreateVehicleEntity(user, vehicleEntityID);
      WrappedEntity ridingEntity = synchronizedEntityMap.get(vehicleEntityID);
      if (movementData.hasRidingEntity()) {
        movementData.dismountRidingEntity();
      }
      if (ridingEntity != null && !(ridingEntity instanceof DestroyedWrappedEntity)) {
        movementData.setRidingEntity(ridingEntity);
      }
    }
  }

  private void tryCreateVehicleEntity(User user, int entityID) {
    Entity entity = serverEntityByIdentifier(user.player(), entityID);
    if (entity != null) {
      spawnMobByBukkitEntity(user, entity);
    }
  }

  @PacketSubscription(
    packetsOut = {
      SPAWN_ENTITY_LIVING, SPAWN_ENTITY, NAMED_ENTITY_SPAWN
    },
    ignoreCancelled = false
  )
  public void sendEntitySpawn(PacketEvent event) {
    /* IMPORTANT: If the entity spawn packet gets synchronized the player could be spammed with transaction packets
     *   which could cause a too many packets kick
     *
     * Also: When this packet gets synchronized (via appending the event on the next transaction packet) the entity_teleport and other entity move packets needs
     *  to be verified too because these packets could come in in the wrong order.
     */
//    plugin.eventService().transactionFeedbackService().requestPong(event.getPlayer(), event, this::processEntitySpawn);
//    Thread.dumpStack();
    processEntitySpawn(event.getPlayer(), event);
//    plugin.eventService().feedback().singleSynchronize(event.getPlayer(), event, this::processEntitySpawn, APPEND_ON_OVERFLOW);
  }

  private void processEntitySpawn(Player player, PacketEvent event) {
    User user = UserRepository.userOf(player);
    AttackMetadata attackData = user.meta().attack();
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();
    EntityTypeData entityTypeData;
    boolean entityIsPlayer = false;
    Integer entityId = packet.getIntegers().read(0);
    if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
      // dead entities
      entityTypeData = entityTypeResolver.entityTypeDataOfDeadEntity(event);
    } else if (packetType == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
      // entities
      entityTypeData = entityTypeResolver.entityTypeDataOfLivingEntity(event);
    } else {
      // player
      FakePlayer fakePlayer = attackData.fakePlayer();
      String entityName;
      if (fakePlayer != null && fakePlayer.identifier() == entityId) {
        entityName = "Intave-Bot";
      } else {
        entityName = "Player";
      }

      HitBoxBoundaries hitBoxBoundaries = HitBoxBoundaries.player();
      entityIsPlayer = true;
      entityTypeData = new EntityTypeData(entityName, hitBoxBoundaries, 105, true);
    }
    if (entityTypeData == null) {
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        IntavePlugin.singletonInstance().logger().error("Cannot resolve entityType: " + entityId);
      }
      return;
    }
    processPacketSpawnMob(user, event.getPacketType(), entityTypeData, packet, entityId, entityIsPlayer);
  }

  private final boolean INT_LIST_ENTITY_DESTROY = MinecraftVersions.VER1_17_1.atOrAbove();;
  private final boolean SINGLE_INT_ENTITY_DESTROY = !INT_LIST_ENTITY_DESTROY && MinecraftVersions.VER1_17_0.atOrAbove();;
  private final boolean INT_ARRAY_ENTITY_DESTROY = !SINGLE_INT_ENTITY_DESTROY;;

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_DESTROY
    },
    ignoreCancelled = false
  )
  public void receiveEntityDestroy(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    if (INT_LIST_ENTITY_DESTROY) {
      List<Integer> entityIDs = packet.getIntLists().read(0);
      entityIDs.forEach(entityID -> enterEntityDestroy(player, entityID));
    } else if (INT_ARRAY_ENTITY_DESTROY) {
      int[] entityIDs = packet.getIntegerArrays().read(0);
      for (int entityID : entityIDs) {
        enterEntityDestroy(player, entityID);
      }
    } else {
      enterEntityDestroy(player, packet.getIntegers().read(0));
    }
  }

  private void enterEntityDestroy(Player player, int entityID) {
    /*
    Important: When the destroy entity packet is synchronised the spawn entity packet needs also be synchronized because:
    When you respawn the server sends a destroy entity packet and a spawn entity packet pretty fast one after another and if the
    destroy entity packet gets executed after the spawn packet the entity will be destroyed right after it gets spawned
     */
    User user = UserRepository.userOf(player);
    ConnectionMetadata synchronizeData = user.meta().connection();
    WrappedEntity wrappedEntity = synchronizeData.synchronizedEntityMap().get(entityID);
    if (wrappedEntity instanceof WrappedEntityFirework) {
      plugin.eventService().feedback().singleSynchronize(player, entityID, this::processEntityDestroy, APPEND_ON_OVERFLOW);
    } else {
      processEntityDestroy(player, entityID);
    }
  }

  private void processEntityDestroy(Player player, int entityId) {
    User user = UserRepository.userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    MovementMetadata movementData = user.meta().movement();

    WrappedEntity entity = synchronizedEntityMap.get(entityId);
    if (entity != null && movementData.ridingEntity() == entity) {
      movementData.dismountRidingEntity();
    }
    synchronizedEntityMap.put(entityId, WrappedEntity.destroyedEntity());

    if (attackData.lastAttackedEntity() != null && attackData.lastAttackedEntityID() == entityId) {
      attackData.nullifyLastAttackedEntity();
    }
    if (NEW_POSITION_PROCESSING_1_9) {
      for (WrappedEntity wrappedEntity : synchronizedEntityMap.values()) {
        if (wrappedEntity.mountedEntity() != null) {
          if (wrappedEntity.mountedEntity().entityId() == entityId) {
            wrappedEntity.unmountFromEntity();
          }
        }
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGHEST,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ConnectionMetadata synchronizeData = user.meta().connection();
    MovementMetadata movementData = user.meta().movement();

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
    packetsOut = {
      ENTITY_TELEPORT
    },
    ignoreCancelled = false
  )
  public void receiveEntityTeleport(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    final WrappedEntity entity = wrappedEntityByEntityTeleportPacket(event);

    if(entity == null) return;

    if (entity.entityTypeData.isLivingEntity() && entity.tracingEnabled()) {
      Callback<PacketEvent> task = (player1, event1) -> {
        entity.verifiedPosition = false;
        entity.handleEntityTeleport(packet);
        entity.clientSynchronized = true;
      };

      if (entity.doubleVerification) {
        Callback<PacketEvent> verificationTask = (x, theEvent) -> entity.verifiedPosition = true;
        plugin.eventService().feedback().doubleSynchronize(player, event, event, task, verificationTask);
      } else {
        plugin.eventService().feedback().singleSynchronize(player, event, task);
      }
    } else {
      entity.handleEntityTeleport(packet);
      entity.clientSynchronized = false;
    }
  }

  private WrappedEntity wrappedEntityByEntityTeleportPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    WrappedEntity entity = entityByIdentifier(user, entityId);

    if (entity == null) {
      Entity bukkitEntity = serverEntityByIdentifier(player, entityId);
      if (bukkitEntity != null) {
        return spawnMobByBukkitEntity(user, bukkitEntity);
      } else {
//      IntaveLogger.logger().info("Unable to create entity (id " + entityId + ")");
//      throw new NullPointerException("entity could not be created");
      }
    }
    return entity;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK, ENTITY_LOOK
    },
    ignoreCancelled = false
  )
  public void receiveEntityMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    /* NOTE: An entity can't be created by the entityID when the entity doesn't
     gets teleported afterwards because the Bukkit location isn't specific enough */
    WrappedEntity entity = entityByIdentifier(user, entityId);

    if(entity == null) return;

    if (entity.entityTypeData.isLivingEntity() && entity.tracingEnabled()) {
      Callback<PacketEvent> task = (player1, event1) -> {
        entity.verifiedPosition = false;
        entity.handleEntityMovement(packet);
      };
      if (entity.doubleVerification) {
        Callback<PacketEvent> verificationTask = (x, theEvent) -> entity.verifiedPosition = true;
        plugin.eventService().feedback().doubleSynchronize(player, event, event, task, verificationTask);
      } else {
        plugin.eventService().feedback().singleSynchronize(player, event, task);
      }
    } else {
      entity.handleEntityMovement(packet);
      entity.clientSynchronized = false;
    }
  }

  private WrappedEntity spawnMobByBukkitEntity(User user, Entity bukkitEntity) {
    Location location = bukkitEntity.getLocation();
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
      entityID, entityTypeData,
      serverPosX, serverPosY, serverPosZ,
      bukkitEntity.getType() == EntityType.PLAYER
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
    int entityId, boolean player
  ) {
    if (NEW_POSITION_PROCESSING_1_9) {
      double posX = packet.getDoubles().read(0);
      double posY = packet.getDoubles().read(1);
      double posZ = packet.getDoubles().read(2);

      processEntitySpawnNewVersion(
        user, entityTypeData, entityId,
        posX, posY, posZ,
        player
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
        user, entityId, entityTypeData,
        serverPosX, serverPosY, serverPosZ,
        player
      );

//      WrappedEntity wrappedEntity = entityByIdentifier(user, entityID);
//      if (wrappedEntity != null)
//        Bukkit.broadcastMessage("pt " + packetType.name() + " p " + user.player().getName() + " e " + wrappedEntity.position);
    }
  }

  private void processEntitySpawnNewVersion(
    User user, EntityTypeData entityTypeData, int entityId,
    double posX, double posY, double posZ,
    boolean player
  ) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    WrappedEntity entity = createEntityOf(user, entityId, entityTypeData, player);
    entity.serverPosX = WrappedMathHelper.getPositionLong(posX);
    entity.serverPosY = WrappedMathHelper.getPositionLong(posY);
    entity.serverPosZ = WrappedMathHelper.getPositionLong(posZ);
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
    synchronizedEntityMap.put(entityId, entity);
  }

  private WrappedEntity processEntitySpawn(
    User user, int entityId, EntityTypeData entityTypeData,
    long serverPosX, long serverPosY, long serverPosZ,
    boolean player
  ) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    Map<Integer, WrappedEntity> synchronizedEntityMap = synchronizeData.synchronizedEntityMap();
    double posX = serverPosX / 32d;
    double posY = serverPosY / 32d;
    double posZ = serverPosZ / 32d;
    WrappedEntity entity = createEntityOf(user, entityId, entityTypeData, player);
    entity.serverPosX = serverPosX;
    entity.serverPosY = serverPosY;
    entity.serverPosZ = serverPosZ;
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
    synchronizedEntityMap.put(entityId, entity);

    return entity;
  }

  private WrappedEntity createEntityOf(
    User user,
    int entityId,
    EntityTypeData entityTypeData,
    boolean player
  ) {
    WrappedEntity entity;
    if (entityTypeData.entityName() != null && entityTypeData.entityName().contains("Firework")) {
      entity = new WrappedEntityFirework(user, entityId, entityTypeData);
    } else {
      entity = new WrappedEntity(entityId, entityTypeData, player);
    }
    return entity;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      ENTITY_STATUS
    },
    ignoreCancelled = false
  )
  public void receiveEntityStatus(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    if (user == null || !user.hasPlayer()) {
      return;
    }
    PacketContainer packet = event.getPacket();
    Integer entityID = packet.getIntegers().read(0);
    Byte type = packet.getBytes().read(0);
    WrappedEntity entity = entityByIdentifier(user, entityID);
    if (entity == null || type != 3) {
      return;
    }
    boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
    if (synchronize) {
      Callback<WrappedEntity> task = (p, e) -> updateDeadState(e);
      plugin.eventService().feedback().singleSynchronize(player, entity, task);
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
    packetsOut = {
      ENTITY_METADATA
    },
    ignoreCancelled = false
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
    if (!entity.entityTypeData.isLivingEntity()) {
      return;
    }

    List<WrappedWatchableObject> watchableObjects = packet.getWatchableCollectionModifier().read(0);
    if (watchableObjects != null) {
      int entityTypeId = entity.entityTypeData.entityTypeId();
      EntityTypeData entityTypeData = entityTypeResolver.entityTypeDataOfEntityMetaData(event, entityTypeId, watchableObjects);
      if (entityTypeData != null) {
        entity.entityTypeData = entityTypeData;
      } else {
//        IntaveLogger.logger().info("Unable to update entity metadata of entity " + entityId + " of type " + entityTypeId);
      }

      Float health = readHealthOf(watchableObjects);
      if (health != null) {
        boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
        if (synchronize) {
          plugin.eventService().feedback().singleSynchronize(player, entity, (p, e) -> updateHealthState(e, health));
        } else {
          updateHealthState(entity, health);
        }
      }
    }
  }

  private void synchronizePlayerHealth(Player player, PacketContainer packet) {
    Float health = readHealthOf(packet.getWatchableCollectionModifier().read(0));
    if (health != null) {
      AbilityMetadata abilityData = UserRepository.userOf(player).meta().abilities();
      abilityData.unsynchronizedHealth = health;
      plugin.eventService().feedback().singleSynchronize(player, health, (p, retrievedHealth) -> {
        abilityData.health = retrievedHealth;
        abilityData.ticksToLastHealthUpdate = 0;
      });
    }
  }

  private Float readHealthOf(List<WrappedWatchableObject> watchableObjects) {
    for (WrappedWatchableObject watchableObject : watchableObjects) {
      int index = watchableObject.getIndex();
      int requiredIndex;
      if (MinecraftVersions.VER1_17_0.atOrAbove()) {
        requiredIndex = 9;
      } else if (HEALTH_PROCESSING_1_14) {
        requiredIndex = 8;
      } else if (HEALTH_PROCESSING_1_10) {
        requiredIndex = 7;
      } else {
        requiredIndex = 6;
      }
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

  private final static Map<World, EquivalentConverter<Entity>> ENTITY_CONVERTER = GarbageCollector.watch(new HashMap<>());

  @Nullable
  public static Entity serverEntityByIdentifier(Player player, int entityID) {
    if (entityID < 0) {
      return null;
    }
    EquivalentConverter<Entity> converter =
      ENTITY_CONVERTER.computeIfAbsent(player.getWorld(), BukkitConverters::getEntityConverter);
    return converter.getSpecific(entityID);
  }

  @Nullable
  public static WrappedEntity entityByIdentifier(User user, int entityID) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    return synchronizeData.synchronizedEntityMap().getOrDefault(entityID, null);
  }
}