package de.jpx3.intave.block.collision;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;

import java.util.Arrays;

abstract class CollisionModifier {
  private Material[] materials;

  protected CollisionModifier() {}

  protected CollisionModifier(Material... materials) {
    this.materials = materials;
  }

  public abstract BlockShape modify(User user, BoundingBox userBox, int posX, int posY, int posZ, BlockShape shape, CollisionOrigin type);

  public BlockShape imaginaryBlockShape(Material type, User user, int posX, int posY, int posZ, int data) {
    return null;
  }

  public boolean matches(Material material) {
    return Arrays.asList(materials).contains(material);
  }
}
