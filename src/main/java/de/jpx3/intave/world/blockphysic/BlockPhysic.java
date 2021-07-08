package de.jpx3.intave.world.blockphysic;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.tools.annotate.Nullable;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.List;

public interface BlockPhysic {
  void setup(MinecraftVersion serverVersion);

  // Called from #doBlockCollisions
  @Nullable
  default Vector entityCollidedWithBlock(
    User user,
    Location location, Location from,
    double motionX, double motionY, double motionZ
  ) {
    return null;
  }

  @Nullable
  default Vector entityCollidedWithBlock(User user, double motionX, double motionY, double motionZ) {
    return null;
  }

  @Nullable
  default Vector landed(User user, double motionX, double motionY, double motionZ) {
    return null;
  }

  default float speedFactor(User user) {
    return 1.0f;
  }

  // Since 1.15
  default float jumpFactor(User user) {
    return 1.0f;
  }

  default void fallenUpon(User user) {
  }

  default boolean supportedOnServerVersion() {
    return true;
  }

  List<Material> materials();
}