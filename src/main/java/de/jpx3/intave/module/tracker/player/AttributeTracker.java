package de.jpx3.intave.module.tracker.player;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedAttributeModifier;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
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
  public void sentAttributes(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    if (packet.getIntegers().read(0) == player.getEntityId()) {
      StructureModifier<List<WrappedAttribute>> attributeModifier = packet.getAttributeCollectionModifier();
      List<WrappedAttribute> attributes = patchAttributes(user, attributeModifier.read(0));
      attributeModifier.write(0, attributes);
//      Modules.feedback().synchronize(player, attributes, (player1, target) -> target.forEach(attribute -> receivedAttribute(user, attribute)));
      user.tickFeedback(() -> {
        attributes.forEach(attribute -> receivedAttribute(user, attribute));
      });
    }
  }

//  private final static UUID SPRINTING_MODIFIER_ID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
//  private final static WrappedAttributeModifier SPRINTING_MODIFIER = WrappedAttributeModifier.newBuilder(SPRINTING_MODIFIER_ID).amount(0.3F).operation(ADD_PERCENTAGE).name("Sprint Boost").build();

  private List<WrappedAttribute> patchAttributes(User user, List<WrappedAttribute> attributes) {
//    MetadataBundle meta = user.meta();
//    AbilityMetadata abilities = meta.abilities();
//    MovementMetadata movement = meta.movement();
//    for (int i = 0; i < attributes.size(); i++) {
//      WrappedAttribute attribute = attributes.get(i);
//      boolean sprinting = movement.sprinting;
//      if (abilities.findAttribute(attribute.getAttributeKey()) != null) {
//        boolean setsSprinting = attribute.getModifiers().contains(SPRINTING_MODIFIER);
//        if (sprinting && !setsSprinting) {
//          Set<WrappedAttributeModifier> modifiers = new HashSet<>(attribute.getModifiers());
//          modifiers.add(SPRINTING_MODIFIER);
//          attributes.set(i, attribute.withModifiers(modifiers));
//        } else if (!sprinting && setsSprinting) {
//          Set<WrappedAttributeModifier> modifiers = new HashSet<>(attribute.getModifiers());
//          modifiers.remove(SPRINTING_MODIFIER);
//          attributes.set(i, attribute.withModifiers(modifiers));
//        }
//      }
//    }
    return attributes;
  }

  private void receivedAttribute(User user, WrappedAttribute attribute) {
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    if (abilities.findAttribute(attribute.getAttributeKey()) != null) {
//      System.out.println(attribute.getModifiers());
      List<WrappedAttributeModifier> intaveAttributes = abilities.modifiersOf(attribute);
      intaveAttributes.clear();
      Set<WrappedAttributeModifier> serverAttributes = attribute.getModifiers();
      movement.hasSprintSpeed = serverAttributes.contains(MovementMetadata.SPRINTING_MODIFIER);
      intaveAttributes.addAll(new HashSet<>(serverAttributes));
//      System.out.println(attribute.getAttributeKey());
      abilities.modifyBaseValue(attribute.getAttributeKey(), attribute.getBaseValue());
    }
  }
}
