package de.jpx3.intave.tools.client;

import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_AQUATIC_UPDATE;

public final class PlayerMovementPoseHelper {
  private final static boolean ELYTRA_ENABLED = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE);

  public static boolean flyingWithElytra(Player player) {
    return ELYTRA_ENABLED && player.isGliding();
  }

  public static boolean isSwimming(Player player) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    boolean canSwim = movementPoseSuitableForSwimming(player);
    return clientData.protocolVersion() >= PROTOCOL_VERSION_AQUATIC_UPDATE && canSwim && movementData.lastSprinting;
  }

  private static boolean movementPoseSuitableForSwimming(Player player) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    return movementData.eyesInWater && movementData.inWater;
  }
}