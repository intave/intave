package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import static org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN;

final class InternalTeleportApplier {
  void teleport(Player player, Location dest, double motionY, float yaw, float pitch, boolean rotationFlags) {
    User user = UserRepository.userOf(player);
    if (!user.hasPlayer()) {
      return;
    }
    float fallDistance = player.getFallDistance();
    Location teleportTarget = dest.clone();
    if (rotationFlags) {
      Location current = player.getLocation();
      teleportTarget.setYaw(current.getYaw());
      teleportTarget.setPitch(current.getPitch());
    } else {
      teleportTarget.setYaw(yaw);
      teleportTarget.setPitch(pitch);
    }
    player.teleport(teleportTarget, UNKNOWN);
    if (motionY > 0) {
      motionY = 0;
    }
    player.setFallDistance((float) (fallDistance + -motionY));
  }
}
