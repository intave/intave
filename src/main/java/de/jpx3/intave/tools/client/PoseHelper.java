package de.jpx3.intave.tools.client;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.adapter.ProtocolLibraryAdapter;
import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.detect.checks.movement.physics.Pose;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;

@IdoNotBelongHere
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
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    return clientData.canUseElytra();
  }

  public static boolean isSwimming(User user) {
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
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
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    ProtocolMetadata clientData = meta.protocol();
    InventoryMetadata inventoryData = meta.inventory();
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