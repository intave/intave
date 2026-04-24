package de.jpx3.intave.util;

import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import org.bukkit.util.Vector;

public final class PacketEventsConversions {
  private PacketEventsConversions() {
  }

  public static BlockPosition toBlockPosition(Vector3i vector) {
    return vector == null ? null : new BlockPosition(vector.x, vector.y, vector.z);
  }

  public static Vector3i toVector3i(BlockPosition position) {
    return position == null ? null : new Vector3i(position.getX(), position.getY(), position.getZ());
  }

  public static Vector toBukkitVector(Vector3f vector) {
    return vector == null ? null : new Vector(vector.x, vector.y, vector.z);
  }

  public static Direction toDirection(BlockFace blockFace) {
    if (blockFace == null || blockFace == BlockFace.OTHER) {
      return null;
    }
    return Direction.getFront(blockFace.getFaceValue());
  }
}
