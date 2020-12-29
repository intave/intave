package de.jpx3.intave.detect.checks.movement.physics.collision;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_COLOR_UPDATE;

final class PhysicsCollisionBed extends PhysicsCollision {
  private List<Material> materials;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    if (serverVersion.isAtLeast(ProtocolLibAdapter.COLOR_UPDATE)) {
      materials = resolveBedMaterials();
    } else {
      materials = Collections.singletonList(Material.getMaterial("BED_BLOCK"));
    }
  }

  @Override
  public Vector landed(User user, double motionX, double motionY, double motionZ) {
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData userMetaClientData = meta.clientData();
    if (userMetaClientData.protocolVersion() < PROTOCOL_VERSION_COLOR_UPDATE) {
      return null;
    }
    if (motionY < 0.0) {
      motionY = -motionY * 0.66f;
    }
    return movementData.sneaking ? null : new Vector(motionX, motionY, motionZ);
  }

  private List<Material> resolveBedMaterials() {
    return Arrays.stream(Material.values())
      .filter(this::bedMaterial)
      .collect(Collectors.toList());
  }

  private boolean bedMaterial(Material material) {
    return material.name().toLowerCase().contains("bed");
  }

  @Override
  public List<Material> materials() {
    return materials;
  }
}