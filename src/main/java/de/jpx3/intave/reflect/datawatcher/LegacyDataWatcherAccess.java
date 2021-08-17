package de.jpx3.intave.reflect.datawatcher;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.Entity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class LegacyDataWatcherAccess implements DataWatcherAccess {
  @Override
  @PatchyAutoTranslation
  public void setDataWatcherFlag(Player player, int key, boolean flag) {
    Entity handle = ((CraftEntity) player).getHandle();
    DataWatcher dataWatcher = handle.getDataWatcher();
    byte b0 = dataWatcher.getByte(0);
    if (flag) {
      dataWatcher.watch(0, (byte) (b0 | 1 << key));
    } else {
      dataWatcher.watch(0, (byte) (b0 & ~(1 << key)));
    }
  }

  @Override
  @PatchyAutoTranslation
  public boolean getDataWatcherFlag(Player player, int key) {
    Entity handle = ((CraftEntity) player).getHandle();
    DataWatcher dataWatcher = handle.getDataWatcher();
    return (dataWatcher.getByte(0) & 1 << key) != 0;
  }
}
