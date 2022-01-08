package de.jpx3.intave.check.other.protocolscanner;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;

public final class SkinBlinker extends CheckPart<ProtocolScanner> {
  private final static boolean HAS_OFF_HAND = MinecraftVersions.VER1_9_0.atOrAbove();

  private static Class<?> enumMainHandClass;

  public SkinBlinker(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    ProtocolMetadata clientData = user.meta().protocol();
    if (HAS_OFF_HAND && clientData.combatUpdate()) {
      if (enumMainHandClass == null) {
        enumMainHandClass = Lookup.serverClass("EnumMainHand");
      }
      HandSlot sentHand = packet.getEnumModifier(HandSlot.class, enumMainHandClass).read(0);
      if (!equalHand(player.getMainHand(), sentHand)) {
        return;
      }
    }
    MovementMetadata movementData = user.meta().movement();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    double distanceMoved = Hypot.fast(movementData.motionX(), movementData.motionZ());
    if (movementData.inWeb || movementData.recentlyEncounteredFlyingPacket(2)) {
      return;
    }
    if ((keyForward != 0 || keyStrafe != 0) && distanceMoved > 0.1) {
      event.setCancelled(true);
    }
  }

  private boolean equalHand(Object bukkitHand, HandSlot hand) {
    return bukkitHand == MainHand.LEFT && hand == HandSlot.LEFT
      || bukkitHand == MainHand.RIGHT && hand == HandSlot.RIGHT;
  }

  @KeepEnumInternalNames
  public enum HandSlot {
    LEFT,
    RIGHT
  }
}