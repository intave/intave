package de.jpx3.intave.block.physics;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_15;

final class SoulSandPhysics implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setupFor(MinecraftVersion serverVersion) {
    material = Collections.singletonList(Material.SOUL_SAND);
  }

  @Override
  public Motion entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    boolean useBlockCollision = useBlockCollision(user);
    return useBlockCollision ? new Motion(motionX * 0.4, motionY, motionZ * 0.4) : null;
  }

  private boolean useBlockCollision(User user) {
    ProtocolMetadata clientData = user.meta().protocol();
    return clientData.protocolVersion() < VER_1_15;
  }

  @Override
  public List<Material> applicableMaterials() {
    return material;
  }
}
