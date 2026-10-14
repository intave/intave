package de.jpx3.intave.block.shape.resolve.patch;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BlockShapePatcher {
  private static final Map<Material, BlockShapePatch> patches = new HashMap<>();

  public static void setup() {
    add(TrapdoorBlockPatch.class);
    add(DoorBlockPatch.class);
    add(AnvilBlockPatch.class);
    add(LadderBlockPatch.class);
    add(LilyPadBlockPatch.class);
    add(FenceGateBlockPatch.class);
    add(FarmlandBlockPatch.class);
    add(BambooBlockPatch.class);
    add(ThinBlockPatch.class);
    add(EnderPortalFramePatch.class);
    add(CauldronBlockPatch.class);
    add(PointedDripstoneBlockPatch.class);
    add(CarpetPatch.class);
    add(HopperPatch.class);
    add(CobbleStoneWallPatch.class);
    add(BambooPotPatch.class);
    add(SmallFlowerPatch.class);
    add(MangrovePropagulePatch.class);
    add(TorchPatch.class);
    add(MudPatch.class);
//    add(StairPatch.class);
  }

  private static void add(Class<? extends BlockShapePatch> patchClass) {
    try {
      add(patchClass.newInstance());
    } catch (Exception | Error exception) {
      IntaveLogger.logger().info("Failed to load bounding box patch (" + patchClass + ")");
      exception.printStackTrace();
    }
  }

  private static void add(BlockShapePatch patch) {
    List<Material> materials = Arrays.stream(Material.values()).filter(patch::appliesTo).collect(Collectors.toList());
    materials.forEach(type -> patches.put(type, patch));
  }

  public static BlockShape patchCollision(World world, User user, int blockX, int blockY, int blockZ, Material type, int blockState, BlockShape shape) {
    BlockShapePatch blockPatch = patches.get(type);
    if (blockPatch == null) {
      return shape;
    } else {
      BlockShape normalized = normalize(blockPatch, shape, blockX, blockY, blockZ);
      BlockShape patched = blockPatch.collisionPatch(world, user, blockX, blockY, blockZ, type, blockState, normalized);
      return contextualize(patched, blockX, blockY, blockZ);
    }
  }

  public static BlockShape patchOutline(World world, User user, int blockX, int blockY, int blockZ, Material type, int blockState, BlockShape shape) {
    BlockShapePatch blockPatch = patches.get(type);
    if (blockPatch == null) {
      return shape;
    } else {
      BlockShape normalized = normalize(blockPatch, shape, blockX, blockY, blockZ);
      BlockShape patched = blockPatch.outlinePatch(world, user, blockX, blockY, blockZ, type, blockState, normalized);
      return contextualize(patched, blockX, blockY, blockZ);
    }
  }

  private static BlockShape normalize(BlockShapePatch patch, BlockShape input, int posX, int posY, int posZ) {
    return patch.requireNormalization() ? input.normalized(posX, posY, posZ) : input;
  }

  private static BlockShape contextualize(BlockShape shape, int posX, int posY, int posZ) {
    return shape.contextualized(posX, posY, posZ);
  }
}
