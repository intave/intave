package de.jpx3.intave.world.blockshape.resolver.pipeline.patcher;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BlockThinPatch extends BoundingBoxPatch {
  protected static final WrappedAxisAlignedBB[] STATES_8 = new WrappedAxisAlignedBB[] {
    new WrappedAxisAlignedBB(0.0F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F), // full ew connection
    new WrappedAxisAlignedBB(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 1.0F), // full ns connection
    new WrappedAxisAlignedBB(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 0.5F), // north
    new WrappedAxisAlignedBB(0.5F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F), // east
    new WrappedAxisAlignedBB(0.4375F, 0.0F, 0.5F, 0.5625F, 1.0F, 1.0F), // south
    new WrappedAxisAlignedBB(0.0F, 0.0F, 0.4375F, 0.5F, 1.0F, 0.5625F), // west
  };

  protected static final WrappedAxisAlignedBB[] STATES_9 = new WrappedAxisAlignedBB[] {
    new WrappedAxisAlignedBB(0.4375D, 0.0D, 0.4375D, 0.5625D, 1.0D, 0.5625D), // base
    new WrappedAxisAlignedBB(0.4375D, 0.0D, 0.0D, 0.5625D, 1.0D, 0.5625D), // north
    new WrappedAxisAlignedBB(0.4375D, 0.0D, 0.4375D, 1.0D, 1.0D, 0.5625D), // east
    new WrappedAxisAlignedBB(0.4375D, 0.0D, 0.4375D, 0.5625D, 1.0D, 1.0D), // south
    new WrappedAxisAlignedBB(0.0D, 0.0D, 0.4375D, 0.5625D, 1.0D, 0.5625D), // west
  };

  public BlockThinPatch() {
    Arrays.stream(STATES_8).forEach(WrappedAxisAlignedBB::setOriginBox);
    Arrays.stream(STATES_9).forEach(WrappedAxisAlignedBB::setOriginBox);
  }

  @Override
  protected List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    return patch(world, player, block.getX(), block.getY(), block.getZ(), block.getType(), BlockDataAccess.dataIndexOf(block), bbs);
  }

  @Override
  protected List<WrappedAxisAlignedBB> patch(World world, Player player, int posX, int posY, int posZ, Material type, int blockState, List<WrappedAxisAlignedBB> bbs) {
    User user = UserRepository.userOf(player);
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      if (!user.meta().clientData().combatUpdate()) {
        // update 1.9 to 1.8
        int count = 0;
        int[] indices = new int[bbs.size()];
        for (WrappedAxisAlignedBB bb : bbs) {
          indices[count++] = indexOf9(bb);
        }

        boolean north = false;
        boolean south = false;
        boolean west = false;
        boolean east = false;

        for (int index : indices) {
          north |= index == 1;
          east  |= index == 2;
          south |= index == 3;
          west  |= index == 4;
        }

        List<WrappedAxisAlignedBB> bbList = new ArrayList<>(count + 2);
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
      if (user.meta().clientData().combatUpdate()) {
        // update 1.8 to 1.9
        int count = 0;
        int[] indices = new int[bbs.size()];
        for (WrappedAxisAlignedBB bb : bbs) {
          indices[count++] = indexOf8(bb);
        }
        
        boolean north = false;
        boolean east = false;
        boolean south = false;
        boolean west = false;

        for (int index : indices) {
          north |= index == 2 || index == 1;
          east  |= index == 3 || index == 0;
          south |= index == 4 || index == 1;
          west  |= index == 5 || index == 0;
        }

        // via version emulates 1.8 behaviour of panes, we can account for it
        if(!(north || east || south || west) && user.meta().clientData().waterUpdate()) {
          north = south = east = west = true;
        }

        List<WrappedAxisAlignedBB> bbList = new ArrayList<>(count);
        bbList.add(STATES_9[0]);
        if (north) bbList.add(STATES_9[1]);
        if (east)  bbList.add(STATES_9[2]);
        if (south) bbList.add(STATES_9[3]);
        if (west)  bbList.add(STATES_9[4]);
        return bbList;
      }
    }

    return super.patch(world, player, posX, posY, posZ, type, blockState, bbs);
  }

  private int indexOf8(WrappedAxisAlignedBB axisAlignedBB) {
    for (int i = 0, length = STATES_8.length; i < length; i++) {
      WrappedAxisAlignedBB wrappedAxisAlignedBB = STATES_8[i];
      if (wrappedAxisAlignedBB.equals(axisAlignedBB)) {
        return i;
      }
    }
    return -1;
  }

  private int indexOf9(WrappedAxisAlignedBB axisAlignedBB) {
    for (int i = 0, length = STATES_9.length; i < length; i++) {
      WrappedAxisAlignedBB wrappedAxisAlignedBB = STATES_9[i];
      if (wrappedAxisAlignedBB.equals(axisAlignedBB)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  protected boolean requireRepose() {
    return true;
  }

  @Override
  public boolean appliesTo(Material material) {
    String name = material.name();
    return name.contains("GLASS_PANE") || name.contains("THIN_GLASS") || name.contains("IRON_BAR") || name.contains("IRON_FENCE");
  }
}