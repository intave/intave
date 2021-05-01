package de.jpx3.intave.world.blockphysics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_BEE_UPDATE;

final class BlockPhysicSoulSand implements BlockPhysic {
  private List<Material> material;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    material = Collections.singletonList(Material.SOUL_SAND);
  }

  @Override
  public Vector entityCollidedWithBlock(User user, Location location, Location from, double motionX, double motionY, double motionZ) {
    boolean requiresSpeedFactor = requiresSpeedFactor(user);
    return !requiresSpeedFactor ? new Vector(motionX * 0.4, motionY, motionZ * 0.4) : null;
  }

  @Override
  public float speedFactor(User user) {
    boolean requiresSpeedFactor = requiresSpeedFactor(user);
    return requiresSpeedFactor ? 0.4f : 1.0f;
  }

  private boolean requiresSpeedFactor(User user) {
    UserMetaClientData clientData = user.meta().clientData();
    return clientData.protocolVersion() >= PROTOCOL_VERSION_BEE_UPDATE;
  }

  @Override
  public List<Material> materials() {
    return material;
  }
}