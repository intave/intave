package de.jpx3.intave.player.fake.equipment;

import org.bukkit.Material;

import java.util.List;

public final class Equipment {
  private final List<ArmorPiece> armorPieces;
  private final Material heldItem;

  public Equipment(List<ArmorPiece> armorPieces, Material heldItem) {
    this.armorPieces = armorPieces;
    this.heldItem = heldItem;
  }

  public List<ArmorPiece> armorPieces() {
    return this.armorPieces;
  }

  public Material heldItem() {
    return heldItem;
  }
}