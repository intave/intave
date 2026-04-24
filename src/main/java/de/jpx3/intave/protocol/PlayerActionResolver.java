package de.jpx3.intave.protocol;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;

public final class PlayerActionResolver {
  private PlayerActionResolver() {
  }

  public static PlayerAction resolveActionFromPacket(WrapperPlayClientEntityAction packet) {
    switch (packet.getAction()) {
      case START_SNEAKING:
        return PlayerAction.START_SNEAKING;
      case STOP_SNEAKING:
        return PlayerAction.STOP_SNEAKING;
      case START_SPRINTING:
        return PlayerAction.START_SPRINTING;
      case STOP_SPRINTING:
        return PlayerAction.STOP_SPRINTING;
      case LEAVE_BED:
        return PlayerAction.STOP_SLEEPING;
      case START_JUMPING_WITH_HORSE:
        return PlayerAction.START_RIDING_JUMP;
      case STOP_JUMPING_WITH_HORSE:
        return PlayerAction.STOP_RIDING_JUMP;
      case OPEN_HORSE_INVENTORY:
        return PlayerAction.OPEN_INVENTORY;
      case START_FLYING_WITH_ELYTRA:
        return PlayerAction.START_FALL_FLYING;
      default:
        throw new IllegalStateException("Unhandled entity action " + packet.getAction());
    }
  }
}
