package de.jpx3.intave.entity.datawatcher;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.clazz.Lookup;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_9_R2.DataWatcher;
import net.minecraft.server.v1_9_R2.DataWatcherObject;
import net.minecraft.server.v1_9_R2.Entity;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

@PatchyAutoTranslation
public final class ModernDataWatcherAccessor implements DataWatcherAccessor {
  private final Field livingFlags;

  public ModernDataWatcherAccessor() {
    Field field = searchField();
    field.setAccessible(true);
    livingFlags = field;
  }

  private Field searchField() {
    Class<?> livingEntity = Lookup.serverClass("EntityLiving");
    Class<?> dataWatcherObj = Lookup.serverClass("DataWatcherObject");
    Field searchedField = null;
    for (Field field : livingEntity.getDeclaredFields()) {
      Class<?> type = field.getType();
      if (type == dataWatcherObj) {
        searchedField = field;
        break;
      }
    }
    if (searchedField == null) {
      throw new IntaveInternalException("Could not find DataWatcher field in " + livingEntity.getName());
    }
    return searchedField;
  }

  @Override
  @PatchyAutoTranslation
  public void setDataWatcherFlag(Player player, int key, boolean flag) {
    Entity handle = ((CraftEntity) player).getHandle();
    DataWatcher dataWatcher = handle.getDataWatcher();
    //noinspection unchecked
    DataWatcherObject<Byte> byteDataWatcherObject = (DataWatcherObject<Byte>) accessLivingFlags();
    int i = dataWatcher.get(byteDataWatcherObject);
    if (flag) {
      i |= key;
    } else {
      i &= ~key;
    }
    dataWatcher.set(byteDataWatcherObject, (byte) i);
  }

  @Override
  @PatchyAutoTranslation
  public boolean getDataWatcherFlag(Player player, int key) {
    Entity handle = ((CraftEntity) player).getHandle();
    DataWatcher dataWatcher = handle.getDataWatcher();
    //noinspection unchecked
    DataWatcherObject<Byte> byteDataWatcherObject = (DataWatcherObject<Byte>) accessLivingFlags();
    return (dataWatcher.get(byteDataWatcherObject) & 1 << key) != 0;
  }

  private Object accessLivingFlags() {
    try {
      return  livingFlags.get(null);
    } catch (IllegalAccessException e) {
      throw new IntaveInternalException(e);
    }
  }
}