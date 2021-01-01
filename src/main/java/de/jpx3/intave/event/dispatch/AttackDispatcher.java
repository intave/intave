package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class AttackDispatcher implements EventProcessor {
  public AttackDispatcher(IntavePlugin plugin) {
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();

    PacketContainer packet = event.getPacket();
    Integer entityId = packet.getIntegers().read(0);
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);

    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      attackData.setLastAttackedEntityID(entityId);
      if (playerAttack(entityId)) {
        movementData.pastPlayerAttackPhysics = 0;
      }
    }
  }

  private boolean playerAttack(Integer entityId) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getEntityId() == entityId) {
        return true;
      }
    }
    return false;
  }
}