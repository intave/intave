package de.jpx3.intave.event.entity;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectiveHandleAccess;
import de.jpx3.intave.reflect.hitbox.HitBoxBoundaries;
import de.jpx3.intave.reflect.hitbox.ReflectiveEntityHitBoxAccess;
import de.jpx3.intave.reflect.hitbox.typeaccess.DualEntityTypeAccess;
import de.jpx3.intave.reflect.hitbox.typeaccess.EntityTypeData;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;
import java.util.List;

public final class PacketEntityTypeResolver {
  private final static boolean AT_OR_ABOVE_1_9 = MinecraftVersions.VER1_9_0.atOrAbove();
  private final static boolean AT_OR_ABOVE_1_10 = MinecraftVersions.VER1_10_0.atOrAbove();
  private final static boolean AT_OR_ABOVE_1_14 = MinecraftVersions.VER1_14_0.atOrAbove();
  private final static boolean AT_OR_ABOVE_1_15 = MinecraftVersions.VER1_15_0.atOrAbove();
  private static final boolean DATA_WATCHER_ACCESS_UNDER_1_15 = !MinecraftVersions.VER1_15_0.atOrAbove();
  private static final boolean ENTITY_TYPE_ACCESS_1_14 = !MinecraftVersions.VER1_14_0.atOrAbove();
  private String dataWatcherEntityFieldName;

  public PacketEntityTypeResolver(IntavePlugin plugin) {
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

    Class<?> entityClass = ReflectiveAccess.NMS_ENTITY_CLASS;
    Class<?> dataWatcherClass = ReflectiveAccess.lookupServerClass("DataWatcher");

    for (Field declaredField : dataWatcherClass.getDeclaredFields()) {
      if (declaredField.getType() == entityClass) {
        String fieldName = declaredField.getName();
        if (!dataWatcherEntityFieldName.equals(fieldName)) {
          IntaveLogger.logger().pushPrintln("[Intave] Conflicting method name: \"" + dataWatcherEntityFieldName + "\" expected but found \"" + fieldName + "\" for entity-from-dw access");
        }
        dataWatcherEntityFieldName = fieldName;
        break;
      }
    }
  }

