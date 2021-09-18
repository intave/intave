package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_15;

final class SoulSandPhysics implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    material = Collections.singletonList(Material.SOUL_SAND);
  }

  @Override
  public Vector entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    boolean useBlockCollision = useBlockCollision(user);
    return useBlockCollision ? new Vector(motionX * 0.4, motionY, motionZ * 0.4) : null;
  }

  private boolean useBlockCollision(User user) {
    ProtocolMetadata clientData = user.meta().protocol();
    return clientData.protocolVersion() < VER_1_15;
  }

  @Override
  public List<Material> materials() {
    return material;
  }
}