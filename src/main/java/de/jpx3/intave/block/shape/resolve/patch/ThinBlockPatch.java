package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

final class ThinBlockPatch extends BlockShapePatch {
  private static final BoundingBox[] STATES_8 = new BoundingBox[] {
    BoundingBox.originFrom(0.0F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F), // full ew connection
    BoundingBox.originFrom(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 1.0F), // full ns connection
    BoundingBox.originFrom(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 0.5F), // north
    BoundingBox.originFrom(0.5F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F), // east
    BoundingBox.originFrom(0.4375F, 0.0F, 0.5F, 0.5625F, 1.0F, 1.0F), // south
    BoundingBox.originFrom(0.0F, 0.0F, 0.4375F, 0.5F, 1.0F, 0.5625F), // west
  };

  private static final BoundingBox[] STATES_9 = new BoundingBox[] {
    BoundingBox.originFromX16(7, 0, 7, 9, 16, 9), // base
    BoundingBox.originFromX16(7, 0, 0, 9, 16, 9), // north
    BoundingBox.originFromX16(7, 0, 7, 16, 16, 9), // east
    BoundingBox.originFromX16(7, 0, 7, 9, 16, 16), // south
    BoundingBox.originFromX16(0, 0, 7, 9, 16, 9), // west
  };

  @Override
  protected List<BoundingBox> collisionPatch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    User user = UserRepository.userOf(player);
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      if (!user.meta().protocol().combatUpdate()) {
        // update 1.9 to 1.8
        int count = 0;
        int[] indices = new int[bbs.size()];
        for (BoundingBox bb : bbs) {
          indices[count++] = indexOf9(bb);
        }
        boolean north = false;
        boolean south = false;
        boolean west = false;
        boolean east = false;
        for (int index : indices) {
          north |= index == 1;
          east |= index == 2;
          south |= index == 3;
          west |= index == 4;
        }
        List<BoundingBox> bbList = new ArrayList<>(count + 2);
        boolean anyConnection = west || east || north || south;
        if ((!west || !east) && anyConnection) {
          if (west) {
            bbList.add(STATES_8[5]);
          } else if (east) {
            bbList.add(STATES_8[3]);
          }
        } else {
          bbList.add(STATES_8[0]);
        }
        if ((!north || !south) && anyConnection) {
          if (north) {
            bbList.add(STATES_8[2]);
          } else if (south) {
            bbList.add(STATES_8[4]);
          }
        } else {
          bbList.add(STATES_8[1]);
        }
        return bbList;
      }
    } else {
      if (user.meta().protocol().combatUpdate()) {
        // update 1.8 to 1.9
        int count = 0;
        int[] indices = new int[bbs.size()];
        for (BoundingBox bb : bbs) {
          indices[count++] = indexOf8(bb);
        }
        boolean north = false;
        boolean east = false;
        boolean south = false;
        boolean west = false;
        for (int index : indices) {
          north |= index == 2 || index == 1;
          east |= index == 3 || index == 0;
          south |= index == 4 || index == 1;
          west |= index == 5 || index == 0;
        }
        // via version emulates 1.8 behaviour of panes, we can account for it
        if (!(north || east || south || west) && user.meta().protocol().waterUpdate()) {
          north = south = east = west = true;
        }
        List<BoundingBox> bbList = new ArrayList<>(count);
        bbList.add(STATES_9[0]);
        if (north) bbList.add(STATES_9[1]);
        if (east) bbList.add(STATES_9[2]);
        if (south) bbList.add(STATES_9[3]);
        if (west) bbList.add(STATES_9[4]);
        return bbList;
      }
    }
    return bbs;
  }

  private int indexOf8(BoundingBox axisAlignedBB) {
    for (int i = 0, length = STATES_8.length; i < length; i++) {
      BoundingBox boundingBox = STATES_8[i];
      if (boundingBox.equals(axisAlignedBB)) {
        return i;
      }
    }
    return -1;
  }

  private int indexOf9(BoundingBox axisAlignedBB) {
    for (int i = 0, length = STATES_9.length; i < length; i++) {
      BoundingBox boundingBox = STATES_9[i];
      if (boundingBox.equals(axisAlignedBB)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  protected boolean requireNormalization() {
    return true;
  }

  @Override
  public boolean appliesTo(Material material) {
    String name = material.name();
    return name.contains("GLASS_PANE") || name.contains("THIN_GLASS") || name.contains("IRON_BAR") || name.contains("IRON_FENCE");
  }
}