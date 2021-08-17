package de.jpx3.intave.world.blockshape.boxresolver.patcher;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public final class BoundingBoxPatcher {
  private final static Map<Material, BoundingBoxPatch> patches = new HashMap<>();

  public static void setup() {
    add(TrapdoorBlockPatch.class);
    add(AnvilBlockPatch.class);
    add(LadderBlockPatch.class);
    add(LilyPadBlockPatch.class);
    add(FenceGateBlockPatch.class);
    add(FarmlandBlockPatch.class);
    add(BambooBlockPatch.class);
    add(ThinBlockPatch.class);
//    add(BlockDoorPatch.class);
  }

  private static void add(Class<? extends BoundingBoxPatch> patchClass) {
    try {
      add(patchClass.newInstance());
    } catch (Exception | Error exception) {
      IntaveLogger.logger().info("Failed to load bounding box patch (class " + patchClass + ")");
      exception.printStackTrace();
    }
  }

  private static void add(BoundingBoxPatch patch) {
    Arrays.stream(Material.values()).filter(patch::appliesTo).forEach(type -> patches.put(type, patch));
  }

  @Deprecated
  public static List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    BoundingBoxPatch patch = patches.get(BlockTypeAccess.typeAccess(block, player));
    if (patch == null) {
      return bbs;
    } else {
      List<WrappedAxisAlignedBB> reposedBoxes = repose(patch, bbs, block.getX(), block.getY(), block.getZ());
      List<WrappedAxisAlignedBB> patchedBoxes = patch.patch(world, player, block, reposedBoxes);
      return transposeIfRequired(patchedBoxes, block.getX(), block.getY(), block.getZ());
    }
  }

  public static List<WrappedAxisAlignedBB> patch(World world, Player player, int blockX, int blockY, int blockZ, Material type, int blockState, List<WrappedAxisAlignedBB> boxes) {
    BoundingBoxPatch patch = patches.get(type);
    return patch == null ? boxes : transposeIfRequired(patch.patch(world, player, blockX, blockY, blockZ, type, blockState, repose(patch, boxes, blockX, blockY, blockZ)), blockX, blockY, blockZ);
  }

  private static List<WrappedAxisAlignedBB> transposeIfRequired(List<WrappedAxisAlignedBB> boundingBoxes, int posX, int posY, int posZ) {
    if (boundingBoxes.isEmpty()) {
      return boundingBoxes;
    }
    for (int i = 0; i < boundingBoxes.size(); i++) {
      WrappedAxisAlignedBB boundingBox = boundingBoxes.get(i);
      if (boundingBox.isOriginBox()) {
        boundingBoxes.set(i, boundingBox.offset(posX, posY, posZ));
      }
    }
    return boundingBoxes;
  }

  private static List<WrappedAxisAlignedBB> repose(BoundingBoxPatch patch, List<WrappedAxisAlignedBB> boundingBoxes, int posX, int posY, int posZ) {
    if (!patch.requireRepose() || boundingBoxes.isEmpty()) {
      return boundingBoxes;
    }
    List<WrappedAxisAlignedBB> reposedList = new ArrayList<>(boundingBoxes);
    for (int i = 0; i < reposedList.size(); i++) {
      WrappedAxisAlignedBB boundingBox = reposedList.get(i);
      WrappedAxisAlignedBB newBox = boundingBox.offset(-posX, -posY, -posZ);
      newBox.makeOriginBox();
      reposedList.set(i, newBox);
    }
    return reposedList;
  }
}