  public EntityTypeData entityTypeDataOfDeadEntity(PacketEvent event) {
    PacketContainer packet = event.getPacket();

    int entityId = packet.getIntegers().read(0);
    Entity entity = ClientSideEntityService.serverEntityByIdentifier(event.getPlayer(), entityId);

    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    } else {
      if (ENTITY_TYPE_ACCESS_1_14) {
        int deadEntityType = packet.getIntegers().read(9);
        String name = nameByDeadEntityType(deadEntityType);
        HitBoxBoundaries boundaries = hitboxBoundariesByDeadEntityType(deadEntityType);
        return new EntityTypeData(name, boundaries, -1);
      } else {
        if(IntaveControl.DISABLE_LICENSE_CHECK) {
          IntaveLogger.logger().info("Zero BoundingBox 2");
        }
        return new EntityTypeData("null", HitBoxBoundaries.zero(), -2);
      }
    }
  }

  public EntityTypeData entityTypeDataOfLivingEntity(PacketEvent event) {
    PacketContainer packet = event.getPacket();

    int entityId = packet.getIntegers().read(0);
    Entity entity = ClientSideEntityService.serverEntityByIdentifier(event.getPlayer(), entityId);

    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    } else {
      if (DATA_WATCHER_ACCESS_UNDER_1_15) {
        WrappedDataWatcher dataWatcher = packet.getDataWatcherModifier().read(0);
        // Guckt ob das Packet ein Datawatcher hat
        if (dataWatcher != null) {
          return entityTypeDataOfDataWatcher(dataWatcher);
        } else {
          int entityTypeId = packet.getIntegers().read(1);
          return entityTypeDataOfEntityType(entityTypeId);
        }
      } else {
        int entityTypeId = packet.getIntegers().read(1);
        return DualEntityTypeAccess.resolveFromId(entityTypeId);
      }
    }
  }

  private Boolean isChildByWatchableObjects(List<WrappedWatchableObject> watchableObjects, int entityTypeId) {
    final int correctIndex;
    if (AT_OR_ABOVE_1_9) {
      if (AT_OR_ABOVE_1_10) {
        if(AT_OR_ABOVE_1_14) {
          if(AT_OR_ABOVE_1_15) {
            // 1.15+
            if(entityTypeId == 1) {
              correctIndex = 14;
            } else {
              correctIndex = 15;
            }
          } else {
            // 1.14+
            correctIndex = 14;
          }
        } else {
          if(entityTypeId == 1) {
            correctIndex = 14;
          } else {
            // 1.10+
            correctIndex = 12;
          }
        }
      } else {
        // 1.9
        correctIndex = 11;
      }
    } else {
      // 1.8
      correctIndex = 12;
    }

    for (WrappedWatchableObject watchableObject : watchableObjects) {
      int index = watchableObject.getIndex();
      Object object = watchableObject.getRawValue();

      if(object != null) {
        if(index == correctIndex) {
          if(object instanceof Boolean) {
            Boolean isChild = (Boolean) object;
            return isChild;
          } else if(object instanceof Byte) {
            byte isChild = (byte) object;
            return isChild < 0;
          } else {
//          IntaveLogger.logger().info("Failed to read EntityMetaData packet. " + object.getClass());
            return null;
          }
        }
      }
    }

    return null;
  }

  public EntityTypeData entityTypeDataOfEntityMetaData(PacketEvent event, int entityTypeId, List<WrappedWatchableObject> watchableObjects) {
    PacketContainer packet = event.getPacket();
    int entityId = packet.getIntegers().read(0);
    Entity entity = ClientSideEntityService.serverEntityByIdentifier(event.getPlayer(), entityId);
    Boolean isChild = isChildByWatchableObjects(watchableObjects, entityTypeId);

    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    } else {
      EntityTypeData entityTypeData = entityTypeDataOfEntityType(entityTypeId);

      if(isChild == null) {
        return null;
      } else if (isChild) {
        return convertHitboxBoundariesToBaby(entityTypeData);
      } else {
        return entityTypeData;
      }
    }
  }

  private EntityTypeData convertHitboxBoundariesToBaby(EntityTypeData entityTypeData) {
    HitBoxBoundaries hitBoxBoundaries = HitBoxBoundaries.of(entityTypeData.hitBoxBoundaries().width() * 0.5f, entityTypeData.hitBoxBoundaries().length() * 0.5f);
    return new EntityTypeData(entityTypeData.entityName(), hitBoxBoundaries, entityTypeData.entityTypeId());
  }

  public HitBoxBoundaries hitBoxBoundariesByBukkitEntity(Entity bukkitEntity) {
    return ReflectiveEntityHitBoxAccess.boundariesOf(bukkitEntity);
  }

  public String entityNameByBukkitEntity(Entity entity) {
    return entityNameOf(ReflectiveHandleAccess.handleOf(entity));
  }

  public EntityTypeData entityTypeDataOfBukkitEntity(Entity entity) {
    HitBoxBoundaries hitBoxBoundaries = hitBoxBoundariesByBukkitEntity(entity);
    String name = entityNameByBukkitEntity(entity);
    return new EntityTypeData(name, hitBoxBoundaries, entity.getType().getTypeId());
  }

  public EntityTypeData entityTypeDataOfEntityType(int entityTypeId) {
    HitBoxBoundaries hitBoxBoundaries = DualEntityTypeAccess.boundariesFromId(entityTypeId);
    String name = DualEntityTypeAccess.nameFromID(entityTypeId);
    return new EntityTypeData(name, hitBoxBoundaries, entityTypeId);
  }

  private EntityTypeData entityTypeDataOfDataWatcher(WrappedDataWatcher dataWatcher) {
    Object entity = entityOfDataWatcher(dataWatcher);
    HitBoxBoundaries hitBoxBoundaries = ReflectiveEntityHitBoxAccess.boundariesOf(entity);
    String name = entityNameOf(entity);
    int entityTypeId = entityTypeIdOfDataWatcher(dataWatcher);
    return new EntityTypeData(name, hitBoxBoundaries, entityTypeId);
  }

  private int entityTypeIdOfDataWatcher(WrappedDataWatcher dataWatcher) {
    return dataWatcher.getEntity().getType().getTypeId();
  }

  private String entityNameOf(Object entity) {
    String entityName = entity.getClass().getSimpleName();
    if (entityName.startsWith("Entity")) {
      entityName = entityName.substring("Entity".length());
    }
    return entityName;
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
      ReflectiveAccess.ensureAccessible(entityField);
      return entityField.get(handle);
    } catch (Exception e) {
      throw new IntaveInternalException(e);
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

  private HitBoxBoundaries hitboxBoundariesByDeadEntityType(int deadEntityType) {
    switch (deadEntityType) {
      case 1:
        return HitBoxBoundaries.of(1.5F, 0.6F);
      case 2:
      case 61:
      case 62:
      case 65:
      case 72:
      case 73:
      case 75:
      case 76:
      case 90:
        return HitBoxBoundaries.of(0.25F, 0.25F);
      case 10:
        return HitBoxBoundaries.of(0.98F, 0.7F);
      case 50:
      case 70:
        return HitBoxBoundaries.of(0.98F, 0.98F);
      case 51:
        return HitBoxBoundaries.of(2.0F, 2.0F);
      case 63:
        return HitBoxBoundaries.of(1.0F, 1.0F);
      case 64:
      case 66:
        return HitBoxBoundaries.of(0.3125F, 0.3125F);
      case 77:
        return HitBoxBoundaries.of(0.5F, 0.5F);
      case 78:
        return HitBoxBoundaries.of(0.5F, 1.975F);
    }
    if (IntaveControl.DISABLE_LICENSE_CHECK) {
      IntaveLogger.logger().info("Zero BoundingBox 1");
    }
    return HitBoxBoundaries.zero();
  }
}