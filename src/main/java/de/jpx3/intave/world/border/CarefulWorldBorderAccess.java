package de.jpx3.intave.world.border;

import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.EnumWorldBorderState;
import net.minecraft.server.v1_8_R3.WorldBorder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

@PatchyAutoTranslation
public final class CarefulWorldBorderAccess implements WorldBorderAccess {
  @Override
  @PatchyAutoTranslation
  public double sizeOf(World world) {
    WorldBorder worldBorder = ((CraftWorld) world).getHandle().getWorldBorder();
    long remainingMillis = worldBorder.i();
    if (worldBorder.getState() != EnumWorldBorderState.STATIONARY) {
      if (remainingMillis <= 500) {
        return 0;
      }
    }
    return worldBorder.getSize();
  }

  @Override
  public Location centerOf(World world) {
    return world.getWorldBorder().getCenter();
  }
}
