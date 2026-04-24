package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.share.PositionMoveRotationConverter;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

@PatchyAutoTranslation
class v214TeleportApplier implements TeleportApplier {
  private final Method internalTeleportMethod;

  public v214TeleportApplier() {
    Class<?> playerConnectionClass = Lookup.serverClass("PlayerConnection");
    try {
      internalTeleportMethod = playerConnectionClass.getDeclaredMethod("internalTeleport", PositionMoveRotationConverter.NATIVE_CLASS, Set.class);
      if (!internalTeleportMethod.isAccessible()) {
        internalTeleportMethod.setAccessible(true);
      }
    } catch (NoSuchMethodException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  @Override
  @PatchyAutoTranslation
  public void teleport(Player player, double posX, double posY, double posZ, float yaw, float pitch, Set<?> relatives) {
    try {
      User user = UserRepository.userOf(player);
      if (!user.hasPlayer()) {
        return;
      }
      Object playerConnection = user.playerConnection();
      PositionMoveRotation posMoveRot = new PositionMoveRotation(
        new Position(posX, posY, posZ),
        new Motion(),
        new Rotation(yaw, pitch)
      );
      Object posMoveRotNative = PositionMoveRotationConverter.toNative(posMoveRot);
      internalTeleportMethod.invoke(playerConnection, posMoveRotNative, relatives);
    } catch (InvocationTargetException | IllegalAccessException e) {
      e.printStackTrace();
    }
  }
}
