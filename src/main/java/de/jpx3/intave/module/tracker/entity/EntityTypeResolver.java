package de.jpx3.intave.module.tracker.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.size.HitboxSizeAccess;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.entity.type.EntityTypeDataAccessor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.List;
public final class EntityTypeResolver {
  public EntityTypeData entityTypeDataOfSpawnEntity(Player observer, WrapperPlayServerSpawnEntity packet) {
    org.bukkit.entity.Entity entity = EntityTracker.serverEntityByIdentifier(observer, packet.getEntityId());
    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    }
    return packetTypeData(packet.getEntityType(), false);
  }

  public EntityTypeData entityTypeDataOfLivingEntity(Player observer, WrapperPlayServerSpawnLivingEntity packet) {
    org.bukkit.entity.Entity entity = EntityTracker.serverEntityByIdentifier(observer, packet.getEntityId());
    if (entity != null) {
      return entityTypeDataOfBukkitEntity(entity);
    }
    return packetTypeData(packet.getEntityType(), true);
  }

  public EntityTypeData entityTypeDataOfEntityMetadata(Player observer, int entityTypeId, List<EntityData<?>> metadata) {
    AgeCategory age = entityAgeByMetadata(metadata, entityTypeId);
    if (age == AgeCategory.UNKNOWN) {
      return null;
    }
    EntityTypeData entityTypeData = EntityTypeDataAccessor.resolveFromId(entityTypeId, false);
    if (age == AgeCategory.BABY) {
      return convertHitboxBoundariesToBaby(entityTypeData);
    }
    return entityTypeData;
  }

  private AgeCategory entityAgeByMetadata(List<EntityData<?>> metadata, int entityTypeId) {
    int correctIndex = hardcodedAgeMetaIndexFor(entityTypeId);
    Object object = metadataValue(metadata, correctIndex);
    if (object instanceof Boolean) {
      return (boolean) object ? AgeCategory.BABY : AgeCategory.ADULT;
    } else if (object instanceof Byte) {
      byte isChild = (byte) object;
      if (entityTypeId == 30) {
        return isChild == 1 ? AgeCategory.BABY : AgeCategory.ADULT;
      }
      return isChild < 0 ? AgeCategory.BABY : AgeCategory.ADULT;
    }
    return AgeCategory.UNKNOWN;
  }

  private Object metadataValue(List<EntityData<?>> metadata, int index) {
    if (metadata == null) {
      return null;
    }
    for (EntityData<?> entry : metadata) {
      if (entry.getIndex() == index) {
        return entry.getValue();
      }
    }
    return null;
  }

  public enum AgeCategory {
    BABY,
    ADULT,
    UNKNOWN
  }

  private int hardcodedAgeMetaIndexFor(int entityTypeId) {
    if (entityTypeId == 30) {
      return 14;
    }
    return 15;
  }

  private EntityTypeData convertHitboxBoundariesToBaby(EntityTypeData entityTypeData) {
    if (entityTypeData == null) {
      return null;
    }
    HitboxSize size = HitboxSize.of(entityTypeData.size().width() * 0.5f, entityTypeData.size().height() * 0.5f);
    return new EntityTypeData(entityTypeData.name(), size, entityTypeData.typeId(), entityTypeData.isLivingEntity(), 5);
  }

  public HitboxSize hitBoxBoundariesByBukkitEntity(org.bukkit.entity.Entity bukkitEntity) {
    return HitboxSizeAccess.dimensionsOfBukkit(bukkitEntity);
  }

  public String entityNameByBukkitEntity(org.bukkit.entity.Entity entity) {
    return entity.getType().name();
  }

  public EntityTypeData entityTypeDataOfBukkitEntity(org.bukkit.entity.Entity entity) {
    HitboxSize hitBoxSize = hitBoxBoundariesByBukkitEntity(entity);
    String name = entityNameByBukkitEntity(entity);

    if (entity.getType() == org.bukkit.entity.EntityType.ZOMBIE || entity.getType() == org.bukkit.entity.EntityType.PIG_ZOMBIE) {
      Zombie zombie = (Zombie) entity;
      if (zombie.isBaby()) {
        hitBoxSize = HitboxSize.playerDefault();
      }
    }
    boolean isEntityLiving = entity instanceof LivingEntity;
    return new EntityTypeData(name, hitBoxSize, entity.getType().getTypeId(), isEntityLiving, 6);
  }

  private EntityTypeData packetTypeData(EntityType packetType, boolean living) {
    if (packetType == null) {
      return new EntityTypeData("Unknown", HitboxSize.zero(), -1, living, 8);
    }
    ClientVersion version = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    int typeId = packetType.getId(version);
    EntityTypeData resolved = EntityTypeDataAccessor.resolveFromId(typeId, living);
    if (resolved != null) {
      return resolved;
    }
    String name = packetType.getName().getKey();
    return new EntityTypeData(name, HitboxSize.zero(), typeId, living, 8);
  }

}
