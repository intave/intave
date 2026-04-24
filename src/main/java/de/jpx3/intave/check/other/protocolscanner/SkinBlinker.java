package de.jpx3.intave.check.other.protocolscanner;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.HumanoidArm;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.ProtocolScanner;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.SETTINGS;

public final class SkinBlinker extends CheckPart<ProtocolScanner> {
  private static final boolean HAS_OFF_HAND = MinecraftVersions.VER1_9_0.atOrAbove();

  public SkinBlinker(ProtocolScanner parentCheck) {
    super(parentCheck);
  }

  @PacketSubscription(
    packetsIn = {
      SETTINGS
    }
  )
  public void receiveClientOptions(ProtocolPacketEvent event, WrapperPlayClientSettings packet) {
    Player player = event.getPlayer();
    User user = userOf(player);

    if (MinecraftVersions.VER1_20_2.atOrAbove()) {
      return;
    }

    ProtocolMetadata clientData = user.meta().protocol();
    if (HAS_OFF_HAND && clientData.combatUpdate()) {
      HandSlot sentHand = handSlotOf(packet.getMainHand());
      if (!equalHand(player.getMainHand(), sentHand)) {
        return;
      }
    }
    MovementMetadata movementData = user.meta().movement();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;
    double distanceMoved = Hypot.fast(movementData.motionX(), movementData.motionZ());
    if (movementData.inWeb || movementData.receivedFlyingPacketIn(2)) {
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

  private HandSlot handSlotOf(HumanoidArm arm) {
    return arm == HumanoidArm.LEFT ? HandSlot.LEFT : HandSlot.RIGHT;
  }

  public enum HandSlot {
    LEFT,
    RIGHT
  }
}
