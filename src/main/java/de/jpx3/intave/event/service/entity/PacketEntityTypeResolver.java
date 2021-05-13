package de.jpx3.intave.event.service.entity;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
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

public final class PacketEntityTypeResolver {
  private static final boolean DATA_WATCHER_ACCESS = !MinecraftVersions.VER1_15_0.atOrAbove();
  private String dataWatcherEntityFieldName;

  public PacketEntityTypeResolver(IntavePlugin plugin) {
    if (DATA_WATCHER_ACCESS) {
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
          IntaveLogger.logger().globalPrintLn("[Intave] Conflicting method name: \"" + dataWatcherEntityFieldName + "\" expected but found \"" + fieldName + "\" for entity-from-dw access");
        }
        dataWatcherEntityFieldName = fieldName;
        break;
      }
    }
  }

  public String entityNameByBukkitEntity(Entity entity) {
    return entityNameOf(ReflectiveHandleAccess.handleOf(entity));
  }

  public EntityTypeData spawnInformationOf(PacketContainer packet) {
    return DATA_WATCHER_ACCESS ? dataWatcherAccess(packet) : typeAccess(packet);
  }

  //
  // Type Access
  //

  private EntityTypeData typeAccess(PacketContainer packet) {
    Integer type = packet.getIntegers().read(1);
    return DualEntityTypeAccess.resolveFromId(type);
  }

  //
  // DataWatcher Access
  //

  private EntityTypeData dataWatcherAccess(PacketContainer packet) {
    Object entity = entityOfDataWatcher(packet.getDataWatcherModifier().read(0));
    int entityTypeID = packet.getIntegers().read(1);
    HitBoxBoundaries hitBoxBoundaries;
    String name;
    if (entity != null) {
      name = entityNameOf(entity);
      hitBoxBoundaries = ReflectiveEntityHitBoxAccess.boundariesOf(entity);
    } else {
      name = DualEntityTypeAccess.nameFromID(entityTypeID);
      hitBoxBoundaries = DualEntityTypeAccess.boundariesFromId(entityTypeID);
    }
    return new EntityTypeData(name, hitBoxBoundaries);
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
}