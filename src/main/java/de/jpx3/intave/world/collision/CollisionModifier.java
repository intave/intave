package de.jpx3.intave.world.collision;

import de.jpx3.intave.user.User;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

public abstract class CollisionModifier {
  private Material[] materials;

  protected CollisionModifier() {
  }

  protected CollisionModifier(Material... materials) {
    this.materials = materials;
  }

  public abstract List<WrappedAxisAlignedBB> modify(User user, WrappedAxisAlignedBB userBox, int posX, int posY, int posZ, List<WrappedAxisAlignedBB> boxes);

  public boolean matches(Material material) {
    return Arrays.asList(materials).contains(material);
  }
}
