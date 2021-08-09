package de.jpx3.intave.tools.client;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.user.*;
import org.bukkit.entity.Player;

public final class PoseHelper {
  private final static boolean ELYTRA_ENABLED = ProtocolLibraryAdapter.serverVersion().isAtLeast(MinecraftVersions.VER1_9_0);

  public static boolean flyingWithElytra(Player player) {
    return ELYTRA_ENABLED && canUseElytra(player) && player.isGliding();
  }

  private static boolean canUseElytra(Player player) {
    if (!UserRepository.hasUser(player)) {
      return true;
    }
    User user = UserRepository.userOf(player);
    UserMeta meta = user.meta();
    UserMetaClientData clientData = meta.clientData();
    return clientData.canUseElytra();
  }

  public static boolean isSwimming(User user) {
    UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    if (!clientData.swimmingMechanics()) {
      return false;
    }
    boolean sprinting = movementData.lastSprinting;
    boolean swimming = movementData.pose() == Pose.SWIMMING;
    if (swimming) {
      return sprinting && movementData.inWater;
    } else {
      return sprinting && ((movementData.pose() == Pose.FALL_FLYING && movementData.inWater) || movementData.areEyesInWater());
    }
  }

  public static boolean poseSneaking(User user) {
    UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    UserMetaInventoryData inventoryData = meta.inventoryData();
    boolean sneakingAllowed = movementData.sneaking && !inventoryData.inventoryOpen();
    boolean actualSneaking;
    if (clientData.delayedSneak()) {
      actualSneaking = movementData.lastSneaking;
    } else if (clientData.alternativeSneak()) {
      actualSneaking = movementData.lastSneaking || sneakingAllowed;
    } else {
      actualSneaking = sneakingAllowed;
    }
    return actualSneaking;
  }
}