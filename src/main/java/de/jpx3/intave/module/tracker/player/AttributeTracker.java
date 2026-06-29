package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    if (packet.getIntegers().read(0) == player.getEntityId()) {
      StructureModifier<List<WrappedAttribute>> mod = packet.getAttributeCollectionModifier();
      List<WrappedAttribute> attributes = mod.read(0);
      mod.write(0, attributes);
      user.tickFeedback(() -> {
        attributes.forEach(attribute -> receivedAttribute(user, attribute));
      });
    }
  }

  private void receivedAttribute(User user, WrappedAttribute attribute) {
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    String attributeKey = attribute.getAttributeKey();
    if (abilities.findAttribute(attributeKey) != null) {
      Attribute intaveAttribute = Attribute.fromProtocolLib(attribute);
      List<AttributeModifier> intaveAttributes = abilities.modifiersOf(intaveAttribute);
      intaveAttributes.clear();
      Set<AttributeModifier> serverAttributes = intaveAttribute.modifiers();
      movement.hasSprintSpeed = serverAttributes.contains(MovementMetadata.SPRINTING_MODIFIER);
      intaveAttributes.addAll(new HashSet<>(serverAttributes));
      abilities.modifyBaseValue(attributeKey, attribute.getBaseValue());
    }
  }
}
