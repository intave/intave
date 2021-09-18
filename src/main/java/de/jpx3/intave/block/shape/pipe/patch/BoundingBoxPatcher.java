package de.jpx3.intave.block.shape.pipe.patch;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.shade.BoundingBox;
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
  public static List<BoundingBox> patch(World world, Player player, Block block, List<BoundingBox> bbs) {
    BoundingBoxPatch patch = patches.get(BlockTypeAccess.typeAccess(block, player));
    if (patch == null) {
      return bbs;
    } else {
      List<BoundingBox> reposedBoxes = normalize(patch, bbs, block.getX(), block.getY(), block.getZ());
      List<BoundingBox> patchedBoxes = patch.patch(world, player, block, reposedBoxes);
      return contextualizeModifying(patchedBoxes, block.getX(), block.getY(), block.getZ());
    }
  }

  public static BlockShape patch(World world, Player player, int blockX, int blockY, int blockZ, Material type, int blockState, BlockShape shape) {
    BoundingBoxPatch patch = patches.get(type);
    if (patch == null) {
      return shape;
    } else {
      List<BoundingBox> normalized = normalize(patch, shape.boundingBoxes(), blockX, blockY, blockZ);
      return BlockShapes.ofBoxes(contextualizeModifying(
        patch.patch(world, player, blockX, blockY, blockZ, type, blockState, normalized),
        blockX, blockY, blockZ
      ));
    }
  }

  public static List<BoundingBox> patch(World world, Player player, int blockX, int blockY, int blockZ, Material type, int blockState, List<BoundingBox> boxes) {
    BoundingBoxPatch patch = patches.get(type);
    if (patch == null) {
      return boxes;
    } else {
      List<BoundingBox> normalized = normalize(patch, boxes, blockX, blockY, blockZ);
      return contextualizeModifying(
        patch.patch(world, player, blockX, blockY, blockZ, type, blockState, normalized),
        blockX, blockY, blockZ
      );
    }
  }

  private static List<BoundingBox> contextualizeModifying(List<BoundingBox> boundingBoxes, int posX, int posY, int posZ) {
    if (boundingBoxes.isEmpty()) {
      return boundingBoxes;
    }
//    boundingBoxes = new ArrayList<>(boundingBoxes);
    for (int i = 0; i < boundingBoxes.size(); i++) {
      BoundingBox boundingBox = boundingBoxes.get(i);
      if (boundingBox.isOriginBox()) {
        boundingBoxes.set(i, boundingBox.offset(posX, posY, posZ));
      }
    }
    return boundingBoxes;
  }

  private static List<BoundingBox> normalize(BoundingBoxPatch patch, List<BoundingBox> boundingBoxes, int posX, int posY, int posZ) {
    if (!patch.requireNormalization() || boundingBoxes.isEmpty()) {
      return boundingBoxes;
    }
    List<BoundingBox> reposedList = new ArrayList<>(boundingBoxes);
    for (int i = 0; i < reposedList.size(); i++) {
      BoundingBox boundingBox = reposedList.get(i);
      BoundingBox newBox = boundingBox.offset(-posX, -posY, -posZ);
      newBox.makeOriginBox();
      reposedList.set(i, newBox);
    }
    return reposedList;
  }
}