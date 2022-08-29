package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.check.movement.physics.Pose;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.feedback.FeedbackCallback;
import de.jpx3.intave.module.feedback.FeedbackTracker;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.nayoro.Nayoro;
import de.jpx3.intave.module.nayoro.event.EntityMoveEvent;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.packet.reader.EntityDestroyReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.share.ClientMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

  public EntityTracker(IntavePlugin plugin) {
    this.plugin = plugin;
    this.entityTypeResolver = new EntityTypeResolver(plugin);
//    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
//    this.setupSynchronizer();
  }

  @Override
  public void enable() {
    //noinspection deprecation
    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, this::reevaluateTracingEntities, 0, 20);
    TaskTracker.begun(taskId);
  }

  private void reevaluateTracingEntities() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      selectEntitiesToTraceFor(player);
    }
  }

  private static final int REQUIRED_DISTANCE = 16;
  private static final int MAX_TRACED_ENTITIES = 4;
  private static final int MAX_DOUBLE_TRACED_ENTITIES = 1;

  private void selectEntitiesToTraceFor(Player player) {
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    ConnectionMetadata synchronizeData = user.meta().connection();
//    Vector location = new Vector(0, 0, 0);
    Vector playerLocation = player.getLocation().toVector();
    List<EntityShade> validEntities = new ArrayList<>();
    for (EntityShade entity : synchronizeData.entities()) {
      boolean firstSurvive = false;
      if (entity.typeData() != null) {
        double distance = entity.distanceTo(playerLocation);
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
    synchronizeData.tracedEntities().clear();
    for (EntityShade entity : validEntities) {
      boolean trace = count < MAX_TRACED_ENTITIES;
      if (trace) {
        synchronizeData.tracedEntities().add(entity);
      }
      entity.setResponseTracingEnabled(trace);
      entity.doubleVerification = trace && count < MAX_DOUBLE_TRACED_ENTITIES;
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

    if (event.getPacketType() == PacketType.Play.Server.MOUNT) {
      //1.9+ servers
      int[] entityIDs = event.getPacket().getIntegerArrays().read(0);
      int vehicleEntityID = packet.getIntegers().read(0);

      for (int entityID : entityIDs) {
        processAttachEntity(player, entityID, vehicleEntityID);
      }
    } else if (event.getPacketType() == PacketType.Play.Server.ATTACH_ENTITY && !NEW_POSITION_PROCESSING_1_9) {
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
    MetadataBundle metadataBundle = user.meta();
    MovementMetadata movementData = metadataBundle.movement();
    ConnectionMetadata connection = metadataBundle.connection();
//    Map<Integer, WrappedEntity> entities = connection.entities();
    EntityShade sittingEntity = connection.entityBy(entityID);

    if (sittingEntity != null) {
      // Another entity
      if (vehicleEntityID == -1) {
        // when an entity dismounts
        sittingEntity.unmountFromEntity();
      } else {
        // mounts on entity
        EntityShade sittingOnEntity = connection.entityBy(vehicleEntityID);
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
      EntityShade target = connection.entityBy(vehicleEntityID);
      if (target == null) {
        target = EntityShade.destroyedEntity();
      }
      Modules.feedback().synchronize(player, target, (a, ridingEntity) -> {
        if (movementData.isInVehicle()) {
          movementData.dismountRidingEntity();
        }
        if (!(ridingEntity == null || ridingEntity instanceof EntityShade.Destroyed)) {
          movementData.setVehicle(ridingEntity);
        }
      });
    }
  }

  private void tryCreateVehicleEntity(User user, int entityID) {
    Entity entity = serverEntityByIdentifier(user.player(), entityID);
    if (entity != null && user.meta().connection().entityBy(entityID) == null) {
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
//    Modules.feedback().singleSynchronize(event.getPlayer(), event, this::processEntitySpawn, APPEND_ON_OVERFLOW);
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

      HitboxSize hitBoxSize = HitboxSize.playerDefault();
      entityIsPlayer = true;
      entityTypeData = new EntityTypeData(entityName, hitBoxSize, 105, true, 1);
    }
    if (entityTypeData == null) {
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        IntavePlugin.singletonInstance().logger().error("Cannot resolve entityType: " + entityId);
      }
      return;
    }
    processPacketSpawnMob(user, event.getPacketType(), entityTypeData, packet, entityId, entityIsPlayer);
  }


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
    EntityDestroyReader reader = PacketReaders.readerOf(packet);
    reader.readEntities(entityId -> enterEntityDestroy(player, entityId));
    reader.release();
  }

  private void enterEntityDestroy(Player player, int entityID) {
    // Entity destroy packets are NEVER to be synchronized

    /*
    Important: When the destroy entity packet is synchronised the spawn entity packet needs also be synchronized because:
    When you respawn the server sends a destroy entity packet and a spawn entity packet pretty fast one after another and if the
    destroy entity packet gets executed after the spawn packet the entity will be destroyed right after it gets spawned
     */
//    User user = UserRepository.userOf(player);
//    ConnectionMetadata synchronizeData = user.meta().connection();
//    EntityShade entityShade = synchronizeData.entityBy(entityID);
    processEntityDestroy(player, entityID);
  }

  private void processEntityDestroy(Player player, int entityId) {
    User user = UserRepository.userOf(player);
    AttackMetadata attackData = user.meta().attack();
    ConnectionMetadata synchronizeData = user.meta().connection();
    MovementMetadata movementData = user.meta().movement();

    EntityShade entity = synchronizeData.entityBy(entityId);//synchronizedEntityMap.get(entityId);
    if (entity != null && movementData.ridingEntity() == entity) {
      movementData.dismountRidingEntity();
    }
    synchronizeData.destroyEntity(entityId);
    if (attackData.lastAttackedEntity() != null && attackData.lastAttackedEntityID() == entityId) {
      attackData.nullifyLastAttackedEntity();
    }
    if (NEW_POSITION_PROCESSING_1_9) {
      for (EntityShade entityShade : synchronizeData.entities()) {
        if (entityShade.mountedEntity() != null) {
          if (entityShade.mountedEntity().entityId() == entityId) {
            entityShade.unmountFromEntity();
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
    for (EntityShade value : synchronizeData.entities()) {
      int ticksAfterPositionChange = value.position.newPosRotationIncrements;
      value.onUpdate();
      if (value.tracingEnabled() && ticksAfterPositionChange > 0) {
        nayoroEntityPositionUpdate(player, value);
      }
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
    EntityShade entity = wrappedEntityByEntityTeleportPacket(event);

    if (entity == null) {
      return;
    }
    entity.immediateEntityTeleport(packet);
    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      FeedbackCallback<PacketEvent> task = (player1, event1) -> {
        entity.verifiedPosition = false;
        entity.handleEntityTeleport(packet);
        entity.clientSynchronized = true;
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackTracker feedbackTracker = entity.feedbackTracker();
//      if (entity.doubleVerification) {
//        FeedbackCallback<PacketEvent> verificationTask = (x, theEvent) -> entity.verifiedPosition = true;
//        Modules.feedback().tracedDoubleSynchronize(player, event, event, task, verificationTask, feedbackTracker, feedbackTracker);
//      } else {
      Modules.feedback().tracedSingleSynchronize(player, event, task, feedbackTracker);
//      }
    } else {
      entity.handleEntityTeleport(packet);
      entity.clientSynchronized = false;
    }
  }

  private EntityShade wrappedEntityByEntityTeleportPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    EntityShade entity = entityByIdentifier(user, entityId);

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
    EntityShade entity = entityByIdentifier(user, entityId);

    if (entity == null) {
      return;
    }
    entity.immediateEntityMovement(packet);

    if (entity.typeData().isLivingEntity() && entity.tracingEnabled()) {
      FeedbackCallback<PacketEvent> task = (player1, event1) -> {
        entity.verifiedPosition = false;
        entity.handleEntityMovement(packet);
        nayoroEntityPositionUpdate(player, entity);
      };
      FeedbackTracker tracker = entity.feedbackTracker();
//      if (entity.doubleVerification) {
//        FeedbackCallback<PacketEvent> verificationTask = (x, theEvent) -> entity.verifiedPosition = true;
//        Modules.feedback().tracedDoubleSynchronize(player, event, event, task, verificationTask, tracker, tracker);
//      } else {
      Modules.feedback().tracedSingleSynchronize(player, event, task, tracker);
//      }
    } else {
      entity.handleEntityMovement(packet);
      entity.clientSynchronized = false;
    }
  }

  private final BiConsumer<User, Consumer<EventSink>> sinkCallback = Modules.nayoro().sinkCallback();

  private void nayoroEntityPositionUpdate(Player player, EntityShade entity) {
    Nayoro nayoro = Modules.nayoro();
    if (!nayoro.recordingActiveFor(UserRepository.userOf(player))) {
      return;
    }
    EntityShade.EntityPositionContext position = entity.position;
    EntityShade.EntityPositionContext lastPosition = entity.lastPosition;
    EntityMoveEvent event = new EntityMoveEvent(
      entity.entityId(),
      position.posX,
      position.posY,
      position.posZ,
      lastPosition.posX,
      lastPosition.posY,
      lastPosition.posZ,
      0,
      0,
      0,
      0
    );
    sinkCallback.accept(UserRepository.userOf(player), event::accept);
  }

  private EntityShade spawnMobByBukkitEntity(User user, Entity bukkitEntity) {
    Location location = bukkitEntity.getLocation();
    int entityID = bukkitEntity.getEntityId();

    long serverPosX;
    long serverPosY;
    long serverPosZ;

    if (NEW_POSITION_PROCESSING_1_9) {
      serverPosX = ClientMathHelper.positionLong(location.getX());
      serverPosY = ClientMathHelper.positionLong(location.getY());
      serverPosZ = ClientMathHelper.positionLong(location.getZ());
    } else {
      serverPosX = ClientMathHelper.floor(location.getX() * 32d);
      serverPosY = ClientMathHelper.floor(location.getY() * 32d);
      serverPosZ = ClientMathHelper.floor(location.getZ() * 32d);
    }

    EntityTypeData entityTypeData = entityTypeResolver.entityTypeDataOfBukkitEntity(bukkitEntity);

    EntityShade entity = processEntitySpawn(
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
//    Map<Integer, WrappedEntity> entities = synchronizeData.entities();
    EntityShade entity = createEntityOf(user, entityId, entityTypeData, player);
    entity.serverPosX = ClientMathHelper.positionLong(posX);
    entity.serverPosY = ClientMathHelper.positionLong(posY);
    entity.serverPosZ = ClientMathHelper.positionLong(posZ);
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
//    entities.put(entityId, entity);
    synchronizeData.enterEntity(entity);
  }

  private EntityShade processEntitySpawn(
    User user, int entityId, EntityTypeData entityTypeData,
    long serverPosX, long serverPosY, long serverPosZ,
    boolean player
  ) {
    ConnectionMetadata synchronizeData = user.meta().connection();
//    Map<Integer, WrappedEntity> entities = synchronizeData.entities();
    double posX = serverPosX / 32d;
    double posY = serverPosY / 32d;
    double posZ = serverPosZ / 32d;
    EntityShade entity = createEntityOf(user, entityId, entityTypeData, player);
    entity.serverPosX = serverPosX;
    entity.serverPosY = serverPosY;
    entity.serverPosZ = serverPosZ;
    entity.setPositionAndRotationSpawnMob(posX, posY, posZ, posY);
//    entities.put(entityId, entity);
    synchronizeData.enterEntity(entity);

    return entity;
  }

  private EntityShade createEntityOf(
    User user,
    int entityId,
    EntityTypeData entityTypeData,
    boolean player
  ) {
    return new EntityShade(entityId, entityTypeData, player);
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
    EntityShade entity = entityByIdentifier(user, entityID);
    if (entity == null || type != 3) {
      return;
    }
    boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
    if (synchronize) {
      FeedbackCallback<EntityShade> task = (p, e) -> updateDeadState(e);
      Modules.feedback().tracedSingleSynchronize(player, entity, task, entity.feedbackTracker());
    } else {
      updateDeadState(entity);
    }
  }

  private void updateDeadState(EntityShade entity) {
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
    EntityShade entity = entityByIdentifier(user, entityId);
    if (entity == null) {
      return;
    }
    EntityTypeData type = entity.typeData();
    if (type == null) {
      return;
    }

    boolean livingEntity = entity.typeData().isLivingEntity();
    int entityTypeId = type.identifier();

    boolean fireWorkRocket = type.name() != null && type.name().contains("Firework");
    List<WrappedWatchableObject> watchableObjects = packet.getWatchableCollectionModifier().read(0);

    // Firework
    if (fireWorkRocket) {
      handleFirework(player, watchableObjects);
    } else if (livingEntity && watchableObjects != null) {
      // Health
      processHealthMetaData(player, entity, watchableObjects);

      // Entity Size
      EntityTypeData entityTypeData = entityTypeResolver.entityTypeDataOfEntityMetaData(event, entityTypeId, watchableObjects);
      if (entityTypeData != null) {
        entity.setTypeData(entityTypeData);
      } else {
//        IntaveLogger.logger().info("Unable to update entity metadata of entity " + entityId + " of type " + entityTypeId);
      }
    }
  }

  private void handleFirework(
    Player player,
    List<WrappedWatchableObject> watchableObjects
  ) {
    if (!MinecraftVersions.VER1_11_0.atOrAbove()) {
      return;
    }
    for (WrappedWatchableObject watchableObject : watchableObjects) {
      if (watchableObject != null) {
        int index = watchableObject.getIndex();
        Object value = watchableObject.getValue();
        if (MinecraftVersions.VER1_14_0.atOrAbove()) {
          if (processFireworkModern(player, index, value)) {
            // ?
            return;
          }
        } else {
          if (processFireworkLegacy(player, index, value)) {
            // ?
            return;
          }
        }
      }
    }
  }

  private boolean processFireworkLegacy(Player player, int index, Object value) {
    User user = UserRepository.userOf(player);
    if (index == 7) {
      if (!(value instanceof Integer)) {
        return false;
      }
      int entityId = (int) value;
      MovementMetadata movement = user.meta().movement();
      if (movement.pose() == Pose.FALL_FLYING && entityId == player.getEntityId()) {
        movement.fireworkRocketsTicks = 0;
      }
      return true;
    }
    return false;
  }

  private static final int MODERN_ENTITY_ID_ACCESS_INDEX = MinecraftVersions.VER1_17_0.atOrAbove() ? 9 : 8;

  private boolean processFireworkModern(Player player, int index, Object value) {
    User user = UserRepository.userOf(player);
    if (index == MODERN_ENTITY_ID_ACCESS_INDEX && value instanceof OptionalInt) {
      OptionalInt optionalId = (OptionalInt) value;
      if (!optionalId.isPresent()) {
        return false;
      }
      int entityId = optionalId.getAsInt();
      MovementMetadata movement = user.meta().movement();
      if ((movement.pose() == Pose.FALL_FLYING || movement.elytraFlying) && entityId == player.getEntityId()) {
        movement.fireworkRocketsTicks = 0;
      }
      return true;
    }
    return false;
  }

  private void processHealthMetaData(
    Player player,
    EntityShade entity,
    List<WrappedWatchableObject> watchableObjects
  ) {
    Float health = readHealthOf(watchableObjects);
    if (health != null) {
      boolean synchronize = entity.clientSynchronized && entity.tracingEnabled();
      if (synchronize) {
        FeedbackTracker tracker = entity.feedbackTracker();
        Modules.feedback().tracedSingleSynchronize(player, entity, (p, e) -> updateHealthState(e, health), tracker);
      } else {
        updateHealthState(entity, health);
      }
    }
  }

  private void synchronizePlayerHealth(Player player, PacketContainer packet) {
    List<WrappedWatchableObject> watchableObjects = packet.getWatchableCollectionModifier().read(0);
    if (watchableObjects == null) {
      return;
    }
    Float health = readHealthOf(watchableObjects);
    if (health != null) {
      AbilityMetadata abilityData = UserRepository.userOf(player).meta().abilities();
      abilityData.unsynchronizedHealth = health;
      Modules.feedback().synchronize(player, health, (p, retrievedHealth) -> {
        abilityData.health = retrievedHealth;
        abilityData.ticksToLastHealthUpdate = 0;
      });
    }
  }

  private final boolean HEALTH_PROCESSING_1_10 = MinecraftVersions.VER1_10_0.atOrAbove();
  private final boolean HEALTH_PROCESSING_1_14 = MinecraftVersions.VER1_14_0.atOrAbove();

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
        return readHealthFromWatchableObject(watchableObject);
      }
    }
    return null;
  }

  private Float readHealthFromWatchableObject(WrappedWatchableObject watchableObject) {
    Object rawValue = watchableObject.getRawValue();
    if (rawValue instanceof OptionalInt) {
      OptionalInt optionalInt = (OptionalInt) rawValue;
      if (!optionalInt.isPresent()) {
        return null;
      }

      rawValue = optionalInt.getAsInt();
    }
    return ((Number) rawValue).floatValue();
  }

  private void updateHealthState(EntityShade entity, float health) {
    entity.health = health;
  }

//  private final static Map<World, EquivalentConverter<Entity>> ENTITY_CONVERTER = GarbageCollector.watch(new HashMap<>());

  @Nullable
  public static Entity serverEntityByIdentifier(Player player, int entityID) {
    if (entityID < 0) {
      return null;
    }
    return EntityLookup.findEntity(player.getWorld(), entityID);
  }

  @Nullable
  public static EntityShade entityByIdentifier(User user, int entityID) {
    ConnectionMetadata synchronizeData = user.meta().connection();
    return synchronizeData.entityBy(entityID);
  }
}