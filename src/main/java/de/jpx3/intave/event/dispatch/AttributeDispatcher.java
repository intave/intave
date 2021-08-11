package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import com.comphenix.protocol.wrappers.WrappedAttributeModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.event.packet.PacketId.Server.UPDATE_ATTRIBUTES;

public final class AttributeDispatcher implements EventProcessor {
//  private static final UUID SPEED_MODIFIER_SPRINTING_UUID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
  private final IntavePlugin plugin;

  public AttributeDispatcher(IntavePlugin plugin) {
    this.plugin = plugin;
    this.plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    this.plugin.eventLinker().registerEventsIn(this);
  }

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
      plugin.eventService().feedback().singleSynchronize(player, attributes, (player1, target) -> target.forEach(attribute -> receivedAttribute(user, attribute)));
    }
  }

  private List<WrappedAttribute> filterAttributes(List<WrappedAttribute> attributes) {
//    if (ThreadLocalRandom.current().nextBoolean()) {
      return attributes;
//    }
//    if (attributes.isEmpty()) {
//      return Collections.emptyList();
//    }
//    attributes = new ArrayList<>(attributes);
//    for (int i = 0; i < attributes.size(); i++) {
//      WrappedAttribute original = attributes.get(i);
//      List<WrappedAttributeModifier> modifiers = new ArrayList<>(original.getModifiers());
//      modifiers.removeIf(wrappedAttributeModifier -> wrappedAttributeModifier.getUUID().equals(SPEED_MODIFIER_SPRINTING_UUID));
//      attributes.set(i, WrappedAttribute.newBuilder(original).modifiers(modifiers).build());
//    }
//    return attributes;
  }

  private void receivedAttribute(User user, WrappedAttribute attribute) {
    AbilityMetadata abilityData = user.meta().abilities();
    List<WrappedAttributeModifier> modifiers = abilityData.modifiersOf(attribute);
    modifiers.clear();
    modifiers.addAll(attribute.getModifiers());
  }
}
