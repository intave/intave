package de.jpx3.intave.module.tracker.entity;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.size.HitboxSizeAccess;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.entity.type.EntityTypeDataAccessor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.packet.reader.EntityReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.jpx3.intave.module.tracker.entity.EntityTypeResolver.AgeCategory.*;

public final class EntityTypeResolver {
  private final boolean AT_OR_ABOVE_1_9 = MinecraftVersions.VER1_9_0.atOrAbove();
  private final boolean AT_OR_ABOVE_1_10 = MinecraftVersions.VER1_10_0.atOrAbove();
  private final boolean AT_OR_ABOVE_1_14 = MinecraftVersions.VER1_14_0.atOrAbove();
  private final boolean AT_OR_ABOVE_1_15 = MinecraftVersions.VER1_15_0.atOrAbove();
  private static final boolean DATA_WATCHER_ACCESS_UNDER_1_15 = !MinecraftVersions.VER1_15_0.atOrAbove();
  private static final boolean ENTITY_TYPE_ACCESS_UNDER_1_14 = !MinecraftVersions.VER1_14_0.atOrAbove();
  private String dataWatcherEntityFieldName;

  public EntityTypeResolver(IntavePlugin plugin) {
    if (DATA_WATCHER_ACCESS_UNDER_1_15) {
      registerDataWatcherEntityFieldName();
    }
  }

  private void registerDataWatcherEntityFieldName() {
    com.comphenix.protocol.utility.MinecraftVersion serverVersion = ProtocolLibraryAdapter.serverVersion();
    if (serverVersion.isAtLeast(MinecraftVersions.VER1_14_0)) {
      dataWatcherEntityFieldName = "entity";
    } else if (serverVersion.isAtLeast(MinecraftVersions.VER1_10_0)) {
      dataWatcherEntityFieldName = "c";
    } else if (serverVersion.isAtLeast(MinecraftVersions.VER1_9_0)) {
      dataWatcherEntityFieldName = "b";
    } else {
      dataWatcherEntityFieldName = "a";
    }

    // search field

    Class<?> entityClass = Lookup.serverClass("Entity");
    Class<?> dataWatcherClass = Lookup.serverClass("DataWatcher");

    for (Field declaredField : dataWatcherClass.getDeclaredFields()) {
      if (declaredField.getType() == entityClass) {
        String fieldName = declaredField.getName();
//        if (!dataWatcherEntityFieldName.equals(fieldName)) {
//          IntaveLogger.logger().pushPrintln("[Intave] Conflicting method name: \"" + dataWatcherEntityFieldName + "\" expected but found \"" + fieldName + "\" for entity-from-dw access");
//        }
        dataWatcherEntityFieldName = fieldName;
        break;
      }
    }
  }

  private static final int ENTITY_DEAD_TYPE_FIELD = MinecraftVersions.VER1_9_0.atOrAbove() ? 6 : 9;

  public EntityTypeData entityTypeDataOfDeadEntity(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);

