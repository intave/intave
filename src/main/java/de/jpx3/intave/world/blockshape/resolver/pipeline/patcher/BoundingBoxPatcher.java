package de.jpx3.intave.world.blockshape.resolver.pipeline.patcher;

import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public final class BoundingBoxPatcher {
  private final static Map<Material, BoundingBoxPatch> patches = new HashMap<>();

  public static void setup() {
    add(BlockTrapdoorPatch.class);
    add(BlockAnvilPatch.class);
    add(BlockLadderPatch.class);
    add(BlockLilyPadPatch.class);
    add(BlockFenceGatePatch.class);
    add(BlockFarmlandPatch.class);
    add(BlockThinPatch.class);
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

  public static List<WrappedAxisAlignedBB> patch(World world, Player player, Block block, List<WrappedAxisAlignedBB> bbs) {
    BoundingBoxPatch patch = patches.get(block.getType());
    return patch == null ? bbs : transpose(patch.patch(world, player, block, reposeIfRequired(patch, bbs, block.getX(), block.getY(), block.getZ())), block.getX(), block.getY(), block.getZ());
  }

  public static List<WrappedAxisAlignedBB> patch(World world, Player player, int blockX, int blockY, int blockZ, Material type, int blockState, List<WrappedAxisAlignedBB> boxes) {
    BoundingBoxPatch patch = patches.get(type);
    return patch == null ? boxes : transpose(patch.patch(world, player, blockX, blockY, blockZ, type, blockState, reposeIfRequired(patch, boxes, blockX, blockY, blockZ)), blockX, blockY, blockZ);
  }

  public static boolean requiresPatch(Material material) {
    return patches.containsKey(material);
  }

  private static List<WrappedAxisAlignedBB> transpose(List<WrappedAxisAlignedBB> boundingBoxes, int posX, int posY, int posZ) {
    if(boundingBoxes.isEmpty()) {
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

  private static List<WrappedAxisAlignedBB> reposeIfRequired(BoundingBoxPatch patch, List<WrappedAxisAlignedBB> boundingBoxes, int posX, int posY, int posZ) {
    if(!patch.requireRepose() || boundingBoxes.isEmpty()) {
      return boundingBoxes;
    }
    List<WrappedAxisAlignedBB> reposedList = new ArrayList<>(boundingBoxes);
    for (int i = 0; i < reposedList.size(); i++) {
      WrappedAxisAlignedBB boundingBox = reposedList.get(i);
      WrappedAxisAlignedBB newBox = boundingBox.offset(-posX, -posY, -posZ);
      newBox.setOriginBox();
      reposedList.set(i, newBox);
    }
    return reposedList;
  }
}
