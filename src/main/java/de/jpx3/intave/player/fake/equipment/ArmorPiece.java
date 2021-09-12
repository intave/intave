package de.jpx3.intave.player.fake.equipment;

import org.bukkit.Material;

public final class ArmorPiece {
  private final ArmorSlot type;
  private final Material material;

  public ArmorPiece(ArmorSlot type, Material material) {
    this.type = type;
    this.material = material;
  }

  public ArmorSlot type() {
    return type;
  }

  public Material material() {
    return material;
  }
}
