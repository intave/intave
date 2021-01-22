package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class Collision {
  public static List<WrappedAxisAlignedBB> resolve(Player player, WrappedAxisAlignedBB playerBoundingBox) {
    int i = WrappedMathHelper.floor(playerBoundingBox.minX);
    int j = WrappedMathHelper.floor(playerBoundingBox.maxX + 1.0D);
    int k = WrappedMathHelper.floor(playerBoundingBox.minY);
    int l = WrappedMathHelper.floor(playerBoundingBox.maxY + 1.0D);
    int i1 = WrappedMathHelper.floor(playerBoundingBox.minZ);
    int j1 = WrappedMathHelper.floor(playerBoundingBox.maxZ + 1.0D);

    int ystart = Math.max(k - 1, 0);

    List<WrappedAxisAlignedBB> resolvedBoundingBoxes = null;
    BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
    World world = player.getWorld();

    // this looks 1000x slower than it actually is
    for (int chunkx = i >> 4; chunkx <= j - 1 >> 4; ++chunkx) {
      int chunkXPos = chunkx << 4;
      for (int chunkz = i1 >> 4; chunkz <= j1 - 1 >> 4; ++chunkz) {
        if (world.isChunkLoaded(chunkx, chunkz)) {
          Chunk chunk = world.getChunkAt(chunkx, chunkz);
          int chunkZPos = chunkz << 4;
          int xstart = Math.max(i, chunkXPos);
          int zstart = Math.max(i1, chunkZPos);
          int xend = Math.min(j, chunkXPos + 16);
          int zend = Math.min(j1, chunkZPos + 16);
          for (int x = xstart; x < xend; ++x) {
            for (int z = zstart; z < zend; ++z) {
              for (int y = ystart; y < l; ++y) {
                List<WrappedAxisAlignedBB> resolve = boundingBoxAccess.resolve(chunk, x, y, z);
                if (resolve != null && !resolve.isEmpty()) {
                  if (resolvedBoundingBoxes == null) {
                    resolvedBoundingBoxes = new ArrayList<>(resolve);
                  } else {
                    resolvedBoundingBoxes.addAll(resolve);
                  }
                }
              }
            }
          }
        }
      }
    }
    if (resolvedBoundingBoxes == null) {
      resolvedBoundingBoxes = Collections.emptyList();
    } else {
      // filter invalid
      final Iterator<WrappedAxisAlignedBB> each = resolvedBoundingBoxes.iterator();
      while (each.hasNext()) {
        if (!each.next().intersectsWith(playerBoundingBox)) {
          each.remove();
        }
      }
    }
    return resolvedBoundingBoxes;
  }

  public static boolean playerInImaginaryBlock(User user, World world, int posX, int posY, int posZ, int type, int data) {
    List<WrappedAxisAlignedBB> boundingboxes =
      user.boundingBoxAccess().constructBlock(world, posX, posY, posZ, type, data);
    if(boundingboxes == null || boundingboxes.isEmpty()) {
      return false;
    }
    WrappedAxisAlignedBB playerBox = user.meta().movementData().boundingBox();
    for (WrappedAxisAlignedBB boundingbox : boundingboxes) {
      if (playerBox.intersectsWith(boundingbox)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasNoCollisions(Player player, WrappedAxisAlignedBB playerBoundingBox) {
    return resolve(player, playerBoundingBox).isEmpty();
  }
}