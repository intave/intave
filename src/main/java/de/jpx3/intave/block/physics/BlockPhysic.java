package de.jpx3.intave.block.physics;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collection;

public interface BlockPhysic {
  void setupFor(MinecraftVersion serverVersion);

  // Called from #doBlockCollisions
  @Nullable
  default Motion entityCollidedWithBlock(
    User user,
    Location location, Location from,
    double motionX, double motionY, double motionZ
  ) {
    return null;
  }

  @Nullable
  default Motion entityCollidedWithBlock(User user, double motionX, double motionY, double motionZ) {
    return null;
  }

  @Nullable
  default Motion landed(User user, double motionX, double motionY, double motionZ) {
    return null;
  }

  default void fallenUpon(User user) {
  }

  default boolean supportedOnServerVersion() {
    return true;
  }

  Collection<Material> applicableMaterials();
}
