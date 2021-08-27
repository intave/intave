package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedAttributeModifier;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.UPDATE_ATTRIBUTES;

public final class AttributeTracker extends Module {
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      UPDATE_ATTRIBUTES
    }
  )
  public void sentAttributes(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    if (packet.getEntityModifier(event).read(0) == player) {
      StructureModifier<List<WrappedAttribute>> attributeModifier = packet.getAttributeCollectionModifier();
      List<WrappedAttribute> attributes = filterAttributes(attributeModifier.read(0));
      attributeModifier.write(0, attributes);
      Modules.feedback().synchronize(player, attributes, (player1, target) -> target.forEach(attribute -> receivedAttribute(user, attribute)));
    }
  }

  private List<WrappedAttribute> filterAttributes(List<WrappedAttribute> attributes) {
    return attributes;
  }

  private void receivedAttribute(User user, WrappedAttribute attribute) {
    AbilityMetadata abilityData = user.meta().abilities();
    if (abilityData.findAttribute(attribute.getAttributeKey()) != null) {
      List<WrappedAttributeModifier> modifiers = abilityData.modifiersOf(attribute);
      modifiers.clear();
      modifiers.addAll(attribute.getModifiers());
    }
  }
}
