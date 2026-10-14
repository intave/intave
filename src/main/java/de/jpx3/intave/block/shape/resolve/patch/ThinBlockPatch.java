/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

final class ThinBlockPatch extends BlockShapePatch {
  private static final int NORTH = 1;
  private static final int EAST = 1 << 1;
  private static final int SOUTH = 1 << 2;
  private static final int WEST = 1 << 3;
  private static final int ALL_DIRECTIONS = NORTH | EAST | SOUTH | WEST;

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
  protected List<BoundingBox> collisionPatch(World world, User user, int posX, int posY, int posZ, Material type, int blockState, List<BoundingBox> bbs) {
    if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      if (!user.meta().protocol().combatUpdate()) {
        // update 1.9 to 1.8
        return modernToLegacy(bbs);
      }
    } else {
      if (user.meta().protocol().combatUpdate()) {
        // update 1.8 to 1.9
        boolean viaVersionConnections = user.meta().protocol().aquaticUpdate()
          && ViaVersionAdapter.serverSideBlockConnections();
        if (viaVersionConnections) {
          int connections = viaVersionConnectionMask(
            neighborType(user, world, posX, posY, posZ - 1),
            neighborType(user, world, posX + 1, posY, posZ),
            neighborType(user, world, posX, posY, posZ + 1),
            neighborType(user, world, posX - 1, posY, posZ)
          );
          return modernBoxes(connections);
        }

        int connections = connectionMask(bbs);
        boolean ambiguousCross = connections == ALL_DIRECTIONS;
        boolean isolated = ambiguousCross && !hasLegacyConnection(user, world, posX, posY, posZ);
        return legacyToModern(bbs, isolated, false);
      }
    }
    return bbs;
  }

  static List<BoundingBox> modernToLegacy(List<BoundingBox> boxes) {
    return legacyBoxes(connectionMask(boxes));
  }

  static List<BoundingBox> legacyToModern(
    List<BoundingBox> boxes,
    boolean isolatedLegacyPane,
    boolean viaVersionCross
  ) {
    int connections = connectionMask(boxes);
    if (connections == ALL_DIRECTIONS && isolatedLegacyPane && !viaVersionCross) {
      connections = 0;
    }
    return modernBoxes(connections);
  }

  static int connectionMask(List<BoundingBox> boxes) {
    // Sample the middle of each arm. This remains stable when VoxelShape decomposes the source
    // boxes into a different set of elementary boxes.
    int connections = 0;
    if (contains(boxes, 0.5, 0.5, 0.25)) connections |= NORTH;
    if (contains(boxes, 0.75, 0.5, 0.5)) connections |= EAST;
    if (contains(boxes, 0.5, 0.5, 0.75)) connections |= SOUTH;
    if (contains(boxes, 0.25, 0.5, 0.5)) connections |= WEST;
    return connections;
  }

  static int viaVersionConnectionMask(
    Material north,
    Material east,
    Material south,
    Material west
  ) {
    int connections = 0;
    if (isViaVersionConnectionMaterial(north)) connections |= NORTH;
    if (isViaVersionConnectionMaterial(east)) connections |= EAST;
    if (isViaVersionConnectionMaterial(south)) connections |= SOUTH;
    if (isViaVersionConnectionMaterial(west)) connections |= WEST;

    // ViaVersion's GlassConnectionHandler sends mask 15 for an otherwise isolated pane/bar
    // on 1.8 and older servers.
    return connections == 0 ? ALL_DIRECTIONS : connections;
  }

  private static boolean contains(List<BoundingBox> boxes, double x, double y, double z) {
    for (BoundingBox box : boxes) {
      if (box.contains(x, y, z)) {
        return true;
      }
    }
    return false;
  }

  private static List<BoundingBox> legacyBoxes(int connections) {
    List<BoundingBox> boxes = new ArrayList<>(2);
    boolean anyConnection = connections != 0;
    boolean east = (connections & EAST) != 0;
    boolean west = (connections & WEST) != 0;
    if (east && west || !anyConnection) {
      boxes.add(STATES_8[0]);
    } else if (east) {
      boxes.add(STATES_8[3]);
    } else if (west) {
      boxes.add(STATES_8[5]);
    }

    boolean north = (connections & NORTH) != 0;
    boolean south = (connections & SOUTH) != 0;
    if (north && south || !anyConnection) {
      boxes.add(STATES_8[1]);
    } else if (north) {
      boxes.add(STATES_8[2]);
    } else if (south) {
      boxes.add(STATES_8[4]);
    }
    return boxes;
  }

  private static List<BoundingBox> modernBoxes(int connections) {
    List<BoundingBox> boxes = new ArrayList<>(5);
    boxes.add(STATES_9[0]);
    if ((connections & NORTH) != 0) boxes.add(STATES_9[1]);
    if ((connections & EAST) != 0) boxes.add(STATES_9[2]);
    if ((connections & SOUTH) != 0) boxes.add(STATES_9[3]);
    if ((connections & WEST) != 0) boxes.add(STATES_9[4]);
    return boxes;
  }

  private static boolean hasLegacyConnection(User user, World world, int posX, int posY, int posZ) {
    return isLegacyConnectionMaterial(neighborType(user, world, posX, posY, posZ - 1))
      || isLegacyConnectionMaterial(neighborType(user, world, posX + 1, posY, posZ))
      || isLegacyConnectionMaterial(neighborType(user, world, posX, posY, posZ + 1))
      || isLegacyConnectionMaterial(neighborType(user, world, posX - 1, posY, posZ));
  }

  private static Material neighborType(User user, World world, int posX, int posY, int posZ) {
    BlockState cachedState = user.blockCache().peekStateAt(posX, posY, posZ);
    return cachedState == null
      ? VolatileBlockAccess.blockAccess(world, posX, posY, posZ).getType()
      : cachedState.type();
  }

  static boolean isLegacyConnectionMaterial(Material material) {
    String name = material.name();
    if (name.equals("AIR") || name.endsWith("_AIR")) {
      return false;
    }
    if (name.equals("GLASS")
      || name.equals("LEGACY_GLASS")
      || name.equals("STAINED_GLASS")
      || name.equals("LEGACY_STAINED_GLASS")
      || name.contains("GLASS_PANE")
      || name.contains("THIN_GLASS")
      || name.contains("IRON_BAR")
      || name.contains("IRON_FENCE")) {
      return true;
    }
    return material.isOccluding();
  }

  static boolean isViaVersionConnectionMaterial(Material material) {
    String name = material.name();
    if (name.contains("LEAVES")
      || name.equals("BARRIER")
      || name.equals("PUMPKIN")
      || name.equals("CARVED_PUMPKIN")
      || name.equals("JACK_O_LANTERN")
      || name.equals("MELON")
      || name.equals("MELON_BLOCK")
      || name.endsWith("SHULKER_BOX")) {
      return false;
    }
    return isLegacyConnectionMaterial(material) || name.endsWith("_WALL");
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
