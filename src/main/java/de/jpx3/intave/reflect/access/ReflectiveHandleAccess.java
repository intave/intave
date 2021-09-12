package de.jpx3.intave.reflect.access;

import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;

@PatchyAutoTranslation
@IdoNotBelongHere
@Deprecated
public final class ReflectiveHandleAccess {
  @PatchyAutoTranslation
  public static Object handleOf(Entity entity) {
    return ((CraftEntity) entity).getHandle();
  }

  @PatchyAutoTranslation
  public static Object playerConnectionOf(Entity entity) {
    return ((EntityPlayer) handleOf(entity)).playerConnection;
  }

  @PatchyAutoTranslation
  public static Object handleOf(World world) {
    return ((CraftWorld) world).getHandle();
  }
}