    EntityReader entityReader = PacketReaders.readerOf(packet);
    Entity entity = entityReader.entityBy(event);
    entityReader.release();

    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    } else {
      if (ENTITY_TYPE_ACCESS_UNDER_1_14) {
        try {
          int deadEntityType = packet.getIntegers().read(ENTITY_DEAD_TYPE_FIELD);
          String name = nameByDeadEntityType(deadEntityType);
          HitboxSize boundaries = hitboxBoundariesByDeadEntityType(deadEntityType);
          return new EntityTypeData(name, boundaries, deadEntityType == 1 ? 41 : -1, false, 2);
        } catch (FieldAccessException exception) {
          IntaveLogger.logger().info("Can't access type data of " + entityId);
          exception.printStackTrace();
        }
        return new EntityTypeData("Invalid", HitboxSize.zero(), -2, false, 3);
      } else {
        EntityType entityType = packet.getEntityTypeModifier().read(0);
        Class<? extends Entity> entityClass = extractSubClassFromEntity(entityType.getEntityClass());
        String entityClassName = entityClass.getSimpleName();
        HitboxSize size = HitboxSizeAccess.dimensionsOfNMSEntityClass(entityClass);
        return new EntityTypeData(entityClassName, size, -2, false, 4);
      }
    }
  }

  public EntityTypeData entityTypeDataOfLivingEntity(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    Entity entity = EntityTracker.serverEntityByIdentifier(event.getPlayer(), entityId);
    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    } else {
      if (DATA_WATCHER_ACCESS_UNDER_1_15) {
        WrappedDataWatcher dataWatcher = packet.getDataWatcherModifier().read(0);
        // Checks if the packet has a Datawatcher
        if (dataWatcher != null && dataWatchesIncludesEntity(dataWatcher)) {
          return entityTypeDataOfDataWatcher(dataWatcher, true);
        } else {
          int entityTypeId = packet.getIntegers().read(1);
          return EntityTypeDataAccessor.resolveFromId(entityTypeId, true);
        }
      } else {
        int entityTypeId = packet.getIntegers().read(1);
        return EntityTypeDataAccessor.resolveFromId(entityTypeId, true);
      }
    }
  }

  public EntityTypeData entityTypeDataOfEntityMetadata(PacketEvent event, int entityTypeId, List<WrappedWatchableObject> watchableObjects) {
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    Entity entity = EntityTracker.serverEntityByIdentifier(event.getPlayer(), entityId);
    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    } else {
      AgeCategory age = entityAgeByWatchableObjects(watchableObjects, entityTypeId);
      if (age == UNKNOWN) {
        return null;
      } else {
        EntityTypeData entityTypeData = EntityTypeDataAccessor.resolveFromId(entityTypeId, false);
        if (age == BABY) {
          return convertHitboxBoundariesToBaby(entityTypeData);
        } else {
          return entityTypeData;
        }
      }
    }
  }

  private AgeCategory entityAgeByWatchableObjects(
    List<? extends WrappedWatchableObject> watchableObjects, int entityTypeId
  ) {
    int correctIndex = hardcodedAgeMetaIndexFor(entityTypeId);
    for (WrappedWatchableObject watchableObject : watchableObjects) {
      // needs a lot of performance and affects tps
      if (watchableObject.getIndex() == correctIndex) {
        // needs a lot of performance and affects tps
        Object object = watchableObject.getRawValue();
        if (object != null) {
          if (object instanceof Boolean) {
            return (boolean) object ? BABY : ADULT;
          } else if (object instanceof Byte) {
            byte isChild = (byte) object;
            if (AT_OR_ABOVE_1_14 && entityTypeId == 30) {
              return isChild == 1 ? BABY : ADULT;
            } else {
              return isChild < 0 ? BABY : ADULT;
            }
          } else {
            return UNKNOWN;
          }
        }
      }
    }
    return UNKNOWN;
  }

  public enum AgeCategory {
    BABY,
    ADULT,
    UNKNOWN
  }

  private int hardcodedAgeMetaIndexFor(int entityTypeId) {
    if (!AT_OR_ABOVE_1_9) {
      // 1.8
      return 12;
    } else if (!AT_OR_ABOVE_1_10) {
      // 1.9
      return 11;
    } else if (!AT_OR_ABOVE_1_14) {
      // 1.10+
      return 12;
    } else if (!AT_OR_ABOVE_1_15) {
      if (entityTypeId == 30) {
        // armorstand
        return 13;
      } else {
        // 1.14+
        return 14;
      }
    } else {
      // 1.15+
      if (entityTypeId == 30) {
        // armorstand
        return 14;
      } else {
        return 15;
      }
    }
  }

  private EntityTypeData convertHitboxBoundariesToBaby(EntityTypeData entityTypeData) {
    if (entityTypeData == null) {
      return null;
    }
    HitboxSize size = HitboxSize.of(entityTypeData.size().width() * 0.5f, entityTypeData.size().height() * 0.5f);
    return new EntityTypeData(entityTypeData.name(), size, entityTypeData.typeId(), entityTypeData.isLivingEntity(), 5);
  }

  public HitboxSize hitBoxBoundariesByBukkitEntity(Entity bukkitEntity) {
    return HitboxSizeAccess.dimensionsOfBukkit(bukkitEntity);
  }

  public String entityNameByBukkitEntity(Entity entity) {
    return entityNameOf(ReflectiveHandleAccess.handleOf(entity));
  }

  public EntityTypeData entityTypeDataOfBukkitEntity(Entity entity) {
    HitboxSize hitBoxSize = hitBoxBoundariesByBukkitEntity(entity);
    String name = entityNameByBukkitEntity(entity);

    if (entity.getType() == EntityType.ZOMBIE || entity.getType() == EntityType.PIG_ZOMBIE) {
      Zombie zombie = (Zombie) entity;
      if (zombie.isBaby()) {
        // setting the hitbox of the zombie to a normal zombie hitbox which is the same as a player hitbox
        hitBoxSize = HitboxSize.playerDefault();
      }
    }
    boolean isEntityLiving = entity instanceof LivingEntity;
    return new EntityTypeData(name, hitBoxSize, entity.getType().getTypeId(), isEntityLiving, 6);
  }

  public boolean dataWatchesIncludesEntity(WrappedDataWatcher dataWatcher) {
    return entityOfDataWatcher(dataWatcher) != null;
  }

  private EntityTypeData entityTypeDataOfDataWatcher(WrappedDataWatcher dataWatcher, boolean isLivingEntity) {
    Object entity = entityOfDataWatcher(dataWatcher);
    HitboxSize hitBoxSize = HitboxSizeAccess.dimensionsOfNative(entity);
    String name = entityNameOf(entity);
    int entityTypeId = entityTypeIdOfDataWatcher(dataWatcher);
    return new EntityTypeData(name, hitBoxSize, entityTypeId, isLivingEntity, 7);
  }

  private int entityTypeIdOfDataWatcher(WrappedDataWatcher dataWatcher) {
    return dataWatcher.getEntity().getType().getTypeId();
  }

  private String entityNameOf(Object entity) {
    String entityName = extractSubClassFromEntity((Class<? extends Entity>) entity.getClass()).getSimpleName();
    if (entityName.startsWith("Entity")) {
      entityName = entityName.substring("Entity".length());
    }
    return entityName;
  }

  private static final Map<Class<? extends Entity>, Class<? extends Entity>> subClassCache = new ConcurrentHashMap<>();

  // support custom entity classes
  private static Class<? extends Entity> extractSubClassFromEntity(Class<? extends Entity> entityClass) {
    if (entityClass == null) {
      return null;
    }
    if (subClassCache.containsKey(entityClass)) {
      return subClassCache.get(entityClass);
    }
    // support for custom shulker entity classes
    Class<?> hierarchySearch = entityClass;
    int attempts = 0;
    while (!hierarchySearch.getPackage().getName().toLowerCase().startsWith("net.minecraft")) {
      if (hierarchySearch.getSuperclass() == null) {
        subClassCache.put(entityClass, (Class<? extends Entity>) hierarchySearch);
        return entityClass;
      }
      hierarchySearch = hierarchySearch.getSuperclass();
      if (attempts++ > 8) {
        subClassCache.put(entityClass, (Class<? extends Entity>) hierarchySearch);
        return entityClass;
      }
    }
    subClassCache.put(entityClass, (Class<? extends Entity>) hierarchySearch);
    return (Class<? extends Entity>) hierarchySearch;
  }

  private Object entityOfDataWatcher(WrappedDataWatcher dataWatcher) {
    Object handle = dataWatcher.getHandle();
    Class<?> handleClass = handle.getClass();
    try {
      return entityByHandle(handle, handleClass.getDeclaredField(dataWatcherEntityFieldName));
    } catch (NoSuchFieldException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private Object entityByHandle(Object handle, Field entityField) {
    try {
      ensureAccessibility(entityField);
      return entityField.get(handle);
    } catch (Exception exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private void ensureAccessibility(Field field) {
    if (!field.isAccessible()) {
      field.setAccessible(true);
    }
  }

  private String nameByDeadEntityType(int deadEntityType) {
    switch (deadEntityType) {
      case 1:
        return "EntityBoat";
      case 2:
        return "EntityItem";
      case 10:
        return "EntityMinecart";
      case 50:
        return "EntityTNTPrimed";
      case 51:
        return "EntityEnderCrystal";
      case 61:
        return "EntitySnowball";
      case 62:
        return "EntityEgg";
      case 63:
        return "EntityFireball";
      case 64:
        return "EntitySmallFireball";
      case 65:
        return "EntityEnderPearl";
      case 66:
        return "EntityWitherSkull";
      case 70:
        return "EntityFallingBlock";
      case 72:
        return "EntityEnderEye";
      case 73:
        return "EntityPotion";
      case 75:
        return "EntityExpBottle";
      case 76:
        return "EntityFireworkRocket";
      case 77:
        return "EntityLeashKnot";
      case 78:
        return "EntityArmorStand";
      case 90:
        return "EntityFishHook";
    }
    return "null";
  }

  private HitboxSize hitboxBoundariesByDeadEntityType(int deadEntityType) {
    switch (deadEntityType) {
      case 1:
        return HitboxSize.of(1.5F, 0.6F);
      case 2:
      case 61:
      case 62:
      case 65:
      case 72:
      case 73:
      case 75:
      case 76:
      case 90:
        return HitboxSize.of(0.25F, 0.25F);
      case 10:
        return HitboxSize.of(0.98F, 0.7F);
      case 50:
      case 70:
        return HitboxSize.of(0.98F, 0.98F);
      case 51:
        return HitboxSize.of(2.0F, 2.0F);
      case 63:
        return HitboxSize.of(3.0F, 3.0F);
      case 64:
      case 66:
        return HitboxSize.of(0.3125F, 0.3125F);
      case 77:
        return HitboxSize.of(0.5F, 0.5F);
      case 78:
        return HitboxSize.of(0.5F, 1.975F);
      case 60: // arrows
        return HitboxSize.zero();
    }
//    if (IntaveControl.DISABLE_LICENSE_CHECK) {
//      IntaveLogger.logger().info("Failed to map bounding box of dead entity " + deadEntityType + "/" + nameByDeadEntityType(deadEntityType));
//    }
    return HitboxSize.zero();
  }
}