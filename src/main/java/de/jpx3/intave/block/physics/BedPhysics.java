package de.jpx3.intave.block.physics;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_12;

final class BedPhysics implements BlockPhysic {
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
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata protocolMetadata = meta.protocol();
    if (protocolMetadata.protocolVersion() < VER_1_12) {
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