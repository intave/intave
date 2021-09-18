package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_15;

final class WebPhysics implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    material = Collections.singletonList(BlockTypeAccess.WEB);
  }

  @Override
  public Vector entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    ProtocolMetadata clientData = user.meta().protocol();
    MovementMetadata movementData = user.meta().movement();
    movementData.inWeb = true;
    movementData.artificialFallDistance = 0;
    if (clientData.protocolVersion() >= VER_1_15) {
      return new Vector(motionX * 0.25, motionY * 0.05f, motionZ * 0.25);
    }
    return null;
  }

  @Override
  public List<Material> materials() {
    return material;
  }
}