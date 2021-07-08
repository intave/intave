package de.jpx3.intave.world.blockphysic;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.user.UserMetaClientData.VER_1_12;

final class BlockBedPhysic implements BlockPhysic {
  private List<Material> materials;

  @Override
  public void setup(MinecraftVersion serverVersion) {
    if (serverVersion.isAtLeast(MinecraftVersions.VER1_12_0)) {
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
    if (userMetaClientData.protocolVersion() < VER_1_12) {
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